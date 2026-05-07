package com.lior.plugin.i18n.settings

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*

class I18nSettingsConfigurable : BoundSearchableConfigurable(
    displayName = "i18n",
    helpTopic = "com.lior.plugin.i18n"
) {

    override fun createPanel(): DialogPanel {
        val s = I18nSettings.getInstance()

        return panel {

            // ── 语言设置 ─────────────────────────────────────────────────────
            group("语言设置") {
                row("来源语言 (sourceLanguage):") {
                    textField()
                        .bindText({ s.sourceLanguage }, { s.sourceLanguage = it })
                        .comment("locale 文件中用作翻译基准的语言代码，如 en、zh-CN")
                }
                row("显示语言 (displayLanguage):") {
                    textField()
                        .bindText({ s.displayLanguage }, { s.displayLanguage = it })
                        .comment("内联注释和树状视图中展示的语言代码，如 zh-CN、ja")
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
                    "每行或以逗号分隔，相对于项目根目录的路径。支持 Glob patterns：<br>" +
                    "例如：<code>src/locales, locales, src/**/i18n, public/locales</code>"
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
                            "auto = 自动检测；或填入逗号分隔列表，如：<code>i18next,vue-i18n,react-intl</code><br>" +
                            "可选值：i18next | vue-i18n | react-intl | ngx-translate | flutter"
                        )
                        .align(AlignX.FILL)
                }
            }

            // ── 显示选项 ─────────────────────────────────────────────────────
            group("显示选项") {
                row {
                    checkBox("启用内联翻译注释 (annotations)")
                        .bindSelected({ s.annotations }, { s.annotations = it })
                        .comment("在代码行尾以灰色文字显示对应翻译内容")
                }
            }
        }
    }
}
