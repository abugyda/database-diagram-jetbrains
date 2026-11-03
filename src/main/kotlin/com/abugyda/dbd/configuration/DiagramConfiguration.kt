package com.abugyda.dbd.configuration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jetbrains.yaml.psi.*

data class SqlImport(val path: String, val schema: String? = null)

data class TableFilter(val mode: String = "exclude", val tables: Set<String> = emptySet()) {
  init { require(mode in setOf("include", "exclude")) { "Unknown table filter mode '$mode'" } }
  fun includes(id: String): Boolean = if (mode == "include") id in tables else id !in tables
}

/** Source configuration is independent of the diagram's saved presentation. */
data class DiagramConfiguration(
  val imports: List<SqlImport> = emptyList(),
  val dialect: String? = null,
  val defaultSchema: String = "public",
  val tableFilter: TableFilter = TableFilter()
) {
  fun yaml(): String = buildString {
    append("schema:\n")
    dialect?.let { append("  dialect: ${quote(it)}\n") }
    append("  default_schema: ${quote(defaultSchema)}\n")
    if (imports.isEmpty()) append("  imports: []\n") else {
      append("  imports:\n")
      imports.forEach {
        append("    - path: ${quote(it.path)}\n")
        it.schema?.takeIf(String::isNotBlank)?.let { name -> append("      schema: ${quote(name)}\n") }
      }
    }
    append("  table_filter:\n    mode: ${tableFilter.mode}\n    tables: ${quote(tableFilter.tables.sorted())}\n")
  }

  companion object {
    private val mapper = jacksonObjectMapper()
    fun quote(value: Any): String = mapper.writeValueAsString(value)
    private fun text(value: YAMLValue?): String? = (value as? YAMLScalar)?.textValue
    fun read(file: YAMLFile): DiagramConfiguration {
      val root = file.documents.firstOrNull()?.topLevelValue as? YAMLMapping
      val schema = root?.getKeyValueByKey("schema")?.value as? YAMLMapping
      val filter = schema?.getKeyValueByKey("table_filter")?.value as? YAMLMapping
      val mode = text(filter?.getKeyValueByKey("mode")?.value) ?: "exclude"
      return DiagramConfiguration(
        imports = (schema?.getKeyValueByKey("imports")?.value as? YAMLSequence)?.items.orEmpty().mapNotNull { item ->
          val mapping = item.value as? YAMLMapping
          val path = text(mapping?.getKeyValueByKey("path")?.value ?: item.value) ?: return@mapNotNull null
          SqlImport(path, text(mapping?.getKeyValueByKey("schema")?.value))
        },
        dialect = text(schema?.getKeyValueByKey("dialect")?.value),
        defaultSchema = text(schema?.getKeyValueByKey("default_schema")?.value) ?: "public",
        tableFilter = TableFilter(mode, (filter?.getKeyValueByKey("tables")?.value as? YAMLSequence)
          ?.items.orEmpty().mapNotNull { text(it.value) }.toSet())
      )
    }
  }
}
