package com.guappa.app

import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import fi.iki.elonen.NanoHTTPD
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private class PendingRequest {
    val latch = CountDownLatch(1)
    var statusCode: Int = 500
    var responseBody: String = """{"error":{"message":"timeout"}}"""
}

/**
 * Represents a streaming SSE request. JS calls streamChunk(requestId, chunk)
 * repeatedly, then streamEnd(requestId) when done.
 */
private class StreamingRequest {
    val pipedOut = PipedOutputStream()
    val pipedIn = PipedInputStream(pipedOut, 65536)
    @Volatile var closed = false

    fun writeChunk(data: String) {
        if (closed) return
        try {
            pipedOut.write("data: $data\n\n".toByteArray(Charsets.UTF_8))
            pipedOut.flush()
        } catch (_: Exception) {
            closed = true
        }
    }

    fun finish() {
        if (closed) return
        closed = true
        try {
            pipedOut.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
            pipedOut.flush()
            pipedOut.close()
        } catch (_: Exception) { }
    }

    fun error(message: String) {
        if (closed) return
        closed = true
        try {
            val errJson = """{"error":{"message":"$message"}}"""
            pipedOut.write("data: $errJson\n\n".toByteArray(Charsets.UTF_8))
            pipedOut.flush()
            pipedOut.close()
        } catch (_: Exception) { }
    }
}

class LocalLlmServerModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "LocalLlmServer"

    private var server: LlmHttpServer? = null

    @ReactMethod
    fun start(port: Int, promise: Promise) {
        try {
            if (server != null) {
                promise.resolve("already running")
                return
            }
            server = LlmHttpServer(port, reactApplicationContext)
            server!!.start()
            promise.resolve("started on port $port")
        } catch (e: Exception) {
            promise.reject("START_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun stop(promise: Promise) {
        server?.stop()
        server = null
        promise.resolve("stopped")
    }

    @ReactMethod
    fun respondToRequest(requestId: String, statusCode: Int, body: String) {
        server?.resolveRequest(requestId, statusCode, body)
    }

    /** Send a single SSE chunk for a streaming request */
    @ReactMethod
    fun streamChunk(requestId: String, chunkJson: String) {
        server?.streamChunk(requestId, chunkJson)
    }

    /** End an SSE streaming request */
    @ReactMethod
    fun streamEnd(requestId: String) {
        server?.streamEnd(requestId)
    }

    /** Error an SSE streaming request */
    @ReactMethod
    fun streamError(requestId: String, message: String) {
        server?.streamError(requestId, message)
    }

    /**
     * Push a streaming token directly to the orchestrator via LocalStreamBridge,
     * bypassing the HTTP layer entirely for real-time delivery.
     */
    @ReactMethod
    fun emitStreamToken(token: String) {
        com.guappa.app.providers.LocalStreamBridge.pushToken(token)
    }

    /** Signal start of direct token streaming */
    @ReactMethod
    fun startDirectStream() {
        com.guappa.app.providers.LocalStreamBridge.startStream()
    }

    /** Signal end of direct token streaming */
    @ReactMethod
    fun endDirectStream() {
        com.guappa.app.providers.LocalStreamBridge.endStream()
    }

    @ReactMethod
    fun addListener(eventName: String?) { /* Required for RN event emitter */ }

    @ReactMethod
    fun removeListeners(count: Int?) { /* Required for RN event emitter */ }

    inner class LlmHttpServer(
        port: Int,
        private val reactContext: ReactApplicationContext
    ) : NanoHTTPD(port) {

        private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()
        private val streamingRequests = ConcurrentHashMap<String, StreamingRequest>()
        private val requestCounter = AtomicLong(0)

        fun resolveRequest(requestId: String, statusCode: Int, body: String) {
            pendingRequests[requestId]?.let {
                it.statusCode = statusCode
                it.responseBody = body
                it.latch.countDown()
            }
        }

        fun streamChunk(requestId: String, chunkJson: String) {
            streamingRequests[requestId]?.writeChunk(chunkJson)
        }

        fun streamEnd(requestId: String) {
            streamingRequests[requestId]?.finish()
            streamingRequests.remove(requestId)
        }

        fun streamError(requestId: String, message: String) {
            streamingRequests[requestId]?.error(message)
            streamingRequests.remove(requestId)
        }

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method

            if (method == Method.POST && uri == "/v1/chat/completions") {
                // Read POST body
                val bodyMap = HashMap<String, String>()
                session.parseBody(bodyMap)
                val postData = bodyMap["postData"] ?: ""

                // Check if client requested streaming
                val isStreamRequest = try {
                    org.json.JSONObject(postData).optBoolean("stream", false)
                } catch (_: Exception) { false }

                val requestId = "req-${requestCounter.incrementAndGet()}"

                if (isStreamRequest) {
                    // SSE streaming response
                    val streaming = StreamingRequest()
                    streamingRequests[requestId] = streaming

                    // Send event to JS with stream flag
                    val params = Arguments.createMap().apply {
                        putString("requestId", requestId)
                        putString("url", uri)
                        putString("method", "POST")
                        putString("body", postData)
                        putBoolean("stream", true)
                    }
                    reactContext
                        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                        .emit("LocalLlmRequest", params)

                    // Return SSE response with piped input stream
                    val response = newChunkedResponse(
                        Response.Status.OK,
                        "text/event-stream",
                        streaming.pipedIn
                    )
                    response.addHeader("Cache-Control", "no-cache")
                    response.addHeader("Connection", "keep-alive")
                    return response
                } else {
                    // Non-streaming: block until complete
                    val pending = PendingRequest()
                    pendingRequests[requestId] = pending

                    val params = Arguments.createMap().apply {
                        putString("requestId", requestId)
                        putString("url", uri)
                        putString("method", "POST")
                        putString("body", postData)
                        putBoolean("stream", false)
                    }
                    reactContext
                        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                        .emit("LocalLlmRequest", params)

                    pending.latch.await(300, TimeUnit.SECONDS)
                    pendingRequests.remove(requestId)

                    val status = if (pending.statusCode == 200) Response.Status.OK
                        else if (pending.statusCode == 503) Response.Status.SERVICE_UNAVAILABLE
                        else Response.Status.INTERNAL_ERROR
                    return newFixedLengthResponse(status, "application/json", pending.responseBody)
                }
            } else if (method == Method.GET && uri == "/v1/models") {
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    """{"data":[{"id":"local","object":"model"}]}"""
                )
            }

            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                """{"error":{"message":"Not found"}}"""
            )
        }
    }
}
