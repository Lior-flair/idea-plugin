package com.lior.plugin.i18n.toolwindow

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.lior.plugin.i18n.settings.I18nProjectSettings
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class I18nTreePanel(private val data: I18nToolWindowData) : JPanel(BorderLayout()) {

    private val rootNode  = DefaultMutableTreeNode("root")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree      = Tree(treeModel)
    private val status    = JBLabel("").apply {
        foreground = JBColor.GRAY
        border = BorderFactory.createEmptyBorder(3, 6, 3, 6)
    }

    init {
        tree.isRootVisible   = false
        tree.showsRootHandles = true

        add(status, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)

        data.addListener { loading ->
            if (loading) { status.text = "⏳ 加载中…" } else { rebuild(data.allKeys); updateStatus() }
        }
    }

    fun applyFilter(query: String) {
        val keys = if (query.isBlank()) data.allKeys else data.allKeys.filter { key ->
            key.contains(query, ignoreCase = true) ||
            data.allTranslations.values.any { it[key]?.contains(query, ignoreCase = true) == true }
        }
        rebuild(keys)
    }

    private fun rebuild(keys: List<String>) {
        rootNode.removeAllChildren()
        val nodeMap = mutableMapOf<String, DefaultMutableTreeNode>()
        val primary = data.languages.firstOrNull()
            ?: I18nProjectSettings.getInstance(data.project).displayLanguage

        for (key in keys) {
            val parts = key.split(".")
            var parent = rootNode
            var path   = ""

            for ((i, part) in parts.withIndex()) {
                path = if (path.isEmpty()) part else "$path.$part"
                val node = nodeMap.getOrPut(path) {
                    val label = if (i == parts.size - 1) {
                        val v = data.allTranslations[primary]?.get(key) ?: ""
                        if (v.isNotEmpty()) "$part: ${if (v.length > 60) v.take(60) + "…" else v}" else part
                    } else part
                    DefaultMutableTreeNode(label).also { parent.add(it) }
                }
                parent = node
            }
        }

        treeModel.reload()
        var row = 0; while (row < tree.rowCount) tree.expandRow(row++)
    }

    private fun updateStatus() {
        status.text = when {
            data.languages.isEmpty() -> "⚠ 未找到语言文件，请检查 localesPaths 配置"
            data.allKeys.isEmpty()   -> "⚠ 语言文件为空（${data.languages.joinToString("、")}）"
            else -> "✓ ${data.allKeys.size} 条翻译，${data.languages.size} 种语言：${data.languages.joinToString("、")}"
        }
    }
}
