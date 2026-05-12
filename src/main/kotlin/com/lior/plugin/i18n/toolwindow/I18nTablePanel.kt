package com.lior.plugin.i18n.toolwindow

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.util.regex.Pattern
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter

class I18nTablePanel(private val data: I18nToolWindowData) : JPanel(BorderLayout()) {

    private val tableModel = object : DefaultTableModel() {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val table     = JBTable(tableModel)
    private val rowSorter = TableRowSorter(tableModel)
    private val status    = JBLabel("").apply {
        foreground = JBColor.GRAY
        border = BorderFactory.createEmptyBorder(3, 6, 3, 6)
    }

    init {
        table.rowSorter = rowSorter
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS

        add(status, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)

        data.addListener { loading ->
            if (loading) { status.text = "⏳ 加载中…" } else { rebuild(); updateStatus() }
        }
    }

    fun applyFilter(query: String) {
        if (query.isBlank()) { rowSorter.rowFilter = null; return }
        rowSorter.rowFilter = RowFilter.regexFilter("(?i)${Pattern.quote(query)}")
    }

    private fun rebuild() {
        tableModel.setColumnIdentifiers((listOf("Key") + data.languages).toTypedArray())
        tableModel.rowCount = 0
        for (key in data.allKeys) {
            tableModel.addRow(buildList<Any> {
                add(key)
                data.languages.forEach { lang -> add(data.allTranslations[lang]?.get(key) ?: "") }
            }.toTypedArray())
        }
        rowSorter.rowFilter = null
    }

    private fun updateStatus() {
        status.text = when {
            data.languages.isEmpty() -> "⚠ 未找到语言文件，请检查 localesPaths 配置"
            data.allKeys.isEmpty()   -> "⚠ 语言文件为空（${data.languages.joinToString("、")}）"
            else -> "✓ ${data.allKeys.size} 条翻译，${data.languages.size} 种语言：${data.languages.joinToString("、")}"
        }
    }
}
