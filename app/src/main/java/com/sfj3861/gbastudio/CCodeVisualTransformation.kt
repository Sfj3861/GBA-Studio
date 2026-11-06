package com.sfj3861.gbastudio

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

object CCodeVisualTransformation : VisualTransformation {
    private data class Token(val start: Int, val end: Int, val color: Color)

    private val patterns = listOf(
        Regex("""(?m)//.*$|/\\*[\\s\\S]*?\\*/""") to Color(0xFF7FAF83),
        Regex("""\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'""") to Color(0xFFD7A86E),
        Regex("""\\b(?:auto|break|case|char|const|continue|default|do|double|else|enum|extern|float|for|goto|if|inline|int|long|register|restrict|return|short|signed|sizeof|static|struct|switch|typedef|union|unsigned|void|volatile|while|bool|class|namespace|using|true|false|nullptr)\\b""") to Color(0xFF83B7F0),
        Regex("""\\b(?:0x[0-9a-fA-F]+|[0-9]+(?:\\.[0-9]+)?)\\b""") to Color(0xFFD6A5E8),
        Regex("""(?m)^\\s*#.*$""") to Color(0xFFE0B96D)
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val tokens = mutableListOf<Token>()
        patterns.forEach { (pattern, color) ->
            pattern.findAll(source).forEach { match ->
                tokens += Token(match.range.first, match.range.last + 1, color)
            }
        }
        val sorted = tokens.sortedWith(compareBy<Token> { it.start }.thenByDescending { it.end })
        val result = buildAnnotatedString {
            var cursor = 0
            sorted.forEach { token ->
                if (token.start >= cursor) {
                    append(source.substring(cursor, token.start))
                    pushStyle(SpanStyle(color = token.color))
                    append(source.substring(token.start, token.end))
                    pop()
                    cursor = token.end
                }
            }
            append(source.substring(cursor))
        }
        return TransformedText(result, OffsetMapping.Identity)
    }
}
