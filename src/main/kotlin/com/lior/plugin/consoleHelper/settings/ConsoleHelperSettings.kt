package com.lior.plugin.consoleHelper.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "ConsoleHelperSettings",
    storages = [Storage("consoleHelper.xml")]
)
class ConsoleHelperSettings : PersistentStateComponent<ConsoleHelperSettings> {

    var logFunction: String = "console.log"
    var availableLogFunctions: String = "console.log,console.debug,console.warn,console.error,console.info,DEBUG_LOG,print"
    var prefix: String = "🚀"
    var showLineNumber: Boolean = true
    var showFileName: Boolean = true
    var useRandomColor: Boolean = false
    var customColor: String = "#00ff00"
    var customBgColor: String = ""
    var fontSize: Int = 14
    var paramCount: Int = 2
    var quoteType: String = "double"
    var useSemicolon: Boolean = true
    var formatComplexObjects: Boolean = false
    var colorTarget: String = "browser"

    override fun getState(): ConsoleHelperSettings = this

    override fun loadState(state: ConsoleHelperSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun getQuoteChar(): String = if (quoteType == "single") "'" else "\""

    fun getSemicolon(): String = if (useSemicolon) ";" else ""

    fun getLogFunctionList(): List<String> =
        availableLogFunctions.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        fun getInstance(): ConsoleHelperSettings =
            ApplicationManager.getApplication().getService(ConsoleHelperSettings::class.java)
    }
}
