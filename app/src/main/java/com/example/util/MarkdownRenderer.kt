package com.example.util

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReaderFontSize

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class NumberedList(val items: List<String>) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class CodeBlock(val code: String, val language: String = "") : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
}

object MarkdownParser {
    fun parse(rawMarkdown: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = rawMarkdown.split("\n")
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Skip empty lines
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Code Block
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // skip closing ```
                blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), lang))
                continue
            }

            // Headings
            if (trimmed.startsWith("#")) {
                val level = trimmed.takeWhile { it == '#' }.length
                val text = trimmed.drop(level).trim()
                if (level in 1..6) {
                    blocks.add(MarkdownBlock.Heading(level, text))
                    i++
                    continue
                }
            }

            // Horizontal Rule
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                blocks.add(MarkdownBlock.HorizontalRule)
                i++
                continue
            }

            // Blockquote
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trim())
                    i++
                }
                blocks.add(MarkdownBlock.Blockquote(quoteLines.joinToString("\n")))
                continue
            }

            // Bullet List
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                val listItems = mutableListOf<String>()
                while (i < lines.size && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* "))) {
                    listItems.add(lines[i].trim().substring(2).trim())
                    i++
                }
                blocks.add(MarkdownBlock.BulletList(listItems))
                continue
            }

            // Numbered List
            val numListMatch = Regex("^\\d+\\.\\s+(.*)").find(trimmed)
            if (numListMatch != null) {
                val listItems = mutableListOf<String>()
                while (i < lines.size) {
                    val match = Regex("^\\d+\\.\\s+(.*)").find(lines[i].trim())
                    if (match != null) {
                        listItems.add(match.groupValues[1].trim())
                        i++
                    } else {
                        break
                    }
                }
                blocks.add(MarkdownBlock.NumberedList(listItems))
                continue
            }

            // Table check
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                if (tableLines.size >= 2) {
                    val headers = tableLines[0].split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    val rowLines = tableLines.drop(if (tableLines[1].contains("---")) 2 else 1)
                    val rows = rowLines.map { r ->
                        r.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    }
                    blocks.add(MarkdownBlock.Table(headers, rows))
                    continue
                }
            }

            // Regular Paragraph
            val paraLines = mutableListOf<String>()
            while (i < lines.size) {
                val cur = lines[i].trim()
                if (cur.isEmpty() || cur.startsWith("#") || cur.startsWith("```") ||
                    cur.startsWith(">") || cur.startsWith("- ") || cur.startsWith("* ") ||
                    Regex("^\\d+\\.\\s+").containsMatchIn(cur) || (cur.startsWith("|") && cur.endsWith("|"))
                ) {
                    break
                }
                paraLines.add(lines[i])
                i++
            }
            if (paraLines.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString(" ")))
            }
        }

        return blocks
    }
}

