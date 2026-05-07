package com.lior.plugin.consoleHelper.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.lior.plugin.consoleHelper.settings.ConsoleHelperSettings

class ClearLogsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val settings = ConsoleHelperSettings.getInstance()

        val patterns = settings.getLogFunctionList().map { fn ->
            Regex("^\\s*${Regex.escape(fn)}\\s*\\(")
        }

        val document = editor.document
        WriteCommandAction.runWriteCommandAction(project, "Clear Log Statements", null, {
            for (i in document.lineCount - 1 downTo 0) {
                val lineStart = document.getLineStartOffset(i)
                val lineEnd = document.getLineEndOffset(i)
                val lineText = document.getText(TextRange(lineStart, lineEnd))

                if (patterns.any { it.containsMatchIn(lineText) }) {
                    val deleteEnd = if (i < document.lineCount - 1) lineEnd + 1 else lineEnd
                    document.deleteString(lineStart, deleteEnd)
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
