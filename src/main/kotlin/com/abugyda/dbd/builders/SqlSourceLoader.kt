package com.abugyda.dbd.builders

import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.yaml.psi.YAMLFile

/** Resolve entrypoints and pgschema-style includes relative to their containing file. */
object SqlSourceLoader {
  data class Source(val file: PsiFile, val schema: String, val virtualFile: VirtualFile)

  private val include = Regex("""(?m)^\s*\\(?:i|ir)\s+(?:'([^']+)'|"([^"]+)"|([^\s;]+))\s*;?\s*$""")

  fun load(yaml: YAMLFile, project: Project): List<Source> {
    return load(
      yaml.virtualFile.parent,
      com.abugyda.dbd.configuration.DiagramConfiguration.read(yaml),
      project
    )
  }

  fun load(
    directory: VirtualFile,
    config: com.abugyda.dbd.configuration.DiagramConfiguration,
    project: Project
  ): List<Source> {
    val dialectName = config.dialect?.lowercase()
    val dialect = when (dialectName) {
      null -> null
      "postgres", "postgresql" -> com.intellij.sql.dialects.SqlLanguageDialect.EP.forDbms(com.intellij.database.Dbms.POSTGRES)
        ?: error("PostgreSQL SQL support is unavailable")

      else -> error("Unsupported diagram SQL dialect '$dialectName'; omit dialect to use the IDE mapping")
    }
    val result = linkedMapOf<String, Source>()
    val visiting = linkedSetOf<String>()
    fun visit(file: VirtualFile, defaultSchema: String) {
      com.intellij.openapi.progress.ProgressManager.checkCanceled()
      check(!file.isDirectory) { "Not a SQL file: ${file.path}" }
      val key = "${file.canonicalPath ?: file.path}::$defaultSchema"
      check(key !in visiting) { "Circular SQL include: ${(visiting + key).joinToString(" -> ")}" }
      if (key in result) return
      visiting.add(key)
      val psi =
        PsiManager.getInstance(project).findFile(file) ?: error("Cannot read SQL: ${file.path}")
      val text = FileDocumentManager.getInstance().getDocument(file)?.text ?: psi.text
      val parsed =
        if (dialect == null) psi else com.intellij.psi.PsiFileFactory.getInstance(project)
          .createFileFromText(file.name, dialect, text)
      result[key] = Source(parsed, defaultSchema, file)
      for (match in include.findAll(text)) {
        val path = match.groupValues.drop(1).first { it.isNotEmpty() }
        val child = file.parent.findFileByRelativePath(path)
          ?: error("Missing SQL include '$path' in ${file.path}")
        visit(child, defaultSchema)
      }
      visiting.remove(key)
    }

    val fileIndex = com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
    fun scan(folder: VirtualFile, schema: String, recursive: Boolean) {
      val seen = mutableSetOf<String>()
      fun walk(current: VirtualFile) {
        com.intellij.openapi.progress.ProgressManager.checkCanceled()
        if (!seen.add(current.canonicalPath ?: current.path)) return
        for (child in current.children.sortedBy { it.name }) {
          com.intellij.openapi.progress.ProgressManager.checkCanceled()
          if (fileIndex.isExcluded(child)) continue
          if (child.isDirectory) {
            if (recursive && !child.`is`(com.intellij.openapi.vfs.VFileProperty.SYMLINK)) walk(child)
          } else if (child.extension.equals("sql", true)) visit(child, schema)
        }
      }
      walk(folder)
    }
    for (item in config.imports) {
      check(item.path.isNotBlank()) { "SQL source path cannot be empty; use '.' for the diagram's folder" }
      val file = (if (com.intellij.openapi.util.io.FileUtil.isAbsolute(item.path))
        com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(item.path)
      else directory.findFileByRelativePath(item.path))
        ?: error("Missing SQL import '${item.path}' relative to ${directory.path}")
      val schema = item.schema?.takeIf { it.isNotBlank() } ?: config.defaultSchema
      if (item.folder) {
        check(file.isDirectory) { "Not a SQL folder: ${item.path}" }
        scan(file, schema, item.recursive)
      } else {
        check(
          !file.isDirectory && file.extension.equals(
            "sql",
            ignoreCase = true
          )
        ) { "Not a SQL file: ${item.path}" }
        visit(file, schema)
      }
    }
    return result.values.toList()
  }
}
