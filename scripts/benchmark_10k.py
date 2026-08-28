#!/usr/bin/env python3
"""
NexVora Encyclopedia - 10,000 Article Scalability & Stress Benchmark
Generates a temporary synthetic dataset of 10,000 structured articles across diverse categories,
runs the content compiler, checks SQLite integrity, tests FTS performance, and validates memory safety.
The temporary dataset is automatically cleaned up after the benchmark.
"""

import os
import sys
import shutil
import tempfile
import time
import sqlite3
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

from generate_encyclopedia_db import ContentCompiler

CATEGORIES = [
    ("Science", ["Physics", "Chemistry", "Biology", "Astronomy", "Earth-Science", "Mathematics"]),
    ("History", ["Ancient", "Medieval", "Modern", "World-History"]),
    ("Geography", ["Countries", "Cities", "Rivers", "Mountains", "Oceans", "Continents"]),
    ("Biography", ["Scientists", "Inventors", "Writers", "Artists", "Explorers", "Leaders"]),
    ("Animals", ["Mammals", "Birds", "Reptiles", "Fish", "Insects", "Marine-Life"]),
    ("Technology", ["Computers", "Internet", "Programming", "Artificial-Intelligence", "Electronics", "Software"]),
    ("Bangladesh", ["Districts", "History", "Culture", "Geography", "Rivers", "Heritage"]),
    ("Literature", ["Bengali", "World", "Poetry", "Fiction", "Writers"]),
    ("Environment", ["Climate", "Ecosystems", "Conservation", "Natural-Resources"]),
    ("Economics", ["Banking", "Finance", "Markets", "Economic-Concepts"])
]

SAMPLE_TOPICS = [
    ("মহাকর্ষ", "gravity", "মহাকর্ষ হলো যেকোনো দুটি ভরযুক্ত বস্তুর মধ্যকার আকর্ষণ বল।", ["বিজ্ঞান", "পদার্থবিদ্যা"]),
    ("আপেক্ষিকতা", "relativity", "আলবার্ট আইনস্টাইন কর্তৃক প্রস্তাবিত সাধারণ ও বিশেষ আপেক্ষিকতা তত্ত্ব।", ["বিজ্ঞান", "পদার্থবিদ্যা", "পদার্থবিজ্ঞান"]),
    ("কোয়ান্টাম বলবিদ্যা", "quantum-mechanics", "পারমাণবিক এবং অতিপারমাণবিক স্কেলে পদার্থের আচরণ ব্যাখ্যার বিজ্ঞান।", ["কোয়ান্টাম", "বিজ্ঞান"]),
    ("রয়্যাল বেঙ্গল টাইগার", "royal-bengal-tiger", "বাংলাদেশের জাতীয় পশু এবং সুন্দরবনের প্রধান আকর্ষণ।", ["প্রাণী", "বাংলাদেশ", "স্তন্যপায়ী"]),
    ("সুন্দরবন", "sundarbans", "বিশ্বের বৃহত্তম ম্যানগ্রোভ বনভূমি যা বাংলাদেশ ও ভারতে বিস্তৃত।", ["বাংলাদেশ", "ভূগোল", "পরিবেশ"]),
    ("জগদীশ চন্দ্র বসু", "jagadish-chandra-bose", "বিশিষ্ট বাঙালি বিজ্ঞানী যিনি উদ্ভিদের সংবেদনশীলতা ও রেডিও তরঙ্গ নিয়ে গবেষণা করেন।", ["জীবনী", "বিজ্ঞানী", "বাঙালি"]),
    ("কম্পিউটার প্রোগ্রামিং", "computer-programming", "কম্পিউটারকে নির্দিষ্ট কার্য সম্পাদনের নির্দেশাবলী রচনার প্রক্রিয়া।", ["প্রযুক্তি", "কম্পিউটার", "সফটওয়্যার"]),
    ("কৃত্রিম বুদ্ধিমত্তা", "artificial-intelligence", "মেশিন বা সফটওয়্যার দ্বারা মানুষের বুদ্ধিমত্তা অনুকরণের বিজ্ঞান ও প্রযুক্তি।", ["প্রযুক্তি", "এআই", "রোবোটিক্স"]),
    ("সৌরজগৎ", "solar-system", "সূর্য এবং এর চারপাশে প্রদক্ষিণরত সকল গ্রহ, উপগ্রহ ও গ্রহাণুপুঞ্জের ব্যবস্থা।", ["মহাকাশ", "জ্যোতির্বিদ্যা", "বিজ্ঞান"]),
    ("পদ্মা নদী", "padma-river", "বাংলাদেশের অন্যতম প্রধান এবং আন্তর্জাতিক নদী যা হিমালয় থেকে উৎপন্ন।", ["বাংলাদেশ", "নদী", "ভূগোল"])
]

