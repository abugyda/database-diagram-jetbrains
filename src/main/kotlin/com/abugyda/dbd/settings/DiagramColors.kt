package com.abugyda.dbd.settings

import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Font
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage

object DiagramColors {
  private val defaults = mapOf(
    "--bg-color" to (0xf4f4f4 to 0x1e1f22),
    "--diagram-grid-color" to (0x9ca3af to 0x555b66),
    "--surface-color" to (0xffffff to 0x2b2d30),
    "--border-color" to (0xd1d5db to 0x4e5157),
    "--input-color" to (0xffffff to 0x2b2d30),
    "--text-color" to (0x1f2937 to 0xdfe1e5),
    "--muted-color" to (0x64748b to 0xa5adba),
    "--accent-color" to (0x2563eb to 0x7aa2f7),
    "--default-table-color" to (0x3b82f6 to 0x3b82f6),
    "--table-header-text" to (0xffffff to 0xffffff),
    "--table-description-color" to (0xffffff to 0xffffff),
    "--column-name-color" to (0x1f2937 to 0xdfe1e5),
    "--column-description-color" to (0x64748b to 0xa5adba),
    "--column-type-color" to (0x64748b to 0xa5adba),
    "--default-color" to (0xb45309 to 0xe9b65b),
    "--key-color" to (0xb7791f to 0xe9b65b),
    "--foreign-key-color" to (0x0f766e to 0x5bcbb7),
    "--indexed-bg" to (0xf5f3ff to 0x342f42),
    "--indexed-border" to (0xc4b5db to 0x655778),
    "--default-note-color" to (0xfef08a to 0xfef08a),
    "--note-header-color" to (0x1f2937 to 0x1f2937),
    "--note-text-color" to (0x1f2937 to 0x1f2937),
    "--group-label-color" to (0x1f2937 to 0xdfe1e5),
    "--relation-color" to (0x64748b to 0xa5adba)
  )
  data class Entry(val label: String, val token: String, val background: Boolean = false) {
    val key = ColorKey.createColorKey("ABUGYDA_DATABASE_DIAGRAM_" + token.removePrefix("--").replace('-', '_').uppercase(),
      defaults.getValue(token).let { JBColor(it.first, it.second) })
  }
  val entries = listOf(
    Entry("Canvas//Background", "--bg-color", true), Entry("Canvas//Grid dots", "--diagram-grid-color"),
    Entry("Surfaces//Background", "--surface-color", true), Entry("Surfaces//Borders and row dividers", "--border-color"),
    Entry("Surfaces//Input background", "--input-color", true), Entry("Text//Default text", "--text-color"),
    Entry("Text//Secondary text", "--muted-color"), Entry("Text//Accent", "--accent-color"),
    Entry("Tables//Default header background", "--default-table-color", true),
    Entry("Tables//Header text", "--table-header-text"), Entry("Tables//Description", "--table-description-color"),
    Entry("Columns//Name", "--column-name-color"), Entry("Columns//Description", "--column-description-color"),
    Entry("Columns//Type", "--column-type-color"), Entry("Columns//Default value", "--default-color"),
    Entry("Columns//Primary key icon", "--key-color"), Entry("Columns//Foreign key icon", "--foreign-key-color"),
    Entry("Columns//Indexed row background", "--indexed-bg", true), Entry("Columns//Indexed row divider", "--indexed-border"),
    Entry("Notes//Default background", "--default-note-color", true), Entry("Notes//Header text", "--note-header-color"),
    Entry("Notes//Body text", "--note-text-color"), Entry("Groups//Label text", "--group-label-color"),
    Entry("Relationships//Line", "--relation-color")
  )
  data class TextEntry(val role: String, val label: String, val foregroundToken: String? = null,
    val backgroundToken: String? = null, val autoContrast: Boolean = false) {
    val key = TextAttributesKey.createTextAttributesKey("ABUGYDA_DATABASE_DIAGRAM_TEXT_" + role.uppercase(), HighlighterColors.TEXT)
  }
  val textEntries = listOf(
    TextEntry("tableHeader", "Tables//Header", "--table-header-text", "--default-table-color", true),
    TextEntry("tableDescription", "Tables//Description", "--table-description-color", autoContrast = true),
    TextEntry("columnName", "Columns//Name", "--column-name-color"),
    TextEntry("columnDescription", "Columns//Description", "--column-description-color"),
    TextEntry("columnType", "Columns//Type", "--column-type-color"),
    TextEntry("defaultValue", "Columns//Default value", "--default-color"),
    TextEntry("nn", "Columns//Not null (NN)"), TextEntry("uq", "Columns//Unique (UQ)"),
    TextEntry("enm", "Type badges//Enum (ENM)"), TextEntry("rec", "Type badges//Record (REC)"),
    TextEntry("dom", "Type badges//Domain (DOM)"), TextEntry("rng", "Type badges//Range (RNG)"),
    TextEntry("arr", "Type badges//Array (ARR)"),
    TextEntry("check", "Column behavior//Check constraint (C)"),
    TextEntry("identity", "Column behavior//Identity (I)"),
    TextEntry("generated", "Column behavior//Generated (G)"),
    TextEntry("noteHeader", "Notes//Header text", "--note-header-color", autoContrast = true),
    TextEntry("noteBody", "Notes//Body text", "--note-text-color", autoContrast = true),
    TextEntry("groupLabel", "Groups//Label text", "--group-label-color"),
    TextEntry("relationLabel", "Relationships//Label")
  )
  private val textTokens get() = textEntries.flatMap { listOfNotNull(it.foregroundToken, it.backgroundToken) }.toSet()
  val colorEntries get() = entries.filter { it.token !in textTokens }

