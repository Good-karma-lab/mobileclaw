package com.guappa.app.swarm

import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.guappa.app.agent.MessageBus
import com.guappa.app.GuappaAgentService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * React Native bridge module exposing the World Wide Swarm to JavaScript.
 *
 * Canonical native module name: "GuappaSwarm"
 *
 * Provides identity management, connector lifecycle, peer discovery,
 * messaging, task handling, reputation tracking, and holon participation.
 */
class GuappaSwarmModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val TAG = "GuappaSwarmModule"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var swarmManager: SwarmManager? = null
    private var configStore: SwarmConfigStore? = null
    private var reputationTracker: SwarmReputationTracker? = null
    private var holonParticipant: SwarmHolonParticipant? = null
    private var eventRelayJob: Job? = null
    private var connectedUrl: String? = null
    private val recentMessages = ConcurrentLinkedDeque<JSONObject>()

    companion object {
        private const val DEFAULT_CONNECTOR_URL = "http://10.0.2.2:9371"
        private const val MAX_RECENT_MESSAGES = 200
    }

    override fun getName(): String = "GuappaSwarm"

    private fun ensureComponents() {
        if (swarmManager != null) return
        val messageBus = GuappaAgentService.messageBus ?: MessageBus()
        configStore = SwarmConfigStore(reactContext)
        reputationTracker = SwarmReputationTracker(reactContext)
        val identity = SwarmIdentity(reactContext)
        val connector = SwarmConnectorClient()
        swarmManager = SwarmManager(reactContext, messageBus)
        holonParticipant = SwarmHolonParticipant(
            connector, messageBus, identity, reputationTracker!!
        )
    }

    // ---- Identity ----

    @ReactMethod
    fun generateIdentity(promise: Promise) {
        try {
            ensureComponents()
            val identity = swarmManager!!.identity
            identity.generateIdentity()
            val json = buildIdentityPayload(identity)
            promise.resolve(json.toString())
        } catch (e: Exception) {
            promise.reject("IDENTITY_ERROR", "Failed to generate identity: ${e.message}", e)
        }
    }

    @ReactMethod
    fun getIdentity(promise: Promise) {
        try {
            ensureComponents()
            val identity = swarmManager!!.identity
            val json = buildIdentityPayload(identity)
            promise.resolve(json.toString())
        } catch (e: Exception) {
            promise.reject("IDENTITY_ERROR", "Failed to get identity: ${e.message}", e)
        }
    }

    @ReactMethod
    fun getFingerprint(promise: Promise) {
        try {
            ensureComponents()
            promise.resolve(swarmManager!!.identity.peerId ?: "")
        } catch (e: Exception) {
            promise.reject("IDENTITY_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun setDisplayName(name: String, promise: Promise) {
        try {
            ensureComponents()
            swarmManager!!.identity.setDisplayName(name)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("IDENTITY_ERROR", e.message, e)
        }
    }

    // ---- Connection ----

    @ReactMethod
    fun connect(connectorUrl: String, promise: Promise) {
        ensureComponents()
        scope.launch {
            try {
                val resolvedUrl = connectorUrl.trim().ifEmpty { DEFAULT_CONNECTOR_URL }
                // Update config with URL
                configStore!!.update {
                    copy(connectorUrl = resolvedUrl, enabled = true)
                }
                connectedUrl = resolvedUrl
                swarmManager!!.setConnectorUrl(resolvedUrl)

                // Start swarm manager
                swarmManager!!.start()

                // Start event relay to JS
                startEventRelay()

                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("CONNECT_ERROR", "Failed to connect: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun disconnect(promise: Promise) {
        try {
            ensureComponents()
            swarmManager!!.stop()
            eventRelayJob?.cancel()
            eventRelayJob = null
            connectedUrl = null
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("DISCONNECT_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun isConnected(promise: Promise) {
        try {
            ensureComponents()
            promise.resolve(swarmManager!!.isConnected.value)
        } catch (e: Exception) {
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun getConnectionStatus(promise: Promise) {
        try {
            ensureComponents()
            val status = if (swarmManager!!.isConnected.value) "connected" else "disconnected"
            promise.resolve(status)
        } catch (e: Exception) {
            promise.resolve("disconnected")
        }
    }

    // ---- Peers ----

    @ReactMethod
    fun getPeers(promise: Promise) {
        try {
            ensureComponents()
            val peers = swarmManager!!.peers.value
            val arr = JSONArray()
            for (peer in peers) {
                arr.put(JSONObject().apply {
                    put("id", peer.peerId)
                    put("displayName", peer.displayName)
                    put("name", peer.displayName)
                    put("fingerprint", peer.peerId)
                    put("capabilities", JSONArray(peer.capabilities))
                    put("address", peer.address)
                    put("reputationTier", "trusted")
                    put("reputationScore", 0)
                    put("lastSeen", peer.lastSeen)
                    put("lastSeenTimestamp", peer.lastSeen)
                    put("online", peer.isOnline)
                })
            }
            promise.resolve(arr.toString())
        } catch (e: Exception) {
            promise.resolve("[]")
        }
    }

    @ReactMethod
    fun getPeerCount(promise: Promise) {
        try {
            ensureComponents()
            promise.resolve(swarmManager!!.peers.value.size)
        } catch (e: Exception) {
            promise.resolve(0)
        }
    }

    // ---- Messaging ----

    @ReactMethod
    fun sendSwarmMessage(recipientId: String, content: String, promise: Promise) {
        ensureComponents()
        scope.launch {
            try {
                val peerId = swarmManager!!.identity.peerId ?: throw Exception("No identity")
                val msg = SwarmMessage(
                    type = SwarmMessageType.BROADCAST,
                    fromPeerId = peerId,
                    toPeerId = recipientId,
                    payload = JSONObject().apply { put("content", content) }
                )
                val sent = swarmManager!!.connector.sendMessage(msg)
                if (sent) {
                    appendRecentMessage(
                        swarmUiMessage(
                            type = "chat",
                            title = swarmManager!!.identity.displayName,
                            content = content,
                            timestamp = msg.timestamp,
                        )
                    )
                }
                promise.resolve(sent)
            } catch (e: Exception) {
                promise.reject("SEND_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun broadcastSwarmMessage(content: String, promise: Promise) {
        ensureComponents()
        scope.launch {
            try {
                val peerId = swarmManager!!.identity.peerId ?: throw Exception("No identity")
                val msg = SwarmMessage(
                    type = SwarmMessageType.BROADCAST,
                    fromPeerId = peerId,
                    payload = JSONObject().apply { put("content", content) }
                )
                val sent = swarmManager!!.connector.sendMessage(msg)
                if (sent) {
                    appendRecentMessage(
                        swarmUiMessage(
                            type = "chat",
                            title = swarmManager!!.identity.displayName,
                            content = content,
                            timestamp = msg.timestamp,
                        )
                    )
                }
                promise.resolve(sent)
            } catch (e: Exception) {
                promise.reject("SEND_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getRecentMessages(limit: Int, promise: Promise) {
        try {
            val effectiveLimit = limit.coerceAtLeast(1)
            val items = synchronized(recentMessages) {
                recentMessages.toList().takeLast(effectiveLimit)
            }
            promise.resolve(JSONArray(items).toString())
        } catch (e: Exception) {
            promise.resolve("[]")
        }
    }

    // ---- Tasks ----

    @ReactMethod
    fun getAvailableTasks(promise: Promise) {
        getActiveTasks(promise)
    }

    @ReactMethod
    fun acceptTask(taskId: String, promise: Promise) {
        ensureComponents()
        scope.launch {
            try {
                promise.resolve(swarmManager!!.acceptTask(taskId))
            } catch (e: Exception) {
                promise.reject("TASK_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun rejectTask(taskId: String, promise: Promise) {
        try {
            ensureComponents()
            promise.resolve(swarmManager!!.rejectTask(taskId))
        } catch (e: Exception) {
            promise.reject("TASK_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun reportTaskResult(taskId: String, result: String, success: Boolean, promise: Promise) {
        try {
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("TASK_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun getActiveTasks(promise: Promise) {
        try {
            ensureComponents()
            val arr = JSONArray()
            swarmManager!!.getPendingTasks().forEach { task ->
                arr.put(JSONObject().apply {
                    put("id", task.id)
                    put("description", task.payload.optString("task", task.payload.optString("content", "Swarm task")))
                    put("progress", 0)
                    put("timeRemainingSeconds", 0)
                    put("status", "pending")
                })
            }
            promise.resolve(arr.toString())
        } catch (e: Exception) {
            promise.resolve("[]")
        }
    }

    @ReactMethod
    fun getCompletedTaskCount(promise: Promise) {
        try {
            ensureComponents()
            promise.resolve(swarmManager!!.stats.value.tasksCompleted.toInt())
        } catch (e: Exception) {
            promise.resolve(0)
        }
    }

    // ---- Reputation ----

    @ReactMethod
    fun getReputation(promise: Promise) {
        try {
            ensureComponents()
            val tracker = reputationTracker!!
            val json = JSONObject().apply {
                put("score", tracker.score.value)
                put("tier", tracker.tier.value.name.lowercase())
                put("tasksCompleted", swarmManager!!.stats.value.tasksCompleted)
                put("tasksFailed", 0)
                put("totalEarned", 0)
                put("joinedAt", 0)
            }
            promise.resolve(json.toString())
        } catch (e: Exception) {
            promise.resolve(JSONObject().apply {
                put("score", 0)
                put("tier", "new")
                put("tasksCompleted", 0)
                put("tasksFailed", 0)
                put("totalEarned", 0)
                put("joinedAt", 0)
            }.toString())
        }
    }

    @ReactMethod
    fun getReputationTier(promise: Promise) {
        try {
            ensureComponents()
            promise.resolve(reputationTracker!!.tier.value.name.lowercase())
        } catch (e: Exception) {
            promise.resolve("new")
        }
    }

    // ---- Holon ----

    @ReactMethod
    fun joinHolon(holonId: String, promise: Promise) {
        ensureComponents()
        scope.launch {
            try {
                val result = holonParticipant!!.joinHolon(holonId, "")
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("HOLON_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun leaveHolon(holonId: String, promise: Promise) {
        try {
            ensureComponents()
            holonParticipant!!.leaveHolon(holonId)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("HOLON_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun submitProposal(holonId: String, proposal: String, promise: Promise) {
        ensureComponents()
        scope.launch {
            try {
                val result = holonParticipant!!.generateProposal(holonId)
                promise.resolve(result != null)
            } catch (e: Exception) {
                promise.reject("HOLON_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun castVote(holonId: String, proposalId: String, ranking: String, promise: Promise) {
        ensureComponents()
        scope.launch {
            try {
                val rankings = JSONArray(ranking).let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
                val result = holonParticipant!!.submitVote(holonId, rankings)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("HOLON_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getActiveHolons(promise: Promise) {
        try {
            ensureComponents()
            val holons = holonParticipant!!.getActiveHolons()
            val arr = JSONArray()
            for ((id, state) in holons) {
                arr.put(JSONObject().apply {
                    put("id", id)
                    put("name", state.taskDescription.take(50))
                    put("memberCount", 0)
                    put("activeProposals", if (state.myProposal != null) 1 else 0)
                    put("myRole", "member")
                })
            }
            promise.resolve(arr.toString())
        } catch (e: Exception) {
            promise.resolve("[]")
        }
    }

    // ---- Stats ----

    @ReactMethod
    fun getSwarmStats(promise: Promise) {
        try {
            ensureComponents()
            val stats = swarmManager!!.stats.value
            val peers = swarmManager!!.peers.value
            val json = JSONObject().apply {
                put("peersOnline", peers.count { it.isOnline })
                put("totalPeers", peers.size)
                put("tasksSent", stats.tasksDelegated)
                put("tasksReceived", stats.messagesReceived)
                put("messagesTotal", stats.messagesSent + stats.messagesReceived)
                put("holonParticipations", holonParticipant?.getActiveHolons()?.size ?: 0)
                put("uptimeSeconds", (System.currentTimeMillis() - stats.connectedSince) / 1000)
            }
            promise.resolve(json.toString())
        } catch (e: Exception) {
            promise.resolve(JSONObject().apply {
                put("peersOnline", 0)
                put("totalPeers", 0)
                put("tasksSent", 0)
                put("tasksReceived", 0)
                put("messagesTotal", 0)
                put("holonParticipations", 0)
                put("uptimeSeconds", 0)
            }.toString())
        }
    }

    // ---- Config ----

    @ReactMethod
    fun setPollingInterval(ms: Int, promise: Promise) {
        try {
            ensureComponents()
            configStore!!.update { copy(activeTaskPollMs = ms.toLong()) }
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun setAutoConnect(enabled: Boolean, promise: Promise) {
        try {
            ensureComponents()
            configStore!!.update { copy(autoConnect = enabled) }
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun registerCapabilities(caps: String, promise: Promise) {
        try {
            ensureComponents()
            val arr = JSONArray(caps)
            val capSet = (0 until arr.length()).map { arr.getString(it) }.toSet()
            configStore!!.update { copy(capabilities = capSet) }
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", e.message, e)
        }
    }

    // ---- SwarmManager-style API for SwarmScreen.tsx compatibility ----
    // These methods match the NativeSwarmManager contract used by SwarmScreen

    @ReactMethod
    fun getStatus(promise: Promise) {
        try {
            ensureComponents()
            val isConn = swarmManager!!.isConnected.value
            val stats = swarmManager!!.stats.value
            val result = Arguments.createMap().apply {
                putString("connectionStatus", if (isConn) "connected" else "disconnected")
                putInt("peerCount", swarmManager!!.peers.value.size)
                putDouble("uptimeSeconds", ((System.currentTimeMillis() - stats.connectedSince) / 1000).toDouble())
                putString("connectorUrl", connectedUrl ?: configStore?.current?.connectorUrl ?: DEFAULT_CONNECTOR_URL)
            }
            promise.resolve(result)
        } catch (e: Exception) {
            val result = Arguments.createMap().apply {
                putString("connectionStatus", "disconnected")
                putInt("peerCount", 0)
                putDouble("uptimeSeconds", 0.0)
                putString("connectorUrl", DEFAULT_CONNECTOR_URL)
            }
            promise.resolve(result)
        }
    }

    @ReactMethod
    fun updateAgentName(name: String, promise: Promise) {
        try {
            ensureComponents()
            swarmManager!!.identity.setDisplayName(name)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("NAME_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun getMessages(since: Double, promise: Promise) {
        try {
            val sinceTs = since.toLong()
            val arr = JSONArray()
            synchronized(recentMessages) {
                recentMessages.forEach { msg ->
                    if (msg.optLong("timestamp", 0) > sinceTs) {
                        arr.put(msg)
                    }
                }
            }
            promise.resolve(arr.toString())
        } catch (e: Exception) {
            promise.resolve("[]")
        }
    }

    @ReactMethod
    fun getStats(promise: Promise) {
        try {
            ensureComponents()
            val stats = swarmManager!!.stats.value
            val result = Arguments.createMap().apply {
                putDouble("tasksCompleted", stats.tasksCompleted.toDouble())
                putDouble("tasksFailed", 0.0)
                putDouble("messagesSent", stats.messagesSent.toDouble())
                putDouble("messagesReceived", stats.messagesReceived.toDouble())
                putInt("holonParticipations", holonParticipant?.getActiveHolons()?.size ?: 0)
                putDouble("totalUptimeSeconds", ((System.currentTimeMillis() - stats.connectedSince) / 1000).toDouble())
            }
            promise.resolve(result)
        } catch (e: Exception) {
            val result = Arguments.createMap().apply {
                putDouble("tasksCompleted", 0.0)
                putDouble("tasksFailed", 0.0)
                putDouble("messagesSent", 0.0)
                putDouble("messagesReceived", 0.0)
                putInt("holonParticipations", 0)
                putDouble("totalUptimeSeconds", 0.0)
            }
            promise.resolve(result)
        }
    }

    // ---- Event emitter support ----

    @ReactMethod
    fun addListener(eventName: String) {
        startEventRelay()
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required by React Native
    }

    private fun startEventRelay() {
        if (eventRelayJob?.isActive == true) return
        val manager = swarmManager ?: return

        eventRelayJob = scope.launch {
            launch {
                manager.isConnected.collectLatest { connected ->
                    emitSwarmEvent("connection_changed", JSONObject().apply {
                        put("connected", connected)
                    }.toString())
                }
            }
            launch {
                manager.peers.collectLatest { peers ->
                    emitSwarmEvent("peer_joined", JSONObject().apply {
                        put("peerCount", peers.size)
                    }.toString())
                }
            }
            launch {
                manager.connector.events.collectLatest { message ->
                    val senderName = manager.peers.value.firstOrNull { it.peerId == message.fromPeerId }?.displayName
                        ?: message.fromPeerId
                    when (message.type) {
                        SwarmMessageType.BROADCAST -> appendRecentMessage(
                            swarmUiMessage(
                                type = "chat",
                                title = senderName,
                                content = message.payload.optString("content", "Broadcast message"),
                                timestamp = message.timestamp,
                            )
                        )
                        SwarmMessageType.TASK_REQUEST -> appendRecentMessage(
                            swarmUiMessage(
                                type = "task_offer",
                                title = senderName,
                                content = message.payload.optString("task", "Task request"),
                                timestamp = message.timestamp,
                            )
                        )
                        SwarmMessageType.HOLON_INVITE -> appendRecentMessage(
                            swarmUiMessage(
                                type = "holon_invite",
                                title = senderName,
                                content = message.payload.optString("topic", "Holon invite"),
                                timestamp = message.timestamp,
                            )
                        )
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun emitSwarmEvent(type: String, data: String) {
        val payload = Arguments.createMap().apply {
            putString("type", type)
            putString("data", data)
        }
        try {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("guappa_swarm_event", payload)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to emit swarm event: ${e.message}")
        }
    }

    override fun onCatalystInstanceDestroy() {
        eventRelayJob?.cancel()
        scope.cancel()
    }

    private fun buildIdentityPayload(identity: SwarmIdentity): JSONObject {
        val fingerprint = identity.peerId ?: ""
        return JSONObject().apply {
            put("publicKey", identity.publicKeyBase64 ?: "")
            put("fingerprint", fingerprint)
            put("displayName", identity.displayName)
            put("createdAt", System.currentTimeMillis())
            put("publicKeyFingerprint", fingerprint)
            put("reputationTier", reputationTracker?.tier?.value?.name?.lowercase() ?: "new")
            put("reputationScore", reputationTracker?.score?.value ?: 0)
            put("hasIdentity", identity.hasIdentity)
        }
    }

    private fun appendRecentMessage(message: JSONObject) {
        synchronized(recentMessages) {
            recentMessages.addLast(message)
            while (recentMessages.size > MAX_RECENT_MESSAGES) {
                recentMessages.pollFirst()
            }
        }
    }

    private fun swarmUiMessage(type: String, title: String, content: String, timestamp: Long = System.currentTimeMillis()): JSONObject {
        return JSONObject().apply {
            put("id", java.util.UUID.randomUUID().toString())
            put("senderName", title)
            put("content", content)
            put("timestamp", timestamp)
            put("type", type)
        }
    }
}
