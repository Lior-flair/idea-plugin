package com.lior.plugin.i18n.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.*
import com.lior.plugin.i18n.service.LocaleFileService
import javax.swing.DefaultComboBoxModel

class I18nSettingsConfigurable : BoundSearchableConfigurable(
    displayName = "i18n",
    helpTopic = "com.lior.plugin.i18n"
) {

    private lateinit var sourceLangCombo: ComboBox<String>
    private lateinit var displayLangCombo: ComboBox<String>
    private lateinit var scanStatusLabel: JBLabel

    override fun createPanel(): DialogPanel {
        val s = I18nSettings.getInstance()

        sourceLangCombo = ComboBox<String>().apply {
            isEditable = true
            addItem(s.sourceLanguage)
            selectedItem = s.sourceLanguage
        }
        displayLangCombo = ComboBox<String>().apply {
            isEditable = true
            addItem(s.displayLanguage)
            selectedItem = s.displayLanguage
        }
        scanStatusLabel = JBLabel("").apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(font.size2D - 1f)
        }

        if (s.autoDetectLanguages) runScan()

        return panel {

            // ── 语言设置 ─────────────────────────────────────────────────────
            group("语言设置") {
                row {
                    checkBox("从项目中自动读取可用语言列表")
                        .bindSelected({ s.autoDetectLanguages }, { s.autoDetectLanguages = it })
                        .onChanged { cb -> if (cb.isSelected) runScan() }

                    button("扫描语言") { runScan() }

                    cell(scanStatusLabel)
                }
                row("来源语言 (sourceLanguage):") {
                    cell(sourceLangCombo)
                        .align(AlignX.FILL)
                        .comment("locale 文件中作为翻译基准的语言代码，如 <b>en</b>、<b>zh-CN</b>")
                }
                row("显示语言 (displayLanguage):") {
                    cell(displayLangCombo)
                        .align(AlignX.FILL)
                        .comment("内联注释展示的语言，如 <b>zh-CN</b>、<b>ja</b>")
                }
            }

            // ── 路径设置 ─────────────────────────────────────────────────────
            group("路径设置 (localesPaths)") {
                row {
                    textArea()
                        .bindText({ s.localesPaths }, { s.localesPaths = it })
                        .rows(4)
                        .align(AlignX.FILL)
                }.rowComment(
                    "以逗号分隔，相对于项目根目录。支持 Glob patterns：<br>" +
                    "示例：<code>src/locales, locales, src/**/i18n, public/locales</code>"
                )
            }

            // ── 文件格式 ─────────────────────────────────────────────────────
            group("文件格式 (enabledExtensions)") {
                val enabledSet = s.getEnabledExtensionSet().toMutableSet()
                row {
                    I18nSettings.ALL_EXTENSIONS.forEach { ext ->
                        checkBox(".$ext")
                            .bindSelected(
                                { ext in s.getEnabledExtensionSet() },
                                { checked ->
                                    if (checked) enabledSet.add(ext) else enabledSet.remove(ext)
                                    s.enabledExtensions = enabledSet.joinToString(",")
                                }
                            )
                    }
                }.rowComment("勾选允许扫描的 locale 文件后缀，取消勾选后对应格式将被忽略")
            }

            // ── 文件名前缀 ────────────────────────────────────────────────────
            group("文件名前缀 (localeFilePrefix)") {
                row {
                    textField()
                        .bindText({ s.localeFilePrefix }, { s.localeFilePrefix = it })
                        .align(AlignX.FILL)
                }.rowComment(
                    "locale 文件名前缀，逗号分隔，留空扫描全部文件。<br>" +
                    "示例：<code>messages</code> 仅匹配 <code>messages_en.properties</code>、<code>messages_zh_CN.properties</code>；" +
                    "多个前缀：<code>messages,validation</code>"
                )
            }

            // ── 键名与框架 ───────────────────────────────────────────────────
            group("键名与框架") {
                row("键名风格 (keystyle):") {
                    comboBox(listOf("nested", "flat"))
                        .bindItem({ s.keystyle }, { s.keystyle = it ?: "nested" })
                        .comment("nested: {\"home\": {\"title\": \"...\"}}  |  flat: {\"home.title\": \"...\"}")
                }
                row("启用的框架 (enabledFrameworks):") {
                    textField()
                        .bindText({ s.enabledFrameworks }, { s.enabledFrameworks = it })
                        .comment(
                            "<b>auto</b> = 自动检测；或填入逗号分隔列表：" +
                            "<code>i18next, vue-i18n, react-intl, ngx-translate, flutter</code>"
                        )
                        .align(AlignX.FILL)
                }
            }

            // ── 显示选项 ─────────────────────────────────────────────────────
            group("显示选项") {
                row {
                    checkBox("启用行尾 Inlay 翻译 (annotations)")
                        .bindSelected({ s.annotations }, { s.annotations = it })
                        .comment("在代码行尾以灰色文字实时显示 i18n key 对应的翻译内容")
                }
                row {
                    checkBox("启用鼠标悬浮气泡 (hoverEnabled)")
                        .bindSelected({ s.hoverEnabled }, { s.hoverEnabled = it })
                        .comment("光标停留在 i18n key 上时，弹出气泡展示所有语言的翻译对照")
                }
            }
        }
    }

    // ── apply / reset / isModified ───────────────────────────────────────────

    override fun apply() {
        super.apply()
        val s = I18nSettings.getInstance()
        s.sourceLanguage  = comboValue(sourceLangCombo,  "en")
        s.displayLanguage = comboValue(displayLangCombo, "en")
        currentProject()?.let { LocaleFileService.getInstance(it).invalidateCache() }
    }

    override fun reset() {
        super.reset()
        val s = I18nSettings.getInstance()
        sourceLangCombo.selectedItem  = s.sourceLanguage
        displayLangCombo.selectedItem = s.displayLanguage
        scanStatusLabel.text = ""
    }

    override fun isModified(): Boolean {
        if (super.isModified()) return true
        val s = I18nSettings.getInstance()
        return comboValue(sourceLangCombo, "en")  != s.sourceLanguage ||
               comboValue(displayLangCombo, "en") != s.displayLanguage
    }

    // ── 扫描逻辑 ─────────────────────────────────────────────────────────────

    private fun runScan() {
        setScanStatus("⏳ 扫描中…", JBColor.GRAY)

        val project = currentProject()
        if (project == null) {
            setScanStatus("⚠ 未找到打开的项目，请先打开一个项目", JBColor.RED)
            return
        }

        // 在后台线程执行文件 IO，完成后切回 EDT 更新 UI
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val s = I18nSettings.getInstance()
                val detected = LocaleFileService.getInstance(project).detectAvailableLanguages()

                ApplicationManager.getApplication().invokeLater {
                    if (detected.isEmpty()) {
                        setScanStatus(
                            "⚠ 未找到语言文件，请检查 localesPaths 配置（当前路径：${s.localesPaths}）",
                            JBColor(0xCC6600, 0xFFAA44)
                        )
                        return@invokeLater
                    }
                    updateCombos(detected)
                    setScanStatus("✓ 已检测到 ${detected.size} 种语言：${detected.joinToString("、")}", JBColor(0x007700, 0x88CC88))
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    setScanStatus("⚠ 扫描出错：${e.message ?: e.javaClass.simpleName}", JBColor.RED)
                }
            }
        }
    }

    private fun updateCombos(detected: List<String>) {
        val prevSource  = sourceLangCombo.selectedItem
        val prevDisplay = displayLangCombo.selectedItem

        sourceLangCombo.model  = DefaultComboBoxModel(detected.toTypedArray())
        displayLangCombo.model = DefaultComboBoxModel(detected.toTypedArray())

        sourceLangCombo.selectedItem  = if (prevSource  in detected) prevSource  else detected.first()
        displayLangCombo.selectedItem = if (prevDisplay in detected) prevDisplay else detected.first()
    }

    private fun setScanStatus(text: String, color: java.awt.Color) {
        scanStatusLabel.text       = text
        scanStatusLabel.foreground = color
    }

    // ── 工具 ─────────────────────────────────────────────────────────────────

    private fun comboValue(combo: ComboBox<String>, default: String): String =
        (combo.selectedItem as? String)?.trim()?.ifEmpty { default } ?: default

    /**
     * 获取当前打开的真实项目（排除 IntelliJ 内部 default project）。
     */
    private fun currentProject() =
        ProjectManager.getInstance().openProjects
            .filterNot { it.isDefault }
            .firstOrNull()
}
