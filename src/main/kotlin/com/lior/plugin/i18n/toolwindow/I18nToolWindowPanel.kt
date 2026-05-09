package com.lior.plugin.i18n.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import com.lior.plugin.i18n.service.LocaleFileService
import com.lior.plugin.i18n.settings.I18nProjectSettings
import java.awt.BorderLayout
import java.util.regex.Pattern
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class I18nToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val tableModel = object : DefaultTableModel() {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val table = JBTable(tableModel)
    private val rowSorter = TableRowSorter(tableModel)

    private val rootNode = DefaultMutableTreeNode("root")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)

    private val searchField = SearchTextField()
    private val statusLabel = JBLabel("").apply { foreground = JBColor.GRAY }

    private var languages: List<String> = emptyList()
    private var allKeys: List<String> = emptyList()
    private var allTranslations: Map<String, Map<String, String>> = emptyMap()

    init {
        setupUI()
        refresh()
    }

    private fun setupUI() {
        val topBar = JPanel(BorderLayout(4, 0)).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 2, 4)
        }
        searchField.textEditor.emptyText.setText("搜索 key 或翻译值…")
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = applyFilter(searchField.text.trim())
        })
        val refreshBtn = JButton("刷新").apply { addActionListener { refresh() } }
        topBar.add(searchField, BorderLayout.CENTER)
        topBar.add(refreshBtn, BorderLayout.EAST)

        val north = JPanel(BorderLayout())
        north.add(topBar, BorderLayout.NORTH)
        north.add(statusLabel.also {
            it.border = BorderFactory.createEmptyBorder(0, 8, 4, 4)
        }, BorderLayout.CENTER)
        add(north, BorderLayout.NORTH)

        table.rowSorter = rowSorter
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS

        tree.isRootVisible = false
        tree.showsRootHandles = true

        val tabs = JBTabbedPane()
        tabs.addTab("Table", JBScrollPane(table))
        tabs.addTab("Tree", JBScrollPane(tree))
        add(tabs, BorderLayout.CENTER)
    }

    fun refresh() {
        statusLabel.text = "⏳ 加载中…"
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val service = LocaleFileService.getInstance(project)
                val langs = service.detectAvailableLanguages()
                val translations = langs.associateWith { service.getTranslationsForLanguage(it) }
                val keys = translations.values.flatMap { it.keys }.toSortedSet().toList()

                ApplicationManager.getApplication().invokeLater {
                    languages = langs
                    allKeys = keys
                    allTranslations = translations
                    rebuildTable(keys, langs, translations)
                    rebuildTree(keys, translations)
                    rowSorter.rowFilter = null
                    statusLabel.text = when {
                        langs.isEmpty() -> "⚠ 未找到语言文件，请检查 localesPaths 配置"
                        keys.isEmpty()  -> "⚠ 语言文件为空（${langs.joinToString("、")}）"
                        else            -> "✓ ${keys.size} 条翻译，${langs.size} 种语言：${langs.joinToString("、")}"
                    }
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = "⚠ 加载出错：${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    private fun rebuildTable(
        keys: List<String>,
        langs: List<String>,
        translations: Map<String, Map<String, String>>
    ) {
        tableModel.setColumnIdentifiers((listOf("Key") + langs).toTypedArray())
        tableModel.rowCount = 0
        for (key in keys) {
            val row = buildList<Any> {
                add(key)
                langs.forEach { lang -> add(translations[lang]?.get(key) ?: "") }
            }.toTypedArray()
            tableModel.addRow(row)
        }
    }

    private fun rebuildTree(
        keys: List<String>,
        translations: Map<String, Map<String, String>>
    ) {
        rootNode.removeAllChildren()
        val nodeMap = mutableMapOf<String, DefaultMutableTreeNode>()
        val primaryLang = languages.firstOrNull() ?: I18nProjectSettings.getInstance(project).displayLanguage

        for (key in keys) {
            val parts = key.split(".")
            var parentNode = rootNode
            var currentPath = ""

            for ((i, part) in parts.withIndex()) {
                currentPath = if (currentPath.isEmpty()) part else "$currentPath.$part"
                val node = nodeMap.getOrPut(currentPath) {
                    val isLeaf = i == parts.size - 1
                    val label = if (isLeaf) {
                        val value = translations[primaryLang]?.get(key) ?: ""
                        val display = if (value.length > 60) value.take(60) + "…" else value
                        if (display.isNotEmpty()) "$part: $display" else part
                    } else {
                        part
                    }
                    DefaultMutableTreeNode(label).also { parentNode.add(it) }
                }
                parentNode = node
            }
        }

        treeModel.reload()
        expandTree()
    }

    private fun expandTree() {
        var row = 0
        while (row < tree.rowCount) tree.expandRow(row++)
    }

    private fun applyFilter(query: String) {
        if (query.isBlank()) {
            rowSorter.rowFilter = null
            rebuildTree(allKeys, allTranslations)
            return
        }
        val quoted = Pattern.quote(query)
        rowSorter.rowFilter = RowFilter.regexFilter("(?i)$quoted")

        val filteredKeys = allKeys.filter { key ->
            key.contains(query, ignoreCase = true) ||
            allTranslations.values.any { it[key]?.contains(query, ignoreCase = true) == true }
        }
        rebuildTree(filteredKeys, allTranslations)
    }
}