@Composable
fun RenderMarkdown(
    markdown: String,
    fontSize: ReaderFontSize = ReaderFontSize.MEDIUM,
    onInternalArticleClick: (String) -> Unit = {},
    onExternalLinkClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val blocks = MarkdownParser.parse(markdown)
    val scale = fontSize.scaleFactor

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy((12 * scale).dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineLarge.copy(
                            fontSize = (26 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        2 -> MaterialTheme.typography.titleLarge.copy(
                            fontSize = (21 * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        3 -> MaterialTheme.typography.titleMedium.copy(
                            fontSize = (18 * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        else -> MaterialTheme.typography.titleSmall.copy(
                            fontSize = (16 * scale).sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = block.text,
                        style = style,
                        modifier = Modifier.padding(top = (8 * scale).dp, bottom = (4 * scale).dp)
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    RichFormattedText(
                        rawText = block.text,
                        scale = scale,
                        onInternalArticleClick = onInternalArticleClick,
                        onExternalLinkClick = onExternalLinkClick
                    )
                }

                is MarkdownBlock.BulletList -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy((6 * scale).dp),
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        block.items.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "• ",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (16 * scale).sp,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                RichFormattedText(
                                    rawText = item,
                                    scale = scale,
                                    onInternalArticleClick = onInternalArticleClick,
                                    onExternalLinkClick = onExternalLinkClick
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.NumberedList -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy((6 * scale).dp),
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        block.items.forEachIndexed { idx, item ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${idx + 1}. ",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = (15 * scale).sp,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                RichFormattedText(
                                    rawText = item,
                                    scale = scale,
                                    onInternalArticleClick = onInternalArticleClick,
                                    onExternalLinkClick = onExternalLinkClick
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = (15 * scale).sp,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = (13 * scale).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = (18 * scale).sp
                        )
                    }
                }

                is MarkdownBlock.Table -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    ) {
                        // Headers
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                .padding(8.dp)
                        ) {
                            block.headers.forEach { h ->
                                Text(
                                    text = h,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (14 * scale).sp,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        // Rows
                        block.rows.forEachIndexed { rIdx, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (rIdx % 2 == 0) Color.Transparent
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    )
                                    .padding(8.dp)
                            ) {
                                row.forEach { cell ->
                                    Text(
                                        text = cell,
                                        fontSize = (13 * scale).sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = (8 * scale).dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
fun RichFormattedText(
    rawText: String,
    scale: Float,
    onInternalArticleClick: (String) -> Unit,
    onExternalLinkClick: (String) -> Unit
) {
    val annotatedString = buildAnnotatedString {
        var cursor = 0
        val linkRegex = Regex("\\[([^\\]]+)\\]\\(([^\\)]+)\\)")
        val boldRegex = Regex("\\*\\*([^*]+)\\*\\*")
        val codeRegex = Regex("`([^`]+)`")

        // Parse links and formatting
        val matches = linkRegex.findAll(rawText).toList()

        if (matches.isEmpty()) {
            appendFormattedPlain(rawText, scale)
        } else {
            for (match in matches) {
                val start = match.range.first
                val end = match.range.last + 1

                if (start > cursor) {
                    appendFormattedPlain(rawText.substring(cursor, start), scale)
                }

                val linkLabel = match.groupValues[1]
                val linkTarget = match.groupValues[2]

                val linkStart = length
                append(linkLabel)
                val linkEnd = length

                if (linkTarget.startsWith("article:")) {
                    val articleId = linkTarget.removePrefix("article:")
                    addStringAnnotation(
                        tag = "ARTICLE_LINK",
                        annotation = articleId,
                        start = linkStart,
                        end = linkEnd
                    )
                    addStyle(
                        style = SpanStyle(
                            color = Color(0xFF1E88E5),
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = linkStart,
                        end = linkEnd
                    )
                } else {
                    addStringAnnotation(
                        tag = "URL_LINK",
                        annotation = linkTarget,
                        start = linkStart,
                        end = linkEnd
                    )
                    addStyle(
                        style = SpanStyle(
                            color = Color(0xFF00897B),
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = linkStart,
                        end = linkEnd
                    )
                }

                cursor = end
            }

            if (cursor < rawText.length) {
                appendFormattedPlain(rawText.substring(cursor), scale)
            }
        }
    }

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = (16 * scale).sp,
            lineHeight = (26 * scale).sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "ARTICLE_LINK", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onInternalArticleClick(annotation.item)
                    return@ClickableText
                }
            annotatedString.getStringAnnotations(tag = "URL_LINK", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onExternalLinkClick(annotation.item)
                }
        }
    )
}

private fun AnnotatedString.Builder.appendFormattedPlain(text: String, scale: Float) {
    // Check for bold `**text**` and code `` `code` ``
    val pattern = Regex("(\\*[^*]+\\*|`[^`]+`)")
    var lastIdx = 0
    pattern.findAll(text).forEach { match ->
        val s = match.range.first
        val e = match.range.last + 1
        if (s > lastIdx) {
            append(text.substring(lastIdx, s))
        }
        val matchText = match.value
        if (matchText.startsWith("**") && matchText.endsWith("**")) {
            val content = matchText.substring(2, matchText.length - 2)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(content)
            }
        } else if (matchText.startsWith("`") && matchText.endsWith("`")) {
            val content = matchText.substring(1, matchText.length - 1)
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x20888888)
                )
            ) {
                append(content)
            }
        } else {
            append(matchText)
        }
        lastIdx = e
    }
    if (lastIdx < text.length) {
        append(text.substring(lastIdx))
    }
}
