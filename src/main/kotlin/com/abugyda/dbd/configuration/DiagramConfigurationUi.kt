package com.abugyda.dbd.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.abugyda.dbd.editor.ErdYamlUpdater
import org.jetbrains.yaml.psi.YAMLFile

object DiagramConfigurationUi {
  val OPEN_IN_PREVIEW = com.intellij.openapi.util.Key.create<Boolean>("dbdiagram.open.in.preview")
  fun create(project: Project, directory: VirtualFile) {
    val dialog =
      DiagramConfigurationDialog(project, directory, DiagramConfiguration(), creating = true)
    if (!dialog.showAndGet()) return
    try {
      var created: VirtualFile? = null
      WriteCommandAction.runWriteCommandAction(project, "Create ERD diagram", null, Runnable {
        check(directory.findChild(dialog.fileName) == null) { "A file with that name already exists" }
        created = directory.createChildData(this, dialog.fileName)
        VfsUtil.saveText(
          created!!,
          dialog.configuration().yaml() + "\ntables: {}\ngroups: []\nnotes: []\n"
        )
      })
      created?.let {
        it.putUserData(OPEN_IN_PREVIEW, true)
        FileEditorManager.getInstance(project).openFile(it, true)
      }
    } catch (error: Exception) {
      Messages.showErrorDialog(
        project,
        error.message ?: "Could not create diagram",
        "Create ERD Diagram"
      )
    }
  }

  fun configure(project: Project, file: VirtualFile, includeTableIds: List<String> = emptyList()) {
    if (project.isDisposed || !file.isValid) return
    try {
      check(file.isWritable) { "The diagram file is read-only" }
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      val yaml = PsiManager.getInstance(project).findFile(file) as? YAMLFile
        ?: error("Expected an ERD YAML file")
      val config = ApplicationManager.getApplication()
        .runReadAction<DiagramConfiguration> { DiagramConfiguration.read(yaml) }
      val dialog =
        DiagramConfigurationDialog(project, file.parent, config, creating = false, includeTableIds)
      if (dialog.showAndGet()) {
        // Re-resolve PSI: the document may have changed while setup was open.
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val current = PsiManager.getInstance(project).findFile(file) as? YAMLFile ?: return
        ErdYamlUpdater.configureDiagram(project, current, dialog.configuration())
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FileDocumentManager.getInstance().getDocument(file)
          ?.let { FileDocumentManager.getInstance().saveDocument(it) }
      }
    } catch (error: Exception) {
      Messages.showErrorDialog(
        project,
        error.message ?: "Could not configure diagram",
        "Configure Diagram"
      )
    }
  }
}
