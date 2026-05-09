package com.lior.plugin.i18n.annotation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.lior.plugin.i18n.pattern.I18nPatternMatcher
import com.lior.plugin.i18n.service.LocaleFileService
import com.lior.plugin.i18n.settings.I18nProjectSettings
import com.lior.plugin.i18n.settings.I18nSettings
import java.util.WeakHashMap

/**
 * 通过 EditorFactory 监听器为每个编辑器管理行尾翻译 Inlay。
 * 使用 InlayModel.addAfterLineEndElement() 直接写入编辑器，
 * 避免依赖 EditorLinePainter 的绘制回调机制。
 */
class I18nInlayHintManager : EditorFactoryListener {

    private val documentListeners = WeakHashMap<Editor, DocumentListener>()

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return

        refresh(editor, project)

        val listener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                refresh(editor, project)
            }
        }
        documentListeners[editor] = listener
        editor.document.addDocumentListener(listener)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        documentListeners.remove(editor)?.let {
            editor.document.removeDocumentListener(it)
        }
    }

    companion object {

        private const val MAX_LEN = 60

        /**
         * 为单个编辑器刷新 Inlay 提示。
         * 必须从 EDT 调用（内部会切换到后台线程读取文档，再切回 EDT 写入 Inlay）。
         */
        fun refresh(editor: Editor, project: Project) {
            val settings = I18nSettings.getInstance()

            if (!settings.annotations) {
                clearInlays(editor)
                return
            }

            val service = LocaleFileService.getInstance(project)
            val language = I18nProjectSettings.getInstance(project).displayLanguage

            if (!service.isCached(language)) return   // 尚未加载，loadInBackground 完成后会再次触发

            // 在后台线程持有 ReadAction 读取文档，避免阻塞 EDT
            ApplicationManager.getApplication().executeOnPooledThread {
                val inlaysData = ApplicationManager.getApplication()
                    .runReadAction<List<Pair<Int, String>>> {
                        if (editor.isDisposed) return@runReadAction emptyList()
                        val document = editor.document
                        buildList {
                            for (line in 0 until document.lineCount) {
                                val lineStart = document.getLineStartOffset(line)
                                val lineEnd   = document.getLineEndOffset(line)
                                if (lineStart >= lineEnd) continue

                                val lineText = document.getText(TextRange(lineStart, lineEnd))
                                for ((_, key) in I18nPatternMatcher.findKeysInLine(lineText, project)) {
                                    val value = service.getCachedTranslation(language, key) ?: continue
                                    val display = if (value.length > MAX_LEN) "${value.take(MAX_LEN)}…" else value
                                    add(lineEnd to "  →  $display")
                                }
                            }
                        }
                    }

                // 回到 EDT 写入 Inlay
                ApplicationManager.getApplication().invokeLater {
                    if (editor.isDisposed) return@invokeLater
                    clearInlays(editor)
                    val docLen = editor.document.textLength
                    for ((offset, text) in inlaysData) {
                        if (offset in 0..docLen) {
                            editor.inlayModel.addAfterLineEndElement(
                                offset, true, I18nInlayRenderer(text)
                            )
                        }
                    }
                }
            }
        }

        /** 清除该编辑器中所有由本插件写入的 Inlay（必须在 EDT 调用）。 */
        fun clearInlays(editor: Editor) {
            if (editor.isDisposed) return
            val docLen = editor.document.textLength
            editor.inlayModel
                .getAfterLineEndElementsInRange(0, docLen, I18nInlayRenderer::class.java)
                .forEach { Disposer.dispose(it) }
        }

        /** 翻译加载完成后，刷新当前项目内所有打开的编辑器。 */
        fun refreshAllEditors(project: Project) {
            EditorFactory.getInstance().allEditors
                .filter { it.project == project }
                .forEach { refresh(it, project) }
        }
    }
}
