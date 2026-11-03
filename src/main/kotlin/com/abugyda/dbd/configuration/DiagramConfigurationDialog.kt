package com.abugyda.dbd.configuration

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.icons.AllIcons
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.Alarm
import com.abugyda.dbd.builders.ErdDataBuilder
import com.abugyda.dbd.builders.SqlSourceLoader
import com.abugyda.dbd.builders.affectsSqlSources
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
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.tree.DefaultTreeModel
import org.jetbrains.concurrency.CancellablePromise

/** Shared setup for new diagrams and the webview's Configure diagram action. */
class DiagramConfigurationDialog(
  private val project: Project,
  private val directory: VirtualFile,
  private val initial: DiagramConfiguration,
  private val creating: Boolean,
  includeTableIds: List<String> = emptyList()
) : DialogWrapper(project, true) {
  private val nameField = JTextField("diagram", 24)
  private val sourceModel = SqlSourcesTableModel(initial.imports)
  private val sourceTable = JBTable(sourceModel)
  private val dialect = JComboBox(
    arrayOf("Use IDE mapping", "PostgreSQL") +
        if (initial.dialect != null && initial.dialect !in setOf("postgres", "postgresql")) arrayOf(
          initial.dialect
        ) else emptyArray()
  )
  private val defaultSchema = JTextField(initial.defaultSchema, 16)
  private val mode = JComboBox(arrayOf("All tables except unchecked", "Only checked tables"))
  private val splitter = OnePixelSplitter(false, "DatabaseDiagram.Configure.Split", .45f)
  private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable)
  private var pending: CancellablePromise<*>? = null
  private val search = SearchTextField()
  private val status = JBLabel("Add SQL files or a folder to discover tables.")
  private val count = JBLabel(" ")
  private val preview = JButton("Refresh")
  private val choices = includeTableIds.associateWith { true }.toMutableMap()
  private var catalog: SchemaPayload? = null
  @Volatile
  private var revision = 0
  private var dirty = true
  private var loading = false
  private var discoveryError: String? = null
  private var rebuilding = false
  private val tree = object : CheckboxTree(
    object : CheckboxTree.CheckboxTreeCellRenderer(true) {
      override fun customizeRenderer(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
      ) {
        val item = (value as CheckedTreeNode).userObject
        textRenderer.append(if (item is DbTable) item.name else item.toString())
      }
    },
    CheckedTreeNode("Tables"),
    com.intellij.ui.CheckboxTreeBase.CheckPolicy(true, true, true, true, true)
  ) {
    override fun onNodeStateChanged(node: CheckedTreeNode?) {
      if (node == null) return
      if (!rebuilding) {
        (node.userObject as? DbTable)?.let { choices[it.id] = node.isChecked }
        updateCount()
      }
    }
  }

  internal val setupPanel: JComponent by lazy {
    JPanel(BorderLayout(0, JBUI.scale(10))).apply {
      preferredSize = Dimension(JBUI.scale(1080), JBUI.scale(600))
      if (creating) add(JPanel(FlowLayout(FlowLayout.LEADING)).apply {
        add(JBLabel("Diagram name:")); add(
        nameField
      )
      }, BorderLayout.NORTH)
      add(splitter, BorderLayout.CENTER)
    }
  }

  init {
    title = if (creating) "New ERD Diagram" else "Configure Diagram"
    setOKButtonText(if (creating) "Create diagram" else "Apply")
    dialect.selectedIndex = when (initial.dialect) {
      null -> 0; "postgres", "postgresql" -> 1; else -> 2
    }
    mode.selectedIndex = if (initial.tableFilter.mode == "include") 1 else 0
    tree.isRootVisible = false
    tree.showsRootHandles = true
    sourceTable.putClientProperty("terminateEditOnFocusLost", true)
    sourceTable.columnModel.getColumn(0).preferredWidth = 260
    sourceTable.columnModel.getColumn(1).preferredWidth = 110
    sourceTable.columnModel.getColumn(2).apply { preferredWidth = 85; maxWidth = JBUI.scale(100) }
    val checkboxRenderer = sourceTable.getDefaultRenderer(java.lang.Boolean::class.java)
    val emptyRenderer = DefaultTableCellRenderer()
    sourceTable.columnModel.getColumn(2).cellRenderer =
      javax.swing.table.TableCellRenderer { table, value, selected, focus, row, column ->
        if (sourceModel.sourceAt(table.convertRowIndexToModel(row)).folder)
          checkboxRenderer.getTableCellRendererComponent(table, value, selected, focus, row, column)
        else emptyRenderer.getTableCellRendererComponent(table, "", selected, focus, row, column)
      }
    sourceTable.columnModel.getColumn(0).cellRenderer = object : DefaultTableCellRenderer() {
      override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        selected: Boolean,
        focus: Boolean,
        row: Int,
        column: Int
      ): java.awt.Component {
        super.getTableCellRendererComponent(table, value, selected, focus, row, column)
        val source = sourceModel.sourceAt(table.convertRowIndexToModel(row))
        icon = if (source.folder) AllIcons.Nodes.Folder else AllIcons.FileTypes.Text
        toolTipText = "${if (source.folder) "Folder" else "SQL file"}: ${source.path}"
        return this
      }
    }
    sourceTable.accessibleContext.accessibleName = "SQL sources"
    sourceTable.emptyText.text = "Add SQL files or folders from your project"
    sourceModel.addTableModelListener { invalidateSources() }
    dialect.addActionListener { invalidateSources() }
    defaultSchema.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) = invalidateSources()
    })
    search.textEditor.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) = rebuildTree()
    })
    mode.addActionListener { updateCount() }
    preview.addActionListener { if (!loading) discover() }
    splitter.firstComponent = sourcesPanel()
    splitter.secondComponent = tablesPanel()
    init()
    setOKActionEnabled(false)
    window?.addWindowListener(object : WindowAdapter() {
      override fun windowOpened(e: WindowEvent) {
        if (initial.imports.isNotEmpty()) {
          if (!loading) discover()
        }
      }
    })
    project.messageBus.connect(disposable)
      .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
        override fun after(events: List<VFileEvent>) {
          if (events.any(::affectsSqlSources)) scheduleSourceRefresh()
        }
      })
    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
        if (FileDocumentManager.getInstance().getFile(event.document)?.extension.equals(
            "sql",
            true
          )
        ) scheduleSourceRefresh()
      }
    }, disposable)
  }

  val fileName: String
    get() = nameField.text.trim().let { if (it.endsWith(".erd.yaml", true)) it else "$it.erd.yaml" }

  private fun sourceConfig(): DiagramConfiguration {
    if (sourceTable.isEditing) sourceTable.cellEditor.stopCellEditing()
    val imports = sourceModel.imports()
    val selectedDialect = when (dialect.selectedIndex) {
      0 -> null; 1 -> "postgresql"; else -> initial.dialect
    }
    return DiagramConfiguration(imports, selectedDialect, defaultSchema.text.trim())
  }

  fun configuration(): DiagramConfiguration {
    val ids = catalog?.tables.orEmpty().map { it.id }.toSet()
    val filterMode = if (mode.selectedIndex == 0) "exclude" else "include"
    val savedUnknown =
      if (filterMode == initial.tableFilter.mode) initial.tableFilter.tables - ids else emptySet()
    val selected =
      ids.filter { id -> if (filterMode == "include") choices[id] == true else choices[id] != true }
        .toSet()
    return sourceConfig().copy(tableFilter = TableFilter(filterMode, selected + savedUnknown))
  }

  override fun createCenterPanel(): JComponent = setupPanel

  override fun getPreferredFocusedComponent(): JComponent = if (creating) nameField else sourceTable

  private fun sourcesPanel(): JComponent = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
    border = JBUI.Borders.empty(12)
    minimumSize = Dimension(JBUI.scale(300), 0)
    add(JPanel(BorderLayout(0, 8)).apply {
      add(com.intellij.ui.TitledSeparator("SQL sources"), BorderLayout.NORTH)
      add(JPanel(java.awt.GridLayout(1, 3, 6, 0)).apply {
        add(JButton("Add SQL files…").apply { addActionListener { addSources(false) } })
        add(JButton("Add folder…").apply { addActionListener { addSources(true) } })
        add(JButton("Remove").apply {
          toolTipText = "Remove selected sources"
          addActionListener {
            if (sourceTable.isEditing) sourceTable.cellEditor.stopCellEditing()
            sourceTable.selectedRows.map { sourceTable.convertRowIndexToModel(it) }
              .sortedDescending().forEach(sourceModel::remove)
          }
        })
      }, BorderLayout.SOUTH)
    }, BorderLayout.NORTH)
    add(JBScrollPane(sourceTable), BorderLayout.CENTER)
    add(JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(JPanel(java.awt.GridLayout(2, 2, 8, 8)).apply {
        add(JBLabel("Dialect:")); add(dialect)
        add(JBLabel("Default schema:")); add(defaultSchema)
      })
      add(Box.createVerticalStrut(JBUI.scale(10)))
      add(JBLabel("<html>Paths are relative to the diagram. Folder sources discover SQL<br>files automatically. Subfolders controls recursive discovery.<br>Schema overrides apply to unqualified names.</html>"))
      add(Box.createVerticalStrut(JBUI.scale(10)))
      add(JPanel(BorderLayout()).apply { add(status, BorderLayout.CENTER) })
    }, BorderLayout.SOUTH)
  }

  private fun tablesPanel(): JComponent = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
    border = JBUI.Borders.empty(12)
    minimumSize = Dimension(JBUI.scale(340), 0)
    add(JPanel(BorderLayout(8, 8)).apply {
      add(JPanel(BorderLayout(8, 0)).apply {
        add(com.intellij.ui.TitledSeparator("Tables"), BorderLayout.CENTER)
        add(preview, BorderLayout.EAST)
      }, BorderLayout.NORTH)
      add(search, BorderLayout.CENTER)
      add(mode, BorderLayout.SOUTH)
    }, BorderLayout.NORTH)
    add(JBScrollPane(tree), BorderLayout.CENTER)
    add(JPanel(BorderLayout()).apply {
      add(JPanel(FlowLayout(FlowLayout.LEADING)).apply {
        add(JButton("Select shown").apply { addActionListener { selectShown(true) } })
        add(JButton("Clear shown").apply { addActionListener { selectShown(false) } })
        add(JButton("Include related tables").apply {
          addActionListener {
            val selected = choices.filterValues { it }.keys
            catalog?.relationships.orEmpty().forEach { rel ->
              if (rel.fromTable in selected || rel.toTable in selected) {
                choices[rel.fromTable] = true; choices[rel.toTable] = true
              }
            }
            rebuildTree()
          }
        })
      }, BorderLayout.NORTH)
      add(count, BorderLayout.SOUTH)
    }, BorderLayout.SOUTH)
  }

  private fun addSources(folder: Boolean) {
    val descriptor = FileChooserDescriptor(!folder, folder, false, false, false, true)
      .withTitle(if (folder) "Add SQL Folders" else "Add SQL Sources")
    if (!folder) descriptor.withExtensionFilter("SQL files", "sql")
    descriptor.isForcedToUseIdeaFileChooser = true
    val roots = com.intellij.openapi.roots.ProjectRootManager.getInstance(project).contentRoots
    if (roots.isNotEmpty()) descriptor.withRoots(*roots).withShowFileSystemRoots(false)
    FileChooser.chooseFiles(descriptor, project, directory) { files ->
      files.forEach { file ->
        val path = VfsUtilCore.getRelativePath(file, directory, '/')
          ?: com.intellij.openapi.util.io.FileUtil.getRelativePath(directory.path, file.path, '/')
          ?: file.path
        sourceModel.add(SqlImport(path.ifEmpty { "." }, folder = folder))
      }
    }
  }

  private fun invalidateSources() {
    revision++
    pending?.cancel()
    pending = null
    refreshAlarm.cancelAllRequests()
    dirty = true
    loading = false
    discoveryError = null
    status.text = "Sources changed. Updating tables…"
    status.toolTipText = status.text
    preview.isEnabled = true
    tree.isEnabled = false
    setOKActionEnabled(false)
    refreshAlarm.addRequest({
      ApplicationManager.getApplication().invokeLater(Runnable {
        if (!refreshAlarm.isDisposed && dirty && !loading) discover()
      }, ModalityState.stateForComponent(setupPanel))
    }, 350)
  }

  private fun scheduleSourceRefresh() {
    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread) {
      if (!refreshAlarm.isDisposed) invalidateSources()
    } else app.invokeLater(Runnable {
      if (!refreshAlarm.isDisposed) invalidateSources()
    }, ModalityState.any())
  }

  private fun discover() {
    refreshAlarm.cancelAllRequests()
    val config = sourceConfig()
    if (config.imports.isEmpty()) {
      status.text = "Add at least one SQL source."; return
    }
    if (config.defaultSchema.isBlank()) {
      status.text = "Enter a default schema name."; return
    }
    val ticket = ++revision
    pending?.cancel()
    loading = true
    preview.isEnabled = false
    tree.isEnabled = false
    setOKActionEnabled(false)
    status.text = "Reading SQL sources…"
    pending = ReadAction.nonBlocking(Callable {
      try {
        val sources = SqlSourceLoader.load(directory, config, project)
        val yaml = YAMLElementGenerator.getInstance(project).createDummyYamlWithText(config.yaml())
        Result.success(ErdDataBuilder.build(yaml, project, sources))
      } catch (error: ProcessCanceledException) {
        throw error
      } catch (error: Exception) {
        Result.failure<SchemaPayload>(error)
      }
    }).withDocumentsCommitted(project).inSmartMode(project).expireWith(disposable)
      .expireWhen { ticket != revision || project.isDisposed }.coalesceBy(this)
      .finishOnUiThread(ModalityState.stateForComponent(setupPanel)) { result ->
        if (ticket == revision) {
          pending = null
          loading = false
          preview.isEnabled = true
          result.fold(onSuccess = { payload ->
            catalog = payload
            payload.tables.forEach {
              val checked = if (mode.selectedIndex == 0)
                initial.tableFilter.mode != "exclude" || initial.tableFilter.includes(it.id)
              else initial.tableFilter.mode == "include" && initial.tableFilter.includes(it.id)
              choices.putIfAbsent(it.id, checked)
            }
            dirty = false
            discoveryError = null
            tree.isEnabled = true
            status.text =
              "${payload.sourceCount ?: 0} SQL files · ${payload.tables.size} tables · ${payload.relationships.size} relationships"
            status.toolTipText = "${status.text}. Checked tables will appear in the diagram."
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

  private fun matchingTables() =
    catalog?.tables.orEmpty().filter { it.id.contains(search.text.trim(), ignoreCase = true) }

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
        tables.sortedBy { it.name }.forEach { table ->
          parent.add(CheckedTreeNode(table).apply {
            isChecked = choices[table.id] == true
          })
        }
        parent.isChecked = tables.all { choices[it.id] == true }
        root.add(parent)
      }
      tree.model = DefaultTreeModel(root)
      var row = 0
      while (row < tree.rowCount) tree.expandRow(row++)
    } finally {
      rebuilding = false
    }
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
      if (directory.findChild(fileName) != null) return ValidationInfo(
        "A file named $fileName already exists.",
        nameField
      )
    }
    if (sourceConfig().imports.isEmpty()) return ValidationInfo(
      "Add at least one SQL source.",
      sourceTable
    )
    if (dirty || catalog == null) return ValidationInfo(
      discoveryError ?: "Wait for table discovery or click Refresh.", preview
    )
    return null
  }
}