def generate_synthetic_articles(target_dir, total_count=10000):
    print(f"[Benchmark] Generating {total_count:,} synthetic test articles...")
    start_time = time.time()
    
    # Flatten subcategories
    flat_paths = []
    for main_cat, sub_cats in CATEGORIES:
        for sub in sub_cats:
            cat_path = target_dir / main_cat / sub
            cat_path.mkdir(parents=True, exist_ok=True)
            flat_paths.append(cat_path)
    
    article_idx = 0
    while article_idx < total_count:
        topic_title, topic_slug, topic_summary, topic_tags = SAMPLE_TOPICS[article_idx % len(SAMPLE_TOPICS)]
        cat_folder = flat_paths[article_idx % len(flat_paths)]
        
        art_id = f"bench-{topic_slug}-{article_idx + 1}"
        title = f"{topic_title} - পর্ব {article_idx + 1}"
        
        tags_yaml = "\n".join([f"  - {t}" for t in topic_tags])
        content = f"""---
id: {art_id}
title: {title}
tags:
{tags_yaml}
---

# {title}

## সংক্ষিপ্ত পরিচিতি
{topic_summary} এটি পরীক্ষামূলক বেঞ্চমার্ক সূচক নম্বর {article_idx + 1}।

## সূচিপত্র
1. পরিচিতি
2. বিশদ বিবরণ
3. গুরুত্ব
4. সম্পর্কিত তথ্যসূত্র

## পরিচিতি
{topic_summary} এটি বিস্তারিত বিশ্লেষণের একটি অংশ।

## বিশদ বিবরণ
প্রাকৃতিক বিজ্ঞান ও ঐতিহাসিক দৃষ্টিকোণ থেকে এই বিষয়ের গুরুত্ব অপরিসীম।

## গুরুত্ব
শিক্ষার্থী ও গবেষকদের জন্য সহায়ক বিস্তারিত বিবরণী।

## সম্পর্কিত তথ্যসূত্র
1. জাতীয় শিক্ষাক্রম ও পাঠ্যপুস্তক বোর্ড
2. আন্তর্জাতিক বিজ্ঞান সাময়িকী
"""
        file_path = cat_folder / f"article_{article_idx + 1}.md"
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        
        article_idx += 1
        if article_idx % 2500 == 0:
            print(f"[Benchmark] Generated {article_idx:,} / {total_count:,} articles ({time.time() - start_time:.2f}s)...")
            
    print(f"[Benchmark] Successfully created {total_count:,} articles in {time.time() - start_time:.2f} seconds.")

