package com.abugyda.dbd.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence

object ErdYamlUpdater {
  private val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
  private val pendingPlaceholder = com.intellij.openapi.util.Key.create<Boolean>("dbdiagram.pending.mapping")

  private fun removePlaceholder(mapping: YAMLMapping) {
    if (mapping.getUserData(pendingPlaceholder) == true) {
      mapping.getKeyValueByKey("_dummy")?.delete()
      mapping.putUserData(pendingPlaceholder, null)
    }
  }

  private fun editableMapping(project: Project, mapping: YAMLMapping): YAMLMapping {
    if (!mapping.text.trimStart().startsWith("{")) return mapping
    val empty = mapping.keyValues.isEmpty()
    val content = if (empty) "  _dummy: value" else mapping.keyValues.joinToString("\n") { it.text.prependIndent("  ") }
    val generated = YAMLElementGenerator.getInstance(project).createDummyYamlWithText("value:\n$content")
    val replacement = (generated.documents.first().topLevelValue as YAMLMapping).getKeyValueByKey("value")!!.value!!
    val result = mapping.replace(replacement) as YAMLMapping
    if (empty) result.putUserData(pendingPlaceholder, true)
    return result
  }

  // ── Table position ─────────────────────────────────────────────────────────

  fun updateTablePosition(
    project: Project, yamlFile: YAMLFile,
    tableName: String, x: Int, y: Int, width: Int?
  ) {
    WriteCommandAction.runWriteCommandAction(project) {
      val root = root(yamlFile) ?: return@runWriteCommandAction
      val table =
        getOrCreateMapping(project, getOrCreateMapping(project, root, "tables"), tableName)
      setScalar(project, table, "x", x.toString())
      setScalar(project, table, "y", y.toString())
      if (width != null) setScalar(project, table, "width", width.toString())
    }
  }

  // ── Table color ────────────────────────────────────────────────────────────

  fun updateTableColor(
    project: Project, yamlFile: YAMLFile,
    tableName: String, color: String
  ) {
    WriteCommandAction.runWriteCommandAction(project) {
      val root = root(yamlFile) ?: return@runWriteCommandAction
      val table =
        getOrCreateMapping(project, getOrCreateMapping(project, root, "tables"), tableName)
      setScalar(project, table, "color", "\"$color\"")
    }
  }

  // ── Note position ──────────────────────────────────────────────────────────
  //
  // Notes are a YAML sequence, not a mapping.  We find the item by its `id` key.

  fun updateNotePosition(
    project: Project, yamlFile: YAMLFile,
    noteName: String, x: Int, y: Int, width: Int, height: Int
  ) {
    WriteCommandAction.runWriteCommandAction(project) {
      val noteMapping = findNoteMapping(yamlFile, noteName) ?: return@runWriteCommandAction
      setScalar(project, noteMapping, "x", x.toString())
      setScalar(project, noteMapping, "y", y.toString())
      setScalar(project, noteMapping, "width", width.toString())
      setScalar(project, noteMapping, "height", height.toString())
    }
  }

  // ── Note color ─────────────────────────────────────────────────────────────

  fun updateNoteColor(
    project: Project, yamlFile: YAMLFile,
    noteId: String, color: String
  ) {
    WriteCommandAction.runWriteCommandAction(project) {
      val noteMapping = findNoteMapping(yamlFile, noteId) ?: return@runWriteCommandAction
      setScalar(project, noteMapping, "color", "\"$color\"")
    }
  }

