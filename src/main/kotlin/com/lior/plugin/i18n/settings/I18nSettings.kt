package com.lior.plugin.i18n.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "I18nSettings",
    storages = [Storage("i18n-global.xml")]
)
class I18nSettings : PersistentStateComponent<I18nSettings> {

    /** 是否启用行尾 Inlay 翻译（全局偏好） */
    var annotations: Boolean = true

    /** 是否启用鼠标悬浮气泡（全局偏好） */
    var hoverEnabled: Boolean = true

    override fun getState(): I18nSettings = this

    override fun loadState(state: I18nSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): I18nSettings =
            ApplicationManager.getApplication().getService(I18nSettings::class.java)
    }
}
