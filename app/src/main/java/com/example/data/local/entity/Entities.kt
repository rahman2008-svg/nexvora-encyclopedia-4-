package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["title"])
    ]
)
data class ArticleEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val slug: String,
    val summary: String,
    val content: String,
    val categoryId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val contentHash: String
)

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["parentId"])
    ]
)
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val parentId: String?,
    val path: String,
    val depth: Int
)

@Entity(
    tableName = "sections",
    indices = [
        Index(value = ["articleId"])
    ]
)
data class SectionEntity(
    @PrimaryKey
    val id: String,
    val articleId: String,
    val title: String,
    val content: String,
    val position: Int,
    val level: Int
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey
    val id: String,
    val name: String
)

@Entity(
    tableName = "article_tags",
    primaryKeys = ["articleId", "tagId"],
    indices = [
        Index(value = ["tagId"])
    ]
)
data class ArticleTagCrossRef(
    val articleId: String,
    val tagId: String
)

@Entity(
    tableName = "article_relations",
    indices = [
        Index(value = ["articleId"]),
        Index(value = ["relatedArticleId"])
    ]
)
data class ArticleRelationEntity(
    @PrimaryKey
    val id: String,
    val articleId: String,
    val relatedArticleId: String,
    val relationType: String
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey
    val articleId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reading_history")
data class ReadingHistoryEntity(
    @PrimaryKey
    val articleId: String,
    val lastReadAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "article_images",
    indices = [
        Index(value = ["articleId"])
    ]
)
data class ArticleImageEntity(
    @PrimaryKey
    val id: String,
    val articleId: String,
    val path: String,
    val caption: String,
    val position: Int
)
