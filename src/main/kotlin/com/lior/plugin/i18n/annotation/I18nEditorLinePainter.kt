package com.lior.plugin.i18n.annotation

import com.intellij.openapi.editor.EditorLinePainter
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.lior.plugin.i18n.pattern.I18nPatternMatcher
import com.lior.plugin.i18n.service.LocaleFileService
import com.lior.plugin.i18n.settings.I18nSettings
import java.awt.Color
import java.awt.Font

class I18nEditorLinePainter : EditorLinePainter() {

    override fun getLineExtensions(
        project: Project,
        file: VirtualFile,
        lineNumber: Int
    ): Collection<LineExtensionInfo>? {
        val settings = I18nSettings.getInstance()
        if (!settings.annotations) return null

        // 通过当前选中编辑器获取文档
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val document = editor.document
        if (lineNumber >= document.lineCount) return null

        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd   = document.getLineEndOffset(lineNumber)
        if (lineStart >= lineEnd) return null

        val lineText = document.getText(TextRange(lineStart, lineEnd))
        val matches  = I18nPatternMatcher.findKeysInLine(lineText)
        if (matches.isEmpty()) return null

        val service = LocaleFileService.getInstance(project)
        val result  = mutableListOf<LineExtensionInfo>()

        for ((_, key) in matches) {
            val translation = service.getTranslation(key) ?: continue
            val displayText = truncate(translation, MAX_DISPLAY_LENGTH)
            result += LineExtensionInfo("  →  $displayText", HINT_COLOR, null, null, Font.PLAIN)
        }

        return result.ifEmpty { null }
    }

    private fun truncate(text: String, max: Int): String =
        if (text.length <= max) text else "${text.take(max)}…"

    companion object {
        private const val MAX_DISPLAY_LENGTH = 60
        private val HINT_COLOR = JBColor(Color(140, 140, 140), Color(150, 150, 150))
    }
}
