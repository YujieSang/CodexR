package com.example.codexmobile.ui

import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

@Composable
fun MarkdownText(text: String, color: Color, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textSize = 15f * density.scaledDensity()
    val renderer = remember(context, color, textSize) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(textSize) { builder ->
                builder.inlinesEnabled(true)
                builder.theme().textColor(color.toArgb())
            })
            .build()
    }
    val normalized = remember(text) { MathMarkdown.normalize(text) }
    AndroidView(
        modifier = modifier,
        factory = { TextView(it).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize)
            setTextIsSelectable(true)
            setPadding(0, 0, 0, 0)
        } },
        update = { view ->
            view.setTextColor(color.toArgb())
            view.setLinkTextColor(color.toArgb())
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize)
            // Avoid resetting Android's text selection on unrelated recompositions.
            val renderKey = normalized to renderer
            if (view.tag != renderKey) {
                renderer.setMarkdown(view, normalized)
                view.tag = renderKey
            }
            view.setOnLongClickListener(if (onLongClick == null) null else android.view.View.OnLongClickListener {
                onLongClick(); true
            })
        },
    )
}

private fun androidx.compose.ui.unit.Density.scaledDensity(): Float = density * fontScale

internal object MathMarkdown {
    // Protect fenced and inline code before adapting common LaTeX delimiters to Markwon's $$ syntax.
    private val code = Regex("(```[\\s\\S]*?(?:```|$)|~~~[\\s\\S]*?(?:~~~|$)|`[^`\\n]*`)")
    private val math = Regex("\\\\\\[([\\s\\S]*?)\\\\\\]|\\\\\\(([\\s\\S]*?)\\\\\\)|(?<![\\\\$])\\$(?![\\s$])([^$\\n]*?\\S)\\$(?![\\d$])")

    fun normalize(text: String): String = buildString {
        var position = 0
        code.findAll(text).forEach { match ->
            append(convert(text.substring(position, match.range.first)))
            append(match.value)
            position = match.range.last + 1
        }
        append(convert(text.substring(position)))
    }

    private fun convert(text: String): String = math.replace(text) { match ->
        when {
            match.value.startsWith("\\[") -> "\n\$\$\n${match.groupValues[1].trim()}\n\$\$\n"
            match.value.startsWith("\\(") -> "\$\$${match.groupValues[2]}\$\$"
            else -> "\$\$${match.groupValues[3]}\$\$"
        }
    }
}
