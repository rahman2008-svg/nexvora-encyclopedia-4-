package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ReaderFontSize
import com.example.ui.components.ArticleCard
import com.example.ui.components.TableOfContentsDialog
import com.example.ui.viewmodel.ArticleViewModel
import com.example.util.RenderMarkdown
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderScreen(
    viewModel: ArticleViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToArticle: (String) -> Unit,
    onNavigateToCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val articleDetail by viewModel.articleDetail.collectAsStateWithLifecycle()
    var showTocDialog by remember { mutableStateOf(false) }
    var currentFontSize by remember { mutableStateOf(ReaderFontSize.MEDIUM) }
    var showFontMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = articleDetail?.article?.title ?: "নিবন্ধ পাঠ",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("reader_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Table of Contents Button
                    IconButton(
                        onClick = { showTocDialog = true },
                        modifier = Modifier.testTag("reader_toc_button")
                    ) {
                        Icon(imageVector = Icons.Default.List, contentDescription = "সূচিপত্র")
                    }

                    // Font Size Adjuster
                    Box {
                        IconButton(onClick = { showFontMenu = true }) {
                            Icon(imageVector = Icons.Default.FormatSize, contentDescription = "অক্ষরের আকার")
                        }
                        DropdownMenu(
                            expanded = showFontMenu,
                            onDismissRequest = { showFontMenu = false }
                        ) {
                            ReaderFontSize.values().forEach { size ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = size.titleBn,
                                            fontWeight = if (currentFontSize == size) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currentFontSize == size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        currentFontSize = size
                                        showFontMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Bookmark Toggle
                    IconButton(
                        onClick = { viewModel.toggleBookmark() },
                        modifier = Modifier.testTag("reader_bookmark_button")
                    ) {
                        val isBookmarked = articleDetail?.isBookmarked == true
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "বুকমার্ক",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Share Button
                    IconButton(
                        onClick = {
                            val art = articleDetail?.article ?: return@IconButton
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, art.title)
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "নেক্সভোরা বিশ্বকোষ থেকে:\n\n${art.title}\n\n${art.summary}\n\n(অফলাইন বিশ্বকোষ)"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "শেয়ার করুন"))
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "শেয়ার")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (articleDetail?.sections?.isNotEmpty() == true) {
                ExtendedFloatingActionButton(
                    onClick = { showTocDialog = true },
                    icon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null) },
                    text = { Text("সূচিপত্র (${articleDetail?.sections?.size})") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (articleDetail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val detail = articleDetail!!
            val article = detail.article
            val category = detail.category

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category Breadcrumb
                if (category != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.clickable { onNavigateToCategory(category.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = category.path,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Article Main Title
                item {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = (26 * currentFontSize.scaleFactor).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Summary Card
                if (article.summary.isNotBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "সংক্ষিপ্ত পরিচিতি",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = article.summary,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = (15 * currentFontSize.scaleFactor).sp,
                                        lineHeight = (22 * currentFontSize.scaleFactor).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Render Main Markdown Body
                item {
                    RenderMarkdown(
                        markdown = article.content,
                        fontSize = currentFontSize,
                        onInternalArticleClick = { targetArticleId ->
                            onNavigateToArticle(targetArticleId)
                        },
                        onExternalLinkClick = {}
                    )
                }

                // Tags Section
                if (detail.tags.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Text(
                                text = "ট্যাগ ও বিষয়সমূহ",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                detail.tags.forEach { tag ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(tag.name) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Tag,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Related Articles
                if (detail.relatedArticles.isNotEmpty()) {
                    item {
                        Text(
                            text = "সম্পর্কিত অন্যান্য নিবন্ধ",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }

                    items(detail.relatedArticles.size) { index ->
                        val related = detail.relatedArticles[index]
                        ArticleCard(
                            article = related,
                            onClick = { onNavigateToArticle(related.id) }
                        )
                    }
                }

                // Bottom padding for FAB
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }

            // Table of Contents Dialog
            if (showTocDialog) {
                TableOfContentsDialog(
                    sections = detail.sections,
                    onSectionClick = { pos ->
                        // Scroll down to section approximation
                        coroutineScope.launch {
                            listState.animateScrollToItem(index = 3 + (pos * 2).coerceAtMost(10))
                        }
                    },
                    onDismiss = { showTocDialog = false }
                )
            }
        }
    }
}
