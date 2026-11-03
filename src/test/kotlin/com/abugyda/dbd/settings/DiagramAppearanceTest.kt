package com.abugyda.dbd.settings

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import javax.swing.JTabbedPane
import javax.swing.JTextField

class DiagramAppearanceTest : BasePlatformTestCase() {
  private fun descendants(component: Component): List<Component> = listOf(component) +
    if (component is Container) component.components.flatMap { descendants(it) } else emptyList()

  fun testLayoutSettingsApplyResetValidateAndPublish() {
    val service = DatabaseDiagramSettings.instance
    val original = service.state.copy(fontSizes = service.state.fontSizes.toMutableMap())
    val configurable = DatabaseDiagramConfigurable()
    try {
      service.loadState(DatabaseDiagramSettings.State())
      val tabs = configurable.createComponent() as JTabbedPane
      val controls = descendants(tabs.getComponentAt(0))
      val spacing = controls.filterIsInstance<javax.swing.JComboBox<*>>()
        .single { it.itemCount == 3 && it.getItemAt(0) == "Compact" }
      val tolerance = controls.filterIsInstance<JTextField>().single()
      assertEquals("Compact", spacing.selectedItem)
      assertEquals("32", tolerance.text)
      spacing.selectedItem = "Spacious"
      tolerance.text = "64"
      assertTrue(configurable.isModified)
      configurable.reset()
      assertEquals("Compact", spacing.selectedItem)
      assertEquals("32", tolerance.text)
      tabs.selectedIndex = 1 // Validate the layout controls even when their tab is hidden.
      tolerance.text = "121"
      try { configurable.apply(); fail("Out-of-range tolerance should prevent Apply") }
      catch (_: com.intellij.openapi.options.ConfigurationException) { }
      assertEquals(32, service.state.groupMergeTolerance)
      spacing.selectedItem = "Comfortable"
      tolerance.text = "64"
      var published: DatabaseDiagramSettings.State? = null
      val connection = com.intellij.openapi.application.ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
      connection.subscribe(DatabaseDiagramSettings.TOPIC, object : DatabaseDiagramSettings.SettingsChangedListener {
        override fun onSettingsChanged(settings: DatabaseDiagramSettings.State) { published = settings }
      })
      configurable.apply()
      assertEquals("comfortable", service.state.tableDensity)
      assertEquals(64, service.state.groupMergeTolerance)
      assertEquals("comfortable", published?.tableDensity)
      assertEquals(64, published?.groupMergeTolerance)
      assertFalse(configurable.isModified)
    } finally {
      configurable.disposeUIResources()
      service.loadState(original)
    }
  }

  fun testLayoutSettingsNormalizeAndSerializeForWebview() {
    val settings = DatabaseDiagramSettings()
    settings.loadState(settings.state.copy(tableDensity = "spacious", groupMergeTolerance = 64))
    assertEquals("spacious", settings.state.tableDensity)
    assertEquals(64, settings.state.groupMergeTolerance)
    val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    val global = com.abugyda.dbd.webview.WebviewBridge.GlobalSettings("Curve", true, 16,
      tableDensity = settings.state.tableDensity, groupMergeTolerance = settings.state.groupMergeTolerance)
    val json = mapper.readTree(mapper.writeValueAsString(
      com.abugyda.dbd.webview.WebviewBridge.Server.UpdateGlobalSettings(global)))
    assertEquals("spacious", json["settings"]["tableDensity"].asText())
    assertEquals(64, json["settings"]["groupMergeTolerance"].asInt())
    settings.loadState(settings.state.copy(tableDensity = "invalid", groupMergeTolerance = -1))
    assertEquals("compact", settings.state.tableDensity)
    assertEquals(0, settings.state.groupMergeTolerance)
    settings.loadState(settings.state.copy(groupMergeTolerance = 999))
    assertEquals(120, settings.state.groupMergeTolerance)
  }

  fun testTabsApplyResetAndPublishIndependentFontSizes() {
    val service = DatabaseDiagramSettings.instance
    val original = service.state.copy(fontSizes = service.state.fontSizes.toMutableMap())
    val configurable = DatabaseDiagramConfigurable()
    try {
      service.loadState(DatabaseDiagramSettings.State())
      val tabs = configurable.createComponent() as JTabbedPane
      assertEquals(listOf("General", "Fonts", "Grid and Snapping", "Descriptions"), (0 until tabs.tabCount).map { tabs.getTitleAt(it) })
      val fields = descendants(tabs.getComponentAt(1)).filterIsInstance<JTextField>()
      assertSize(DiagramFonts.roles.size + 1, fields)
      val tableHeader = fields[1]
      tableHeader.text = "22"
      assertTrue(configurable.isModified)
      configurable.reset()
      assertEquals("0", tableHeader.text)
      tableHeader.text = "1"
      try { configurable.apply(); fail("Invalid font size should prevent Apply") }
      catch (_: com.intellij.openapi.options.ConfigurationException) { }
      assertTrue(service.state.fontSizes.isEmpty())
      tableHeader.text = "22"
      var published: DatabaseDiagramSettings.State? = null
      val connection = com.intellij.openapi.application.ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
      connection.subscribe(DatabaseDiagramSettings.TOPIC, object : DatabaseDiagramSettings.SettingsChangedListener {
        override fun onSettingsChanged(settings: DatabaseDiagramSettings.State) { published = settings }
      })
      configurable.apply()
      assertEquals(22, service.state.fontSizes["tableHeader"])
      assertEquals(22, published?.fontSizes?.get("tableHeader"))
      assertFalse(configurable.isModified)
    } finally {
      configurable.disposeUIResources()
      service.loadState(original)
    }
  }

