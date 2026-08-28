# NexVora Encyclopedia — Content Repository Guide

Welcome to the **NexVora Content System**! This repository holds all real knowledge articles in structured Markdown format.

## 🚀 How to Add a New Article

1. **Choose or Create a Category Folder:**
   - Browse into `content/<Category>/<Subcategory>/` (e.g., `content/Science/Physics/` or `content/Bangladesh/History/`).
   - If you need a new category or subcategory, simply create the directory! The compiler automatically detects new directories and adds them to the category navigation.

2. **Create a Markdown File:**
   - Create `<article-id>.md` inside the chosen category folder.
   - Example: `content/Science/Physics/superconductivity.md`

3. **Follow the Standard Template:**
   - Open `content/ARTICLE_TEMPLATE.md` to see the recommended structure.
   - Always include the YAML front-matter:
     ```yaml
     ---
     id: superconductivity
     title: অতিপরিবাহিতা ও কোয়ান্টাম ঘটনা
     tags:
       - পদার্থবিজ্ঞান
       - কোয়ান্টাম
       - বিদ্যুৎ
     ---
     ```

4. **Internal Cross-Referencing:**
   - Link to other articles using `[নাম](article:target-article-id)`.
   - Example: `[মহাকর্ষ বল](article:gravity)` or `[পরমাণু](article:atom)`.

5. **Images (Optional):**
   - Place images in `assets/images/<category>/` and link them via:
     `![চিত্রের ক্যাপশন](assets/images/science/diagram.png)`

6. **Commit & Push to GitHub:**
   - Once committed, GitHub Actions automatically:
     - Scans `content/**/*.md`
     - Validates IDs, titles, Unicode, headings, and links
     - Compiles the offline SQLite database (`encyclopedia.db`)
     - Generates the FTS4 search index
     - Runs Android tests and builds the APK!

---

*No Kotlin code or database modifications are needed. The entire encyclopedia is generated automatically from your Markdown files.*