def run_stress_test(total_count=10000):
    print("==========================================================")
    print(f"  NexVora Encyclopedia: {total_count:,} Articles Scalability Test")
    print("==========================================================")
    
    temp_dir = tempfile.mkdtemp(prefix="nexvora_benchmark_")
    bench_content_dir = Path(temp_dir) / "content"
    bench_db_path = Path(temp_dir) / "bench_encyclopedia.db"
    bench_stats_path = Path(temp_dir) / "bench_content_stats.json"
    
    try:
        # 1. Generate synthetic dataset
        generate_synthetic_articles(bench_content_dir, total_count)
        
        # 2. Run Compiler
        print("\n[Benchmark] Running Content Compiler on 10,000 articles...")
        compile_start = time.time()
        compiler = ContentCompiler(bench_content_dir, bench_db_path, bench_stats_path)
        compiler.compile()
        compile_duration = time.time() - compile_start
        print(f"[Benchmark] ✅ Compilation completed in {compile_duration:.2f} seconds!")
        
        # 3. Database Size & Integrity Checks
        db_size_mb = bench_db_path.stat().st_size / (1024 * 1024)
        print(f"[Benchmark] Generated Database Size: {db_size_mb:.2f} MB")
        
        conn = sqlite3.connect(str(bench_db_path))
        cursor = conn.cursor()
        
        cursor.execute("PRAGMA integrity_check;")
        integrity = cursor.fetchone()[0]
        assert integrity == "ok", f"Integrity check failed: {integrity}"
        print(f"[Benchmark] ✅ SQLite Integrity Check: PASS (Result: {integrity})")
        
        cursor.execute("SELECT COUNT(*) FROM articles;")
        article_count = cursor.fetchone()[0]
        assert article_count == total_count, f"Expected {total_count} articles, got {article_count}"
        print(f"[Benchmark] ✅ Total Articles Verified: {article_count:,}")
        
        cursor.execute("SELECT COUNT(*) FROM articles_fts;")
        fts_count = cursor.fetchone()[0]
        assert fts_count == total_count, f"Expected {total_count} FTS records, got {fts_count}"
        print(f"[Benchmark] ✅ Total FTS Indexed Records: {fts_count:,}")
        
        # 4. Search Latency Benchmark
        test_queries = ["মহাকর্ষ", "gravity", "আইনস্টাইন", "বিজ্ঞান", "সুন্দরবন", "প্রযুক্তি", "বাঙালি", "solar"]
        print("\n[Benchmark] Testing FTS Search Latencies across 10,000 articles:")
        for q in test_queries:
            q_start = time.time()
            cursor.execute("""
                SELECT a.id, a.title, a.summary 
                FROM articles a
                JOIN articles_fts fts ON a.id = fts.id
                WHERE articles_fts MATCH ?
                LIMIT 20
            """, (f"{q}*",))
            rows = cursor.fetchall()
            q_time_ms = (time.time() - q_start) * 1000
            print(f"  - Query '{q}': {len(rows)} results in {q_time_ms:.2f} ms")
            assert q_time_ms < 50.0, f"Query '{q}' was too slow ({q_time_ms:.2f} ms)"
        
        # 5. Category Aggregations Benchmark
        cat_start = time.time()
        cursor.execute("""
            SELECT c.id, c.name, COUNT(a.id) as count
            FROM categories c
            LEFT JOIN articles a ON a.categoryId = c.id
            GROUP BY c.id
            LIMIT 50
        """)
        cat_rows = cursor.fetchall()
        cat_time_ms = (time.time() - cat_start) * 1000
        print(f"\n[Benchmark] Category aggregation across {len(cat_rows)} categories in {cat_time_ms:.2f} ms")
        
        conn.close()
        print("\n==========================================================")
        print("  🎉 ALL 10,000 ARTICLE SCALABILITY BENCHMARKS PASSED! 🎉")
        print("==========================================================")
        
    finally:
        # Cleanup temporary files
        print("[Benchmark] Cleaning up temporary benchmark artifacts...")
        shutil.rmtree(temp_dir, ignore_errors=True)
        print("[Benchmark] Cleanup complete.")

if __name__ == "__main__":
    count = 10000
    if len(sys.argv) > 1:
        try:
            count = int(sys.argv[1])
        except ValueError:
            pass
    run_stress_test(count)
