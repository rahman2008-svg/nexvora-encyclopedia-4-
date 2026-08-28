package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4(contentEntity = ArticleEntity::class)
@Entity(tableName = "articles_fts")
data class ArticleFtsEntity(
    @PrimaryKey
    val rowid: Int,
    val id: String,
    val title: String,
    val summary: String,
    val content: String
)
