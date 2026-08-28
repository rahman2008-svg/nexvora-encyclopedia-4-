package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.BengaliTextNormalizer
import com.example.util.MarkdownParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NexVora", appName)
  }

  @Test
  fun `bengali text normalizer cleans whitespace and punctuation`() {
    val input = "  মহাকর্ষ   বল।  "
    val normalized = BengaliTextNormalizer.normalize(input)
    assertEquals("মহাকর্ষ বল", normalized)
  }

  @Test
  fun `markdown parser parses headings and paragraphs`() {
    val md = "# প্রধান শিরোনাম\n\nএটি একটি সাধারণ অনুচ্ছেদ।"
    val blocks = MarkdownParser.parse(md)
    assertTrue(blocks.isNotEmpty())
  }

  @Test
  fun `open prepackaged database and query articles`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.AppDatabase.getInstance(context)
    val statsDao = db.statsDao()
    kotlinx.coroutines.runBlocking {
      val count = statsDao.getArticleCount()
      assertTrue("Article count should be greater than 0, got $count", count > 0)
    }
  }

  @Test
  fun `database search finds bengali articles via fts or like`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.AppDatabase.getInstance(context)
    val repo = com.example.data.repository.EncyclopediaRepository(db, context)
    runBlocking {
      val results = repo.searchArticles("মহাকর্ষ").first()
      assertTrue("Search for মহাকর্ষ should return results", results.isNotEmpty())
    }
  }

  @Test
  fun `categories and hierarchy are loaded correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.AppDatabase.getInstance(context)
    val categoryDao = db.categoryDao()
    runBlocking {
      val rootCategories = categoryDao.getRootCategories().first()
      assertTrue("Root categories should not be empty", rootCategories.isNotEmpty())
    }
  }

  @Test
  fun `bookmark and history operations persist and retrieve`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.AppDatabase.getInstance(context)
    val bookmarkRepo = com.example.data.repository.BookmarkRepository(db)
    val historyRepo = com.example.data.repository.HistoryRepository(db)

    runBlocking {
      val testArticleId = "gravity"
      bookmarkRepo.toggleBookmark(testArticleId, false)
      val bookmarks = bookmarkRepo.getBookmarkedArticles().first()
      assertTrue("Bookmarked articles should contain test article", bookmarks.any { it.id == testArticleId })

      historyRepo.recordRead(testArticleId)
      val history = historyRepo.getReadingHistory().first()
      assertTrue("Reading history should contain test article", history.any { it.id == testArticleId })
    }
  }
}
