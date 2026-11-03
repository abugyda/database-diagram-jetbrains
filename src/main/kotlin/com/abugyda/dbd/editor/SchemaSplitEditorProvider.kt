package com.abugyda.dbd.editor

import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.abugyda.dbd.settings.DatabaseDiagramSettings

class SchemaSplitEditorProvider : AsyncFileEditorProvider, DumbAware {

  override fun accept(project: Project, file: VirtualFile): Boolean =
    file.name.endsWith(".erd.yaml", ignoreCase = true)

  override fun createEditor(project: Project, file: VirtualFile): FileEditor =
    createEditorAsync(project, file).build()

  override fun getEditorTypeId() = "erd-yaml-split-editor"
  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  override fun createEditorAsync(
    project: Project,
    file: VirtualFile
  ): AsyncFileEditorProvider.Builder {
    return object : AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
        val previewEditor = SchemaPreviewFileEditor(project, file, textEditor.editor.document)

        val previewKey = com.abugyda.dbd.configuration.DiagramConfigurationUi.OPEN_IN_PREVIEW
        val openInPreview = file.getUserData(previewKey) == true
        file.putUserData(previewKey, null)
        val layout =
          if (openInPreview) TextEditorWithPreview.Layout.SHOW_PREVIEW else when (DatabaseDiagramSettings.instance.state.defaultEditorLayout) {
            DatabaseDiagramSettings.EditorLayout.EDITOR_ONLY -> TextEditorWithPreview.Layout.SHOW_EDITOR
            DatabaseDiagramSettings.EditorLayout.PREVIEW_ONLY -> TextEditorWithPreview.Layout.SHOW_PREVIEW
            DatabaseDiagramSettings.EditorLayout.EDITOR_AND_PREVIEW -> TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW
          }

        return TextEditorWithPreview(textEditor, previewEditor, "ERD Editor", layout)
      }
    }
  }
}