package com.abugyda.dbd.editor

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.JBColor
import com.abugyda.dbd.settings.DatabaseDiagramSettings
import com.abugyda.dbd.webview.WebviewBridge
import com.abugyda.dbd.webview.WebviewPanel
import org.jetbrains.yaml.psi.YAMLFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class SchemaPreviewFileEditor(
  private val project: Project,
  private val file: VirtualFile,
  document: Document
) : UserDataHolderBase(), FileEditor, WebviewPanel.WebviewListener {

  @Volatile
  var isDisposed = false; private set

  @Volatile
  var pendingLayoutSaves = 0; private set

  private val webviewPanel = WebviewPanel(this, file, this, project)
  private val refreshController = SchemaRefreshController(project, file, document, this,
    canRefresh = { !isDisposed && !project.isDisposed && pendingLayoutSaves == 0 },
    deliver = { publishSchema(it) }, reportError = { webviewPanel.showSchemaError(it) })

  init {
    val conn = ApplicationManager.getApplication().messageBus.connect(this)
    conn.subscribe(LafManagerListener.TOPIC, object : LafManagerListener {
      override fun lookAndFeelChanged(source: LafManager) {
        updateTheme()
      }
    })
    conn.subscribe(com.intellij.openapi.editor.colors.EditorColorsManager.TOPIC,
      com.intellij.openapi.editor.colors.EditorColorsListener { updateTheme() })
    conn.subscribe(
      DatabaseDiagramSettings.TOPIC,
      object : DatabaseDiagramSettings.SettingsChangedListener {
        override fun onSettingsChanged(settings: DatabaseDiagramSettings.State) {
          if (!isDisposed) {
            pushSettings(settings); updateTheme()
            webviewPanel.updateFrameRate(settings.targetFrameRate)
          }
        }
      })
  }

  override fun getComponent(): JComponent = webviewPanel.component
  override fun getPreferredFocusedComponent(): JComponent = webviewPanel.component
  override fun getName(): String = "ERD Preview"

  private fun pushSettings(settings: DatabaseDiagramSettings.State) {
    webviewPanel.updateGlobalSettings(
      lineStyle = settings.defaultLineStyle,
      showGrid = settings.defaultShowGrid,
      gridSize = settings.defaultGridSize,
      snapToGrid = settings.defaultSnapToGrid,
      snapAlignment = settings.defaultSnapAlignment,
      overrideDiagramAlignment = settings.overrideDiagramAlignment,
      showTableNotes = settings.showTableNotes,
      showFieldNotes = settings.showFieldNotes,
      tableNoteMaxLines = settings.tableNoteMaxLines,
      fieldNoteMaxLines = settings.fieldNoteMaxLines,
      fontSize = settings.diagramFontSize,
      fontSizes = settings.fontSizes,
      tableDensity = settings.tableDensity,
      groupMergeTolerance = settings.groupMergeTolerance,
    )
  }

  private fun publishSchema(payload: WebviewBridge.SchemaPayload) {
    val s = DatabaseDiagramSettings.instance.state
    webviewPanel.updateSchemaPayload(
      payload, WebviewBridge.GlobalSettings(
        lineStyle = s.defaultLineStyle, showGrid = s.defaultShowGrid, gridSize = s.defaultGridSize,
        snapToGrid = s.defaultSnapToGrid, snapAlignment = s.defaultSnapAlignment,
        overrideDiagramAlignment = s.overrideDiagramAlignment,
        showTableNotes = s.showTableNotes, showFieldNotes = s.showFieldNotes,
        tableNoteMaxLines = s.tableNoteMaxLines, fieldNoteMaxLines = s.fieldNoteMaxLines,
        fontSize = s.diagramFontSize,
        fontSizes = s.fontSizes,
        tableDensity = s.tableDensity,
        groupMergeTolerance = s.groupMergeTolerance,
      )
    )
  }

  // FIX: WebviewPanel calls this once during initBrowser() to get the theme
  // string so it can inject `<body class="dark">` into the HTML before loading.
  // This eliminates the white flash — the body background is correct from the
  // very first painted frame.
  override fun resolveInitialTheme(): String = computeTheme()

  override fun resolveThemeColors(): Map<String, String> {
    val scheme = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance().globalScheme
    val overrides = com.abugyda.dbd.settings.DiagramColors.overrides(scheme)
    if (DatabaseDiagramSettings.instance.state.defaultTheme in setOf("Light", "Dark")) return overrides
    val dark = computeTheme() == "dark"
    val fallbackBackground = if (dark) java.awt.Color(0x1e1f22) else java.awt.Color(0xf4f4f4)
    val fallbackText = if (dark) java.awt.Color(0xdfe1e5) else java.awt.Color(0x1f2937)
    fun color(key: String, fallback: java.awt.Color) = javax.swing.UIManager.getColor(key) ?: fallback
    fun hex(value: java.awt.Color) = "#%02x%02x%02x".format(value.red, value.green, value.blue)
    val panel = color("Panel.background", fallbackBackground)
    val editor = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance().globalScheme.defaultBackground
    val editorDark = (editor.red * 299 + editor.green * 587 + editor.blue * 114) < 128000
    return mapOf(
      "--bg-color" to hex(if (editorDark == dark) editor else panel),
      "--surface-color" to hex(panel),
      "--text-color" to hex(color("Label.foreground", fallbackText)),
      "--muted-color" to hex(color("Label.disabledForeground", if (dark) java.awt.Color(0xa5adba) else java.awt.Color(0x64748b))),
      "--border-color" to hex(color("Component.borderColor", if (dark) java.awt.Color(0x4e5157) else java.awt.Color(0xd1d5db))),
      "--input-color" to hex(color("TextField.background", panel)),
      "--diagram-grid-color" to hex(color("Separator.foreground", if (dark) java.awt.Color(0x555b66) else java.awt.Color(0x9ca3af)))
    ) + overrides
  }

  private fun computeTheme(): String {
    return when (DatabaseDiagramSettings.instance.state.defaultTheme) {
      "Light" -> "light"
      "Dark" -> "dark"
      else -> if (!JBColor.isBright()) "dark" else "light"
    }
  }

  private fun updateTheme() {
    if (isDisposed) return
    webviewPanel.updateTheme(computeTheme(), resolveThemeColors())
  }

  override fun onWebviewReady() {
    if (isDisposed || project.isDisposed) return
    updateTheme()
    refreshController.refresh()
  }

  override fun onConfigureDiagram(includeTableIds: List<String>) {
    // Acknowledge the bridge request before opening a modal dialog.
    ApplicationManager.getApplication().invokeLater {
      if (!isDisposed) com.abugyda.dbd.configuration.DiagramConfigurationUi.configure(project, file, includeTableIds)
    }
  }

  override fun onPresentationSaved(message: WebviewBridge.Client.SavePresentation) {
    pendingLayoutSaves++
    try {
      updateFile { ErdYamlUpdater.savePresentation(project, it, message) }
    } finally {
      ApplicationManager.getApplication()
        .invokeLater { if (pendingLayoutSaves > 0) pendingLayoutSaves--; refreshController.resumePendingRefresh() }
    }
  }

  override fun onTablePositionUpdated(tableName: String, x: Int, y: Int, width: Int?) {
    pendingLayoutSaves++
    updateFile { ErdYamlUpdater.updateTablePosition(project, it, tableName, x, y, width) }
    ApplicationManager.getApplication()
      .invokeLater { if (pendingLayoutSaves > 0) pendingLayoutSaves--; refreshController.resumePendingRefresh() }
  }

  override fun onNotePositionUpdated(name: String, x: Int, y: Int, width: Int, height: Int) {
    pendingLayoutSaves++
    updateFile { ErdYamlUpdater.updateNotePosition(project, it, name, x, y, width, height) }
    ApplicationManager.getApplication()
      .invokeLater { if (pendingLayoutSaves > 0) pendingLayoutSaves--; refreshController.resumePendingRefresh() }
  }

  override fun onTableColorUpdated(tableName: String, color: String) {
    pendingLayoutSaves++
    updateFile { ErdYamlUpdater.updateTableColor(project, it, tableName, color) }
    ApplicationManager.getApplication()
      .invokeLater { if (pendingLayoutSaves > 0) pendingLayoutSaves--; refreshController.resumePendingRefresh() }
  }

  override fun onNoteColorUpdated(noteId: String, color: String) {
    pendingLayoutSaves++
    updateFile { ErdYamlUpdater.updateNoteColor(project, it, noteId, color) }
    ApplicationManager.getApplication()
      .invokeLater { if (pendingLayoutSaves > 0) pendingLayoutSaves--; refreshController.resumePendingRefresh() }
  }

  private fun updateFile(action: (YAMLFile) -> Unit) {
    if (isDisposed || project.isDisposed) return
    refreshController.cancelPending()
    val doc = ApplicationManager.getApplication().runReadAction<Document?> {
      FileDocumentManager.getInstance().getDocument(file)
    } ?: return
    if (!file.isValid || !doc.isWritable) return
    val psi = PsiDocumentManager.getInstance(project).getPsiFile(doc) as? YAMLFile ?: return
    action(psi)
  }

  override fun setState(state: FileEditorState) {}
  override fun isModified() = false
  override fun isValid() = true
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun dispose() {
    isDisposed = true
  }
}
