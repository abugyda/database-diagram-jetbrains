package com.abugyda.dbd.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.abugyda.dbd.builders.ErdDataBuilder
import com.abugyda.dbd.builders.affectsSqlSources
import com.abugyda.dbd.webview.WebviewBridge.SchemaPayload
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.yaml.psi.YAMLFile
import java.util.concurrent.Callable

/** All schema reads share one background pipeline, including startup and Refresh. */
internal class SchemaRefreshController(
  private val project: Project,
  private val file: VirtualFile,
  private val document: Document,
  private val parent: Disposable,
  private val canRefresh: () -> Boolean,
  private val deliver: (SchemaPayload) -> Unit,
  private val reportError: (String) -> Unit,
  private val build: (YAMLFile) -> SchemaPayload = { ErdDataBuilder.build(it, project) }
) {
  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parent)
  private var enabled = false
  private var refreshNeeded = false
  @Volatile private var generation = 0L
  private var pending: CancellablePromise<*>? = null

  init {
    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (event.document === document) {
          if (canRefresh()) request(300)
        }
        else if (FileDocumentManager.getInstance().getFile(event.document)?.extension.equals("sql", true)) request(500)
      }
    }, parent)
    project.messageBus.connect(parent).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        if (events.any(::affectsSqlSources)) request(500)
      }
    })
  }

  fun refresh() {
    enabled = true
    request(0)
  }

  fun resumePendingRefresh() {
    if (refreshNeeded) request(0)
  }

  /** A presentation write already reflects the webview's state; cancel older reads. */
  fun cancelPending() {
    generation++
    alarm.cancelAllRequests()
    pending?.cancel()
    pending = null
  }

  private fun request(delayMs: Int) {
    val app = ApplicationManager.getApplication()
    if (!app.isDispatchThread) {
      app.invokeLater(Runnable { request(delayMs) }, ModalityState.nonModal())
      return
    }
    if (!enabled || alarm.isDisposed) return
    refreshNeeded = true
    if (!canRefresh()) return
    cancelPending()
    val ticket = generation
    alarm.addRequest({
      if (!canRefresh() || ticket != generation) return@addRequest
      pending = ReadAction.nonBlocking(Callable {
        try {
          val yaml = PsiDocumentManager.getInstance(project).getPsiFile(document) as? YAMLFile
            ?: error("Cannot read ERD YAML: ${file.name}")
          Result.success(build(yaml))
        } catch (error: ProcessCanceledException) { throw error }
        catch (error: Exception) { Result.failure<SchemaPayload>(error) }
      }).withDocumentsCommitted(project).inSmartMode(project)
        .expireWith(parent).expireWhen { ticket != generation || !file.isValid || project.isDisposed }
        .coalesceBy(this)
        .finishOnUiThread(ModalityState.nonModal()) { result ->
          if (ticket == generation && canRefresh()) {
            pending = null
            refreshNeeded = false
            result.fold(deliver, { reportError(it.message ?: "Could not read SQL schema") })
          }
        }.submit(AppExecutorUtil.getAppExecutorService())
    }, delayMs)
  }
}
