package com.abugyda.dbd.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile

/**
 * "New → SQL Schema File (.sql)"
 */
class NewSqlSchemaAction : CreateFileFromTemplateAction(
  "SQL Schema File",
  "Create a new SQL schema file for use with the ERD diagram",
  AllIcons.FileTypes.Text
) {

  override fun buildDialog(
    project: Project,
    directory: PsiDirectory,
    builder: CreateFileFromTemplateDialog.Builder
  ) {
    builder
      .setTitle("New SQL Schema File")
      .addKind("SQL schema file (.sql)", AllIcons.FileTypes.Text, "SqlSchema")
  }

  override fun getActionName(
    directory: PsiDirectory,
    newName: String,
    templateName: String
  ): String = "Create SQL Schema '$newName'"

  override fun createFileFromTemplate(
    name: String,
    template: FileTemplate,
    dir: PsiDirectory
  ): PsiFile? {
    val fileName = if (name.endsWith(".sql")) name else "$name.sql"

    val project = dir.project
    val properties = FileTemplateManager.getInstance(project).defaultProperties
    properties.setProperty("NAME", name.removeSuffix(".sql"))

    return FileTemplateUtil.createFromTemplate(template, fileName, properties, dir) as? PsiFile
  }
}