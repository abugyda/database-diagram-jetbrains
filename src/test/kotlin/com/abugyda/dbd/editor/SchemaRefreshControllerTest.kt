package com.abugyda.dbd.editor

import com.abugyda.dbd.builders.ErdDataBuilder
import com.abugyda.dbd.webview.WebviewBridge.SchemaPayload
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.psi.YAMLFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class SchemaRefreshControllerTest : BasePlatformTestCase() {
  fun testStartupAndUnsavedSqlRefreshReadInBackgroundAndPublishOnEdt() {
    val sql = myFixture.addFileToProject("tables.sql", "CREATE TABLE users (id int PRIMARY KEY);")
    val yaml = myFixture.addFileToProject("map.erd.yaml", "schema:\n  dialect: postgresql\n  imports: [tables.sql]") as YAMLFile
    val doc = FileDocumentManager.getInstance().getDocument(yaml.virtualFile)!!
    val parent = Disposer.newDisposable(testRootDisposable, "schema refresh")
    val payloads = mutableListOf<SchemaPayload>()
    val errors = mutableListOf<String>()
    var allowed = true
    var builds = 0
    val app = ApplicationManager.getApplication()
    val controller = SchemaRefreshController(project, yaml.virtualFile, doc, parent, { allowed }, {
      assertTrue(app.isDispatchThread)
      payloads.add(it)
    }, errors::add, { psi ->
      assertFalse("SQL and PSI must never be read on EDT", app.isDispatchThread)
      assertTrue(app.isReadAccessAllowed)
      builds++
      ErdDataBuilder.build(psi, project)
    })
    // Reproduce an unsafe timer-style context. The request itself must not commit PSI.
    app.invokeLater(Runnable { controller.refresh(); controller.refresh() }, ModalityState.any())
    PlatformTestUtil.waitWithEventsDispatching("Startup schema", { payloads.size == 1 }, 15)
    assertEquals(1, builds)
    val sqlDoc = FileDocumentManager.getInstance().getDocument(sql.virtualFile)!!
    WriteCommandAction.runWriteCommandAction(project) {
      sqlDoc.setText("CREATE TABLE users (id int PRIMARY KEY, title text);")
    }
    PlatformTestUtil.waitWithEventsDispatching("Unsaved SQL changes", { payloads.size == 2 }, 15)
    assertEquals(listOf("id", "title"), payloads.last().tables.single().fields.map { it.name })
    allowed = false
    controller.cancelPending()
    WriteCommandAction.runWriteCommandAction(project) { doc.insertString(doc.textLength, "\ntables: {public.users: {x: 80, y: 160}}") }
    // A SQL edit received during a presentation save must be resumed, not lost.
    WriteCommandAction.runWriteCommandAction(project) {
      sqlDoc.setText("CREATE TABLE users (id int PRIMARY KEY, title text, active boolean);")
    }
    allowed = true
    controller.resumePendingRefresh()
    PlatformTestUtil.waitWithEventsDispatching("Latest presentation", { payloads.size == 3 }, 15)
    assertEquals(80, payloads.last().tables.single().horizontal)
    assertEquals(3, payloads.last().tables.single().fields.size)
    WriteCommandAction.runWriteCommandAction(project) { doc.setText("schema:\n  imports: [missing.sql]") }
    PlatformTestUtil.waitWithEventsDispatching("Import error", { errors.isNotEmpty() }, 15)
    assertTrue(errors.single().contains("missing.sql"))
    assertSize(3, payloads)
    Disposer.dispose(parent)
  }

  fun testSupersededReadsAndDisposedEditorsNeverPublishResults() {
    val yaml = myFixture.addFileToProject("empty.erd.yaml", "schema:\n  imports: []") as YAMLFile
    val doc = FileDocumentManager.getInstance().getDocument(yaml.virtualFile)!!
    val parent = Disposer.newDisposable(testRootDisposable, "cancelled schema refresh")
    val started = CountDownLatch(1)
    val release = AtomicBoolean(false)
    val published = mutableListOf<SchemaPayload>()
    val controller = SchemaRefreshController(project, yaml.virtualFile, doc, parent, { true }, published::add, { fail(it) }, { psi ->
      started.countDown()
      while (!release.get()) {
        com.intellij.openapi.progress.ProgressManager.checkCanceled()
        Thread.sleep(5)
      }
      ErdDataBuilder.build(psi, project)
    })
    controller.refresh()
    PlatformTestUtil.waitWithEventsDispatching("Background read started", { started.count == 0L }, 15)
    controller.cancelPending()
    release.set(true)
    controller.refresh()
    PlatformTestUtil.waitWithEventsDispatching("Only latest result delivered", { published.size == 1 }, 15)
    controller.refresh()
    Disposer.dispose(parent)
    controller.refresh()
    com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
    assertSize(1, published)
  }
}
