package com.lior.plugin.consoleHelper.settings

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*

class ConsoleHelperSettingsConfigurable : BoundSearchableConfigurable(
    displayName = "Console Helper",
    helpTopic = "com.lior.plugin.consoleHelper"
) {

    override fun createPanel(): DialogPanel {
        val s = ConsoleHelperSettings.getInstance()

        return panel {

            // ── 日志函数 ─────────────────────────────────────────────────────
            group("日志函数") {
                row("当前日志函数:") {
                    comboBox(listOf(
                        "console.log", "console.debug", "console.warn",
                        "console.error", "console.info", "DEBUG_LOG", "print"
                    )).bindItem(
                        { s.logFunction },
                        { s.logFunction = it ?: "console.log" }
                    )
                }
                row("全部日志函数:") {
                    textField()
                        .bindText({ s.availableLogFunctions }, { s.availableLogFunctions = it })
                        .comment("以逗号分隔；\"清理日志\" 操作会匹配并删除这些函数调用")
                        .align(AlignX.FILL)
                }
            }

            // ── 标签配置 ─────────────────────────────────────────────────────
            group("标签配置") {
                row("前缀标识:") {
                    textField()
                        .bindText({ s.prefix }, { s.prefix = it })
                        .comment("显示在日志最前面，如 🚀 或 [LOG]")
                }
                row {
                    checkBox("显示文件名")
                        .bindSelected({ s.showFileName }, { s.showFileName = it })
                }
                row {
                    checkBox("显示行号")
                        .bindSelected({ s.showLineNumber }, { s.showLineNumber = it })
                }
            }

            // ── 颜色样式 ─────────────────────────────────────────────────────
            group("颜色样式（仅适用于 console.* 系列函数）") {
                row {
                    checkBox("使用随机颜色（每次插入日志时随机选取颜色）")
                        .bindSelected({ s.useRandomColor }, { s.useRandomColor = it })
                }
                row("文字颜色 (hex):") {
                    textField()
                        .bindText({ s.customColor }, { s.customColor = it })
                        .comment("如 #00ff00；启用随机颜色时忽略此项")
                }
                row("背景颜色 (hex):") {
                    textField()
                        .bindText({ s.customBgColor }, { s.customBgColor = it })
                        .comment("留空则无背景色")
                }
                row("字体大小 (px):") {
                    spinner(8..72, 1)
                        .bindIntValue({ s.fontSize }, { s.fontSize = it })
                }
                row("颜色输出目标:") {
                    comboBox(listOf("browser", "terminal"))
                        .bindItem({ s.colorTarget }, { s.colorTarget = it ?: "browser" })
                        .comment("browser: 浏览器控制台 %c 样式 | terminal: ANSI 转义码")
                }
            }

            // ── 格式配置 ─────────────────────────────────────────────────────
            group("格式配置") {
                row {
                    checkBox("格式化复杂对象（使用 JSON.stringify，防止输出 [object Object]）")
                        .bindSelected({ s.formatComplexObjects }, { s.formatComplexObjects = it })
                        .comment("仅适用于 JavaScript / TypeScript")
                }
                row("参数数量:") {
                    spinner(1..3, 1)
                        .bindIntValue({ s.paramCount }, { s.paramCount = it })
                        .comment("1: 纯字符串标签  |  2: 标签 + 变量值  |  3: 位置信息 + 变量标签 + 变量值")
                }
                row("引号类型:") {
                    comboBox(listOf("double", "single"))
                        .bindItem({ s.quoteType }, { s.quoteType = it ?: "double" })
                        .comment("double: 双引号 (\")  |  single: 单引号 (')")
                }
                row {
                    checkBox("末尾添加分号 (;)")
                        .bindSelected({ s.useSemicolon }, { s.useSemicolon = it })
                }
            }
        }
    }
}
