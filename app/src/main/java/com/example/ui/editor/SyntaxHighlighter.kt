package com.example.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.theme.SyntaxAnnotation
import com.example.ui.theme.SyntaxComment
import com.example.ui.theme.SyntaxKeyword
import com.example.ui.theme.SyntaxNumber
import com.example.ui.theme.SyntaxProperty
import com.example.ui.theme.SyntaxString
import com.example.ui.theme.SyntaxTypeOrTag

class CodeSyntaxVisualTransformation(
    private val fileExtension: String
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length > 35_000) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val highlighted = highlightCode(raw, fileExtension.lowercase())
        return TransformedText(highlighted, OffsetMapping.Identity)
    }

    companion object {
        private val kotlinKeywords = Regex(
            """\b(package|import|class|data|sealed|enum|object|interface|fun|val|var|override|private|public|internal|protected|open|abstract|suspend|inline|return|if|else|when|for|while|in|is|as|true|false|null|this|super|try|catch|finally|throw|companion|init|by|remember|mutableStateOf)\b"""
        )
        private val jsKeywords = Regex(
            """\b(const|let|var|function|return|if|else|for|while|switch|case|break|continue|import|export|from|async|await|try|catch|new|class|true|false|null|undefined|document|window|console)\b"""
        )
        private val annotationRegex = Regex("""@[A-Za-z_][A-Za-z0-9_]*""")
        private val numberRegex = Regex("""\b\d+(\.\d+)?[fFLu]?\b""")
        private val stringRegex = Regex(""""([^"\\]|\\.)*"|'([^'\\]|\\.)*'|`([^`\\]|\\.)*`""")
        private val slashCommentRegex = Regex("""//.*$|/\*[\s\S]*?\*/""", RegexOption.MULTILINE)
        private val hashCommentRegex = Regex("""#.*$""", RegexOption.MULTILINE)
        private val xmlTagRegex = Regex("""</?[A-Za-z0-9_.:-]+|/?\s*>""")
        private val xmlAttrRegex = Regex("""\b[a-zA-Z_:][a-zA-Z0-9_.:-]*(?==)""")
        private val yamlKeyRegex = Regex("""^\s*[A-Za-z0-9_-]+(?=:)""", RegexOption.MULTILINE)
        private val cssPropRegex = Regex("""\b[a-z-]+(?=\s*:)""")

        fun highlightCode(code: String, ext: String): AnnotatedString {
            val builder = AnnotatedString.Builder(code)

            when (ext) {
                "kt", "kts", "java" -> {
                    applyRegex(builder, kotlinKeywords, code, SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold))
                    applyRegex(builder, annotationRegex, code, SpanStyle(color = SyntaxAnnotation))
                    applyRegex(builder, numberRegex, code, SpanStyle(color = SyntaxNumber))
                    applyRegex(builder, stringRegex, code, SpanStyle(color = SyntaxString))
                    applyRegex(builder, slashCommentRegex, code, SpanStyle(color = SyntaxComment, fontStyle = FontStyle.Italic))
                }
                "js", "ts" -> {
                    applyRegex(builder, jsKeywords, code, SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold))
                    applyRegex(builder, numberRegex, code, SpanStyle(color = SyntaxNumber))
                    applyRegex(builder, stringRegex, code, SpanStyle(color = SyntaxString))
                    applyRegex(builder, slashCommentRegex, code, SpanStyle(color = SyntaxComment, fontStyle = FontStyle.Italic))
                }
                "xml", "html", "htm", "svg" -> {
                    applyRegex(builder, xmlTagRegex, code, SpanStyle(color = SyntaxTypeOrTag, fontWeight = FontWeight.SemiBold))
                    applyRegex(builder, xmlAttrRegex, code, SpanStyle(color = SyntaxProperty))
                    applyRegex(builder, stringRegex, code, SpanStyle(color = SyntaxString))
                }
                "css" -> {
                    applyRegex(builder, cssPropRegex, code, SpanStyle(color = SyntaxProperty))
                    applyRegex(builder, numberRegex, code, SpanStyle(color = SyntaxNumber))
                    applyRegex(builder, stringRegex, code, SpanStyle(color = SyntaxString))
                    applyRegex(builder, slashCommentRegex, code, SpanStyle(color = SyntaxComment, fontStyle = FontStyle.Italic))
                }
                "yml", "yaml", "toml", "properties" -> {
                    applyRegex(builder, yamlKeyRegex, code, SpanStyle(color = SyntaxTypeOrTag, fontWeight = FontWeight.Medium))
                    applyRegex(builder, numberRegex, code, SpanStyle(color = SyntaxNumber))
                    applyRegex(builder, stringRegex, code, SpanStyle(color = SyntaxString))
                    applyRegex(builder, hashCommentRegex, code, SpanStyle(color = SyntaxComment, fontStyle = FontStyle.Italic))
                }
                "json" -> {
                    applyRegex(builder, stringRegex, code, SpanStyle(color = SyntaxString))
                    applyRegex(builder, numberRegex, code, SpanStyle(color = SyntaxNumber))
                }
            }
            return builder.toAnnotatedString()
        }

        private fun applyRegex(
            builder: AnnotatedString.Builder,
            regex: Regex,
            input: String,
            style: SpanStyle
        ) {
            regex.findAll(input).forEach { match ->
                builder.addStyle(style, match.range.first, match.range.last + 1)
            }
        }
    }
}
