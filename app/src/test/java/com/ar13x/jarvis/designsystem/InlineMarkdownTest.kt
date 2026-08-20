package com.ar13x.jarvis.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.ar13x.jarvis.designsystem.component.toInlineMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser exists because models write `**like this**` by habit and the
 * asterisks were reaching the screen. Most of these tests are about the cases
 * where text only *looks* like markup.
 */
class InlineMarkdownTest {

    private fun parse(text: String) = text.toInlineMarkdown(codeColor = Color.Red)

    @Test
    fun `bold is styled and its markers are removed`() {
        val result = parse("I've proposed a task: **Go to the gym** for tomorrow.")

        assertEquals("I've proposed a task: Go to the gym for tomorrow.", result.text)
        val bold = result.spanStyles.single()
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        assertEquals("Go to the gym", result.text.substring(bold.start, bold.end))
    }

    @Test
    fun `italic, strikethrough and code each style their span`() {
        assertEquals(FontStyle.Italic, parse("an *emphasis* here").spanStyles.single().item.fontStyle)
        assertEquals(
            TextDecoration.LineThrough,
            parse("a ~~mistake~~ here").spanStyles.single().item.textDecoration,
        )
        assertEquals("use due_date", parse("use `due_date`").text)
    }

    /**
     * The case that makes a naive parser embarrassing: arithmetic and file globs
     * are full of asterisks and underscores that are not emphasis.
     */
    @Test
    fun `an unmatched delimiter stays literal`() {
        assertEquals("2 * 3 = 6", parse("2 * 3 = 6").text)
        assertTrue(parse("2 * 3 = 6").spanStyles.isEmpty())

        // A turn truncated mid-emphasis should show what was written, not
        // swallow the rest of the line.
        assertEquals("proposing **Go to the", parse("proposing **Go to the").text)
    }

    @Test
    fun `empty emphasis is not emphasis`() {
        assertEquals("nothing **** here", parse("nothing **** here").text)
    }

    @Test
    fun `double asterisks are never read as two single ones`() {
        val result = parse("**bold**")
        assertEquals("bold", result.text)
        assertEquals(FontWeight.Bold, result.spanStyles.single().item.fontWeight)
        assertNull(result.spanStyles.single().item.fontStyle)
    }

    @Test
    fun `emphasis nests`() {
        val result = parse("**bold with *italic* inside**")
        assertEquals("bold with italic inside", result.text)
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(result.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun `markup inside code is content, not markup`() {
        val result = parse("run `a ** b` now")
        assertEquals("run a ** b now", result.text)
        assertTrue(result.spanStyles.none { it.item.fontWeight == FontWeight.Bold })
    }

    /**
     * Structure is not parsed — that is what the components array is for
     * (plan §4.5). A numbered list stays plain text on its own lines.
     */
    @Test
    fun `block structure is left alone`() {
        val text = "To help you:\n1. Search by keyword\n2. Check its details"
        assertEquals(text, parse(text).text)
    }
}
