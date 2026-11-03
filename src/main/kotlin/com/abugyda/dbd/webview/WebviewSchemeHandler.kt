package com.abugyda.dbd.webview

import com.intellij.openapi.diagnostic.thisLogger
import org.cef.callback.CefCallback
import org.cef.handler.CefResourceHandler
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.InputStream
import java.net.URI

class WebviewSchemeHandler(
  private val theme: String = "light",
  private val colors: Map<String, String> = emptyMap()
) : CefResourceHandler {
  private val logger = thisLogger()
  private var inputStream: InputStream? = null
  private var mimeType: String = "text/html"

  override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
    val path = URI(request.url).path
    val actualPath = if (path == "/" || path.isEmpty()) "/index.html" else path
    val resourcePath = "/web$actualPath"

    mimeType = when {
      actualPath.endsWith(".html") -> "text/html"
      actualPath.endsWith(".js") -> "application/javascript"
      actualPath.endsWith(".css") -> "text/css"
      actualPath.endsWith(".woff2") -> "font/woff2"
      actualPath.endsWith(".svg") -> "image/svg+xml"
      else -> "text/plain"
    }

    if (actualPath.contains("..")) return false
    inputStream = javaClass.getResourceAsStream(resourcePath)?.let { stream ->
      if (actualPath.endsWith(".html")) stream.bufferedReader()
        .use {
          val variables =
            colors.filter { (key, value) -> key.matches(Regex("--[a-z-]+")) && value.matches(Regex("#[0-9a-fA-F]{6}")) }
              .entries.joinToString(";") { (key, value) -> "$key:$value" }
          it.readText().replace("<html", "<html data-theme=\"$theme\" style=\"$variables\"")
            .replace("<body>", "<body class=\"$theme\">").byteInputStream()
        }
      else stream
    }

    if (inputStream != null) {
      callback.Continue()
      return true
    } else {
      return false
    }
  }

  override fun getResponseHeaders(
    response: CefResponse,
    responseLength: IntRef,
    redirectUrl: StringRef?
  ) {
    if (inputStream == null) {
      response.status = 404
    } else {
      response.status = 200
      response.mimeType = mimeType
      try {
        val available = inputStream?.available() ?: 0
        if (available > 0) responseLength.set(available)
      } catch (_: Exception) {
      }
    }
  }

  override fun readResponse(
    dataOut: ByteArray,
    bytesToRead: Int,
    bytesRead: IntRef,
    callback: CefCallback
  ): Boolean {
    val stream = inputStream ?: return false
    return try {
      val count = stream.read(dataOut, 0, bytesToRead)
      if (count > 0) {
        bytesRead.set(count)
        true
      } else {
        bytesRead.set(0)
        stream.close()
        false
      }
    } catch (e: Exception) {
      bytesRead.set(0)
      false
    }
  }

  override fun cancel() {
    inputStream?.close()
  }
}