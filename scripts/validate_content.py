#!/usr/bin/env python3
"""
NexVora Encyclopedia - Standalone Content Validator
Validates article integrity, front matter, headings, internal links, duplicate detection,
Bengali Unicode consistency, and generates comprehensive validation reports.
"""

import sys
from pathlib import Path

# Add scripts directory to path if needed
PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

from generate_encyclopedia_db import ContentCompiler, CONTENT_DIR, DB_OUTPUT_PATH, STATS_OUTPUT_PATH

def main():
    print("==================================================")
    print("   NexVora Encyclopedia - Content Validation Tool")
    print("==================================================")
    compiler = ContentCompiler(CONTENT_DIR, DB_OUTPUT_PATH, STATS_OUTPUT_PATH)
    compiler.discover_categories()
    compiler.process_articles()
    
    try:
        compiler.validate()
        print("\n✅ All articles passed validation successfully!")
        sys.exit(0)
    except Exception as e:
        print(f"\n❌ Validation failed: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