  private fun effective(scheme: EditorColorsScheme, entry: TextEntry): TextAttributes {
    val attributes = scheme.getAttributes(entry.key).clone()
    // Keep colors saved by the previous color-only settings page until replaced.
    for ((token, background) in listOf(entry.foregroundToken to false, entry.backgroundToken to true)) {
      val legacy = entries.find { it.token == token } ?: continue
      val color = scheme.getColor(legacy.key) ?: continue
      if (color.rgb != legacy.key.defaultColor?.rgb) {
        val default = defaultAttributes(scheme, entry)
        if (background && attributes.backgroundColor == default.backgroundColor) attributes.backgroundColor = color
        if (!background && attributes.foregroundColor == default.foregroundColor) attributes.foregroundColor = color
      }
    }
    return attributes
  }

  private fun defaultAttributes(scheme: EditorColorsScheme, entry: TextEntry): TextAttributes {
    val manager = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance()
    val background = scheme.defaultBackground
    val dark = background.red * 299 + background.green * 587 + background.blue * 114 < 128000
    return manager.getScheme(if (dark) "Darcula" else "Default")?.getAttributes(entry.key) ?: scheme.getAttributes(entry.key)
  }

  fun textStyles(scheme: EditorColorsScheme): Map<String, com.abugyda.dbd.webview.WebviewBridge.TextStyle> = textEntries.associate { entry ->
    val attributes = effective(scheme, entry)
    // Defaults use per-card automatic contrast, while explicit scheme colors win.
    val foreground = attributes.foregroundColor?.takeUnless {
      entry.autoContrast && it == defaultAttributes(scheme, entry).foregroundColor
    }
    entry.role to com.abugyda.dbd.webview.WebviewBridge.TextStyle(
      foreground = foreground?.let(::hex), background = attributes.backgroundColor?.takeUnless {
        entry.role in setOf("noteHeader", "noteBody") && it == defaultAttributes(scheme, entry).backgroundColor
      }?.let(::hex),
      bold = attributes.fontType and Font.BOLD != 0, italic = attributes.fontType and Font.ITALIC != 0)
  }

  fun overrides(scheme: EditorColorsScheme): Map<String, String> = buildMap {
    colorEntries.forEach { entry -> scheme.getColor(entry.key)?.let { put(entry.token, hex(it)) } }
    val styles = textStyles(scheme)
    textEntries.forEach { entry ->
      val style = styles.getValue(entry.role)
      entry.foregroundToken?.let { token -> style.foreground?.let { put(token, it) } }
      entry.backgroundToken?.let { token -> style.background?.let { put(token, it) } }
    }
  }
  private fun hex(color: java.awt.Color) = "#%02x%02x%02x".format(color.red, color.green, color.blue)
}

class DatabaseDiagramColorSettingsPage : ColorSettingsPage {
  override fun getDisplayName() = "Database Diagram"
  override fun getIcon() = AllIcons.Nodes.DataTables
  override fun getHighlighter() = PlainSyntaxHighlighter()
  override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DiagramColors.textEntries.map { AttributesDescriptor(it.label, it.key) }.toTypedArray()
  override fun getColorDescriptors(): Array<ColorDescriptor> = DiagramColors.colorEntries.map {
    ColorDescriptor(it.label, it.key, if (it.background) ColorDescriptor.Kind.BACKGROUND else ColorDescriptor.Kind.FOREGROUND)
  }.toTypedArray()
  override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> =
    DiagramColors.textEntries.associate { it.role to it.key }
  override fun getDemoText() = """
    <tableHeader>users</tableHeader>
    <tableDescription>Accounts that publish posts.</tableDescription>
      <columnName>id</columnName>        <columnType>uuid</columnType>          <nn>NN</nn> <uq>UQ</uq>
      <columnDescription>Unique account identifier.</columnDescription>
      <columnName>created_at</columnName> <columnType>timestamp</columnType> <defaultValue>= now()</defaultValue>
      <columnDescription>Time the account was created.</columnDescription>
      <columnName>gender</columnName> <enm>ENM</enm> <columnType>gender</columnType>
      <columnName>addresses</columnName> <rec>REC</rec> <arr>ARR</arr> <columnType>contact_address</columnType>
      <columnName>amount</columnName> <dom>DOM</dom> <columnType>non_negative_money</columnType> <check>C</check>
      <columnName>budget</columnName> <rng>RNG</rng> <columnType>price_range</columnType>
      <columnName>sequence_id</columnName> <columnType>bigint</columnType> <identity>I</identity>
      <columnName>line_total</columnName> <columnType>numeric(14,2)</columnType> <generated>G</generated>

    <noteHeader>Guide</noteHeader>
    <noteBody>Notes explain the diagram.</noteBody>
    <groupLabel>Publishing</groupLabel>  <relationLabel>users.id → posts.author_id</relationLabel>
  """.trimIndent()
}
