package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.viewmodel.*

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : Screen("home", "হোম", Icons.Filled.Home, Icons.Outlined.Home)
    object Categories : Screen("categories", "বিষয়শ্রেণী", Icons.Filled.Category, Icons.Outlined.Category)
    object Bookmarks : Screen("bookmarks", "বুকমার্ক", Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks)
    object History : Screen("history", "ইতিহাস", Icons.Filled.History, Icons.Outlined.History)
    object Settings : Screen("settings", "সেটিংস", Icons.Filled.Settings, Icons.Outlined.Settings)

    object Search : Screen("search", "অনুসন্ধান", Icons.Filled.Home, Icons.Outlined.Home)
    object ArticleReader : Screen("article/{articleId}", "নিবন্ধ", Icons.Filled.Home, Icons.Outlined.Home) {
        fun createRoute(articleId: String) = "article/$articleId"
    }
    object CategoryDetail : Screen("category/{categoryId}", "বিষয়", Icons.Filled.Category, Icons.Outlined.Category) {
        fun createRoute(categoryId: String) = "category/$categoryId"
    }
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Categories,
    Screen.Bookmarks,
    Screen.History,
    Screen.Settings
)

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isBottomBarVisible = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (isBottomBarVisible) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            modifier = Modifier.testTag("nav_item_${screen.route}")
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                    onNavigateToArticle = { id -> navController.navigate(Screen.ArticleReader.createRoute(id)) },
                    onNavigateToCategories = { navController.navigate(Screen.Categories.route) },
                    onNavigateToCategoryDetail = { catId -> navController.navigate(Screen.CategoryDetail.createRoute(catId)) },
                    onNavigateToBookmarks = { navController.navigate(Screen.Bookmarks.route) },
                    onNavigateToHistory = { navController.navigate(Screen.History.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(Screen.Categories.route) {
                val categoryViewModel: CategoryViewModel = viewModel()
                CategoriesScreen(
                    viewModel = categoryViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCategoryDetail = { catId -> navController.navigate(Screen.CategoryDetail.createRoute(catId)) },
                    onNavigateToSearch = { navController.navigate(Screen.Search.route) }
                )
            }

            composable(
                route = Screen.CategoryDetail.route,
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
            ) { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getString("categoryId") ?: ""
                val categoryViewModel: CategoryViewModel = viewModel()
                CategoryArticlesScreen(
                    categoryId = categoryId,
                    viewModel = categoryViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToArticle = { id -> navController.navigate(Screen.ArticleReader.createRoute(id)) },
                    onNavigateToSubcategory = { subId -> navController.navigate(Screen.CategoryDetail.createRoute(subId)) }
                )
            }

            composable(Screen.Search.route) {
                val searchViewModel: SearchViewModel = viewModel()
                SearchScreen(
                    viewModel = searchViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToArticle = { id -> navController.navigate(Screen.ArticleReader.createRoute(id)) }
                )
            }

            composable(
                route = Screen.ArticleReader.route,
                arguments = listOf(navArgument("articleId") { type = NavType.StringType })
            ) { backStackEntry ->
                val articleId = backStackEntry.arguments?.getString("articleId") ?: ""
                val articleViewModel: ArticleViewModel = viewModel(
                    key = articleId,
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            val app = backStackEntry.destination.let {
                                navController.context.applicationContext as android.app.Application
                            }
                            return ArticleViewModel(app, articleId) as T
                        }
                    }
                )
                ArticleReaderScreen(
                    viewModel = articleViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToArticle = { nextId ->
                        navController.navigate(Screen.ArticleReader.createRoute(nextId))
                    },
                    onNavigateToCategory = { catId ->
                        navController.navigate(Screen.CategoryDetail.createRoute(catId))
                    }
                )
            }

            composable(Screen.Bookmarks.route) {
                val bookmarksViewModel: BookmarksViewModel = viewModel()
                BookmarksScreen(
                    viewModel = bookmarksViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToArticle = { id -> navController.navigate(Screen.ArticleReader.createRoute(id)) }
                )
            }

            composable(Screen.History.route) {
                val historyViewModel: HistoryViewModel = viewModel()
                HistoryScreen(
                    viewModel = historyViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToArticle = { id -> navController.navigate(Screen.ArticleReader.createRoute(id)) }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
