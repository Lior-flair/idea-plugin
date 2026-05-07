package com.lior.plugin.i18n.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.lior.plugin.i18n.service.LocaleFileService

class RefreshTranslationsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LocaleFileService.getInstance(project).invalidateCache()
        // 强制重绘所有编辑器，刷新行尾注释
        FileEditorManager.getInstance(project).allEditors.forEach {
            it.component.repaint()
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
