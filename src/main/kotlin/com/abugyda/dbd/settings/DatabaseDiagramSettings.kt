package com.abugyda.dbd.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic

@State(
  name = "DbDiagramSettings",
  storages = [Storage("dbdiagram.xml")]
)
class DatabaseDiagramSettings : PersistentStateComponent<DatabaseDiagramSettings.State> {

  enum class EditorLayout(val displayName: String) {
    EDITOR_ONLY("Editor"),
    EDITOR_AND_PREVIEW("Editor and Preview"),
    PREVIEW_ONLY("Preview");

    override fun toString() = displayName
  }

  data class State(
    var defaultLineStyle: String = "Curve",
    var defaultShowGrid: Boolean = true,
    var defaultGridSize: Int = 16,
    var defaultSnapToGrid: Boolean = false,
    var defaultSnapAlignment: String = "grid",
    var overrideDiagramAlignment: Boolean = false,
    var defaultTheme: String = "System",
    var diagramFontSize: Int = 12,
    var fontSizes: MutableMap<String, Int> = mutableMapOf(),
    var targetFrameRate: Int = 60,
    /** Which panels to show when opening an .erd.yaml file */
    var defaultEditorLayout: EditorLayout = EditorLayout.EDITOR_AND_PREVIEW,
    var showTableNotes: Boolean = true,
    var showFieldNotes: Boolean = true,
    var tableNoteMaxLines: Int = 2,
    var fieldNoteMaxLines: Int = 2,
    var tableDensity: String = "compact",
    var groupMergeTolerance: Int = 32,
  )

  private var myState = State()

  override fun getState(): State = myState
  override fun loadState(state: State) {
    state.fontSizes = DiagramFonts.normalize(state.fontSizes)
    state.diagramFontSize = state.diagramFontSize.coerceIn(10, 24)
    state.targetFrameRate = state.targetFrameRate.coerceIn(30, 60)
    state.defaultGridSize = state.defaultGridSize.coerceIn(4, 64)
    if (state.tableDensity !in setOf("compact", "comfortable", "spacious")) state.tableDensity =
      "compact"
    state.groupMergeTolerance = state.groupMergeTolerance.coerceIn(0, 120)
    if (state.defaultSnapAlignment !in setOf("grid", "midpoint")) state.defaultSnapAlignment =
      "grid"
    myState = state
  }

  companion object {
    val instance: DatabaseDiagramSettings
      get() = ApplicationManager.getApplication().getService(DatabaseDiagramSettings::class.java)

    val TOPIC: Topic<SettingsChangedListener> =
      Topic.create("DbDiagramSettingsChanged", SettingsChangedListener::class.java)
  }

  interface SettingsChangedListener {
    fun onSettingsChanged(settings: State)
  }
}
