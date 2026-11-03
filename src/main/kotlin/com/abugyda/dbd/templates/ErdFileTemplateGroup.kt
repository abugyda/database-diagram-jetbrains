package com.abugyda.dbd.templates

import com.intellij.icons.AllIcons
import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory

/**
 * Registers the ERD plugin's file templates with IntelliJ's file-template system.
 *
 * Resources must be placed at:
 *   src/main/resources/fileTemplates/ErdYaml.ft
 *   src/main/resources/fileTemplates/SqlSchema.ft
 *
 * The template name (first arg) must match the .ft filename without extension,
 * and must also match the string passed to builder.addKind(…) in the action classes.
 */
class ErdFileTemplateGroup : FileTemplateGroupDescriptorFactory {

  override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
    val group = FileTemplateGroupDescriptor("ERD Diagram", AllIcons.FileTypes.Yaml)

    // FileTemplateDescriptor only takes (name, icon) — fileName is read-only
    // and derived by IntelliJ from the template name + the extension provided
    // by the action (getDefaultExtension / addKind).
    group.addTemplate(FileTemplateDescriptor("ErdYaml", AllIcons.FileTypes.Yaml))
    group.addTemplate(FileTemplateDescriptor("SqlSchema", AllIcons.FileTypes.Text))

    return group
  }
}