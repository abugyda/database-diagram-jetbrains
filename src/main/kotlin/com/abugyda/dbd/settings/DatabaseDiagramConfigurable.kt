package com.abugyda.dbd.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBScrollPane

class DatabaseDiagramConfigurable : Configurable {

  private val settings = DatabaseDiagramSettings.instance
  private val panels = mutableListOf<DialogPanel>()
  private val fontFields = linkedMapOf<String, javax.swing.JTextField>()
  private var groupMergeToleranceField: javax.swing.JTextField? = null

  override fun getDisplayName(): String = "Database Diagram"

  override fun createComponent(): JComponent {
    panels.clear()
    fontFields.clear()
    groupMergeToleranceField = null
    val general = panel {

      group("Editor") {
        row("Default layout:") {
          comboBox(DatabaseDiagramSettings.EditorLayout.entries)
            .bindItem(
              getter = { settings.state.defaultEditorLayout },
              setter = {
                settings.state.defaultEditorLayout =
                  it ?: DatabaseDiagramSettings.EditorLayout.EDITOR_AND_PREVIEW
              }
            )
            .comment("Which panels to show when opening an .erd.yaml file")
        }
      }

      group("Defaults") {
        row("Line Style:") {
          comboBox(listOf("Curve", "Rectilinear", "RoundRectilinear", "Oblique", "RoundOblique"))
            .bindItem(
              getter = { settings.state.defaultLineStyle },
              setter = { settings.state.defaultLineStyle = it ?: "Curve" }
            )
        }
      }

      group("Layout") {
        row("Table spacing:") {
          comboBox(listOf("Compact", "Comfortable", "Spacious"))
            .bindItem(
              getter = { when (settings.state.tableDensity) {
                "comfortable" -> "Comfortable"
                "spacious" -> "Spacious"
                else -> "Compact"
              } },
              setter = { settings.state.tableDensity = when (it) {
                "Comfortable" -> "comfortable"
                "Spacious" -> "spacious"
                else -> "compact"
              } }
            )
            .comment("Padding inside table headers and column rows. Individual diagrams can save their own spacing.")
        }
        row("Group merge tolerance (px):") {
          intTextField(range = 0..120)
            .bindIntText(
              getter = { settings.state.groupMergeTolerance },
              setter = { settings.state.groupMergeTolerance = it }
            ).also { groupMergeToleranceField = it.component }
            .comment("32 px is the default. Higher values fill small gaps and remove small outline steps. Individual diagrams can save their own tolerance.")
        }
      }

      group("Rendering") {
        row("Offscreen frame-rate target:") {
          comboBox(listOf(30, 60))
            .bindItem(getter = { settings.state.targetFrameRate }, setter = { settings.state.targetFrameRate = it ?: 60 })
            .comment("60 FPS recommended. Applies to offscreen JCEF rendering; actual FPS depends on the IDE and hardware.")
        }
      }

      group("Appearance") {
        row("Theme:") {
          comboBox(listOf("System", "Light", "Dark"))
            .bindItem(
              getter = { settings.state.defaultTheme },
              setter = { settings.state.defaultTheme = it ?: "System" }
            )
            .comment("System follows the IDE theme. Customize colors under Editor → Color Scheme → Database Diagram.")
        }
      }

    }
    val fonts = panel {
      group("Default size") {
        row("Diagram font size (px):") {
          intTextField(range = 10..24)
            .bindIntText(
              getter = { settings.state.diagramFontSize },
              setter = { settings.state.diagramFontSize = it }
            ).also { fontFields["default"] = it.component }
            .comment("12 px is the default. Scales table, note, group and details text in all open diagrams.")
        }
      }
      group("Individual sizes") {
        row { comment("Sizes are in pixels. Use 0 to inherit the default text hierarchy (8–48 px for an override).") }
        for ((role, label) in DiagramFonts.roles) {
          row("$label:") {
            intTextField(range = 0..48)
              .bindIntText(getter = { settings.state.fontSizes[role] ?: 0 }, setter = {
                if (it == 0) settings.state.fontSizes.remove(role) else settings.state.fontSizes[role] = it.coerceAtLeast(8)
              }).also { fontFields[role] = it.component }
              .validationOnInput { field ->
                val value = field.text.toIntOrNull()
                if (value != null && value in 1..7) com.intellij.openapi.ui.ValidationInfo("Use 0 or a size between 8 and 48", field) else null
              }
              .validationOnApply { field ->
                val value = field.text.toIntOrNull()
                if (value != null && value in 1..7) com.intellij.openapi.ui.ValidationInfo("Use 0 or a size between 8 and 48", field) else null
              }
          }
        }
      }
    }
    val grid = panel {
      group("Grid and snapping") {
        row {
          checkBox("Override diagram grid and snapping settings")
            .bindSelected(getter = { settings.state.overrideDiagramAlignment }, setter = { settings.state.overrideDiagramAlignment = it })
            .comment("Off: global defaults with per-diagram overrides. On: enforce these settings in every diagram without replacing its saved preferences.")
        }
        row("Grid and snap spacing (px):") {
          intTextField(range = 4..64)
            .bindIntText(
              getter = { settings.state.defaultGridSize },
              setter = { settings.state.defaultGridSize = it }
            )
            .comment("Shared by the grid and snapping. 16 px recommended; 8 px for finer placement, 32 px for wider spacing")
        }
        row {
          checkBox("Show grid")
            .bindSelected(
              getter = { settings.state.defaultShowGrid },
              setter = { settings.state.defaultShowGrid = it }
            )
        }
        row {
          checkBox("Snap to grid")
            .bindSelected(getter = { settings.state.defaultSnapToGrid }, setter = { settings.state.defaultSnapToGrid = it })
            .comment("Snapping works even when the grid is hidden")
        }
        row("Snap alignment:") {
          comboBox(listOf("Grid points", "Between grid points"))
            .bindItem(
              getter = { if (settings.state.defaultSnapAlignment == "midpoint") "Between grid points" else "Grid points" },
              setter = { settings.state.defaultSnapAlignment = if (it == "Between grid points") "midpoint" else "grid" }
            )
        }
      }

    }
    val descriptions = panel {
      group("Doc-Comment Notes") {
        lateinit var showTableNotes: Cell<javax.swing.JCheckBox>
        lateinit var showFieldNotes: Cell<javax.swing.JCheckBox>

        row {
          showTableNotes = checkBox("Show table header notes")
            .bindSelected(
              getter = { settings.state.showTableNotes },
              setter = { settings.state.showTableNotes = it }
            )
        }
        row("Max table note lines:") {
          intTextField(range = 0..10)
            .bindIntText(
              getter = { settings.state.tableNoteMaxLines },
              setter = { settings.state.tableNoteMaxLines = it }
            )
            .comment("0 = show all lines (no clamp)")
            .enabledIf(showTableNotes.selected)
        }
        row {
          showFieldNotes = checkBox("Show column / field notes")
            .bindSelected(
              getter = { settings.state.showFieldNotes },
              setter = { settings.state.showFieldNotes = it }
            )
        }
        row("Max field note lines:") {
          intTextField(range = 0..10)
            .bindIntText(
              getter = { settings.state.fieldNoteMaxLines },
              setter = { settings.state.fieldNoteMaxLines = it }
            )
            .comment("0 = show all lines (no clamp)")
            .enabledIf(showFieldNotes.selected)
        }
      }
    }
    panels.addAll(listOf(general, fonts, grid, descriptions))
    return JBTabbedPane().apply {
      listOf("General", "Fonts", "Grid and Snapping", "Descriptions").zip(panels).forEach { (title, contents) ->
        addTab(title, JBScrollPane(contents).apply { border = null })
      }
    }
  }

