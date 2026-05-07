package com.lior.plugin.consoleHelper.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.lior.plugin.consoleHelper.generator.LogStatementGenerator

class InsertLogAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val document = editor.document
        val selectionModel = editor.selectionModel
        val caretModel = editor.caretModel

        val variable = when {
            selectionModel.hasSelection() -> selectionModel.selectedText?.trim()?.takeIf { it.isNotEmpty() }
            else -> getWordAtCaret(editor)
        }

        val caretLine = caretModel.logicalPosition.line
        val lineStart = document.getLineStartOffset(caretLine)
        val lineEnd = document.getLineEndOffset(caretLine)
        val lineText = document.getText(TextRange(lineStart, lineEnd))
        val indentation = lineText.takeWhile { it == ' ' || it == '\t' }

        val logStatement = LogStatementGenerator.generate(
            variable = variable,
            lineNumber = caretLine,
            file = file,
            indentation = indentation
        )

        WriteCommandAction.runWriteCommandAction(project, "Insert Log Statement", null, {
            document.insertString(lineEnd, "\n$logStatement")
            caretModel.moveToOffset(lineEnd + logStatement.length + 1)
        })
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun getWordAtCaret(editor: Editor): String? {
        val document = editor.document
        val offset = editor.caretModel.offset
        val text = document.charsSequence
        if (offset > text.length) return null

        var start = offset
        var end = offset

        while (start > 0 && isWordChar(text[start - 1])) start--
        while (end < text.length && isWordChar(text[end])) end++

        return if (start < end) text.subSequence(start, end).toString() else null
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$' || c == '.'
}
