package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ArticleEntity
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ReadingHistoryEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    fun getArticleById(id: String): Flow<ArticleEntity?>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getArticleByIdDirect(id: String): ArticleEntity?

    @Query("SELECT * FROM sections WHERE articleId = :articleId ORDER BY position ASC")
    fun getSectionsForArticle(articleId: String): Flow<List<SectionEntity>>

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN article_tags at ON t.id = at.tagId
        WHERE at.articleId = :articleId
    """)
    fun getTagsForArticle(articleId: String): Flow<List<TagEntity>>

    @Query("""
        SELECT a.* FROM articles a
        INNER JOIN article_relations ar ON a.id = ar.relatedArticleId
        WHERE ar.articleId = :articleId
    """)
    fun getRelatedArticles(articleId: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE categoryId = :categoryId ORDER BY title ASC")
    fun getArticlesForCategory(categoryId: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles ORDER BY title ASC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT COUNT(*) FROM articles")
    fun getTotalArticlesCount(): Flow<Int>

    @Query("SELECT * FROM articles ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomArticle(): ArticleEntity?

    @Query("SELECT * FROM articles ORDER BY id ASC LIMIT :limit")
    fun getFeaturedArticles(limit: Int = 6): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id IN (:ids)")
    fun getArticlesByIds(ids: List<String>): Flow<List<ArticleEntity>>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE parentId IS NULL OR parentId = '' ORDER BY name ASC")
    fun getRootCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE parentId = :parentId ORDER BY name ASC")
    fun getSubcategories(parentId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    fun getCategoryById(id: String): Flow<CategoryEntity?>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryByIdDirect(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    fun getTotalCategoriesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles WHERE categoryId = :categoryId")
    suspend fun getArticleCountForCategory(categoryId: String): Int
}

@Dao
interface SearchDao {
    @Query("""
        SELECT a.* FROM articles a
        JOIN articles_fts f ON a.id = f.id
        WHERE articles_fts MATCH :query
        LIMIT 50
    """)
    fun searchArticlesFts(query: String): Flow<List<ArticleEntity>>

    @Query("""
        SELECT DISTINCT a.* FROM articles a
        LEFT JOIN article_tags at ON a.id = at.articleId
        LEFT JOIN tags t ON at.tagId = t.id
        WHERE a.title LIKE '%' || :query || '%'
           OR a.summary LIKE '%' || :query || '%'
           OR a.content LIKE '%' || :query || '%'
           OR t.name LIKE '%' || :query || '%'
        ORDER BY 
           CASE WHEN a.title LIKE :query || '%' THEN 1
                WHEN a.title LIKE '%' || :query || '%' THEN 2
                ELSE 3 END,
           a.title ASC
        LIMIT 50
    """)
    fun searchArticlesLike(query: String): Flow<List<ArticleEntity>>
}

@Dao
interface BookmarkDao {
    @Query("""
        SELECT a.* FROM articles a
        INNER JOIN bookmarks b ON a.id = b.articleId
        ORDER BY b.createdAt DESC
    """)
    fun getBookmarkedArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE articleId = :articleId)")
    fun isBookmarked(articleId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE articleId = :articleId")
    suspend fun removeBookmark(articleId: String)

    @Query("DELETE FROM bookmarks")
    suspend fun clearAllBookmarks()
}

@Dao
interface HistoryDao {
    @Query("""
        SELECT a.* FROM articles a
        INNER JOIN reading_history h ON a.id = h.articleId
        ORDER BY h.lastReadAt DESC
        LIMIT :limit
    """)
    fun getReadingHistory(limit: Int = 50): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordHistory(history: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history")
    suspend fun clearHistory()
}

@Dao
interface StatsDao {
    @Query("SELECT COUNT(*) FROM articles")
    suspend fun getArticleCount(): Int

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int

    @Query("SELECT COUNT(*) FROM sections")
    suspend fun getSectionCount(): Int

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun getTagCount(): Int

    @Query("SELECT COUNT(*) FROM bookmarks")
    fun getBookmarkCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM reading_history")
    fun getHistoryCount(): Flow<Int>
}
