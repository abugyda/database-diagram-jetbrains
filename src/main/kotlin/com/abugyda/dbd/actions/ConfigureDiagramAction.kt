package com.abugyda.dbd.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.abugyda.dbd.configuration.DiagramConfigurationUi

class ConfigureDiagramAction : AnAction(), DumbAware {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = event.project != null &&
        event.getData(CommonDataKeys.VIRTUAL_FILE)?.name?.endsWith(".erd.yaml", true) == true
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    DiagramConfigurationUi.configure(project, file)
  }
}
