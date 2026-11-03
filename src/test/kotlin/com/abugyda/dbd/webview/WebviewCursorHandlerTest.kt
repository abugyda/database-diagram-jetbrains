package com.abugyda.dbd.webview

import org.junit.Test
import org.junit.Assert.*
import java.awt.Cursor
import javax.swing.JPanel
import javax.swing.SwingUtilities

class WebviewCursorHandlerTest {
  @Test
  fun explicitWebCursorsReachTheSurfaceAndIdeGlassPaneAndReleaseOnExit() {
    val root = JPanel()
    val overrides = mutableListOf<Cursor?>()
    val pane = java.lang.reflect.Proxy.newProxyInstance(
      com.intellij.openapi.wm.IdeGlassPane::class.java.classLoader,
      arrayOf(com.intellij.openapi.wm.IdeGlassPane::class.java)
    ) { _, method, args ->
      if (method.name == "setCursor") overrides.add(args?.get(0) as Cursor?)
      null
    } as com.intellij.openapi.wm.IdeGlassPane
    val handler = WebviewCursorHandler({ listOf(root) }, { true }, { pane })
    for ((css, type) in listOf(
      "pointer" to Cursor.HAND_CURSOR,
      "move" to Cursor.MOVE_CURSOR, "col-resize" to Cursor.E_RESIZE_CURSOR,
      "nwse-resize" to Cursor.NW_RESIZE_CURSOR, "text" to Cursor.TEXT_CURSOR
    )) {
      handler.onCssCursorChange(css)
      SwingUtilities.invokeAndWait { }
      assertEquals(type, root.cursor.type)
      assertEquals(type, overrides.last()?.type)
    }
    handler.onCssCursorChange(null)
    SwingUtilities.invokeAndWait { }
    assertEquals(Cursor.DEFAULT_CURSOR, root.cursor.type)
    assertNull(overrides.last())
    handler.onCssCursorChange("move")
    SwingUtilities.invokeAndWait { }
    handler.reset()
    SwingUtilities.invokeAndWait { }
    assertNull(overrides.last())
    assertEquals(Cursor.DEFAULT_CURSOR, root.cursor.type)
    val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    val message = mapper.readValue(
      """{"type":"UPDATE_CURSOR","cursor":"move"}""",
      WebviewBridge.Client::class.java
    )
    assertEquals("move", (message as WebviewBridge.Client.UpdateCursor).cursor)
  }

  @Test
  fun panningUsesDistinctOpenAndClosedHandsInsteadOfTheLinkPointer() {
    val open = PanCursors.image(false)
    val closed = PanCursors.image(true)
    assertEquals(32, open.width)
    assertEquals(32, open.height)
    assertEquals(32, closed.width)
    assertEquals(32, closed.height)
    assertTrue(open.colorModel.hasAlpha())
    assertTrue(closed.colorModel.hasAlpha())
    assertFalse(
      open.getRGB(0, 0, 32, 32, null, 0, 32).contentEquals(closed.getRGB(0, 0, 32, 32, null, 0, 32))
    )
    val root = JPanel()
    val handler = WebviewCursorHandler({ listOf(root) }, { true })
    for ((css, grabbing) in listOf("grab" to false, "grabbing" to true)) {
      handler.onCssCursorChange(css)
      SwingUtilities.invokeAndWait { }
      assertSame(PanCursors.get(grabbing), root.cursor)
      assertNotEquals(Cursor.HAND_CURSOR, root.cursor.type)
    }
    handler.onCssCursorChange("pointer")
    SwingUtilities.invokeAndWait { }
    assertEquals(Cursor.HAND_CURSOR, root.cursor.type)
  }

  @Test
  fun cursorChangesReachChildrenAndResetWithoutSuppressingJcefHandling() {
    val root = JPanel()
    val child = JPanel()
    val canvas = JPanel()
    child.add(canvas)
    root.add(child)
    val handler = WebviewCursorHandler({ listOf(root) }, { true })
    for (type in listOf(
      Cursor.HAND_CURSOR,
      Cursor.MOVE_CURSOR,
      Cursor.E_RESIZE_CURSOR,
      Cursor.TEXT_CURSOR,
      Cursor.DEFAULT_CURSOR
    )) {
      assertFalse(
        "JCEF must still update its native window or offscreen surface",
        handler.onCursorChange(null, type)
      )
      SwingUtilities.invokeAndWait { }
      assertEquals(type, root.cursor.type)
      assertEquals(type, canvas.cursor.type)
    }
    handler.onCursorChange(null, -1)
    SwingUtilities.invokeAndWait { }
    assertEquals(Cursor.DEFAULT_CURSOR, canvas.cursor.type)
  }

  @Test
  fun closedWebviewsDoNotChangeCursors() {
    val root = JPanel()
    root.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
    val handler = WebviewCursorHandler({ listOf(root) }, { false })
    assertFalse(handler.onCursorChange(null, Cursor.MOVE_CURSOR))
    SwingUtilities.invokeAndWait { }
    assertEquals(Cursor.DEFAULT_CURSOR, root.cursor.type)
  }

  @Test
  fun queuedCursorChangesAreIgnoredAfterDisposal() {
    val root = JPanel()
    root.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
    var active = true
    val handler = WebviewCursorHandler({ listOf(root) }, { active })
    SwingUtilities.invokeAndWait {
      assertFalse(handler.onCursorChange(null, Cursor.MOVE_CURSOR))
      active = false
    }
    SwingUtilities.invokeAndWait { }
    assertEquals(Cursor.DEFAULT_CURSOR, root.cursor.type)
  }
}
