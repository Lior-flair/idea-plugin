package com.lior.plugin.i18n.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.lior.plugin.i18n.service.LocaleFileService

/** 两个视图面板共享的数据加载与存储。 */
class I18nToolWindowData(val project: Project) {

    var languages: List<String> = emptyList()
        private set
    var allKeys: List<String> = emptyList()
        private set
    var allTranslations: Map<String, Map<String, String>> = emptyMap()
        private set

    private val listeners = mutableListOf<(loading: Boolean) -> Unit>()

    fun addListener(l: (loading: Boolean) -> Unit) = listeners.add(l)

    fun refresh() {
        listeners.forEach { it(true) }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val service = LocaleFileService.getInstance(project)
                val langs   = service.detectAvailableLanguages()
                val trans   = langs.associateWith { service.getTranslationsForLanguage(it) }
                val keys    = trans.values.flatMap { it.keys }.toSortedSet().toList()

                ApplicationManager.getApplication().invokeLater {
                    languages        = langs
                    allKeys          = keys
                    allTranslations  = trans
                    listeners.forEach { it(false) }
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    listeners.forEach { it(false) }
                }
            }
        }
    }
}
