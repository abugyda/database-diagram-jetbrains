package com.abugyda.dbd.configuration

import javax.swing.table.AbstractTableModel

/** Keeps source kind attached to its row when paths are edited or rows removed. */
internal class SqlSourcesTableModel(initial: List<SqlImport>) : AbstractTableModel() {
  private val sources = initial.toMutableList()
  override fun getRowCount() = sources.size
  override fun getColumnCount() = 3
  override fun getColumnName(column: Int) =
    arrayOf("SQL source", "Schema override", "Subfolders")[column]

  override fun getColumnClass(column: Int): Class<*> =
    if (column == 2) java.lang.Boolean::class.java else String::class.java

  override fun isCellEditable(row: Int, column: Int) = column != 2 || sources[row].folder
  override fun getValueAt(row: Int, column: Int): Any? = sources[row].let {
    when (column) {
      0 -> it.path; 1 -> it.schema ?: ""; else -> if (it.folder) it.recursive else null
    }
  }

  override fun setValueAt(value: Any?, row: Int, column: Int) {
    val source = sources[row]
    sources[row] = when (column) {
      0 -> source.copy(path = value?.toString()?.trim().orEmpty())
      1 -> source.copy(schema = value?.toString()?.trim()?.takeIf { it.isNotEmpty() })
      else -> if (source.folder) source.copy(recursive = value == true) else source
    }
    if (sources[row] != source) fireTableRowsUpdated(row, row)
  }

  fun sourceAt(row: Int) = sources[row]
  fun imports(): List<SqlImport> = sources.toList()
  fun add(source: SqlImport) {
    if (sources.any { it.path == source.path && it.folder == source.folder && it.schema == source.schema }) return
    sources.add(source)
    fireTableRowsInserted(sources.lastIndex, sources.lastIndex)
  }

  fun remove(row: Int) {
    sources.removeAt(row); fireTableRowsDeleted(row, row)
  }
}
