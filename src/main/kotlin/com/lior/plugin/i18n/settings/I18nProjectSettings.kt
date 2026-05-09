package com.lior.plugin.i18n.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "I18nProjectSettings",
    storages = [Storage("i18n.xml")]
)
@Service(Service.Level.PROJECT)
class I18nProjectSettings : PersistentStateComponent<I18nProjectSettings> {

    var sourceLanguage: String = "en"
    var displayLanguage: String = "en"
    var localesPaths: String = "src/locales,locales,src/i18n,i18n,src/assets/i18n,public/locales"
    var keystyle: String = "nested"
    var enabledFrameworks: String = "auto"
    var localeFilePrefix: String = ""
    var enabledExtensions: String = ALL_EXTENSIONS.joinToString(",")
    var autoDetectLanguages: Boolean = false

    override fun getState(): I18nProjectSettings = this

    override fun loadState(state: I18nProjectSettings) {
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
        val ALL_EXTENSIONS = listOf("json", "yaml", "yml", "properties", "js", "ts")

        fun getInstance(project: Project): I18nProjectSettings =
            project.getService(I18nProjectSettings::class.java)
    }
}
