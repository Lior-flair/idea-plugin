package com.lior.plugin.i18n.annotation

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.ui.awt.RelativePoint
import com.lior.plugin.i18n.pattern.I18nPatternMatcher
import com.lior.plugin.i18n.service.LocaleFileService
import com.lior.plugin.i18n.settings.I18nSettings
import javax.swing.UIManager

/**
 * 为每个新打开的编辑器注入鼠标悬浮监听，当光标停留在 i18n key 上时
 * 弹出气泡展示所有已缓存语言的翻译内容。
 */
class I18nEditorHoverHandler : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        event.editor.addEditorMouseMotionListener(I18nMouseMotionListener(event.editor))
    }
}

// ── 鼠标移动监听 ─────────────────────────────────────────────────────────────

private class I18nMouseMotionListener(private val editor: Editor) : EditorMouseMotionListener {

    private var currentBalloon: Balloon? = null
    private var lastKey: String?         = null

    override fun mouseMoved(e: EditorMouseEvent) {
        val settings = I18nSettings.getInstance()
        if (!settings.hoverEnabled) { dismiss(); return }

        val project  = editor.project ?: return
        val document = editor.document
        val line     = e.logicalPosition.line
        if (line >= document.lineCount) { dismiss(); return }

        val lineStart = document.getLineStartOffset(line)
        val lineEnd   = document.getLineEndOffset(line)
        if (lineStart >= lineEnd) { dismiss(); return }

        val lineText = document.getText(TextRange(lineStart, lineEnd))
        val matches  = I18nPatternMatcher.findKeysInLine(lineText, project)
        val col      = e.logicalPosition.column
        val match    = matches.firstOrNull { (range, _) -> col in range.first..range.last }

        if (match == null) { dismiss(); return }

        val key = match.second
        // 已在展示同一个 key 则不重复弹出
        if (key == lastKey) return

        val service = LocaleFileService.getInstance(project)
        val langs   = service.getCachedLanguages()
        if (langs.isEmpty()) { dismiss(); return }

        // 构建多语言表格行（只取有值的语言）
        val rows = langs.joinToString("") { lang ->
            val value = service.getCachedTranslation(lang, key) ?: return@joinToString ""
            val escaped = value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            "<tr>" +
            "<td style='color:#888;padding:1px 12px 1px 0;white-space:nowrap'>$lang</td>" +
            "<td style='padding:1px 0'>$escaped</td>" +
            "</tr>"
        }
        if (rows.isEmpty()) { dismiss(); return }

        dismiss()
        lastKey = key

        val html = """
            <html><body style='margin:0;padding:0'>
              <div style='padding:2px 0 4px 0;color:#6897BB;font-weight:bold'>$key</div>
              <table cellpadding=0 cellspacing=0>$rows</table>
            </body></html>
        """.trimIndent()

        currentBalloon = JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(html, null, UIManager.getColor("ToolTip.background"), null)
            .setHideOnClickOutside(true)
            .setHideOnKeyOutside(false)
            .setHideOnAction(false)
            .setAnimationCycle(120)
            .setFadeoutTime(0)
            .createBalloon()
            .also { balloon ->
                val mouseEvent = e.mouseEvent
                val point = java.awt.Point(mouseEvent.x, mouseEvent.y - 12)
                balloon.show(RelativePoint(mouseEvent.component, point), Balloon.Position.above)
            }
    }

    private fun dismiss() {
        currentBalloon?.hide()
        currentBalloon = null
        lastKey        = null
    }
}
