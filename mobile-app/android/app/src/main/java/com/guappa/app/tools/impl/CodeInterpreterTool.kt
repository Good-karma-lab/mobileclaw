package com.guappa.app.tools.impl

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Execute JavaScript code in a sandboxed Android WebView.
 * The WebView has no network access and no filesystem access.
 * Console output is captured and returned.
 */
class CodeInterpreterTool : Tool {
    override val name = "code_interpreter"
    override val description =
        "Execute JavaScript code in a sandboxed WebView environment. " +
        "No network or filesystem access. Returns console output and the final expression result."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "code": {
                    "type": "string",
                    "description": "JavaScript code to execute"
                },
                "timeout_ms": {
                    "type": "integer",
                    "description": "Execution timeout in milliseconds (default: 10000, max: 30000)"
                }
            },
            "required": ["code"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val code = params.optString("code", "")
        if (code.isEmpty()) {
            return ToolResult.Error("JavaScript code is required.", "INVALID_PARAMS")
        }
        val timeoutMs = params.optInt("timeout_ms", 10000).coerceIn(1000, 30000).toLong()

        return try {
            withTimeout(timeoutMs) {
                executeInWebView(code, context)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            ToolResult.Error(
                "JavaScript execution timed out after ${timeoutMs}ms.",
                "TIMEOUT",
                retryable = false
            )
        } catch (e: Exception) {
            ToolResult.Error(
                "JavaScript execution failed: ${e.message}",
                "EXECUTION_ERROR"
            )
        }
    }

    private suspend fun executeInWebView(code: String, context: Context): ToolResult {
        val resultDeferred = CompletableDeferred<ToolResult>()

        // WebView operations must happen on the main thread
        withContext(Dispatchers.Main) {
            val webView = WebView(context)

            try {
                // Configure sandbox: disable network, file access, etc.
                webView.settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    blockNetworkImage = true
                    databaseEnabled = false
                    domStorageEnabled = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }

                val consoleOutput = StringBuilder()
                val jsInterface = object {
                    @JavascriptInterface
                    fun log(message: String) {
                        consoleOutput.appendLine(message)
                    }

                    @JavascriptInterface
                    fun error(message: String) {
                        consoleOutput.appendLine("[ERROR] $message")
                    }

                    @JavascriptInterface
                    fun done(result: String) {
                        val data = JSONObject().apply {
                            put("console_output", consoleOutput.toString().trim())
                            put("result", result)
                        }
                        val content = buildString {
                            if (consoleOutput.isNotEmpty()) {
                                appendLine("Console output:")
                                appendLine(consoleOutput.toString().trim())
                                appendLine()
                            }
                            appendLine("Result: $result")
                        }
                        resultDeferred.complete(ToolResult.Success(content = content.trim(), data = data))
                        webView.destroy()
                    }

                    @JavascriptInterface
                    fun fail(error: String) {
                        resultDeferred.complete(
                            ToolResult.Error("JavaScript error: $error", "JS_ERROR")
                        )
                        webView.destroy()
                    }
                }

                webView.addJavascriptInterface(jsInterface, "_guappa")

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Wrap user code: override console.log, capture result, handle errors
                        val wrappedCode = """
                            (function() {
                                var _console = [];
                                var _origLog = console.log;
                                console.log = function() {
                                    var msg = Array.prototype.slice.call(arguments).map(function(a) {
                                        try { return typeof a === 'object' ? JSON.stringify(a) : String(a); }
                                        catch(e) { return String(a); }
                                    }).join(' ');
                                    _guappa.log(msg);
                                };
                                console.error = function() {
                                    var msg = Array.prototype.slice.call(arguments).map(function(a) {
                                        try { return typeof a === 'object' ? JSON.stringify(a) : String(a); }
                                        catch(e) { return String(a); }
                                    }).join(' ');
                                    _guappa.error(msg);
                                };
                                console.warn = console.log;
                                console.info = console.log;
                                try {
                                    var _result = (function() {
                                        ${escapeForJs(code)}
                                    })();
                                    var _resultStr;
                                    try {
                                        _resultStr = typeof _result === 'object' ? JSON.stringify(_result, null, 2) : String(_result);
                                    } catch(e) {
                                        _resultStr = String(_result);
                                    }
                                    _guappa.done(_resultStr);
                                } catch(e) {
                                    _guappa.fail(e.toString());
                                }
                            })();
                        """.trimIndent()

                        view?.evaluateJavascript(wrappedCode, null)
                    }
                }

                // Load a blank page to trigger onPageFinished
                webView.loadData(
                    "<html><body></body></html>",
                    "text/html",
                    "UTF-8"
                )
            } catch (e: Exception) {
                webView.destroy()
                resultDeferred.complete(
                    ToolResult.Error("Failed to initialize WebView: ${e.message}", "EXECUTION_ERROR")
                )
            }
        }

        return resultDeferred.await()
    }

    private fun escapeForJs(code: String): String {
        // The code is injected inside a function body, no need for string escaping
        // but we need to handle potential issues with </script> tags etc.
        return code.replace("</script>", "<\\/script>")
    }
}
