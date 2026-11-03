package com.abugyda.dbd.builders

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.abugyda.dbd.configuration.DiagramConfiguration
import com.abugyda.dbd.configuration.SqlImport
import com.abugyda.dbd.configuration.TableFilter
import com.abugyda.dbd.editor.ErdYamlUpdater
import com.abugyda.dbd.webview.WebviewBridge
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLFile

class DiagramConfigurationTest : BasePlatformTestCase() {
  private fun schema(): YAMLFile {
    myFixture.addFileToProject(
      "schema.sql", """
      CREATE TABLE users (id int PRIMARY KEY);
      CREATE TABLE posts (id int PRIMARY KEY, user_id int REFERENCES users(id));
    """.trimIndent()
    )
    return myFixture.addFileToProject(
      "diagram.erd.yaml", """
      schema:
        dialect: postgresql
        imports: [schema.sql]
      tables:
        public.users: {x: 120, y: 80, width: 320, color: '#123456'}
        public.posts: {x: 720, y: 80, width: 300}
      groups:
        - id: g
          name: Publishing
          color: '#123456'
          nodeIds: [public.users, public.posts]
      notes:
        - id: guide
          text: Keep this note
          x: 10
          y: 20
      custom: preserve-me
    """.trimIndent()
    ) as YAMLFile
  }

  fun testExcludedTablesHideEdgesButPreserveReferencesAndBothDirectionIndicators() {
    val yaml = schema()
    val config = DiagramConfiguration.read(yaml)
    ErdYamlUpdater.configureDiagram(
      project,
      yaml,
      config.copy(tableFilter = TableFilter("exclude", setOf("public.users")))
    )
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val postsOnly = ErdDataBuilder.build(yaml, project)
    assertEquals(listOf("public.posts"), postsOnly.tables.map { it.id })
    assertEmpty(postsOnly.relationships)
    assertEquals(
      "users",
      postsOnly.tables.single().fields.single { it.name == "user_id" }.reference?.toTable
    )
    assertEquals(listOf("public.users"), postsOnly.tables.single().hiddenRelatedTables)
    ErdYamlUpdater.configureDiagram(
      project,
      yaml,
      config.copy(tableFilter = TableFilter("include", setOf("public.users")))
    )
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val usersOnly = ErdDataBuilder.build(yaml, project)
    assertEquals(listOf("public.posts"), usersOnly.tables.single().hiddenRelatedTables)
    ErdYamlUpdater.configureDiagram(project, yaml, config)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val restored = ErdDataBuilder.build(yaml, project)
    assertSize(2, restored.tables)
    assertSize(1, restored.relationships)
    val user = restored.tables.single { it.id == "public.users" }
    assertEquals(120, user.horizontal)
    assertEquals("#123456", user.color)
    assertEquals(listOf("public.users", "public.posts"), restored.groups.single()["nodeIds"])
    assertEquals("Keep this note", restored.notes.single().content)
    assertTrue(yaml.text.contains("custom: preserve-me"))
  }

