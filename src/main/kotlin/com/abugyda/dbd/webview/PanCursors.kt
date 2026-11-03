package com.abugyda.dbd.webview

import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** AWT's HAND_CURSOR is a pointing hand. Panning needs Chromium's open and closed hands. */
object PanCursors {
  private val open by lazy { create(false) }
  private val closed by lazy { create(true) }

  fun get(grabbing: Boolean): Cursor = if (grabbing) closed else open

  internal fun image(grabbing: Boolean): BufferedImage = ImageIO.read(
    requireNotNull(PanCursors::class.java.getResource(
      if (grabbing) "/cursors/hand_grabbing.png" else "/cursors/hand_grab.png"
    ))
  )

  private fun create(grabbing: Boolean): Cursor {
    val fallback = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
    if (GraphicsEnvironment.isHeadless()) return fallback
    val toolkit = Toolkit.getDefaultToolkit()
    val size = toolkit.getBestCursorSize(32, 32)
    if (size.width == 0 || size.height == 0) return fallback
    // Preserve the asset's hotspot when the desktop requires a different cursor size.
    val image = image(grabbing)
    return toolkit.createCustomCursor(
      image.getScaledInstance(size.width, size.height, java.awt.Image.SCALE_SMOOTH),
      Point(13 * size.width / 32, 13 * size.height / 32),
      if (grabbing) "Diagram pan (closed hand)" else "Diagram pan (open hand)"
    )
  }
}
