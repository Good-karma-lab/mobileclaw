package com.guappa.app.swarm

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Client for communicating with the WWSP connector sidecar.
 *
 * In production, this talks to a local wws-connector process
 * via JSON-RPC 2.0 over TCP (port 9370) or HTTP REST (port 9371).
 *
 * For initial release, we implement the HTTP REST path which is
 * simpler and sufficient for mobile use cases.
 */
class SwarmConnectorClient(
    private val baseUrl: String = "http://10.0.2.2:9371"
) {
    private val TAG = "SwarmConnector"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    private val _events = MutableSharedFlow<SwarmMessage>(
        extraBufferCapacity = 64
    )
    val events: SharedFlow<SwarmMessage> = _events.asSharedFlow()

    private var eventJob: Job? = null

    val isConnected: Boolean
        get() = eventJob?.isActive == true

    /**
     * Get connector status and peer count.
     */
    suspend fun getStatus(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/status")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                JSONObject(response.body?.string() ?: "{}")
            } else null
        } catch (e: Exception) {
            Log.d(TAG, "Connector not available: ${e.message}")
            null
        }
    }

    /**
     * List discovered peers from the connector.
     */
    suspend fun getPeers(): List<PeerInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/peers")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val array = JSONArray(response.body?.string() ?: "[]")
                (0 until array.length()).map { PeerInfo.fromJSON(array.getJSONObject(it)) }
            } else emptyList()
        } catch (e: Exception) {
            Log.d(TAG, "Failed to get peers: ${e.message}")
            emptyList()
        }
    }

    /**
     * Send a message to a specific peer or broadcast.
     */
    suspend fun sendMessage(message: SwarmMessage): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = message.toJSON().toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/api/messages")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.message}")
            false
        }
    }

    /**
     * Register this agent's capabilities with the connector.
     */
    suspend fun registerCapabilities(
        peerId: String,
        displayName: String,
        capabilities: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("peer_id", peerId)
                put("display_name", displayName)
                put("capabilities", JSONArray(capabilities))
            }
            val body = payload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/api/register")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register: ${e.message}")
            false
        }
    }

    /**
     * Start listening for SSE events from the connector.
     */
    fun startEventStream(scope: CoroutineScope) {
        eventJob?.cancel()
        eventJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url("$baseUrl/api/events")
                        .header("Accept", "text/event-stream")
                        .get()
                        .build()

                    val response = client.newCall(request).execute()
                    val reader = BufferedReader(
                        InputStreamReader(response.body?.byteStream() ?: return@launch)
                    )

                    var line: String?
                    var eventData = StringBuilder()

                    while (isActive) {
                        line = reader.readLine() ?: break
                        if (line.startsWith("data: ")) {
                            eventData.append(line.removePrefix("data: "))
                        } else if (line.isEmpty() && eventData.isNotEmpty()) {
                            try {
                                val json = JSONObject(eventData.toString())
                                val message = SwarmMessage.fromJSON(json)
                                _events.emit(message)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse event: ${e.message}")
                            }
                            eventData = StringBuilder()
                        }
                    }
                    reader.close()
                } catch (e: Exception) {
                    Log.d(TAG, "Event stream disconnected: ${e.message}")
                    delay(5000) // Reconnect after 5s
                }
            }
        }
    }

    fun stopEventStream() {
        eventJob?.cancel()
        eventJob = null
    }

    fun shutdown() {
        stopEventStream()
        client.dispatcher.executorService.shutdown()
    }
}
