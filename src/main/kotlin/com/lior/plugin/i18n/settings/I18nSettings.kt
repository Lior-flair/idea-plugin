package com.lior.plugin.i18n.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "I18nSettings",
    storages = [Storage("i18n.xml")]
)
class I18nSettings : PersistentStateComponent<I18nSettings> {

    /** 来源语言：locale 文件中用作翻译基准的语言 */
    var sourceLanguage: String = "en"

    /** 显示语言：内联注释和树状视图中展示哪种语言的翻译 */
    var displayLanguage: String = "en"

    /**
     * locale 文件目录，逗号分隔，相对于项目根目录。
     * 支持 Glob patterns，例如：src/locales,locales,src/ ** /i18n
     */
    var localesPaths: String = "src/locales,locales,src/i18n,i18n,src/assets/i18n,public/locales"

    /**
     * 键名风格：
     *   flat   → {"home.title": "Welcome"}
     *   nested → {"home": {"title": "Welcome"}}
     */
    var keystyle: String = "nested"

    /** 是否启用行尾 Inlay 翻译（行尾显示翻译内容） */
    var annotations: Boolean = true

    /** 是否启用鼠标悬浮气泡（停留在 i18n key 上时弹出多语言对照） */
    var hoverEnabled: Boolean = true

    /**
     * 启用扫描的 locale 文件后缀，逗号分隔。
     * 留空或与 ALL_EXTENSIONS 相同时表示全部启用。
     */
    var enabledExtensions: String = ALL_EXTENSIONS.joinToString(",")

    /** 是否从项目 locale 目录中自动读取可用语言列表 */
    var autoDetectLanguages: Boolean = false

    /**
     * 启用的框架，逗号分隔。
     * 默认 "auto" 表示自动检测；可指定：i18next,vue-i18n,react-intl,ngx-translate,flutter
     */
    var enabledFrameworks: String = "auto"

    /**
     * locale 文件名前缀（basename），逗号分隔，留空表示不过滤。
     * 例如填写 "messages" 时，只扫描 messages.properties / messages_en.properties / messages_zh_CN.properties。
     * 支持多个前缀，如 "messages,validation,errors"。
     */
    var localeFilePrefix: String = ""

    override fun getState(): I18nSettings = this

    override fun loadState(state: I18nSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun getLocalesPathList(): List<String> =
        localesPaths.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun getEnabledFrameworkList(): List<String> =
        enabledFrameworks.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun getLocaleFilePrefixList(): List<String> =
        localeFilePrefix.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun getEnabledExtensionSet(): Set<String> {
        val list = enabledExtensions.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        return if (list.isEmpty()) ALL_EXTENSIONS.toSet() else list.toSet()
    }

    companion object {
        /** 插件支持的全部 locale 文件后缀（有序，用于 UI 展示）*/
        val ALL_EXTENSIONS = listOf("json", "yaml", "yml", "properties", "js", "ts")

        fun getInstance(): I18nSettings =
            ApplicationManager.getApplication().getService(I18nSettings::class.java)
    }
}
