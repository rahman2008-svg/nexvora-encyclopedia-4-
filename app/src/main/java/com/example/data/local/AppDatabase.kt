package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ArticleDao
import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.HistoryDao
import com.example.data.local.dao.SearchDao
import com.example.data.local.dao.StatsDao
import com.example.data.local.entity.ArticleEntity
import com.example.data.local.entity.ArticleFtsEntity
import com.example.data.local.entity.ArticleImageEntity
import com.example.data.local.entity.ArticleRelationEntity
import com.example.data.local.entity.ArticleTagCrossRef
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ReadingHistoryEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.entity.TagEntity

@Database(
    entities = [
        ArticleEntity::class,
        CategoryEntity::class,
        SectionEntity::class,
        TagEntity::class,
        ArticleTagCrossRef::class,
        ArticleRelationEntity::class,
        BookmarkEntity::class,
        ReadingHistoryEntity::class,
        ArticleImageEntity::class,
        ArticleFtsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun categoryDao(): CategoryDao
    abstract fun searchDao(): SearchDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun statsDao(): StatsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "encyclopedia.db"
                )
                    .createFromAsset("encyclopedia.db")
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