  fun testNewTablesRespectFilterModeAndEmptySelectionIsValid() {
    val yaml = schema()
    val config = DiagramConfiguration.read(yaml)
    val sql = myFixture.findFileInTempDir("schema.sql")
    val document = FileDocumentManager.getInstance().getDocument(sql)!!
    WriteCommandAction.runWriteCommandAction(project) {
      document.insertString(
        document.textLength,
        "\nCREATE TABLE future (id int PRIMARY KEY);"
      )
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    ErdYamlUpdater.configureDiagram(
      project,
      yaml,
      config.copy(tableFilter = TableFilter("include", setOf("public.users")))
    )
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals(listOf("public.users"), ErdDataBuilder.build(yaml, project).tables.map { it.id })
    ErdYamlUpdater.configureDiagram(
      project,
      yaml,
      config.copy(tableFilter = TableFilter("exclude", setOf("public.posts")))
    )
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals(
      setOf("public.users", "public.future"),
      ErdDataBuilder.build(yaml, project).tables.map { it.id }.toSet()
    )
    ErdYamlUpdater.configureDiagram(
      project,
      yaml,
      config.copy(tableFilter = TableFilter("include"))
    )
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val empty = ErdDataBuilder.build(yaml, project)
    assertEmpty(empty.tables)
    assertEmpty(empty.relationships)
    assertEquals(1, empty.sourceCount)
    assertEquals(2, (empty.groups.single()["nodeIds"] as List<*>).size)
  }

  fun testConfigurationRoundTripsQuotedPathsAndKeepsUnknownSchemaSettings() {
    val sqlName = "schema 'quoted' definitions.sql"
    myFixture.addFileToProject(sqlName, "CREATE TABLE catalog.items (id int PRIMARY KEY);")
    val config = DiagramConfiguration(
      listOf(SqlImport(sqlName, "catalog")),
      "postgresql",
      "public",
      TableFilter("include", setOf("catalog.items", "catalog.future"))
    )
    val yaml = myFixture.addFileToProject("quoted.erd.yaml", config.yaml() + "\n") as YAMLFile
    assertEquals(config, DiagramConfiguration.read(yaml))
    assertEquals("catalog.items", ErdDataBuilder.build(yaml, project).tables.single().id)
    assertEquals(config.imports, DiagramConfiguration.read(yaml).imports)
    val existing = myFixture.addFileToProject(
      "existing.erd.yaml",
      "schema: {imports: [], custom_option: keep}\ntables: {}"
    ) as YAMLFile
    ErdYamlUpdater.configureDiagram(project, existing, config)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals(config, DiagramConfiguration.read(existing))
    assertTrue(existing.text.contains("custom_option: keep"))
    assertEquals("catalog.items", ErdDataBuilder.build(existing, project).tables.single().id)
  }

  fun testNativeDialogPreviewsTablesAndSupportsSchemaCheckboxes() {
    val yaml = schema()
    val dialog = com.abugyda.dbd.configuration.DiagramConfigurationDialog(
      project, yaml.virtualFile.parent,
      DiagramConfiguration.read(yaml), creating = false
    )

    fun descendants(component: java.awt.Component): List<java.awt.Component> = listOf(component) +
        if (component is java.awt.Container) component.components.flatMap { descendants(it) } else emptyList()
    try {
      val components = descendants(dialog.setupPanel)
      assertEmpty(components.filterIsInstance<javax.swing.JTabbedPane>())
      assertSize(1, components.filterIsInstance<com.intellij.ui.OnePixelSplitter>())
      components.filterIsInstance<javax.swing.JButton>().single { it.text == "Refresh" }.doClick()
      com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching(
        "Wait for SQL preview",
        { dialog.isOKActionEnabled },
        15
      )
      val tree = components.filterIsInstance<com.intellij.ui.CheckboxTree>().single()
      val root = tree.model.root as com.intellij.ui.CheckedTreeNode
      val schema = root.getChildAt(0) as com.intellij.ui.CheckedTreeNode
      tree.setNodeState(schema, false)
      assertEquals(setOf("public.users", "public.posts"), dialog.configuration().tableFilter.tables)
      val first = schema.getChildAt(0) as com.intellij.ui.CheckedTreeNode
      tree.setNodeState(first, true)
      val selectedId = (first.userObject as WebviewBridge.DbTable).id
      val mode = components.filterIsInstance<javax.swing.JComboBox<*>>()
        .single { it.getItemAt(0) == "All tables except unchecked" }
      mode.selectedIndex = 1
      assertEquals(TableFilter("include", setOf(selectedId)), dialog.configuration().tableFilter)
    } finally {
      dialog.close(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE)
    }
  }

  fun testSourceDiscoveryAndBridgeSupportConfigurationWithoutARealDiagramFile() {
    val yaml = schema()
    val config = DiagramConfiguration.read(yaml)
    val sources = SqlSourceLoader.load(yaml.virtualFile.parent, config, project)
    val preview = YAMLElementGenerator.getInstance(project).createDummyYamlWithText(config.yaml())
    assertSize(2, ErdDataBuilder.build(preview, project, sources).tables)
    val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    val request = mapper.readValue(
      """{"type":"CONFIGURE_DIAGRAM","includeTableIds":["public.posts"]}""",
      WebviewBridge.Client::class.java
    ) as WebviewBridge.Client.ConfigureDiagram
    assertEquals(listOf("public.posts"), request.includeTableIds)
    val simple = mapper.readValue(
      """{"type":"CONFIGURE_DIAGRAM"}""",
      WebviewBridge.Client::class.java
    ) as WebviewBridge.Client.ConfigureDiagram
    assertEmpty(simple.includeTableIds)
  }

  fun testFolderImportsRoundTripAsBlockYamlAndDeduplicateIncludesAndOverlaps() {
    myFixture.addFileToProject(
      "sql/start.sql",
      "\\i nested/items.sql\nCREATE TABLE users (id int PRIMARY KEY);"
    )
    myFixture.addFileToProject("sql/nested/items.sql", "CREATE TABLE items (id int PRIMARY KEY);")
    myFixture.addFileToProject("sql/nested/other.SQL", "CREATE TABLE other (id int PRIMARY KEY);")
    myFixture.addFileToProject("sql/ignored.txt", "CREATE TABLE ignored (id int PRIMARY KEY);")
    val config = DiagramConfiguration(
      listOf(
        SqlImport("sql", folder = true),
        SqlImport("sql/nested", folder = true), SqlImport("sql/start.sql")
      ), "postgresql"
    )
    val yaml = myFixture.addFileToProject("folders.erd.yaml", config.yaml()) as YAMLFile
    assertEquals(config, DiagramConfiguration.read(yaml))
    val sources = SqlSourceLoader.load(yaml, project)
    assertSize(3, sources)
    assertEquals(
      setOf("public.users", "public.items", "public.other"),
      ErdDataBuilder.build(yaml, project).tables.map { it.id }.toSet()
    )
    val existing = schema()
    ErdYamlUpdater.configureDiagram(project, existing, config)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals(config, DiagramConfiguration.read(existing))
    val root = existing.documents.first().topLevelValue as org.jetbrains.yaml.psi.YAMLMapping
    val mapping = root.getKeyValueByKey("schema")!!.value as org.jetbrains.yaml.psi.YAMLMapping
    assertFalse(mapping.getKeyValueByKey("imports")!!.value!!.text.trimStart().startsWith("["))
    assertTrue(existing.text.contains("custom: preserve-me"))
    assertTrue(existing.text.contains("Keep this note"))
    val stamp = existing.modificationStamp
    ErdYamlUpdater.configureDiagram(project, existing, config)
    assertEquals(
      "Unchanged configuration should not rewrite YAML",
      stamp,
      existing.modificationStamp
    )
  }

  fun testFolderRecursionSchemaOverridesAndNewFiles() {
    myFixture.addFileToProject("definitions/one.sql", "CREATE TABLE one (id int PRIMARY KEY);")
    myFixture.addFileToProject(
      "definitions/nested/two.sql",
      "CREATE TABLE two (id int PRIMARY KEY);"
    )
    val flat = DiagramConfiguration(
      listOf(
        SqlImport(
          "definitions",
          "catalog",
          folder = true,
          recursive = false
        )
      ), "postgresql"
    )
    val yaml = myFixture.addFileToProject("flat.erd.yaml", flat.yaml()) as YAMLFile
    assertEquals(flat, DiagramConfiguration.read(yaml))
    assertEquals(listOf("catalog.one"), ErdDataBuilder.build(yaml, project).tables.map { it.id })
    myFixture.addFileToProject("definitions/three.sql", "CREATE TABLE three (id int PRIMARY KEY);")
    assertEquals(
      setOf("catalog.one", "catalog.three"),
      ErdDataBuilder.build(yaml, project).tables.map { it.id }.toSet()
    )
    val twoSchemas = flat.copy(imports = flat.imports + SqlImport("definitions/one.sql", "public"))
    ErdYamlUpdater.configureDiagram(project, yaml, twoSchemas)
    assertEquals(
      setOf("catalog.one", "catalog.three", "public.one"),
      ErdDataBuilder.build(yaml, project).tables.map { it.id }.toSet()
    )
  }

  fun testFolderSourcesAcceptDiagramDirectoryAndReportInvalidPaths() {
    myFixture.addFileToProject("same/one.sql", "CREATE TABLE one (id int PRIMARY KEY);")
    val config = DiagramConfiguration(listOf(SqlImport(".", folder = true)), "postgresql")
    val yaml = myFixture.addFileToProject("same/map.erd.yaml", config.yaml()) as YAMLFile
    assertEquals(listOf("public.one"), ErdDataBuilder.build(yaml, project).tables.map { it.id })
    val root = yaml.virtualFile.parent
    fun fails(source: SqlImport, expected: String) {
      try {
        SqlSourceLoader.load(
          root,
          config.copy(imports = listOf(source)),
          project
        ); fail("Expected $expected")
      } catch (error: IllegalStateException) {
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(expected))
      }
    }
    fails(SqlImport(""), "cannot be empty")
    fails(SqlImport("one.sql", folder = true), "Not a SQL folder")
    fails(SqlImport("."), "Not a SQL file")
    fails(SqlImport("missing", folder = true), "Missing SQL import")
  }

