package com.ar13x.jarvis.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Inline markdown in agent prose.
 *
 * §4.5 says the app "renders cards and buttons, not markdown", and that stands:
 * *structure* still comes from the components array, never from parsing text.
 * What it did not anticipate is that a model writes `**like this**` by habit, and
 * printing the asterisks verbatim makes the assistant look broken.
 *
 * So this handles **emphasis only** — bold, italic, strikethrough, inline code.
 * Deliberately no headings, lists, tables, images or links: those are structure,
 * and structure arriving as text would be exactly the thing §4.5 forbids.
 *
 * Hand-rolled rather than a markdown dependency. The grammar is four delimiters;
 * a library would bring a block parser, an HTML model and a renderer to do less.
 *
 * **Unmatched delimiters stay literal.** A lone `*` in "2 * 3" is not emphasis,
 * and an unclosed `**` at the end of a truncated turn should read as the
 * asterisks the model actually wrote rather than swallowing the rest of the line.
 */
fun String.toInlineMarkdown(
    codeColor: Color,
    codeSize: TextUnit = 13.sp,
): AnnotatedString = buildAnnotatedString {
    appendMarkdown(this@toInlineMarkdown, Marks(), codeColor, codeSize)
}

private data class Marks(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
)

private val DELIMITERS = listOf("`", "**", "~~", "*", "_")

private fun AnnotatedString.Builder.appendMarkdown(
    text: String,
    marks: Marks,
    codeColor: Color,
    codeSize: TextUnit,
) {
    var index = 0
    while (index < text.length) {
        val opener = DELIMITERS
            .mapNotNull { d -> text.indexOf(d, index).takeIf { it >= 0 }?.let { it to d } }
            // Earliest wins; on a tie the longest does, so `**` is never read as
            // two separate `*` emphases.
            .minWithOrNull(compareBy({ it.first }, { -it.second.length }))

        if (opener == null) {
            appendStyled(text.substring(index), marks, codeColor, codeSize)
            return
        }

        val (start, delimiter) = opener
        val contentStart = start + delimiter.length
        val close = text.indexOf(delimiter, contentStart)

        // No closing delimiter, or nothing between them: not emphasis. Emit the
        // delimiter as the literal characters the model wrote and move past it.
        if (close < 0 || close == contentStart) {
            appendStyled(text.substring(index, contentStart), marks, codeColor, codeSize)
            index = contentStart
            continue
        }

        appendStyled(text.substring(index, start), marks, codeColor, codeSize)
        val inner = text.substring(contentStart, close)

        if (delimiter == "`") {
            // Code is a leaf: markup inside it is content, not markup.
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = codeSize,
                    color = codeColor,
                ),
            ) { append(inner) }
        } else {
            val nested = when (delimiter) {
                "**" -> marks.copy(bold = true)
                "~~" -> marks.copy(strike = true)
                else -> marks.copy(italic = true)
            }
            appendMarkdown(inner, nested, codeColor, codeSize)
        }
        index = close + delimiter.length
    }
}

private fun AnnotatedString.Builder.appendStyled(
    text: String,
    marks: Marks,
    codeColor: Color,
    codeSize: TextUnit,
) {
    if (text.isEmpty()) return
    if (marks == Marks()) {
        append(text)
        return
    }
    withStyle(
        SpanStyle(
            fontWeight = if (marks.bold) FontWeight.Bold else null,
            fontStyle = if (marks.italic) FontStyle.Italic else null,
            textDecoration = if (marks.strike) TextDecoration.LineThrough else null,
        ),
    ) { append(text) }
}
