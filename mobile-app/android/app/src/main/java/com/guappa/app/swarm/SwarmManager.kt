package com.guappa.app.swarm

import android.content.Context
import android.util.Log
import com.guappa.app.agent.BusMessage
import com.guappa.app.agent.MessageBus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates swarm participation: identity, peer tracking, message routing.
 * Bridges the WWSP connector with the GUAPPA agent loop.
 */
class SwarmManager(
    private val context: Context,
    private val messageBus: MessageBus
) {
    private val TAG = "SwarmManager"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val identity = SwarmIdentity(context)
    val connector = SwarmConnectorClient()

    private val _peers = MutableStateFlow<List<PeerInfo>>(emptyList())
    val peers: StateFlow<List<PeerInfo>> = _peers.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _stats = MutableStateFlow(SwarmStats())
    val stats: StateFlow<SwarmStats> = _stats.asStateFlow()

    private val pendingTasks = ConcurrentHashMap<String, SwarmMessage>()
    private var heartbeatJob: Job? = null
    private var peerRefreshJob: Job? = null

    fun start() {
        if (!identity.hasIdentity) {
            Log.d(TAG, "No swarm identity — generate one first")
            return
        }

        scope.launch {
            // Register with connector
            val peerId = identity.peerId ?: return@launch
            val registered = connector.registerCapabilities(
                peerId = peerId,
                displayName = identity.displayName,
                capabilities = listOf(
                    "text_generation", "tool_use", "vision",
                    "app_control", "web_search", "translation"
                )
            )

            if (registered) {
                _isConnected.value = true
                Log.d(TAG, "Registered with swarm as $peerId")

                // Start event stream
                connector.startEventStream(scope)

                // Listen for incoming messages
                scope.launch {
                    connector.events.collect { message ->
                        handleIncomingMessage(message)
                    }
                }

                // Start heartbeat
                startHeartbeat(peerId)

                // Start peer refresh
                startPeerRefresh()

                // Notify via MessageBus
                messageBus.publish(
                    BusMessage.SystemEvent(
                        type = "swarm_connected",
                        data = mapOf("peer_id" to peerId)
                    )
                )
            } else {
                Log.w(TAG, "Failed to register with swarm connector")
                _isConnected.value = false
            }
        }
    }

    fun stop() {
        heartbeatJob?.cancel()
        peerRefreshJob?.cancel()
        connector.stopEventStream()
        _isConnected.value = false
        _peers.value = emptyList()

        scope.launch {
            messageBus.publish(
                BusMessage.SystemEvent(
                    type = "swarm_disconnected",
                    data = emptyMap()
                )
            )
        }
    }

    private fun startHeartbeat(peerId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(30_000) // 30s heartbeat interval
                val message = SwarmMessage(
                    type = SwarmMessageType.HEARTBEAT,
                    fromPeerId = peerId,
                    payload = JSONObject().apply {
                        put("uptime_ms", System.currentTimeMillis() - _stats.value.connectedSince)
                        put("peer_count", _peers.value.size)
                    }
                )
                connector.sendMessage(message)
            }
        }
    }

    private fun startPeerRefresh() {
        peerRefreshJob?.cancel()
        peerRefreshJob = scope.launch {
            while (isActive) {
                val peers = connector.getPeers()
                _peers.value = peers
                _stats.update { it.copy(peerCount = peers.size) }
                delay(15_000) // Refresh every 15s
            }
        }
    }

    private suspend fun handleIncomingMessage(message: SwarmMessage) {
        Log.d(TAG, "Received ${message.type} from ${message.fromPeerId}")

        when (message.type) {
            SwarmMessageType.TASK_REQUEST -> {
                _stats.update { it.copy(messagesReceived = it.messagesReceived + 1) }
                pendingTasks[message.id] = message
                // Forward to agent via MessageBus
                messageBus.publish(
                    BusMessage.SystemEvent(
                        type = "swarm_task_request",
                        data = mapOf(
                            "message_id" to message.id,
                            "from_peer" to message.fromPeerId,
                            "payload" to message.payload.toString()
                        )
                    )
                )
            }

            SwarmMessageType.TASK_RESPONSE -> {
                _stats.update { it.copy(tasksCompleted = it.tasksCompleted + 1) }
                messageBus.publish(
                    BusMessage.SystemEvent(
                        type = "swarm_task_response",
                        data = mapOf(
                            "message_id" to message.id,
                            "from_peer" to message.fromPeerId,
                            "payload" to message.payload.toString()
                        )
                    )
                )
            }

            SwarmMessageType.PEER_DISCOVERY -> {
                // Connector handles this; we just refresh peers
                scope.launch {
                    _peers.value = connector.getPeers()
                }
            }

            SwarmMessageType.HOLON_INVITE -> {
                messageBus.publish(
                    BusMessage.SystemEvent(
                        type = "swarm_holon_invite",
                        data = mapOf(
                            "holon_id" to message.payload.optString("holon_id", ""),
                            "topic" to message.payload.optString("topic", ""),
                            "from_peer" to message.fromPeerId
                        )
                    )
                )
            }

            SwarmMessageType.BROADCAST -> {
                _stats.update { it.copy(messagesReceived = it.messagesReceived + 1) }
            }

            else -> {
                Log.d(TAG, "Unhandled message type: ${message.type}")
            }
        }
    }

    /**
     * Delegate a task to the best-matching peer.
     */
    suspend fun delegateTask(
        taskDescription: String,
        requiredCapability: String? = null
    ): Boolean {
        val peerId = identity.peerId ?: return false
        val targetPeer = if (requiredCapability != null) {
            _peers.value.firstOrNull { requiredCapability in it.capabilities }
        } else {
            _peers.value.firstOrNull { it.isOnline }
        } ?: return false

        val message = SwarmMessage(
            type = SwarmMessageType.TASK_REQUEST,
            fromPeerId = peerId,
            toPeerId = targetPeer.peerId,
            payload = JSONObject().apply {
                put("task", taskDescription)
                requiredCapability?.let { put("required_capability", it) }
            }
        )

        val sent = connector.sendMessage(message)
        if (sent) {
            _stats.update {
                it.copy(
                    messagesSent = it.messagesSent + 1,
                    tasksDelegated = it.tasksDelegated + 1
                )
            }
        }
        return sent
    }

    fun shutdown() {
        stop()
        connector.shutdown()
        scope.cancel()
    }

    fun getStatsMap(): Map<String, Any> = mapOf(
        "connected" to _isConnected.value,
        "peer_count" to _peers.value.size,
        "messages_sent" to _stats.value.messagesSent,
        "messages_received" to _stats.value.messagesReceived,
        "tasks_delegated" to _stats.value.tasksDelegated,
        "tasks_completed" to _stats.value.tasksCompleted
    )
}

data class SwarmStats(
    val peerCount: Int = 0,
    val messagesSent: Long = 0,
    val messagesReceived: Long = 0,
    val tasksDelegated: Long = 0,
    val tasksCompleted: Long = 0,
    val connectedSince: Long = System.currentTimeMillis()
)
