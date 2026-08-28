package com.example.data.model

import com.example.data.local.entity.ArticleEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.entity.TagEntity

data class ArticleDetail(
    val article: ArticleEntity,
    val category: CategoryEntity?,
    val sections: List<SectionEntity>,
    val tags: List<TagEntity>,
    val relatedArticles: List<ArticleEntity>,
    val isBookmarked: Boolean
)

data class CategoryWithCount(
    val category: CategoryEntity,
    val articleCount: Int
)

data class ContentStats(
    val version: String = "1.0.0",
    val compiledAt: Long = 0L,
    val articles: Int = 0,
    val categories: Int = 0,
    val sections: Int = 0,
    val tags: Int = 0,
    val relations: Int = 0,
    val images: Int = 0,
    val databaseSizeBytes: Long = 0L,
    val databaseSizeFormatted: String = "0 KB",
    val ftsEnabled: Boolean = true
)

data class TableOfContentsItem(
    val id: String,
    val title: String,
    val level: Int,
    val position: Int
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class ReaderFontSize(val titleBn: String, val scaleFactor: Float) {
    SMALL("ছোট", 0.9f),
    MEDIUM("স্বাভাবিক", 1.0f),
    LARGE("বড়", 1.15f),
    EXTRA_LARGE("অনেক বড়", 1.3f)
}
