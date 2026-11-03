package com.abugyda.dbd.configuration

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.abugyda.dbd.builders.ErdDataBuilder
import com.abugyda.dbd.builders.SqlSourceLoader
import com.abugyda.dbd.webview.WebviewBridge.DbTable
import com.abugyda.dbd.webview.WebviewBridge.SchemaPayload
import org.jetbrains.yaml.YAMLElementGenerator
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.Callable
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultTreeModel

/** Shared setup for new diagrams and the webview's Configure diagram action. */
class DiagramConfigurationDialog(
  private val project: Project,
  private val directory: VirtualFile,
  private val initial: DiagramConfiguration,
  private val creating: Boolean,
  includeTableIds: List<String> = emptyList()
) : DialogWrapper(project, true) {
  private val nameField = JTextField("diagram", 24)
  private val sourceModel = DefaultTableModel(arrayOf("SQL source (relative to diagram)", "Schema override (optional)"), 0)
  private val sourceTable = JBTable(sourceModel)
  private val dialect = JComboBox(arrayOf("Use IDE mapping", "PostgreSQL") +
    if (initial.dialect != null && initial.dialect !in setOf("postgres", "postgresql")) arrayOf(initial.dialect) else emptyArray())
  private val defaultSchema = JTextField(initial.defaultSchema, 16)
  private val mode = JComboBox(arrayOf("All tables except unchecked", "Only checked tables"))
  private val tabs = JTabbedPane()
  private val search = SearchTextField()
  private val status = JBLabel("Choose SQL sources, then preview the tables.")
  private val count = JBLabel(" ")
  private val preview = JButton("Preview tables")
  private val choices = includeTableIds.associateWith { true }.toMutableMap()
  private var catalog: SchemaPayload? = null
  private var revision = 0
  private var dirty = true
  private var loading = false
  private var discoveryError: String? = null
  private var rebuilding = false
  private val tree = object : CheckboxTree(object : CheckboxTree.CheckboxTreeCellRenderer(true) {
    override fun customizeRenderer(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
      val item = (value as CheckedTreeNode).userObject
      textRenderer.append(if (item is DbTable) item.name else item.toString())
    }
  }, CheckedTreeNode("Tables"), com.intellij.ui.CheckboxTreeBase.CheckPolicy(true, true, true, true, true)) {
    override fun onNodeStateChanged(node: CheckedTreeNode?) {
      if (node == null) return
      if (!rebuilding) {
        (node.userObject as? DbTable)?.let { choices[it.id] = node.isChecked }
        updateCount()
      }
    }
  }

  internal val setupPanel: JComponent by lazy { JPanel(BorderLayout(0, JBUI.scale(10))).apply {
    preferredSize = Dimension(JBUI.scale(780), JBUI.scale(540))
    if (creating) add(JPanel(FlowLayout(FlowLayout.LEADING)).apply { add(JBLabel("Diagram name:")); add(nameField) }, BorderLayout.NORTH)
    add(tabs, BorderLayout.CENTER)
    add(status, BorderLayout.SOUTH)
  } }

  init {
    title = if (creating) "New ERD Diagram" else "Configure Diagram"
    setOKButtonText(if (creating) "Create diagram" else "Apply")
    initial.imports.forEach { sourceModel.addRow(arrayOf(it.path, it.schema ?: "")) }
    dialect.selectedIndex = when (initial.dialect) { null -> 0; "postgres", "postgresql" -> 1; else -> 2 }
    mode.selectedIndex = if (initial.tableFilter.mode == "include") 1 else 0
    tree.isRootVisible = false
    tree.showsRootHandles = true
    sourceTable.putClientProperty("terminateEditOnFocusLost", true)
    sourceTable.columnModel.getColumn(0).preferredWidth = 460
    sourceTable.columnModel.getColumn(1).preferredWidth = 170
    sourceTable.emptyText.text = "Add SQL entrypoints from your project"
    sourceModel.addTableModelListener { invalidateSources() }
    dialect.addActionListener { invalidateSources() }
    defaultSchema.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) = invalidateSources()
    })
    search.textEditor.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) = rebuildTree()
    })
    mode.addActionListener { updateCount() }
    preview.addActionListener { tabs.selectedIndex = 1; if (!loading) discover() }
    tabs.addTab("1. SQL sources", sourcesPanel())
    tabs.addTab("2. Tables", tablesPanel())
    tabs.addChangeListener { if (tabs.selectedIndex == 1 && dirty && !loading && tabs.isShowing) discover() }
    init()
    setOKActionEnabled(false)
    window?.addWindowListener(object : WindowAdapter() {
      override fun windowOpened(e: WindowEvent) {
        if (initial.imports.isNotEmpty()) {
          if (includeTableIds.isNotEmpty()) tabs.selectedIndex = 1
          if (!loading) discover()
        }
      }
    })
  }

  val fileName: String get() = nameField.text.trim().let { if (it.endsWith(".erd.yaml", true)) it else "$it.erd.yaml" }

  private fun sourceConfig(): DiagramConfiguration {
    if (sourceTable.isEditing) sourceTable.cellEditor.stopCellEditing()
    val imports = (0 until sourceModel.rowCount).map { row ->
      SqlImport(sourceModel.getValueAt(row, 0).toString().trim(), sourceModel.getValueAt(row, 1)?.toString()?.trim()?.takeIf { it.isNotEmpty() })
    }
    val selectedDialect = when (dialect.selectedIndex) { 0 -> null; 1 -> "postgresql"; else -> initial.dialect }
    return DiagramConfiguration(imports, selectedDialect, defaultSchema.text.trim())
  }

  fun configuration(): DiagramConfiguration {
    val ids = catalog?.tables.orEmpty().map { it.id }.toSet()
    val filterMode = if (mode.selectedIndex == 0) "exclude" else "include"
    val savedUnknown = if (filterMode == initial.tableFilter.mode) initial.tableFilter.tables - ids else emptySet()
    val selected = ids.filter { id -> if (filterMode == "include") choices[id] == true else choices[id] != true }.toSet()
    return sourceConfig().copy(tableFilter = TableFilter(filterMode, selected + savedUnknown))
  }

  override fun createCenterPanel(): JComponent = setupPanel

  override fun getPreferredFocusedComponent(): JComponent = if (creating) nameField else sourceTable

  private fun sourcesPanel(): JComponent = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
    border = JBUI.Borders.empty(12)
    add(JBLabel("Choose SQL entrypoints. Included SQL files are discovered automatically."), BorderLayout.NORTH)
    add(JBScrollPane(sourceTable), BorderLayout.CENTER)
    add(JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(JPanel(FlowLayout(FlowLayout.LEADING)).apply {
        add(JButton("Add SQL files…").apply { addActionListener { addSources() } })
        add(JButton("Remove selected").apply { addActionListener {
          sourceTable.selectedRows.map { sourceTable.convertRowIndexToModel(it) }.sortedDescending().forEach(sourceModel::removeRow)
        } })
        add(preview)
      })
      add(JPanel(FlowLayout(FlowLayout.LEADING)).apply {
        add(JBLabel("Dialect:")); add(dialect); add(JBLabel("Default schema:")); add(defaultSchema)
      })
      add(JBLabel("Edit a source path to repair an import. Schema overrides apply to unqualified names."))
    }, BorderLayout.SOUTH)
  }

  private fun tablesPanel(): JComponent = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
    border = JBUI.Borders.empty(12)
    add(JPanel(BorderLayout(8, 8)).apply { add(search, BorderLayout.CENTER); add(mode, BorderLayout.EAST) }, BorderLayout.NORTH)
    add(JBScrollPane(tree), BorderLayout.CENTER)
    add(JPanel(BorderLayout()).apply {
      add(JPanel(FlowLayout(FlowLayout.LEADING)).apply {
        add(JButton("Select shown").apply { addActionListener { selectShown(true) } })
        add(JButton("Clear shown").apply { addActionListener { selectShown(false) } })
        add(JButton("Include related tables").apply { addActionListener {
          val selected = choices.filterValues { it }.keys
          catalog?.relationships.orEmpty().forEach { rel ->
            if (rel.fromTable in selected || rel.toTable in selected) { choices[rel.fromTable] = true; choices[rel.toTable] = true }
          }
          rebuildTree()
        } })
      }, BorderLayout.NORTH)
      add(count, BorderLayout.SOUTH)
    }, BorderLayout.SOUTH)
  }

  private fun addSources() {
    val descriptor = FileChooserDescriptor(true, false, false, false, false, true)
      .withTitle("Add SQL Sources").withExtensionFilter("SQL files", "sql")
    descriptor.isForcedToUseIdeaFileChooser = true
    val roots = com.intellij.openapi.roots.ProjectRootManager.getInstance(project).contentRoots
    if (roots.isNotEmpty()) descriptor.withRoots(*roots).withShowFileSystemRoots(false)
    FileChooser.chooseFiles(descriptor, project, directory) { files ->
      files.forEach { file ->
        val path = VfsUtilCore.getRelativePath(file, directory, '/')
          ?: com.intellij.openapi.util.io.FileUtil.getRelativePath(directory.path, file.path, '/') ?: file.path
        if ((0 until sourceModel.rowCount).none { sourceModel.getValueAt(it, 0) == path }) sourceModel.addRow(arrayOf(path, ""))
      }
    }
  }

  private fun invalidateSources() {
    revision++
    dirty = true
    loading = false
    catalog = null
    discoveryError = null
    status.text = "Sources changed. Preview tables to validate them."
    preview.isEnabled = true
    setOKActionEnabled(false)
    rebuildTree()
  }

  private fun discover() {
    val config = sourceConfig()
    if (config.imports.isEmpty()) { status.text = "Add at least one SQL source."; return }
    if (config.defaultSchema.isBlank()) { status.text = "Enter a default schema name."; return }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val ticket = ++revision
    loading = true
    preview.isEnabled = false
    setOKActionEnabled(false)
    status.text = "Reading SQL sources…"
    ReadAction.nonBlocking(Callable {
      try {
        val sources = SqlSourceLoader.load(directory, config, project)
        val yaml = YAMLElementGenerator.getInstance(project).createDummyYamlWithText(config.yaml())
        Result.success(ErdDataBuilder.build(yaml, project, sources))
      } catch (error: ProcessCanceledException) { throw error }
      catch (error: Exception) { Result.failure<SchemaPayload>(error) }
    }).withDocumentsCommitted(project).expireWith(disposable)
      .finishOnUiThread(ModalityState.stateForComponent(tabs)) { result ->
        if (ticket == revision) {
          loading = false
          preview.isEnabled = true
          result.fold(onSuccess = { payload ->
            catalog = payload
            payload.tables.forEach { choices.putIfAbsent(it.id, initial.tableFilter.includes(it.id)) }
            dirty = false
            discoveryError = null
            status.text = "${payload.tables.size} tables · ${payload.relationships.size} relationships found. Checked tables will appear in the diagram."
            rebuildTree()
            setOKActionEnabled(true)
          }, onFailure = { error ->
            catalog = null
            dirty = true
            discoveryError = error.message ?: "Could not read SQL sources"
            status.text = discoveryError
            status.toolTipText = discoveryError
            rebuildTree()
          })
        }
      }.submit(AppExecutorUtil.getAppExecutorService())
  }

  private fun matchingTables() = catalog?.tables.orEmpty().filter { it.id.contains(search.text.trim(), ignoreCase = true) }

  private fun selectShown(checked: Boolean) {
    matchingTables().forEach { choices[it.id] = checked }
    rebuildTree()
  }

  private fun rebuildTree() {
    rebuilding = true
    try {
      val root = CheckedTreeNode("Tables")
      matchingTables().groupBy { it.schema }.toSortedMap().forEach { (schema, tables) ->
        val parent = CheckedTreeNode(schema)
        tables.sortedBy { it.name }.forEach { table -> parent.add(CheckedTreeNode(table).apply { isChecked = choices[table.id] == true }) }
        parent.isChecked = tables.all { choices[it.id] == true }
        root.add(parent)
      }
      tree.model = DefaultTreeModel(root)
      var row = 0
      while (row < tree.rowCount) tree.expandRow(row++)
    } finally { rebuilding = false }
    updateCount()
  }

  private fun updateCount() {
    val tables = catalog?.tables.orEmpty()
    val selected = tables.count { choices[it.id] == true }
    count.text = "$selected of ${tables.size} tables selected. " + if (mode.selectedIndex == 0)
      "New tables will be included automatically." else "New tables will stay hidden until selected."
  }

  override fun doValidate(): ValidationInfo? {
    if (creating) {
      if (nameField.text.isBlank() || nameField.text.trim() == ".erd.yaml" || fileName.any { it in "\\/:*?\"<>|" })
        return ValidationInfo("Enter a valid diagram file name.", nameField)
      if (directory.findChild(fileName) != null) return ValidationInfo("A file named $fileName already exists.", nameField)
    }
    if (sourceConfig().imports.isEmpty()) return ValidationInfo("Add at least one SQL source.", sourceTable)
    if (dirty || catalog == null) return ValidationInfo(discoveryError ?: "Preview the tables before applying.", preview)
    return null
  }
}
