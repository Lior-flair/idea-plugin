package com.lior.plugin.i18n.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.content.ContentFactory
import com.lior.plugin.i18n.settings.I18nSettingsConfigurable
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

class I18nToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val data       = I18nToolWindowData(project)
        val tablePanel = I18nTablePanel(data)
        val treePanel  = I18nTreePanel(data)

        // Table View / Tree View 作为原生 Content tab 出现在 header
        val cf = ContentFactory.getInstance()
        toolWindow.contentManager.addContent(cf.createContent(tablePanel, "Table View", false))
        toolWindow.contentManager.addContent(cf.createContent(treePanel,  "Tree View",  false))

        // 右侧 title actions：搜索 | 刷新 | 设置
        val searchAction = object : AnAction(), CustomComponentAction {
            override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
                val field = SearchTextField(false)
                field.textEditor.emptyText.setText("搜索…")
                field.addDocumentListener(object : DocumentAdapter() {
                    override fun textChanged(e: DocumentEvent) {
                        val q = field.text.trim()
                        tablePanel.applyFilter(q)
                        treePanel.applyFilter(q)
                    }
                })
                return field
            }
            override fun actionPerformed(e: AnActionEvent) {}
            override fun updateCustomComponent(component: JComponent, presentation: Presentation) {}
        }

        val refreshAction = object : AnAction("刷新", "重新加载翻译", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) = data.refresh()
        }

        val settingsAction = object : AnAction("设置", "打开 i18n 配置", AllIcons.General.Settings) {
            override fun actionPerformed(e: AnActionEvent) {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, I18nSettingsConfigurable::class.java)
            }
        }

        (toolWindow as? ToolWindowEx)?.setTitleActions(listOf(searchAction, refreshAction, settingsAction))

        data.refresh()
    }
}
