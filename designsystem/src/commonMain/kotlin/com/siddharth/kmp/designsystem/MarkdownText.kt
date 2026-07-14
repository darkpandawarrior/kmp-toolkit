package com.siddharth.kmp.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Lightweight, hand-rolled Markdown renderer — headers (#/##/###), bold (**x**), inline code (`x`),
 * links ([t](u) rendered as underlined brand text), bullet/numbered lists, blockquotes, `---` rules,
 * and GitHub-style pipe tables. Deliberately minimal (no new dependency) — enough to render the A–G
 * evaluation reports faithfully. Not a full CommonMark parser; unknown syntax falls back to plain text.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val lines = markdown.replace("\r\n", "\n").split("\n")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> Spacer(Modifier.height(DesignTokens.Spacing.s))
                trimmed == "---" || trimmed == "***" -> {
                    Spacer(Modifier.height(DesignTokens.Spacing.s))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Spacer(Modifier.height(DesignTokens.Spacing.s))
                }
                trimmed.startsWith("### ") -> MdHeading(trimmed.removePrefix("### "), MaterialTheme.typography.titleMedium)
                trimmed.startsWith("## ") -> MdHeading(trimmed.removePrefix("## "), MaterialTheme.typography.titleLarge)
                trimmed.startsWith("# ") -> MdHeading(trimmed.removePrefix("# "), MaterialTheme.typography.headlineSmall)
                trimmed.startsWith("> ") -> MdQuote(trimmed.removePrefix("> "))
                isTableRow(trimmed) -> {
                    val block = mutableListOf<String>()
                    while (i < lines.size && isTableRow(lines[i].trim())) {
                        block += lines[i].trim()
                        i++
                    }
                    MdTable(block)
                    continue
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> MdBullet(trimmed.drop(2), ordered = null)
                Regex("^\\d+\\. ").containsMatchIn(trimmed) -> {
                    val num = trimmed.substringBefore(". ")
                    MdBullet(trimmed.substringAfter(". "), ordered = num)
                }
                else -> Text(inline(line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            i++
        }
    }
}

@Composable
private fun MdHeading(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
) {
    Spacer(Modifier.height(DesignTokens.Spacing.s))
    Text(inline(text), style = style, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MdQuote(text: String) {
    Row {
        Box(Modifier.width(3.dp).height(20.dp).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(DesignTokens.Spacing.s))
        Text(inline(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MdBullet(
    text: String,
    ordered: String?,
) {
    Row(Modifier.padding(start = DesignTokens.Spacing.s)) {
        Text(if (ordered != null) "$ordered. " else "• ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(inline(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun isTableRow(line: String): Boolean = line.startsWith("|") && line.endsWith("|") && line.count { it == '|' } >= 2

@Composable
private fun MdTable(rows: List<String>) {
    val parsed = rows.map { r -> r.trim('|').split("|").map { it.trim() } }
    // Drop the separator row (---|---).
    val body = parsed.filterNot { cells -> cells.all { it.isEmpty() || it.all { c -> c == '-' || c == ':' } } }
    if (body.isEmpty()) return
    val header = body.first()
    Column(Modifier.fillMaxWidth().padding(vertical = DesignTokens.Spacing.s)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            header.forEach { cell ->
                Text(cell, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        body.drop(1).forEach { cells ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                cells.forEach { cell ->
                    Text(inline(cell), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
        }
    }
}

/** Inline span parser: **bold**, `code`, [text](url). Single pass, left-to-right. */
@Composable
private fun inline(text: String) =
    buildAnnotatedString {
        val brand = MaterialTheme.colorScheme.primary
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > 0) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = brand)) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text[i] == '[' -> {
                    val close = text.indexOf(']', i)
                    val open = if (close > 0) text.indexOf('(', close) else -1
                    val end = if (open > 0) text.indexOf(')', open) else -1
                    if (close > 0 && open == close + 1 && end > 0) {
                        withStyle(SpanStyle(color = brand, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) {
                            append(text.substring(i + 1, close))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
