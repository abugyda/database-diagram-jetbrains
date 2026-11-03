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
      val sequence = root.getKeyValueByKey("notes")?.value as? YAMLSequence
      sequence?.items?.filter { item ->
        val mapping = item.value as? YAMLMapping
        mapping?.getKeyValueByKey("id")?.valueText in message.deletedNoteIds
      }?.forEach { it.delete() }
      for (item in message.notes) {
        val id = item["id"] as? String ?: continue
        if (id in message.deletedNoteIds) continue
        var note = findNoteMapping(yamlFile, id)
        if (note == null) {
          val content = item["content"] as? String ?: continue
          appendNote(project, root, mapOf("id" to id, "name" to (item["name"] ?: id), "text" to content))
          note = findNoteMapping(yamlFile, id) ?: error("Could not create note '$id'")
        }
        item["name"]?.let { setScalar(project, note, "name", mapper.writeValueAsString(it)) }
        item["content"]?.let {
          val key = if (note.getKeyValueByKey("text") != null) "text"
            else if (note.getKeyValueByKey("content") != null) "content" else "text"
          setScalar(project, note, key, mapper.writeValueAsString(it))
        }
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

  private fun appendNote(project: Project, root: YAMLMapping, values: Map<String, Any?>) {
    val gen = YAMLElementGenerator.getInstance(project)
    val json = mapper.writeValueAsString(values)
    val sequence = blockNotes(project, root)
    if (sequence == null) {
      val fields = values.entries.joinToString("\n    ") { (key, value) -> "$key: ${mapper.writeValueAsString(value)}" }
      val file = gen.createDummyYamlWithText("notes:\n  - $fields")
      root.putKeyValue((file.documents.first().topLevelValue as YAMLMapping).keyValues.first())
      removePlaceholder(root)
    } else {
      sequence.add(gen.createEol())
      sequence.add(gen.createIndent(2))
      sequence.add(gen.createSequenceItem(json))
    }
  }

  /** Block mappings cannot be inserted into a flow-style sequence. Normalize that list first. */
  private fun blockNotes(project: Project, root: YAMLMapping): YAMLSequence? {
    val sequence = root.getKeyValueByKey("notes")?.value as? YAMLSequence ?: return null
    if (!sequence.text.trimStart().startsWith("[")) return sequence
    if (sequence.items.isEmpty()) {
      root.getKeyValueByKey("notes")?.delete()
      return null
    }
    val items = sequence.items.joinToString("\n") { item ->
      val mapping = item.value as? YAMLMapping
      if (mapping == null) "  - ${item.value?.text ?: "null"}"
      else "  - " + mapping.keyValues.joinToString("\n    ") { it.text }
    }
    val file = YAMLElementGenerator.getInstance(project).createDummyYamlWithText("notes:\n$items")
    val replacement = (file.documents.first().topLevelValue as YAMLMapping).keyValues.first().value!!
    return sequence.replace(replacement) as YAMLSequence
  }

  /** Find the YAMLMapping for a note item by its `id` value. */
  private fun findNoteMapping(yamlFile: YAMLFile, noteId: String): YAMLMapping? {
    val root = root(yamlFile) ?: return null
    val seq = blockNotes(yamlFile.project, root) ?: return null
    return seq.items
      .mapNotNull { it.value as? YAMLMapping }
      .firstOrNull { it.getKeyValueByKey("id")?.valueText?.removeSurrounding("\"") == noteId }
      ?.let { editableMapping(yamlFile.project, it) }
  }

  fun configureDiagram(project: Project, yamlFile: YAMLFile, config: com.abugyda.dbd.configuration.DiagramConfiguration) {
    WriteCommandAction.runWriteCommandAction(project, "Configure ERD diagram", null, Runnable {
      val root = root(yamlFile) ?: error("Expected a YAML root mapping")
      val schema = getOrCreateMapping(project, root, "schema")
      val current = com.abugyda.dbd.configuration.DiagramConfiguration.read(yamlFile)
      // Copy block-style entries instead of serializing collections as one long JSON line.
      val generated = YAMLElementGenerator.getInstance(project).createDummyYamlWithText(config.yaml())
      val generatedSchema = (generated.documents.first().topLevelValue as YAMLMapping)
        .getKeyValueByKey("schema")!!.value as YAMLMapping
      for (key in listOf("imports", "table_filter")) {
        val replacement = generatedSchema.getKeyValueByKey(key)!!
        val existing = schema.getKeyValueByKey(key)
        val value = existing?.value
        if (key == "imports" && current.imports == config.imports && value is YAMLSequence &&
          (value.items.isEmpty() || !value.text.trimStart().startsWith("["))) continue
        if (key == "table_filter" && current.tableFilter == config.tableFilter && value is YAMLMapping &&
          !value.text.trimStart().startsWith("{")) {
          val tables = value.getKeyValueByKey("tables")?.value as? YAMLSequence
          if (tables != null && (tables.items.isEmpty() || !tables.text.trimStart().startsWith("["))) continue
        }
        if (existing?.text == replacement.text) continue
        if (existing == null) schema.putKeyValue(replacement) else existing.replace(replacement)
        removePlaceholder(schema)
      }
      if (config.dialect == null) schema.getKeyValueByKey("dialect")?.delete()
      else setScalar(project, schema, "dialect", mapper.writeValueAsString(config.dialect))
      setScalar(project, schema, "default_schema", mapper.writeValueAsString(config.defaultSchema))
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