  override fun isModified(): Boolean = panels.any { it.isModified() }

  override fun apply() {
    groupMergeToleranceField?.let { field ->
      val value = field.text.toIntOrNull()
      if (value == null || value !in 0..120) throw com.intellij.openapi.options.ConfigurationException(
        "Group merge tolerance must be between 0 and 120 px")
    }
    // Validate inactive tabs too: DialogPanel skips controls that are not showing.
    for ((role, field) in fontFields) {
      val value = field.text.toIntOrNull()
      val valid = if (role == "default") value != null && value in 10..24
        else value != null && (value == 0 || value in 8..48)
      if (!valid) throw com.intellij.openapi.options.ConfigurationException(
        if (role == "default") "Default font size must be between 10 and 24 px"
        else "${DiagramFonts.roles[role]}: use 0 or a size between 8 and 48 px")
    }
    panels.flatMap { it.validateAll() }.firstOrNull()?.let {
      throw com.intellij.openapi.options.ConfigurationException(it.message)
    }
    panels.forEach { it.apply() }
    ApplicationManager.getApplication().messageBus
      .syncPublisher(DatabaseDiagramSettings.TOPIC)
      .onSettingsChanged(settings.state)
  }

  override fun reset() { panels.forEach { it.reset() } }

  override fun disposeUIResources() { panels.clear(); fontFields.clear(); groupMergeToleranceField = null }
}