  fun savePresentation(
    project: Project,
    yamlFile: YAMLFile,
    message: com.abugyda.dbd.webview.WebviewBridge.Client.SavePresentation
  ) {
    WriteCommandAction.runWriteCommandAction(project) {
      val root = root(yamlFile) ?: return@runWriteCommandAction
      for (item in message.tables) {
        val id = item["id"] as? String ?: continue
        val table = getOrCreateMapping(project, getOrCreateMapping(project, root, "tables"), id)
        for (key in listOf("x", "y", "width", "color")) item[key]?.let {
          setScalar(
            project,
            table,
            key,
            mapper.writeValueAsString(it)
          )
        }
      }
      for (item in message.notes) {
        val id = item["id"] as? String ?: continue
        val note = findNoteMapping(yamlFile, id) ?: continue
        for (key in listOf(
          "x",
          "y",
          "width",
          "height",
          "color"
        )) item[key]?.let { setScalar(project, note, key, mapper.writeValueAsString(it)) }
      }
      setScalar(project, root, "groups", mapper.writeValueAsString(message.groups))
      message.viewport?.let { setScalar(project, root, "viewport", mapper.writeValueAsString(it)) }
      setScalar(project, root, "display", mapper.writeValueAsString(message.displaySettings))
    }
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  /** Find the YAMLMapping for a note item by its `id` value. */
  private fun findNoteMapping(yamlFile: YAMLFile, noteId: String): YAMLMapping? {
    val root = root(yamlFile) ?: return null
    val seq = root.getKeyValueByKey("notes")?.value as? YAMLSequence ?: return null
    return seq.items
      .mapNotNull { it.value as? YAMLMapping }
      .firstOrNull { it.getKeyValueByKey("id")?.valueText?.removeSurrounding("\"") == noteId }
      ?.let { editableMapping(yamlFile.project, it) }
  }

  fun configureDiagram(project: Project, yamlFile: YAMLFile, config: com.abugyda.dbd.configuration.DiagramConfiguration) {
    WriteCommandAction.runWriteCommandAction(project, "Configure ERD diagram", null, Runnable {
      val root = root(yamlFile) ?: error("Expected a YAML root mapping")
      val schema = getOrCreateMapping(project, root, "schema")
      setScalar(project, schema, "imports", mapper.writeValueAsString(config.imports.map { item ->
        buildMap { put("path", item.path); item.schema?.takeIf { it.isNotBlank() }?.let { put("schema", it) } }
      }))
      if (config.dialect == null) schema.getKeyValueByKey("dialect")?.delete()
      else setScalar(project, schema, "dialect", mapper.writeValueAsString(config.dialect))
      setScalar(project, schema, "default_schema", mapper.writeValueAsString(config.defaultSchema))
      setScalar(project, schema, "table_filter", mapper.writeValueAsString(mapOf(
        "mode" to config.tableFilter.mode, "tables" to config.tableFilter.tables.sorted())))
    }, yamlFile)
  }

  private fun root(yamlFile: YAMLFile): YAMLMapping? =
    yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping

  private fun getOrCreateMapping(
    project: Project, parent: YAMLMapping, key: String
  ): YAMLMapping {
    val existing = parent.getKeyValueByKey(key)
    if (existing != null) {
      val v = existing.value
      if (v is YAMLMapping) return editableMapping(project, v)
      throw IllegalStateException("YAML key '$key' is not a mapping — refusing to overwrite it.")
    }
    val gen = YAMLElementGenerator.getInstance(project)
    val dummy = gen.createDummyYamlWithText("$key:\n  _dummy: value")
    parent.putKeyValue(
      (dummy.documents.first().topLevelValue as YAMLMapping).getKeyValueByKey(key)!!
    )
    removePlaceholder(parent)
    val added = parent.getKeyValueByKey(key)!!.value as YAMLMapping
    added.putUserData(pendingPlaceholder, true)
    return added
  }

  private fun setScalar(project: Project, mapping: YAMLMapping, key: String, value: String) {
    val gen = YAMLElementGenerator.getInstance(project)
    val existing = mapping.getKeyValueByKey(key)
    if (existing?.value?.text == value) return
    if (existing?.value != null && runCatching { mapper.readTree(existing.value!!.text) == mapper.readTree(value) }.getOrDefault(false)) return
    if (existing != null) {
      val dummy = gen.createDummyYamlWithText("$key: $value")
      existing.replace(
        (dummy.documents.first().topLevelValue as YAMLMapping).keyValues.first()
      )
    } else {
      mapping.putKeyValue(gen.createYamlKeyValue(key, value))
      removePlaceholder(mapping)
    }
  }
}