package com.abugyda.dbd.webview

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.Timer

class WebviewPanel(
  private val parentDisposable: Disposable,
  private val file: VirtualFile,
  private val listener: WebviewListener,
  private val project: Project,
) : Disposable {

  interface WebviewListener {
    fun onConfigureDiagram(includeTableIds: List<String>)
    fun onWebviewReady()
    fun onPresentationSaved(message: WebviewBridge.Client.SavePresentation)
    fun onTablePositionUpdated(tableName: String, x: Int, y: Int, width: Int?)
    fun onNotePositionUpdated(name: String, x: Int, y: Int, width: Int, height: Int)
    fun onTableColorUpdated(tableName: String, color: String)
    fun onNoteColorUpdated(noteId: String, color: String)
    fun resolveInitialTheme(): String
    fun resolveThemeColors(): Map<String, String>
  }

  companion object {
    private const val RESOURCE_PATH = "/web/index.html"
    private val mapper = jacksonObjectMapper()

    // How long to wait for the JS READY message before triggering a fallback
    // render.  CEF loading is normally <500 ms; 4 s is a safe upper bound.
    private const val READY_TIMEOUT_MS = 4_000
  }

  private val logger = thisLogger()
  val component = JPanel(BorderLayout())
  private var jbCefBrowser: JBCefBrowser? = null
  private var cursorHandler: WebviewCursorHandler? = null

  // True once onWebviewReady() has been called at least once this session.
  @Volatile
  private var webviewReady = false

  // Fallback timer: if JS never sends READY (e.g. first-paint race on restore)
  // trigger a render after the timeout so the user isn't left with a blank panel.
  private var readyTimeoutTimer: Timer? = null

  init {
    scheduleInit()
  }

  // ── Init scheduling ────────────────────────────────────────────────────────

  private fun scheduleInit() {
    val startup = StartupManager.getInstance(project)

    // FIX: Two-path scheduling to handle both IDE startup scenarios:
    //
    // Path A — fresh project open (normal case):
    //   createEditorAsync runs *before* startup activities finish.
    //   `startupActivityPassed()` returns false → we register with runAfterOpened
    //   → callback fires after all services are ready → safe to create JBCefBrowser.
    //
    // Path B — session restore (IDE reopens with previously open tabs):
    //   createEditorAsync runs *during* or *after* startup because the project
    //   is already past the startup phase.  `startupActivityPassed()` may return
    //   true → runAfterOpened may never fire, or fires asynchronously past
    //   the disposal window.  We must call initBrowser directly via invokeLater.
    //
    // Using invokeLater in both paths guarantees we're on the EDT (required for
    // Swing/JCEF) and avoids the JBCefApp <clinit> service-access error.

    val initRunnable = Runnable {
      ApplicationManager.getApplication().invokeLater {
        if (!Disposer.isDisposed(parentDisposable)) {
          initBrowser()
        }
      }
    }

    @Suppress("UnstableApiUsage")
    if (startup.postStartupActivityPassed()) {
      // Project already fully initialised — run directly on the EDT.
      initRunnable.run()
    } else {
      // Project still starting up — register for after startup completes.
      startup.runAfterOpened(initRunnable)
    }
  }

  // ── Browser creation ───────────────────────────────────────────────────────

  private fun initBrowser() {
    if (!JBCefApp.isSupported()) {
      component.add(JLabel("JCEF Not Supported"), BorderLayout.CENTER)
      return
    }

    val browser = JBCefBrowser.createBuilder()
      .setWindowlessFramerate(com.abugyda.dbd.settings.DatabaseDiagramSettings.instance.state.targetFrameRate.coerceIn(30, 60))
      .setEnableOpenDevToolsMenuItem(true)
      .build()

    jbCefBrowser = browser
    val handler = WebviewCursorHandler(
      targets = { listOf(browser.component, browser.cefBrowser.uiComponent) },
      isActive = { jbCefBrowser === browser },
      glassPane = { javax.swing.SwingUtilities.getRootPane(browser.component)?.glassPane as? com.intellij.openapi.wm.IdeGlassPane }
    )
    cursorHandler = handler
    browser.jbCefClient.addDisplayHandler(handler, browser.cefBrowser)
    component.add(browser.component, BorderLayout.CENTER)
    component.revalidate()

    val router = CefMessageRouter.create()
    router.addHandler(object : CefMessageRouterHandlerAdapter() {
      override fun onQuery(
        browser: CefBrowser?, frame: CefFrame?, queryId: Long,
        request: String?, persistent: Boolean, callback: CefQueryCallback?
      ): Boolean {
        ApplicationManager.getApplication().invokeLater {
          try {
            handleClientQuery(request); callback?.success("ok")
          } catch (error: Exception) {
            callback?.failure(1, error.message ?: "Diagram request failed")
          }
        }
        return true
      }
    }, true)
    browser.jbCefClient.cefClient.addMessageRouter(router)

    browser.jbCefClient.addRequestHandler(object : org.cef.handler.CefRequestHandlerAdapter() {
      override fun getResourceRequestHandler(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: org.cef.network.CefRequest?,
        isNavigation: Boolean,
        isDownload: Boolean,
        requestInitiator: String?,
        disableDefaultHandling: org.cef.misc.BoolRef?
      ): org.cef.handler.CefResourceRequestHandler? {
        if (request?.url?.startsWith("https://diagram.local/") != true) return null
        return object : org.cef.handler.CefResourceRequestHandlerAdapter() {
          override fun getResourceHandler(
            browser: CefBrowser?,
            frame: CefFrame?,
            request: org.cef.network.CefRequest?
          ): org.cef.handler.CefResourceHandler =
            WebviewSchemeHandler(listener.resolveInitialTheme(), listener.resolveThemeColors())
        }
      }
    }, browser.cefBrowser)
    browser.loadURL("https://diagram.local/index.html")

    Disposer.register(parentDisposable, browser)
    Disposer.register(parentDisposable, this)

    // Start a fallback timer in case the JS READY message is missed.
    // This can happen on session-restore when CEF loads the cached page
    // but the tab is not yet visible, so the JS listener registration
    // races with our postMessage calls.
    startReadyTimeout()
  }

  // ── Fallback ready-timeout ─────────────────────────────────────────────────

  private fun startReadyTimeout() {
    readyTimeoutTimer?.stop()
    val timer = Timer(READY_TIMEOUT_MS) {
      if (!webviewReady && !Disposer.isDisposed(parentDisposable)) {
        logger.warn("WebviewPanel: JS READY message not received within ${READY_TIMEOUT_MS}ms, triggering fallback render")
        // Swing timer callbacks are not write-safe contexts. Queue startup through
        // the platform's modality-aware dispatcher; the listener schedules a background read.
        ApplicationManager.getApplication().invokeLater(Runnable {
          if (!webviewReady && !project.isDisposed && !Disposer.isDisposed(parentDisposable)) listener.onWebviewReady()
        }, ModalityState.nonModal())
      }
    }
    timer.isRepeats = false
    timer.start()
    readyTimeoutTimer = timer
  }

  // ── Message handling ───────────────────────────────────────────────────────

  private fun handleClientQuery(request: String?) {
    if (request == null) return
    try {
      when (val msg = mapper.readValue(request, WebviewBridge.Client::class.java)) {
        is WebviewBridge.Client.Ready -> {
          webviewReady = true
          readyTimeoutTimer?.stop()
          listener.onWebviewReady()
        }

        is WebviewBridge.Client.ConfigureDiagram -> listener.onConfigureDiagram(msg.includeTableIds)
        is WebviewBridge.Client.UpdateCursor -> cursorHandler?.onCssCursorChange(msg.cursor)
        is WebviewBridge.Client.RefreshSchema -> listener.onWebviewReady()
        is WebviewBridge.Client.SavePresentation -> listener.onPresentationSaved(msg)
        is WebviewBridge.Client.Log -> logger.info("Webview: ${msg.message}")
        is WebviewBridge.Client.UpdateTablePos -> listener.onTablePositionUpdated(
          msg.tableName,
          msg.x,
          msg.y,
          msg.width
        )

        is WebviewBridge.Client.UpdateNotePos -> listener.onNotePositionUpdated(
          msg.name,
          msg.x,
          msg.y,
          msg.width,
          msg.height
        )

        is WebviewBridge.Client.UpdateTableColor -> listener.onTableColorUpdated(
          msg.tableName,
          msg.color
        )

        is WebviewBridge.Client.UpdateNoteColor -> listener.onNoteColorUpdated(
          msg.noteId,
          msg.color
        )
      }
    } catch (e: Exception) {
      logger.warn("Failed to process webview message", e)
      throw e
    }
  }

  // ── Public API ─────────────────────────────────────────────────────────────

  fun updateSchemaPayload(
    payload: WebviewBridge.SchemaPayload,
    settings: WebviewBridge.GlobalSettings? = null
  ) = executeJs(WebviewBridge.Server.UpdateSchemaPayload(payload, settings))

  fun updateFrameRate(target: Int) {
    val browser = jbCefBrowser ?: return
    if (browser.isCefBrowserCreated && browser.cefBrowser.isWindowless) {
      browser.cefBrowser.setWindowlessFrameRate(target.coerceIn(30, 60))
    }
  }

  fun showSchemaError(message: String) = executeJs(WebviewBridge.Server.SchemaError(message))

  fun updateTheme(theme: String, colors: Map<String, String> = emptyMap()) =
    executeJs(WebviewBridge.Server.UpdateTheme(theme, colors, com.abugyda.dbd.settings.DiagramColors.textStyles(
      com.intellij.openapi.editor.colors.EditorColorsManager.getInstance().globalScheme)))

  fun updateGlobalSettings(
    lineStyle: String, showGrid: Boolean, gridSize: Int,
    snapToGrid: Boolean = false, snapAlignment: String = "grid",
    overrideDiagramAlignment: Boolean = false,
    showTableNotes: Boolean = true, showFieldNotes: Boolean = true,
    tableNoteMaxLines: Int = 2, fieldNoteMaxLines: Int = 2,
    fontSize: Int = 12,
    fontSizes: Map<String, Int> = emptyMap(),
    tableDensity: String = "compact",
    groupMergeTolerance: Int = 32,
  ) = executeJs(
    WebviewBridge.Server.UpdateGlobalSettings(
      WebviewBridge.GlobalSettings(
        lineStyle = lineStyle,
        showGrid = showGrid,
        gridSize = gridSize,
        snapToGrid = snapToGrid,
        snapAlignment = snapAlignment,
        overrideDiagramAlignment = overrideDiagramAlignment,
        showTableNotes = showTableNotes,
        showFieldNotes = showFieldNotes,
        tableNoteMaxLines = tableNoteMaxLines,
        fieldNoteMaxLines = fieldNoteMaxLines,
        fontSize = fontSize.coerceIn(10, 24),
        fontSizes = com.abugyda.dbd.settings.DiagramFonts.normalize(fontSizes),
        tableDensity = tableDensity,
        groupMergeTolerance = groupMergeTolerance.coerceIn(0, 120),
      )
    )
  )

  // ── JS execution ─────────────────────────────────────────────────────────

  private fun executeJs(payload: WebviewBridge.Server) {
    val browser = jbCefBrowser
    if (browser?.cefBrowser == null) return   // browser not ready; READY fires render
    try {
      val json = mapper.writeValueAsString(payload)
      browser.cefBrowser.executeJavaScript(
        "window.postMessage($json, '*')",
        browser.cefBrowser.url,
        0
      )
    } catch (_: Exception) {
    }
  }

  override fun dispose() {
    readyTimeoutTimer?.stop()
    readyTimeoutTimer = null
    cursorHandler?.reset()
    cursorHandler = null
    jbCefBrowser = null
  }
}
