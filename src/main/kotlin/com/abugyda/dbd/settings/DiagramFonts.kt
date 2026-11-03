package com.abugyda.dbd.settings

object DiagramFonts {
  val roles = linkedMapOf(
    "tableHeader" to "Table headers", "tableDescription" to "Table descriptions",
    "columnName" to "Column names", "columnDescription" to "Column descriptions",
    "columnMetadata" to "Column types and defaults", "noteHeader" to "Note headers",
    "noteBody" to "Note text", "groupHeader" to "Group labels",
    "semanticHeader" to "Overview table headers", "semanticColumn" to "Overview columns",
    "semanticNoteHeader" to "Overview note headers", "details" to "Details view text",
    "relationLabel" to "Relationship labels"
  )
  fun normalize(values: Map<String, Int>): MutableMap<String, Int> = values
    .filter { (key, value) -> key in roles && value > 0 }.mapValues { it.value.coerceIn(8, 48) }.toMutableMap()
}
