package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ArticleEntity
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ReadingHistoryEntity
import com.example.data.model.ArticleDetail
import com.example.data.model.CategoryWithCount
import com.example.data.model.ContentStats
import com.example.util.BengaliTextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Calendar

class EncyclopediaRepository(
    private val database: AppDatabase,
    private val context: Context
) {
    private val articleDao = database.articleDao()
    private val categoryDao = database.categoryDao()
    private val searchDao = database.searchDao()
    private val bookmarkDao = database.bookmarkDao()
    private val statsDao = database.statsDao()

    fun getArticleDetail(articleId: String): Flow<ArticleDetail?> {
        val articleFlow = articleDao.getArticleById(articleId)
        val sectionsFlow = articleDao.getSectionsForArticle(articleId)
        val tagsFlow = articleDao.getTagsForArticle(articleId)
        val relatedFlow = articleDao.getRelatedArticles(articleId)
        val isBookmarkedFlow = bookmarkDao.isBookmarked(articleId)

        return combine(
            articleFlow,
            sectionsFlow,
            tagsFlow,
            relatedFlow,
            isBookmarkedFlow
        ) { article, sections, tags, related, isBookmarked ->
            if (article == null) null
            else {
                val category = if (article.categoryId.isNotBlank()) {
                    categoryDao.getCategoryByIdDirect(article.categoryId)
                } else null
                ArticleDetail(
                    article = article,
                    category = category,
                    sections = sections,
                    tags = tags,
                    relatedArticles = related,
                    isBookmarked = isBookmarked
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    fun getFeaturedArticles(limit: Int = 6): Flow<List<ArticleEntity>> {
        return articleDao.getFeaturedArticles(limit)
    }

    fun getArticlesByCategory(categoryId: String): Flow<List<ArticleEntity>> {
        return articleDao.getArticlesForCategory(categoryId)
    }

    suspend fun getRandomArticle(): ArticleEntity? {
        return withContext(Dispatchers.IO) {
            articleDao.getRandomArticle()
        }
    }

    /**
     * Deterministic daily article selection based on calendar day.
     * Guaranteed to produce the exact same article throughout the entire calendar day offline.
     */
    fun getDailyArticle(): Flow<ArticleEntity?> = flow {
        val all = withContext(Dispatchers.IO) {
            // Read from DB
            database.articleDao().getAllArticles()
        }
        all.collect { articles ->
            if (articles.isEmpty()) {
                emit(null)
            } else {
                val cal = Calendar.getInstance()
                val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
                val year = cal.get(Calendar.YEAR)
                val seed = (year * 366 + dayOfYear)
                val selectedIndex = kotlin.math.abs(seed) % articles.size
                emit(articles[selectedIndex])
            }
        }
    }.flowOn(Dispatchers.IO)

    fun searchArticles(query: String): Flow<List<ArticleEntity>> {
        val normalized = BengaliTextNormalizer.normalize(query)
        if (normalized.isBlank()) {
            return flow { emit(emptyList()) }
        }
        val ftsQuery = BengaliTextNormalizer.prepareFtsQuery(query)
        return flow {
            // First attempt FTS search, fallback to LIKE query
            try {
                if (ftsQuery.isNotBlank()) {
                    searchDao.searchArticlesFts(ftsQuery).collect { ftsResults ->
                        if (ftsResults.isNotEmpty()) {
                            emit(ftsResults)
                        } else {
                            searchDao.searchArticlesLike(normalized).collect { likeResults ->
                                emit(likeResults)
                            }
                        }
                    }
                } else {
                    searchDao.searchArticlesLike(normalized).collect { likeResults ->
                        emit(likeResults)
                    }
                }
            } catch (e: Exception) {
                // Fallback to LIKE on any FTS syntax edge case
                searchDao.searchArticlesLike(normalized).collect { likeResults ->
                    emit(likeResults)
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    fun getRootCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getRootCategories()
    }

    fun getAllCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getAllCategories()
    }

    fun getSubcategories(parentId: String): Flow<List<CategoryEntity>> {
        return categoryDao.getSubcategories(parentId)
    }

    fun getCategoryById(id: String): Flow<CategoryEntity?> {
        return categoryDao.getCategoryById(id)
    }

    fun getCategoryWithCounts(): Flow<List<CategoryWithCount>> = flow {
        categoryDao.getAllCategories().collect { categories ->
            val listWithCounts = withContext(Dispatchers.IO) {
                categories.map { cat ->
                    val count = categoryDao.getArticleCountForCategory(cat.id)
                    CategoryWithCount(cat, count)
                }
            }
            emit(listWithCounts)
        }
    }.flowOn(Dispatchers.IO)

    fun getTotalCounts(): Flow<Pair<Int, Int>> {
        return combine(
            articleDao.getTotalArticlesCount(),
            categoryDao.getTotalCategoriesCount()
        ) { articles, categories ->
            Pair(articles, categories)
        }
    }

    fun getContentStats(): Flow<ContentStats> = flow {
        try {
            val inputStream = context.assets.open("content-stats.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonStr = reader.use { it.readText() }
            val json = JSONObject(jsonStr)
            val stats = ContentStats(
                version = json.optString("version", "1.0.0"),
                compiledAt = json.optLong("compiledAt", 0L),
                articles = json.optInt("articles", 0),
                categories = json.optInt("categories", 0),
                sections = json.optInt("sections", 0),
                tags = json.optInt("tags", 0),
                relations = json.optInt("relations", 0),
                images = json.optInt("images", 0),
                databaseSizeBytes = json.optLong("databaseSizeBytes", 0L),
                databaseSizeFormatted = json.optString("databaseSizeFormatted", "0 KB"),
                ftsEnabled = json.optBoolean("ftsEnabled", true)
            )
            emit(stats)
        } catch (e: Exception) {
            val articleCount = withContext(Dispatchers.IO) { statsDao.getArticleCount() }
            val catCount = withContext(Dispatchers.IO) { statsDao.getCategoryCount() }
            val secCount = withContext(Dispatchers.IO) { statsDao.getSectionCount() }
            val tagCount = withContext(Dispatchers.IO) { statsDao.getTagCount() }
            emit(
                ContentStats(
                    version = "1.0.0",
                    articles = articleCount,
                    categories = catCount,
                    sections = secCount,
                    tags = tagCount
                )
            )
        }
    }.flowOn(Dispatchers.IO)
}

class BookmarkRepository(private val database: AppDatabase) {
    private val bookmarkDao = database.bookmarkDao()

    fun getBookmarkedArticles(): Flow<List<ArticleEntity>> = bookmarkDao.getBookmarkedArticles()

    suspend fun toggleBookmark(articleId: String, currentStatus: Boolean) {
        withContext(Dispatchers.IO) {
            if (currentStatus) {
                bookmarkDao.removeBookmark(articleId)
            } else {
                bookmarkDao.addBookmark(BookmarkEntity(articleId = articleId))
            }
        }
    }

    suspend fun clearAllBookmarks() {
        withContext(Dispatchers.IO) {
            bookmarkDao.clearAllBookmarks()
        }
    }
}

class HistoryRepository(private val database: AppDatabase) {
    private val historyDao = database.historyDao()

    fun getReadingHistory(): Flow<List<ArticleEntity>> = historyDao.getReadingHistory()

    suspend fun recordRead(articleId: String) {
        withContext(Dispatchers.IO) {
            historyDao.recordHistory(
                ReadingHistoryEntity(
                    articleId = articleId,
                    lastReadAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            historyDao.clearHistory()
        }
    }
}
