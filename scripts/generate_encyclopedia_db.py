#!/usr/bin/env python3
"""
NexVora Encyclopedia - Build-Time Content Compiler & Database Generator
Recursively scans 'content/', parses Markdown + YAML frontmatter, extracts metadata,
validates integrity, generates normalized SQLite database with FTS, and creates content stats.
"""

import os
import sys
import re
import json
import sqlite3
import hashlib
import time
from pathlib import Path

# Paths relative to project root
PROJECT_ROOT = Path(__file__).resolve().parent.parent
CONTENT_DIR = PROJECT_ROOT / "content"
ASSETS_DIR = PROJECT_ROOT / "app" / "src" / "main" / "assets"
ASSETS_IMAGES_DIR = PROJECT_ROOT / "assets" / "images"
DB_OUTPUT_PATH = ASSETS_DIR / "encyclopedia.db"
STATS_OUTPUT_PATH = ASSETS_DIR / "content-stats.json"

class ContentCompiler:
    def __init__(self, content_dir, output_db_path, output_stats_path):
        self.content_dir = Path(content_dir)
        self.output_db_path = Path(output_db_path)
        self.output_stats_path = Path(output_stats_path)
        self.categories = {} # id -> dict
        self.articles = {}   # id -> dict
        self.sections = []   # list of dict
        self.tags = {}       # name -> id
        self.article_tags = [] # (article_id, tag_id)
        self.relations = []  # (id, article_id, related_id, type)
        self.images = []     # (id, article_id, path, caption, pos)
        self.errors = []
        self.warnings = []

    def log(self, msg):
        print(f"[NexVora Compiler] {msg}")

    def error(self, file_path, msg, line=None, suggested_fix=None):
        self.errors.append({
            "file": str(file_path),
            "error": msg,
            "line": line,
            "suggested_fix": suggested_fix
        })

    def warning(self, file_path, msg):
        self.warnings.append({
            "file": str(file_path),
            "warning": msg
        })

    def slugify(self, text):
        # Normalize to URL/path-friendly slug while supporting Unicode/Bengali
        text = text.strip().lower()
        text = re.sub(r'[\s_]+', '-', text)
        text = re.sub(r'[^\w\-\u0980-\u09FF]', '', text)
        return text.strip('-')

    def parse_front_matter(self, content):
        front_matter = {}
        body = content
        if content.startswith("---"):
            parts = content.split("---", 2)
            if len(parts) >= 3:
                raw_fm = parts[1]
                body = parts[2].strip()
                # Simple robust YAML parser for key-values and lists
                current_key = None
                for line in raw_fm.splitlines():
                    line_str = line.strip()
                    if not line_str or line_str.startswith("#"):
                        continue
                    if line_str.startswith("- ") and current_key:
                        val = line_str[2:].strip().strip('"').strip("'")
                        if isinstance(front_matter[current_key], list):
                            front_matter[current_key].append(val)
                    elif ":" in line:
                        k, v = line.split(":", 1)
                        k = k.strip()
                        v = v.strip().strip('"').strip("'")
                        if not v:
                            front_matter[k] = []
                            current_key = k
                        else:
                            front_matter[k] = v
                            current_key = None
        return front_matter, body

    def extract_sections(self, article_id, body):
        # Extract H2 / H3 headings and their respective text
        lines = body.splitlines()
        sections = []
        current_section = None
        pos = 0

        for line in lines:
            h2_match = re.match(r'^##\s+(.+)$', line)
            h3_match = re.match(r'^###\s+(.+)$', line)
            if h2_match:
                if current_section:
                    sections.append(current_section)
                pos += 1
                current_section = {
                    "id": f"{article_id}_sec_{pos}",
                    "articleId": article_id,
                    "title": h2_match.group(1).strip(),
                    "content": "",
                    "position": pos,
                    "level": 2
                }
            elif h3_match:
                if current_section:
                    sections.append(current_section)
                pos += 1
                current_section = {
                    "id": f"{article_id}_sec_{pos}",
                    "articleId": article_id,
                    "title": h3_match.group(1).strip(),
                    "content": "",
                    "position": pos,
                    "level": 3
                }
            else:
                if current_section:
                    current_section["content"] += line + "\n"
        
        if current_section:
            sections.append(current_section)
        
        # Clean section contents
        for s in sections:
            s["content"] = s["content"].strip()

        return sections

    def extract_summary(self, body, explicit_summary=None):
        if explicit_summary and explicit_summary.strip():
            return explicit_summary.strip()
        # Find first non-empty paragraph that doesn't start with '#'
        lines = body.splitlines()
        para_lines = []
        for line in lines:
            clean = line.strip()
            if not clean:
                if para_lines:
                    break
                continue
            if clean.startswith("#"):
                continue
            para_lines.append(clean)
        
        if para_lines:
            summary = " ".join(para_lines)
            # Remove Markdown links and formatting from summary
            summary = re.sub(r'\[([^\]]+)\]\([^\)]+\)', r'\1', summary)
            summary = re.sub(r'[*_`#]', '', summary)
            return summary[:280] + ("..." if len(summary) > 280 else "")
        return ""

    def extract_title(self, file_path, body, explicit_title=None):
        if explicit_title and explicit_title.strip():
            return explicit_title.strip()
        # Find first H1
        for line in body.splitlines():
            h1_match = re.match(r'^#\s+(.+)$', line.strip())
            if h1_match:
                return h1_match.group(1).strip()
        return file_path.stem

    def discover_categories(self):
        # We will discover and record categories from article directory paths
        # during process_articles to ensure every category has valid hierarchy
        pass

    def process_articles(self):
        seen_titles = {} # title.lower() -> file_path

        for root, dirs, files in os.walk(self.content_dir):
            rel_dir = os.path.relpath(root, self.content_dir)
            
            # Skip hidden directories like .git
            if any(part.startswith(".") for part in Path(rel_dir).parts):
                continue

            for file in sorted(files):
                if not file.endswith(".md"):
                    continue

                # Explicitly ignore authoring templates, guides, and documentation files
                file_upper = file.upper()
                if (file.startswith(".") or 
                    file.startswith("_") or 
                    file_upper in ["ARTICLE_TEMPLATE.MD", "README.MD", "TEMPLATE.MD", "CONTRIBUTING.MD", "GUIDE.MD"] or
                    file.endswith(".template.md")):
                    continue

                file_path = Path(root) / file
                rel_file_path = file_path.relative_to(self.content_dir)
                
                try:
                    with open(file_path, "r", encoding="utf-8") as f:
                        raw_content = f.read()
                except UnicodeDecodeError as e:
                    self.error(rel_file_path, f"Invalid Unicode/UTF-8 encoding in file: {e}", 1, "Save file as UTF-8 encoding")
                    continue
                except Exception as e:
                    self.error(rel_file_path, f"Failed to read file: {e}", 1, "Check file permissions and format")
                    continue

                if not raw_content.strip():
                    self.error(rel_file_path, "Empty article file", 1, "Add article content, front matter, and headings")
                    continue

                fm, body = self.parse_front_matter(raw_content)
                
                # Derive and validate Article ID
                raw_id = fm.get("id") or file_path.stem
                if not raw_id:
                    self.error(rel_file_path, "Missing article ID", 1, "Specify 'id: your-slug' in YAML front-matter")
                    continue

                article_id = self.slugify(str(raw_id))
                if not article_id:
                    self.error(rel_file_path, f"Invalid article ID '{raw_id}'", 1, "Use alphanumeric characters and hyphens for article ID")
                    continue

                if article_id in self.articles:
                    existing_file = self.articles[article_id]["file_path"]
                    self.error(
                        rel_file_path,
                        f"DUPLICATE ARTICLE ID: '{article_id}'! This ID conflicts with file: '{existing_file}'",
                        1,
                        "Change the 'id' in front-matter to a unique slug."
                    )
                    continue

                # Title
                title = self.extract_title(file_path, body, fm.get("title"))
                if not title:
                    self.error(rel_file_path, "Missing title or H1 heading", 1, "Add 'title: ...' in front matter or a '# Title' heading")
                    continue

                title_clean = title.strip()
                title_key = title_clean.lower()
                if title_key in seen_titles:
                    self.warning(
                        rel_file_path,
                        f"Duplicate title '{title_clean}' also found in '{seen_titles[title_key]}'"
                    )
                else:
                    seen_titles[title_key] = rel_file_path

                # Automatic Category & Subcategory Detection from Directory Structure
                if rel_dir == ".":
                    category_id = "general"
                    if category_id not in self.categories:
                        self.categories[category_id] = {
                            "id": category_id,
                            "name": "General",
                            "parentId": None,
                            "path": "General",
                            "depth": 1
                        }
                else:
                    cat_parts = Path(rel_dir).parts
                    path_accum = []
                    parent_id = None
                    for depth, part in enumerate(cat_parts, 1):
                        path_accum.append(part)
                        curr_cat_id = self.slugify("-".join(path_accum))
                        curr_cat_path = " → ".join(path_accum)
                        if curr_cat_id not in self.categories:
                            self.categories[curr_cat_id] = {
                                "id": curr_cat_id,
                                "name": part.replace("-", " "),
                                "parentId": parent_id,
                                "path": curr_cat_path,
                                "depth": depth
                            }
                        parent_id = curr_cat_id
                    category_id = self.slugify("-".join(cat_parts))

                slug = self.slugify(title)
                summary = self.extract_summary(body, fm.get("summary"))
                
                # Hash content
                content_hash = hashlib.sha256(raw_content.encode("utf-8")).hexdigest()

                now_ts = int(time.time() * 1000)
                article_data = {
                    "id": article_id,
                    "title": title,
                    "slug": slug,
                    "summary": summary,
                    "content": body,
                    "categoryId": category_id,
                    "createdAt": now_ts,
                    "updatedAt": now_ts,
                    "contentHash": content_hash,
                    "file_path": rel_file_path,
                    "fm": fm
                }
                self.articles[article_id] = article_data

                # Sections
                secs = self.extract_sections(article_id, body)
                self.sections.extend(secs)

                # Tags
                tags_list = fm.get("tags") or []
                if isinstance(tags_list, str):
                    tags_list = [t.strip() for t in tags_list.split(",")]
                for tag_name in tags_list:
                    tag_name = tag_name.strip()
                    if not tag_name:
                        continue
                    tag_id = self.slugify(tag_name)
                    if tag_id not in self.tags:
                        self.tags[tag_id] = tag_name
                    self.article_tags.append((article_id, tag_id))

                # Relations
                related_list = fm.get("related") or []
                if isinstance(related_list, str):
                    related_list = [r.strip() for r in related_list.split(",")]
                for rel_id in related_list:
                    clean_rel_id = self.slugify(rel_id)
                    rel_record_id = f"{article_id}_{clean_rel_id}"
                    self.relations.append({
                        "id": rel_record_id,
                        "articleId": article_id,
                        "relatedArticleId": clean_rel_id,
                        "relationType": "see_also"
                    })

                # Extract inline internal links `[Text](article:target_id)`
                internal_links = re.findall(r'\[([^\]]+)\]\(article:([^\)]+)\)', body)
                for link_text, target_id in internal_links:
                    clean_target = self.slugify(target_id)
                    rel_record_id = f"{article_id}_{clean_target}"
                    if not any(r["id"] == rel_record_id for r in self.relations):
                        self.relations.append({
                            "id": rel_record_id,
                            "articleId": article_id,
                            "relatedArticleId": clean_target,
                            "relationType": "inline_reference"
                        })

                # Extract images `![Caption](path)`
                img_matches = re.findall(r'!\[([^\]]*)\]\(([^\)]+)\)', body)
                for idx, (caption, img_path) in enumerate(img_matches, 1):
                    img_id = f"{article_id}_img_{idx}"
                    self.images.append({
                        "id": img_id,
                        "articleId": article_id,
                        "path": img_path,
                        "caption": caption,
                        "position": idx
                    })

    def validate(self):
        # Validate internal relations
        for rel in self.relations:
            art_id = rel["articleId"]
            target_id = rel["relatedArticleId"]
            if target_id not in self.articles:
                # Warning rather than hard fail to be author-friendly
                self.warning(self.articles[art_id]["file_path"], f"Related article ID '{target_id}' not found in encyclopedia.")

        # Print validation summary
        total_articles = len(self.articles)
        self.log(f"=== CONTENT VALIDATION REPORT ===")
        self.log(f"Total articles found: {total_articles}")
        self.log(f"Categories discovered: {len(self.categories)}")
        self.log(f"Sections extracted: {len(self.sections)}")
        self.log(f"Tags mapped: {len(self.tags)}")
        self.log(f"Errors: {len(self.errors)}")
        self.log(f"Warnings: {len(self.warnings)}")

        if self.errors:
            print("\nCRITICAL ERRORS:")
            for err in self.errors:
                print(f"FILE: {err['file']}\nERROR: {err['error']}\nLINE: {err.get('line')}\nSUGGESTED FIX: {err.get('suggested_fix')}\n")
            raise ValueError(f"Content compilation failed with {len(self.errors)} errors.")

    def get_room_identity_hash(self):
        # Look for schema JSON generated by Room compiler
        schema_path = PROJECT_ROOT / "app" / "schemas" / "com.example.data.local.AppDatabase" / "1.json"
        if schema_path.exists():
            try:
                with open(schema_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    h = data.get("identityHash")
                    if h:
                        return h
            except Exception as e:
                self.log(f"Warning: Could not read schema JSON: {e}")
        # Default compiled identity hash for AppDatabase version 1
        return "7a87c09037d7f2d92b1f27180be0714f"

    def generate_database(self):
        self.output_db_path.parent.mkdir(parents=True, exist_ok=True)
        if self.output_db_path.exists():
            self.output_db_path.unlink()

        conn = sqlite3.connect(str(self.output_db_path))
        cursor = conn.cursor()

        # Create Schema matching Room Entities and RoomOpenDelegate exactly
        cursor.executescript("""
        CREATE TABLE IF NOT EXISTS `categories` (
            `id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `parentId` TEXT,
            `path` TEXT NOT NULL,
            `depth` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        );
        CREATE INDEX IF NOT EXISTS `index_categories_parentId` ON `categories` (`parentId`);

        CREATE TABLE IF NOT EXISTS `articles` (
            `id` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `slug` TEXT NOT NULL,
            `summary` TEXT NOT NULL,
            `content` TEXT NOT NULL,
            `categoryId` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `contentHash` TEXT NOT NULL,
            PRIMARY KEY(`id`)
        );
        CREATE INDEX IF NOT EXISTS `index_articles_categoryId` ON `articles` (`categoryId`);
        CREATE INDEX IF NOT EXISTS `index_articles_title` ON `articles` (`title`);

        CREATE TABLE IF NOT EXISTS `sections` (
            `id` TEXT NOT NULL,
            `articleId` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `content` TEXT NOT NULL,
            `position` INTEGER NOT NULL,
            `level` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        );
        CREATE INDEX IF NOT EXISTS `index_sections_articleId` ON `sections` (`articleId`);

        CREATE TABLE IF NOT EXISTS `tags` (
            `id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            PRIMARY KEY(`id`)
        );

        CREATE TABLE IF NOT EXISTS `article_tags` (
            `articleId` TEXT NOT NULL,
            `tagId` TEXT NOT NULL,
            PRIMARY KEY(`articleId`, `tagId`)
        );
        CREATE INDEX IF NOT EXISTS `index_article_tags_tagId` ON `article_tags` (`tagId`);

        CREATE TABLE IF NOT EXISTS `article_relations` (
            `id` TEXT NOT NULL,
            `articleId` TEXT NOT NULL,
            `relatedArticleId` TEXT NOT NULL,
            `relationType` TEXT NOT NULL,
            PRIMARY KEY(`id`)
        );
        CREATE INDEX IF NOT EXISTS `index_article_relations_articleId` ON `article_relations` (`articleId`);
        CREATE INDEX IF NOT EXISTS `index_article_relations_relatedArticleId` ON `article_relations` (`relatedArticleId`);

        CREATE TABLE IF NOT EXISTS `bookmarks` (
            `articleId` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            PRIMARY KEY(`articleId`)
        );

        CREATE TABLE IF NOT EXISTS `reading_history` (
            `articleId` TEXT NOT NULL,
            `lastReadAt` INTEGER NOT NULL,
            PRIMARY KEY(`articleId`)
        );

        CREATE TABLE IF NOT EXISTS `article_images` (
            `id` TEXT NOT NULL,
            `articleId` TEXT NOT NULL,
            `path` TEXT NOT NULL,
            `caption` TEXT NOT NULL,
            `position` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        );
        CREATE INDEX IF NOT EXISTS `index_article_images_articleId` ON `article_images` (`articleId`);

        -- FTS4 Full-Text Search Virtual Table matching Room FtsTableInfo
        CREATE VIRTUAL TABLE IF NOT EXISTS `articles_fts` USING FTS4(
            `id` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `summary` TEXT NOT NULL,
            `content` TEXT NOT NULL,
            content=`articles`
        );

        -- Room FTS Content Sync Triggers
        CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_BEFORE_UPDATE BEFORE UPDATE ON `articles` BEGIN DELETE FROM `articles_fts` WHERE `docid`=OLD.`rowid`; END;
        CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_BEFORE_DELETE BEFORE DELETE ON `articles` BEGIN DELETE FROM `articles_fts` WHERE `docid`=OLD.`rowid`; END;
        CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_AFTER_UPDATE AFTER UPDATE ON `articles` BEGIN INSERT INTO `articles_fts`(`docid`, `id`, `title`, `summary`, `content`) VALUES (NEW.`rowid`, NEW.`id`, NEW.`title`, NEW.`summary`, NEW.`content`); END;
        CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_AFTER_INSERT AFTER INSERT ON `articles` BEGIN INSERT INTO `articles_fts`(`docid`, `id`, `title`, `summary`, `content`) VALUES (NEW.`rowid`, NEW.`id`, NEW.`title`, NEW.`summary`, NEW.`content`); END;

        -- Room Master Table
        CREATE TABLE IF NOT EXISTS room_master_table (
            id INTEGER PRIMARY KEY,
            identity_hash TEXT
        );
        """)

        # Insert Room Identity Hash
        identity_hash = self.get_room_identity_hash()
        cursor.execute("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)", (identity_hash,))

        # Insert Categories
        for cat in self.categories.values():
            cursor.execute(
                "INSERT INTO categories (id, name, parentId, path, depth) VALUES (?, ?, ?, ?, ?)",
                (cat["id"], cat["name"], cat["parentId"], cat["path"], cat["depth"])
            )

        # Insert Articles
        for art in self.articles.values():
            cursor.execute(
                "INSERT INTO articles (id, title, slug, summary, content, categoryId, createdAt, updatedAt, contentHash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (art["id"], art["title"], art["slug"], art["summary"], art["content"], art["categoryId"], art["createdAt"], art["updatedAt"], art["contentHash"])
            )

        # Insert Sections
        for sec in self.sections:
            cursor.execute(
                "INSERT INTO sections (id, articleId, title, content, position, level) VALUES (?, ?, ?, ?, ?, ?)",
                (sec["id"], sec["articleId"], sec["title"], sec["content"], sec["position"], sec["level"])
            )

        # Insert Tags
        for tag_id, tag_name in self.tags.items():
            cursor.execute(
                "INSERT INTO tags (id, name) VALUES (?, ?)",
                (tag_id, tag_name)
            )

        # Insert Article-Tags
        for art_id, tag_id in self.article_tags:
            cursor.execute(
                "INSERT OR IGNORE INTO article_tags (articleId, tagId) VALUES (?, ?)",
                (art_id, tag_id)
            )

        # Insert Relations
        for rel in self.relations:
            cursor.execute(
                "INSERT OR IGNORE INTO article_relations (id, articleId, relatedArticleId, relationType) VALUES (?, ?, ?, ?)",
                (rel["id"], rel["articleId"], rel["relatedArticleId"], rel["relationType"])
            )

        # Insert Images
        for img in self.images:
            cursor.execute(
                "INSERT INTO article_images (id, articleId, path, caption, position) VALUES (?, ?, ?, ?, ?)",
                (img["id"], img["articleId"], img["path"], img["caption"], img["position"])
            )

        # Populate FTS4 Index
        cursor.execute("INSERT INTO articles_fts(articles_fts) VALUES('rebuild')")

        # Verify DB integrity and FTS indexing
        cursor.execute("PRAGMA integrity_check")
        integrity_res = cursor.fetchone()
        if integrity_res and integrity_res[0] != "ok":
            raise ValueError(f"SQLite integrity check failed: {integrity_res}")

        cursor.execute("SELECT COUNT(*) FROM articles")
        art_count = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM articles_fts")
        fts_count = cursor.fetchone()[0]
        self.log(f"Database validation: {art_count} articles, {fts_count} FTS indexed entries.")

        conn.commit()
        conn.close()

        db_size_bytes = self.output_db_path.stat().st_size
        self.log(f"Successfully compiled SQLite database: {self.output_db_path} ({db_size_bytes / 1024:.2f} KB)")

        # Generate content-stats.json
        stats_data = {
            "version": "1.0.0",
            "compiledAt": int(time.time() * 1000),
            "articles": len(self.articles),
            "categories": len(self.categories),
            "sections": len(self.sections),
            "tags": len(self.tags),
            "relations": len(self.relations),
            "images": len(self.images),
            "databaseSizeBytes": db_size_bytes,
            "databaseSizeFormatted": f"{db_size_bytes / 1024:.2f} KB",
            "ftsEnabled": True
        }

        with open(self.output_stats_path, "w", encoding="utf-8") as f:
            json.dump(stats_data, f, indent=2, ensure_ascii=False)
        self.log(f"Generated stats report: {self.output_stats_path}")

    def compile(self):
        self.log("Starting NexVora content compilation pipeline...")
        self.discover_categories()
        self.process_articles()
        self.validate()
        self.generate_database()
        self.log("Pipeline finished successfully! ✨")

if __name__ == "__main__":
    compiler = ContentCompiler(CONTENT_DIR, DB_OUTPUT_PATH, STATS_OUTPUT_PATH)
    try:
        compiler.compile()
    except Exception as e:
        print(f"[NexVora Error] {e}", file=sys.stderr)
        sys.exit(1)