  fun testSplitDialogAutomaticallyDiscoversSourceChangesAndPreservesCheckboxChoices() {
    myFixture.addFileToProject("definitions/one.sql", "CREATE TABLE one (id int PRIMARY KEY);")
    myFixture.addFileToProject(
      "definitions/nested/two.sql",
      "CREATE TABLE two (id int PRIMARY KEY);"
    )
    val yaml = myFixture.addFileToProject(
      "auto.erd.yaml", DiagramConfiguration(
        listOf(SqlImport("definitions", folder = true)), "postgresql"
      ).yaml()
    ) as YAMLFile
    val dialog = com.abugyda.dbd.configuration.DiagramConfigurationDialog(
      project, yaml.virtualFile.parent,
      DiagramConfiguration.read(yaml), creating = false
    )

    fun descendants(component: java.awt.Component): List<java.awt.Component> = listOf(component) +
        if (component is java.awt.Container) component.components.flatMap { descendants(it) } else emptyList()
    try {
      val components = descendants(dialog.setupPanel)
      val table = components.filterIsInstance<com.intellij.ui.table.JBTable>().single()
      val model = table.model as com.abugyda.dbd.configuration.SqlSourcesTableModel
      assertTrue(model.isCellEditable(0, 2))
      components.filterIsInstance<javax.swing.JButton>().single { it.text == "Refresh" }.doClick()
      com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching(
        "Initial folder discovery",
        { dialog.isOKActionEnabled },
        15
      )
      val tree = components.filterIsInstance<com.intellij.ui.CheckboxTree>().single()
      val schema =
        (tree.model.root as com.intellij.ui.CheckedTreeNode).getChildAt(0) as com.intellij.ui.CheckedTreeNode
      tree.setNodeState(schema.getChildAt(0) as com.intellij.ui.CheckedTreeNode, false)
      model.setValueAt(false, 0, 2)
      com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching(
        "Automatic flat-folder discovery",
        { dialog.isOKActionEnabled },
        15
      )
      assertEquals(setOf("public.one"), dialog.configuration().tableFilter.tables)
      myFixture.addFileToProject(
        "definitions/three.sql",
        "CREATE TABLE three (id int PRIMARY KEY);"
      )
      com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching(
        "New SQL file preview",
        {
          dialog.isOKActionEnabled && (tree.model.root as com.intellij.ui.CheckedTreeNode).let {
            it.childCount == 1 && (it.getChildAt(0) as com.intellij.ui.CheckedTreeNode).childCount == 2
          }
        },
        15
      )
      assertEquals(setOf("public.one"), dialog.configuration().tableFilter.tables)
      assertFalse(dialog.configuration().imports.single().recursive)
      components.filterIsInstance<javax.swing.JComboBox<*>>().single {
        it.getItemAt(0) == "All tables except unchecked"
      }.selectedIndex = 1
      myFixture.addFileToProject("definitions/four.sql", "CREATE TABLE four (id int PRIMARY KEY);")
      com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching(
        "New tables respect current include mode",
        {
          dialog.isOKActionEnabled && (tree.model.root as com.intellij.ui.CheckedTreeNode).let {
            it.childCount == 1 && (it.getChildAt(0) as com.intellij.ui.CheckedTreeNode).childCount == 3
          }
        },
        15
      )
      assertEquals(
        TableFilter("include", setOf("public.three")),
        dialog.configuration().tableFilter
      )
      model.add(SqlImport("definitions/one.sql"))
      assertFalse(model.isCellEditable(1, 2))
      model.remove(0)
      assertFalse(model.sourceAt(0).folder)
    } finally {
      dialog.close(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE)
    }
  }
}
