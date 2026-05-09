package com.lior.plugin.i18n.annotation

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

/**
 * 行尾 Inlay 渲染器：绘制灰色翻译提示文字。
 * 通过 InlayModel.addAfterLineEndElement 写入编辑器，
 * 不依赖 EditorLinePainter 的绘制回调，持久且可靠。
 */
class I18nInlayRenderer(val text: String) : EditorCustomElementRenderer {

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val editor = inlay.editor
        val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        return editor.contentComponent.getFontMetrics(font).stringWidth(text)
    }

    override fun paint(
        inlay: Inlay<*>,
        g: Graphics,
        targetRegion: Rectangle,
        textAttributes: TextAttributes
    ) {
        val editor = inlay.editor
        g.color = HINT_COLOR
        g.font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        g.drawString(text, targetRegion.x, targetRegion.y + editor.ascent)
    }

    companion object {
        val HINT_COLOR: Color = JBColor(Color(140, 140, 140), Color(150, 150, 150))
    }
}
