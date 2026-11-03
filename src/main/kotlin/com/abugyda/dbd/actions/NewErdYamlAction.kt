package com.abugyda.dbd.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiDirectory
import com.abugyda.dbd.configuration.DiagramConfigurationUi

/** Folder context action that creates a configured diagram through the IDE UI. */
class NewErdYamlAction : AnAction("ERD Diagram", "Create an ERD diagram from project SQL files", AllIcons.FileTypes.Yaml), DumbAware {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = event.project != null && event.getData(CommonDataKeys.PSI_ELEMENT) is PsiDirectory
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val directory = event.getData(CommonDataKeys.PSI_ELEMENT) as? PsiDirectory ?: return
    DiagramConfigurationUi.create(project, directory.virtualFile)
  }
}
