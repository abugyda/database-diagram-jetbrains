package com.abugyda.dbd.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.abugyda.dbd.configuration.DiagramConfigurationUi

/** Folder context action that creates a configured diagram through the IDE UI. */
class NewErdYamlAction : AnAction("ERD Diagram (.erd.yaml)", "Create an ERD diagram from project SQL files", AllIcons.FileTypes.Yaml), DumbAware {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = event.project != null && (
      event.getData(LangDataKeys.IDE_VIEW)?.directories?.isNotEmpty() == true ||
        event.getData(CommonDataKeys.PSI_ELEMENT) is PsiDirectory ||
        event.getData(CommonDataKeys.VIRTUAL_FILE)?.isDirectory == true)
  }

  internal fun targetDirectory(event: AnActionEvent): VirtualFile? {
    val view = event.getData(LangDataKeys.IDE_VIEW)
    // Respect cancellation if the IDE needs the user to choose between directories.
    if (view != null) return view.orChooseDirectory?.virtualFile
    return (event.getData(CommonDataKeys.PSI_ELEMENT) as? PsiDirectory)?.virtualFile
      ?: event.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.isDirectory }
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val directory = targetDirectory(event) ?: return
    DiagramConfigurationUi.create(project, directory)
  }
}
