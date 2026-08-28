package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ArticleEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.model.*
import com.example.data.repository.BookmarkRepository
import com.example.data.repository.EncyclopediaRepository
import com.example.data.repository.HistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = EncyclopediaRepository(database, application)
    private val historyRepository = HistoryRepository(database)

    val featuredArticles: StateFlow<List<ArticleEntity>> = repository.getFeaturedArticles(8)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyArticle: StateFlow<ArticleEntity?> = repository.getDailyArticle()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categories: StateFlow<List<CategoryWithCount>> = repository.getCategoryWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCounts: StateFlow<Pair<Int, Int>> = repository.getTotalCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0, 0))

    val recentArticles: StateFlow<List<ArticleEntity>> = historyRepository.getReadingHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openRandomArticle(onFound: (String) -> Unit) {
        viewModelScope.launch {
            val random = repository.getRandomArticle()
            if (random != null) {
                onFound(random.id)
            }
        }
    }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = EncyclopediaRepository(database, application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchHistory = MutableStateFlow(listOf("মহাকর্ষ", "গতি", "ডিএনএ", "কম্পিউটার", "মুক্তিযুদ্ধ"))
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    val searchResults: StateFlow<List<ArticleEntity>> = _query
        .debounce(200)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList())
            else repository.searchArticles(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(newQuery: String) {
        _query.value = newQuery
    }

    fun submitSearch(term: String) {
        val trimmed = term.trim()
        if (trimmed.isNotBlank()) {
            val current = _searchHistory.value.toMutableList()
            current.remove(trimmed)
            current.add(0, trimmed)
            _searchHistory.value = current.take(8)
            _query.value = trimmed
        }
    }

    fun clearHistory() {
        _searchHistory.value = emptyList()
    }
}

class CategoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = EncyclopediaRepository(database, application)

    val categories: StateFlow<List<CategoryEntity>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoriesWithCounts: StateFlow<List<CategoryWithCount>> = repository.getCategoryWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getCategoryArticles(categoryId: String): Flow<List<ArticleEntity>> {
        return repository.getArticlesByCategory(categoryId)
    }

    fun getCategoryById(categoryId: String): Flow<CategoryEntity?> {
        return repository.getCategoryById(categoryId)
    }

    fun getSubcategories(categoryId: String): Flow<List<CategoryEntity>> {
        return repository.getSubcategories(categoryId)
    }
}

class ArticleViewModel(
    application: Application,
    val articleId: String
) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = EncyclopediaRepository(database, application)
    private val bookmarkRepository = BookmarkRepository(database)
    private val historyRepository = HistoryRepository(database)

    val articleDetail: StateFlow<ArticleDetail?> = repository.getArticleDetail(articleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        recordRead()
    }

    fun recordRead() {
        viewModelScope.launch {
            historyRepository.recordRead(articleId)
        }
    }

    fun toggleBookmark() {
        val current = articleDetail.value ?: return
        viewModelScope.launch {
            bookmarkRepository.toggleBookmark(articleId, current.isBookmarked)
        }
    }
}

class BookmarksViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val bookmarkRepository = BookmarkRepository(database)

    val bookmarks: StateFlow<List<ArticleEntity>> = bookmarkRepository.getBookmarkedArticles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeBookmark(articleId: String) {
        viewModelScope.launch {
            bookmarkRepository.toggleBookmark(articleId, true)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            bookmarkRepository.clearAllBookmarks()
        }
    }
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val historyRepository = HistoryRepository(database)

    val history: StateFlow<List<ArticleEntity>> = historyRepository.getReadingHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = EncyclopediaRepository(database, application)
    private val bookmarkRepository = BookmarkRepository(database)
    private val historyRepository = HistoryRepository(database)

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _fontSize = MutableStateFlow(ReaderFontSize.MEDIUM)
    val fontSize: StateFlow<ReaderFontSize> = _fontSize.asStateFlow()

    val contentStats: StateFlow<ContentStats> = repository.getContentStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContentStats())

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun setFontSize(size: ReaderFontSize) {
        _fontSize.value = size
    }

    fun clearAllBookmarks() {
        viewModelScope.launch {
            bookmarkRepository.clearAllBookmarks()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }
}