  fun testSchemeColorsHaveDescriptorsPreviewTagsAndResolvableCssTokens() {
    val page = DatabaseDiagramColorSettingsPage()
    assertEquals("Database Diagram", page.displayName)
    val descriptors = page.colorDescriptors
    assertEquals(DiagramColors.colorEntries.size, descriptors.size)
    assertEquals(descriptors.size, descriptors.map { it.key }.toSet().size)
    assertEquals(DiagramColors.textEntries.size, page.attributeDescriptors.size)
    assertEquals(DiagramColors.textEntries.size, page.additionalHighlightingTagToDescriptorMap.size)
    val scheme = EditorColorsManager.getInstance().globalScheme.clone() as com.intellij.openapi.editor.colors.EditorColorsScheme
    val canvas = DiagramColors.entries.single { it.token == "--bg-color" }
    val column = DiagramColors.entries.single { it.token == "--column-name-color" }
    val combined = DiagramColors.entries.single { it.token == "--combined-key-color" }
    scheme.setColor(canvas.key, java.awt.Color(0x123456))
    scheme.setColor(column.key, java.awt.Color(0xabcdef))
    scheme.setColor(combined.key, java.awt.Color(0x22bb66))
    val tokens = DiagramColors.overrides(scheme)
    assertEquals("#123456", tokens["--bg-color"])
    assertEquals("#abcdef", tokens["--column-name-color"])
    assertEquals("#22bb66", tokens["--combined-key-color"])
    assertTrue(page.demoText.contains("<nn>NN</nn>"))
    assertTrue(com.intellij.openapi.options.colors.ColorSettingsPage.EP_NAME.extensionList.any { it is DatabaseDiagramColorSettingsPage })
  }

  fun testDefaultsAndBadgeTextAttributesSupportForegroundBackgroundBoldAndItalic() {
    val manager = EditorColorsManager.getInstance()
    for (name in listOf("Default", "Darcula")) {
      val scheme = manager.getScheme(name)!!
      DiagramColors.colorEntries.forEach { assertNotNull(it.label, scheme.getColor(it.key)) }
      DiagramColors.textEntries.forEach { assertNotNull(it.label, scheme.getAttributes(it.key).foregroundColor) }
      for (role in listOf("nn", "uq", "enm", "rec", "dom", "rng", "arr", "check", "identity", "generated")) {
        assertNotNull(scheme.getAttributes(DiagramColors.textEntries.single { it.role == role }.key).backgroundColor)
      }
    }
    val scheme = manager.globalScheme.clone() as com.intellij.openapi.editor.colors.EditorColorsScheme
    val badge = DiagramColors.textEntries.single { it.role == "nn" }
    val attributes = scheme.getAttributes(badge.key).clone()
    attributes.foregroundColor = java.awt.Color(0x123456)
    attributes.backgroundColor = java.awt.Color(0xfedcba)
    attributes.fontType = java.awt.Font.BOLD or java.awt.Font.ITALIC
    scheme.setAttributes(badge.key, attributes)
    val style = DiagramColors.textStyles(scheme).getValue("nn")
    assertEquals("#123456", style.foreground)
    assertEquals("#fedcba", style.background)
    assertTrue(style.bold)
    assertTrue(style.italic)
    val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    val json = mapper.readTree(mapper.writeValueAsString(com.abugyda.dbd.webview.WebviewBridge.Server.UpdateTheme(
      "dark", DiagramColors.overrides(scheme), DiagramColors.textStyles(scheme))))
    assertTrue(json["textStyles"]["nn"]["bold"].asBoolean())
  }

  fun testFontOverridesNormalizeAndRoundTripThroughBridge() {
    val service = DatabaseDiagramSettings()
    service.loadState(DatabaseDiagramSettings.State(fontSizes = mutableMapOf("tableHeader" to 80,
      "columnName" to 1, "noteBody" to 0, "unknown" to 20)))
    assertEquals(mapOf("tableHeader" to 48, "columnName" to 8), service.state.fontSizes)
    val global = com.abugyda.dbd.webview.WebviewBridge.GlobalSettings("Curve", true, 16, fontSizes = service.state.fontSizes)
    val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    val json = mapper.readTree(mapper.writeValueAsString(com.abugyda.dbd.webview.WebviewBridge.Server.UpdateGlobalSettings(global)))
    assertEquals(48, json["settings"]["fontSizes"]["tableHeader"].asInt())
  }
}
