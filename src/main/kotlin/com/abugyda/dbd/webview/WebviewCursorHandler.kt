package com.abugyda.dbd.webview

import org.cef.browser.CefBrowser
import org.cef.handler.CefDisplayHandlerAdapter
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import javax.swing.SwingUtilities
import com.intellij.openapi.wm.IdeGlassPane

/** Mirror JCEF's AWT cursor on Swing surfaces without suppressing its native cursor handling. */
class WebviewCursorHandler(
  private val targets: () -> List<Component>,
  private val isActive: () -> Boolean,
  private val glassPane: () -> IdeGlassPane? = { null },
) : CefDisplayHandlerAdapter() {
  private var cursorPane: IdeGlassPane? = null

  fun onCssCursorChange(name: String?) {
    SwingUtilities.invokeLater {
      if (isActive()) {
        val cursor = name?.let {
          when (it) {
            "grab" -> PanCursors.get(false)
            "grabbing" -> PanCursors.get(true)
            else -> Cursor.getPredefinedCursor(cssCursorType(it))
          }
        }
        targets().distinct().forEach { applyCursor(it, cursor ?: Cursor.getDefaultCursor()) }
        cursorPane?.setCursor(null, this)
        cursorPane = if (cursor == null) null else glassPane()
        cursorPane?.setCursor(cursor, this)
      }
    }
  }

  fun reset() {
    val clear = Runnable {
      cursorPane?.setCursor(null, this)
      cursorPane = null
      targets().distinct().forEach { applyCursor(it, Cursor.getDefaultCursor()) }
    }
    if (SwingUtilities.isEventDispatchThread()) clear.run() else SwingUtilities.invokeLater(clear)
  }

  companion object {
    fun cssCursorType(name: String): Int = when (name) {
      "pointer" -> Cursor.HAND_CURSOR
      "move", "all-scroll" -> Cursor.MOVE_CURSOR
      "text", "vertical-text" -> Cursor.TEXT_CURSOR
      "wait", "progress" -> Cursor.WAIT_CURSOR
      "crosshair", "cell" -> Cursor.CROSSHAIR_CURSOR
      "e-resize", "w-resize", "ew-resize", "col-resize" -> Cursor.E_RESIZE_CURSOR
      "n-resize", "s-resize", "ns-resize", "row-resize" -> Cursor.N_RESIZE_CURSOR
      "ne-resize", "sw-resize", "nesw-resize" -> Cursor.NE_RESIZE_CURSOR
      "nw-resize", "se-resize", "nwse-resize" -> Cursor.NW_RESIZE_CURSOR
      else -> Cursor.DEFAULT_CURSOR
    }
  }

  override fun onCursorChange(browser: CefBrowser?, cursorType: Int): Boolean {
    if (!isActive()) return false
    val cursor = Cursor.getPredefinedCursor(
      if (cursorType in Cursor.DEFAULT_CURSOR..Cursor.MOVE_CURSOR) cursorType else Cursor.DEFAULT_CURSOR
    )
    SwingUtilities.invokeLater {
      if (isActive()) targets().distinct().forEach { applyCursor(it, cursor) }
    }
    // Windowed JCEF uses a native child window: setting its Swing parent's cursor
    // is insufficient. Returning true suppresses Chromium's update for that window
    // (and JCEF's own offscreen render handler), leaving the visible cursor unchanged.
    return false
  }

  private fun applyCursor(component: Component, cursor: Cursor) {
    component.cursor = cursor
    // A child canvas can have an explicit cursor that masks the parent's cursor.
    if (component is Container) component.components.forEach { applyCursor(it, cursor) }
  }
}
