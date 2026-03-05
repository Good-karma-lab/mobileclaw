package com.mobileclaw.app

import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private class PendingRequest {
    val latch = CountDownLatch(1)
    var statusCode: Int = 500
    var responseBody: String = """{"error":{"message":"timeout"}}"""
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

    @ReactMethod
    fun addListener(eventName: String?) { /* Required for RN event emitter */ }

    @ReactMethod
    fun removeListeners(count: Int?) { /* Required for RN event emitter */ }

    inner class LlmHttpServer(
        port: Int,
        private val reactContext: ReactApplicationContext
    ) : NanoHTTPD(port) {

        private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()
        private val requestCounter = AtomicLong(0)

        fun resolveRequest(requestId: String, statusCode: Int, body: String) {
            pendingRequests[requestId]?.let {
                it.statusCode = statusCode
                it.responseBody = body
                it.latch.countDown()
            }
        }

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method

            if (method == Method.POST && uri == "/v1/chat/completions") {
                // Read POST body
                val bodyMap = HashMap<String, String>()
                session.parseBody(bodyMap)
                val postData = bodyMap["postData"] ?: ""

                val requestId = "req-${requestCounter.incrementAndGet()}"
                val pending = PendingRequest()
                pendingRequests[requestId] = pending

                // Send event to JS
                val params = Arguments.createMap().apply {
                    putString("requestId", requestId)
                    putString("url", uri)
                    putString("method", "POST")
                    putString("body", postData)
                }
                reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit("LocalLlmRequest", params)

                // Wait for JS to respond (up to 5 minutes for inference)
                pending.latch.await(300, TimeUnit.SECONDS)
                pendingRequests.remove(requestId)

                val status = if (pending.statusCode == 200) Response.Status.OK
                    else if (pending.statusCode == 503) Response.Status.SERVICE_UNAVAILABLE
                    else Response.Status.INTERNAL_ERROR
                return newFixedLengthResponse(status, "application/json", pending.responseBody)
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
