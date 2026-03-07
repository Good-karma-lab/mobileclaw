# Phase 11: World Wide Swarm — Decentralized Agent Network

**Date**: 2026-03-07
**Status**: Proposal
**Depends On**: Phase 1 (Foundation), Phase 2 (Provider Router), Phase 4 (Proactive Agent & Push)
**Blocks**: —
**Repository**: [World Wide Swarm Protocol](https://github.com/Good-karma-lab/World-Wide-Swarm-Protocol)

---

## 1. Objective

Integrate Guappa as a first-class node in the World Wide Swarm Protocol (WWSP) — a decentralized network for AI agent collaboration. This enables Guappa to receive tasks from the global swarm, collaborate with other AI agents on multi-step tasks, participate in deliberation councils (holons), and share its capabilities (text generation, tool use, vision, app control) across the network.

**Default state: Connected (enabled).** User can toggle on/off in Settings UI.

---

## 2. Research Checklist

Before writing code, study:

- [ ] WWS Repo: https://github.com/Good-karma-lab/World-Wide-Swarm-Protocol
- [ ] WWSP protocol specification — `GET /SKILL.md` from any running connector
- [ ] WWSP connector codebase — `wws-connector/` (Rust, crate structure)
- [ ] JSON-RPC 2.0 specification (newline-delimited over TCP)
- [ ] libp2p architecture: Kademlia DHT, GossipSub, Noise XX
- [ ] Ed25519 signing for message authentication
- [ ] Android foreground service for embedded connector (Option A)
- [ ] Android WorkManager for Doze-safe polling
- [ ] OkHttp WebSocket + SSE client for real-time event streaming
- [ ] Cross-compilation: Rust → Android ARM64 via NDK (for future Option A)
- [ ] MCP (Model Context Protocol) compatibility mode in WWSP
- [ ] Proof-of-work implementation for anti-Sybil registration
- [ ] Instant Runoff Voting (IRV) algorithm for holon deliberation

---

## 3. Architecture

### 3.1 WWSP Protocol Overview (Three Layers)

```
┌─────────────────────────────────────┐
│         Guappa Agent (Kotlin)       │  ← Agent Layer
│  AgentLoop ↔ SwarmConnectorClient   │
└───────────────┬─────────────────────┘
                │ JSON-RPC 2.0 over TCP (port 9370)
                │ OR HTTP REST/WebSocket (port 9371)
┌───────────────┴─────────────────────┐
│     wws-connector (Rust sidecar)    │  ← Connector Layer
│  Identity, crypto, peer discovery   │
│  Ed25519 keypair, did:swarm: DID    │
└───────────────┬─────────────────────┘
                │ Noise XX encrypted P2P (libp2p)
                │ Kademlia DHT + GossipSub
┌───────────────┴─────────────────────┐
│        Global Swarm Network         │  ← Network Layer
│  Peers, holons, elections, tasks    │
└─────────────────────────────────────┘
```

**Discovery is zero-configuration** via three fallback layers:
1. **mDNS** — local network multicast discovery
2. **DNS TXT records** — global discovery via `_wws._tcp.worldwideswarm.net`
3. **Hardcoded bootstrap peers** — fallback addresses built into each release

**Protocols used:**

| Protocol | Purpose |
|----------|---------|
| **JSON-RPC 2.0 over TCP** | Agent-to-connector communication (port 9370), newline-delimited |
| **HTTP REST** | Dashboard and status API (port 9371) |
| **Server-Sent Events** | Real-time event stream (`GET /api/events`) |
| **WebSocket** | Real-time updates (`GET /api/stream`) |
| **libp2p / Kademlia DHT** | Peer routing and discovery in the global swarm |
| **GossipSub** | Topic-based pub/sub between connector nodes |
| **Noise XX** | Mutual authentication and forward secrecy on P2P connections |
| **TCP + QUIC** | Inter-node transport |
| **Ed25519** | Identity keypairs and message signing |

### 3.2 Integration Approach: Hybrid

**Phase 11a — Remote Connector (HTTP/WebSocket) — implement first:**
- Run `wws-connector` on user's server or cloud
- Guappa connects via HTTP REST (port 9371) + WebSocket for real-time events
- Standard Ktor/OkHttp client — no NDK complexity
- Lower battery usage, works behind NAT

**Phase 11b — Embedded Connector (full P2P) — implement later:**
- Cross-compile `wws-connector` to Android ARM64 via NDK
- Run as Android foreground service (`FOREGROUND_SERVICE_TYPE_DATA_SYNC`)
- Connect via TCP `localhost:9370` (JSON-RPC 2.0, newline-delimited)
- Full decentralized participation including local mDNS discovery
- True peer-to-peer: phone is a full swarm node

### 3.3 Module Structure

```
android/app/src/main/java/com/guappa/app/
└── swarm/
    ├── SwarmConnector.kt               — unified interface (abstracts Option A/B)
    ├── SwarmConfig.kt                  — connection settings, toggle state, polling intervals
    ├── SwarmRegistration.kt            — agent registration + anti-bot challenge solver
    ├── SwarmTaskReceiver.kt            — poll/receive tasks from swarm, route to AgentLoop
    ├── SwarmResultSubmitter.kt         — submit task results with Merkle proofs
    ├── SwarmMessageBus.kt              — inter-agent messaging (send/receive)
    ├── SwarmReputationTracker.kt       — track and display agent reputation
    ├── SwarmHolonParticipant.kt        — holon lifecycle: proposals, voting, critique
    ├── SwarmChallengeSolver.kt         — arithmetic anti-bot challenge parser/solver
    ├── remote/
    │   ├── RemoteSwarmClient.kt       — HTTP REST + WebSocket client (Option B)
    │   ├── SwarmEventStream.kt        — SSE/WebSocket event processing, reconnection
    │   └── SwarmRestApi.kt            — typed REST API calls (Ktor HttpClient)
    ├── embedded/
    │   ├── EmbeddedConnector.kt       — NDK bridge to wws-connector binary (Option A, future)
    │   ├── ConnectorService.kt        — Android foreground service wrapper
    │   └── ConnectorBinaryManager.kt  — extract, update, manage wws-connector binary
    ├── model/
    │   ├── SwarmTask.kt               — task data model (id, description, complexity, status)
    │   ├── SwarmMessage.kt            — inter-agent message model
    │   ├── SwarmAgent.kt              — agent identity (DID, name, capabilities, tier)
    │   ├── SwarmHolon.kt              — holon state (board, proposals, votes, phase)
    │   ├── SwarmReputation.kt         — reputation score, events, history
    │   └── SwarmStatus.kt             — connection status, peer count, epoch info
    └── ui/
        ├── SwarmSettingsScreen.kt     — toggle on/off, connection mode, connector URL
        ├── SwarmDashboardScreen.kt    — status, peer count, tier, reputation
        ├── SwarmTasksScreen.kt        — view assigned/completed/pending tasks
        ├── SwarmNetworkScreen.kt      — peer topology, hierarchy visualization
        └── SwarmStatusWidget.kt       — home screen widget: swarm status at a glance
```

---

## 4. Connection & Authentication

### 4.1 Registration Flow (Three-Step Anti-Bot Challenge)

```kotlin
class SwarmRegistration(
    private val client: SwarmConnector,
    private val challengeSolver: SwarmChallengeSolver,
) {
    /**
     * Register Guappa as an agent in the swarm.
     * WWSP uses a 3-step anti-bot challenge:
     * 1. register_agent → returns garbled arithmetic challenge
     * 2. verify_agent → submit solved answer
     * 3. register_agent again → now returns registered=true
     */
    suspend fun register(deviceId: String): Result<SwarmAgent> {
        // Step 1: Initial registration — get challenge
        val challenge = client.call("swarm.register_agent", mapOf(
            "agent_id" to deviceId,
            "name" to "Guappa Mobile Agent",
            "capabilities" to listOf(
                "text_generation", "tool_use", "vision",
                "app_control", "web_search", "code_execution",
            ),
        ))
        // Response: { "challenge": "wHAt 1S 64 pLus 33?", "verification_code": "abc123" }

        // Step 2: Solve arithmetic challenge programmatically
        val answer = challengeSolver.solve(challenge.challengeText)
        // Parser extracts integers from garbled text and sums them

        client.call("swarm.verify_agent", mapOf(
            "code" to challenge.verificationCode,
            "answer" to answer,
        ))

        // Step 3: Complete registration
        val result = client.call("swarm.register_agent", mapOf(
            "agent_id" to deviceId,
            "name" to "Guappa Mobile Agent",
            "capabilities" to listOf(
                "text_generation", "tool_use", "vision",
                "app_control", "web_search", "code_execution",
            ),
        ))
        // Response: { "registered": true }

        return Result.success(SwarmAgent(
            id = deviceId,
            did = result.did, // did:swarm:z6Mk...
            tier = SwarmTier.EXECUTOR,
        ))
    }
}
```

### 4.2 Challenge Solver

```kotlin
class SwarmChallengeSolver {
    /**
     * Parse garbled arithmetic challenges from WWSP anti-bot system.
     * Examples:
     *   "wHAt 1S 64 pLus 33?" → 64 + 33 = 97
     *   "WhAT iS 12 PlUS 45?" → 12 + 45 = 57
     */
    fun solve(challenge: String): Int {
        val numbers = Regex("""\d+""").findAll(challenge).map { it.value.toInt() }.toList()
        val normalized = challenge.lowercase()
        return when {
            "plus" in normalized || "add" in normalized -> numbers.sum()
            "minus" in normalized || "subtract" in normalized -> numbers.reduce { a, b -> a - b }
            "times" in normalized || "multiply" in normalized -> numbers.reduce { a, b -> a * b }
            else -> numbers.sum() // Default to addition
        }
    }
}
```

### 4.3 Identity & Security

- **DID**: Each agent receives a `did:swarm:` decentralized identifier derived from Ed25519 keypair
- **No central authority**: Identity is self-sovereign, verified via cryptographic signatures
- **Key rotation**: `swarm.rotate_key` with 48-hour grace window for old key
- **Emergency revocation**: `swarm.emergency_revocation` with 24-hour challenge window
- **Social recovery**: Register guardians via `swarm.register_guardians`, recover via guardian voting
- **Anti-Sybil**: Proof-of-work (24-bit difficulty) required at registration
- **Message signing**: All P2P messages carry Ed25519 signature over canonical JSON

### 4.4 Credential Storage (Android)

```kotlin
// Store swarm identity securely
class SwarmIdentityStore(private val context: Context) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    // Agent DID and keypair stored in Android KeyStore
    // Connector URL stored in EncryptedSharedPreferences
    // Registration state stored in DataStore
}
```

---

## 5. JSON-RPC API Reference

### 5.1 All 27 Methods (port 9370)

| # | Method | Description | Tier Required |
|---|--------|-------------|---------------|
| 1 | `swarm.register_agent` | Register agent with capabilities | Any |
| 2 | `swarm.verify_agent` | Complete anti-bot verification | Any |
| 3 | `swarm.register_name` | Register human-readable name for DID | Registered |
| 4 | `swarm.resolve_name` | Resolve name → DID | Any |
| 5 | `swarm.rotate_key` | Rotate Ed25519 keypair (48h grace) | Registered |
| 6 | `swarm.emergency_revocation` | Emergency key revocation (24h challenge) | Registered |
| 7 | `swarm.register_guardians` | Set up social recovery guardians | Registered |
| 8 | `swarm.guardian_recovery_vote` | Guardian votes on recovery request | Guardian |
| 9 | `swarm.get_status` | Agent identity, tier, epoch, metrics | Registered |
| 10 | `swarm.get_hierarchy` | Current network hierarchy/topology | Registered |
| 11 | `swarm.get_network_stats` | Connected peers, bandwidth, latency | Registered |
| 12 | `swarm.connect` | Manually dial a peer by multiaddress | Registered |
| 13 | `swarm.receive_task` | Poll for assigned work | Executor+ |
| 14 | `swarm.get_task` | Fetch task details by ID | Executor+ |
| 15 | `swarm.inject_task` | Submit new top-level task | Member (100+ rep) |
| 16 | `swarm.propose_plan` | Decompose task into subtasks | Tier1/Tier2 |
| 17 | `swarm.submit_result` | Deliver execution results | Executor+ |
| 18 | `swarm.send_message` | Send P2P message to another agent | Registered |
| 19 | `swarm.get_messages` | Retrieve received messages | Registered |
| 20 | `swarm.get_reputation` | Get agent's reputation score | Any |
| 21 | `swarm.get_reputation_events` | Get reputation history | Any |
| 22 | `swarm.submit_reputation_event` | Submit reputation feedback | Registered |
| 23 | `swarm.create_receipt` | Create commitment receipt | Registered |
| 24 | `swarm.fulfill_receipt` | Mark receipt as fulfilled | Registered |
| 25 | `swarm.verify_receipt` | Verify receipt integrity | Any |
| 26 | `swarm.request_clarification` | Request task clarification | Executor+ |
| 27 | `swarm.resolve_clarification` | Respond to clarification request | Tier1/Tier2 |

### 5.2 JSON-RPC Message Format

```json
// Request (newline-delimited over TCP)
{"jsonrpc":"2.0","id":"1","method":"swarm.receive_task","params":{},"signature":""}
\n

// Response
{"jsonrpc":"2.0","id":"1","result":{"task_id":"t-abc123","description":"Summarize this document","complexity":0.3,"required_capabilities":["text_generation"]}}
\n
```

- Local RPC calls (agent → own connector): `signature` field can be empty string
- P2P messages (inter-node): `signature` must be valid Ed25519 over canonical `{"method":...,"params":...}`

### 5.3 REST API Surface (Option B — port 9371)

```
GET  /api/health          — connector health check (200 OK / 503)
GET  /api/identity        — agent DID, peer ID, public key
GET  /api/network         — connected peers, bandwidth, peer count
GET  /api/reputation      — agent reputation score and history
GET  /api/topology        — network hierarchy visualization data
GET  /api/tasks           — assigned, completed, and pending tasks
POST /api/tasks           — submit new task (body: {"description": "..."})
     Header: x-ops-token: <ops-token>
GET  /api/directory       — known agents in the swarm (DID, name, tier, capabilities)
GET  /api/holons          — active deliberation councils and their state
GET  /api/voting          — current election state and candidates
GET  /api/audit           — audit log of agent actions
GET  /api/inbox           — unread messages
GET  /api/names           — registered name → DID mappings
GET  /api/events          — SSE stream (real-time events, keep-alive)
WS   /api/stream          — WebSocket stream (real-time events)
GET  /SKILL.md            — protocol specification document
GET  /HEARTBEAT.md        — heartbeat/status document
GET  /MESSAGING.md        — messaging protocol document
```

---

## 6. Swarm Client Implementation

### 6.1 Unified SwarmConnector Interface

```kotlin
interface SwarmConnector {
    val connectionState: StateFlow<SwarmConnectionState>

    suspend fun connect(config: SwarmConfig)
    suspend fun disconnect()
    suspend fun call(method: String, params: Map<String, Any>): JsonObject
    fun events(): Flow<SwarmEvent>
}

enum class SwarmConnectionState {
    DISCONNECTED,
    CONNECTING,
    REGISTERING,     // Solving anti-bot challenge
    CONNECTED,       // Registered and active
    RECONNECTING,    // Lost connection, retrying
    ERROR,
}

sealed class SwarmEvent {
    data class TaskAssigned(val task: SwarmTask) : SwarmEvent()
    data class MessageReceived(val message: SwarmMessage) : SwarmEvent()
    data class ReputationChanged(val newScore: Int, val event: String) : SwarmEvent()
    data class ElectionStarted(val epoch: Long) : SwarmEvent()
    data class HolonInvite(val holonId: String, val taskDescription: String) : SwarmEvent()
    data class PeerJoined(val peerId: String) : SwarmEvent()
    data class PeerLeft(val peerId: String) : SwarmEvent()
    data class TierChanged(val newTier: SwarmTier) : SwarmEvent()
}
```

### 6.2 Remote Client (Option B — HTTP/WebSocket)

```kotlin
class RemoteSwarmClient(
    private val httpClient: HttpClient,    // Ktor
    private val config: SwarmConfig,
) : SwarmConnector {

    private val baseUrl get() = config.connectorUrl  // e.g., "http://192.168.1.100:9371"

    override suspend fun call(method: String, params: Map<String, Any>): JsonObject {
        // Map JSON-RPC methods to REST API calls where applicable,
        // or use POST /api/rpc for raw JSON-RPC passthrough
        return when (method) {
            "swarm.get_status" -> httpClient.get("$baseUrl/api/identity").body()
            "swarm.get_network_stats" -> httpClient.get("$baseUrl/api/network").body()
            "swarm.get_reputation" -> httpClient.get("$baseUrl/api/reputation").body()
            "swarm.receive_task" -> {
                val tasks = httpClient.get("$baseUrl/api/tasks").body<SwarmTaskList>()
                tasks.pending.firstOrNull()?.toJsonObject() ?: JsonObject(emptyMap())
            }
            else -> {
                // Fallback: POST raw JSON-RPC to connector
                httpClient.post("$baseUrl/api/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(JsonRpcRequest(method = method, params = params))
                }.body()
            }
        }
    }

    override fun events(): Flow<SwarmEvent> = flow {
        // Connect to SSE endpoint for real-time events
        httpClient.prepareGet("$baseUrl/api/events").execute { response ->
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: continue
                if (line.startsWith("data:")) {
                    val event = parseSwarmEvent(line.removePrefix("data:").trim())
                    if (event != null) emit(event)
                }
            }
        }
    }.retryWhen { cause, attempt ->
        // Exponential backoff: 2s, 4s, 8s, 16s max
        val delay = minOf(2000L * (1L shl attempt.toInt()), 16_000L)
        delay(delay)
        true // Always retry
    }
}
```

### 6.3 Embedded Client (Option A — TCP JSON-RPC, future)

```kotlin
class EmbeddedSwarmClient(
    private val connectorService: ConnectorService,
) : SwarmConnector {

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val requestId = AtomicLong(0)

    override suspend fun connect(config: SwarmConfig) = withContext(Dispatchers.IO) {
        // Ensure wws-connector binary is running as foreground service
        connectorService.start()

        // Connect to local TCP socket
        socket = Socket("127.0.0.1", 9370)
        writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream()))
        reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
    }

    override suspend fun call(method: String, params: Map<String, Any>): JsonObject =
        withContext(Dispatchers.IO) {
            val id = requestId.incrementAndGet().toString()
            val request = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                put("params", Json.encodeToJsonElement(params))
                put("signature", "") // Local calls: empty signature
            }

            writer!!.write(request.toString() + "\n")
            writer!!.flush()

            val responseLine = reader!!.readLine()
                ?: throw SwarmConnectionException("Connection closed")
            Json.parseToJsonElement(responseLine).jsonObject
        }
}
```

---

## 7. Swarm ↔ Agent Loop Integration

### 7.1 Task Execution Pipeline

```kotlin
class SwarmTaskExecutor(
    private val swarmClient: SwarmConnector,
    private val agentLoop: GuappaOrchestrator,
    private val pushNotifier: PushNotifier,
    private val reputationTracker: SwarmReputationTracker,
) {
    /**
     * Main swarm event processing loop.
     * Runs as a coroutine in the agent's CoroutineScope.
     */
    suspend fun processSwarmEvents() {
        swarmClient.events().collect { event ->
            when (event) {
                is SwarmEvent.TaskAssigned -> handleTask(event.task)
                is SwarmEvent.MessageReceived -> handleMessage(event.message)
                is SwarmEvent.ReputationChanged -> handleReputation(event)
                is SwarmEvent.HolonInvite -> handleHolonInvite(event)
                is SwarmEvent.TierChanged -> handleTierChange(event)
                else -> { /* Log other events */ }
            }
        }
    }

    private suspend fun handleTask(task: SwarmTask) {
        try {
            // Route swarm task to normal agent pipeline
            val result = agentLoop.execute(
                prompt = task.description,
                tools = task.requiredCapabilities.mapToTools(),
                context = SwarmTaskContext(
                    taskId = task.id,
                    complexity = task.complexity,
                    deadline = task.deadline,
                ),
            )

            // Submit result back to swarm
            swarmClient.call("swarm.submit_result", mapOf(
                "task_id" to task.id,
                "result" to result.content,
                "status" to "completed",
            ))

            // Notify user
            pushNotifier.notify(
                title = "Swarm task completed",
                body = task.description.take(100),
                channel = NotificationChannel.SWARM,
            )
        } catch (e: Exception) {
            // Report failure to swarm
            swarmClient.call("swarm.submit_result", mapOf(
                "task_id" to task.id,
                "result" to "Execution failed: ${e.message}",
                "status" to "failed",
            ))
        }
    }

    private suspend fun handleHolonInvite(invite: SwarmEvent.HolonInvite) {
        // Auto-accept holon invitations if agent has required capabilities
        // User can configure auto-accept policy in Settings
        swarmClient.call("swarm.board_accept", mapOf(
            "holon_id" to invite.holonId,
        ))
    }
}
```

### 7.2 Task Polling (Fallback When Events Unavailable)

```kotlin
class SwarmTaskPoller(
    private val swarmClient: SwarmConnector,
    private val taskExecutor: SwarmTaskExecutor,
    private val config: SwarmConfig,
) {
    /**
     * Poll for tasks when SSE/WebSocket is not available.
     * Respects Android Doze mode and battery optimization.
     */
    suspend fun startPolling() {
        while (currentCoroutineContext().isActive) {
            try {
                val taskResponse = swarmClient.call("swarm.receive_task", emptyMap())
                val task = taskResponse.parseTask()
                if (task != null) {
                    taskExecutor.handleTask(task)
                }
            } catch (e: Exception) {
                // Log and continue
            }

            // Adaptive polling interval
            val interval = when {
                config.isActiveMode -> config.activeTaskPollMs     // 5-10 seconds
                config.isBackgroundMode -> config.backgroundPollMs // 60 seconds
                else -> config.idlePollMs                         // 30 seconds
            }
            delay(interval)
        }
    }
}
```

---

## 8. Holon Deliberation

### 8.1 Lifecycle

When Guappa participates in a holon (temporary deliberation council):

```
1. Board Formation
   └── Coordinator invites agents matching task's required capabilities
   └── Guappa receives HolonInvite → auto-accept (configurable)

2. Commit-Reveal Phase
   └── Guappa generates proposal for the task
   └── Submits sealed SHA-256 hash of proposal
   └── After all agents commit → reveals full proposal

3. Critique Phase
   └── Adversarial agent (designated critic) challenges all proposals
   └── Each agent may revise based on critique

4. IRV Voting
   └── Ranked-choice elimination with weighted criteria:
       ├── Feasibility:   30%
       ├── Completeness:  30%
       ├── Parallelism:   25%
       └── Risk:          15%

5. Execution
   └── Winning plan decomposed into subtasks
   └── Subtasks with complexity > 0.4 recursively spawn sub-holons
   └── Atomic subtasks assigned to Executor-tier agents

6. Synthesis & Dissolution
   └── Results combined from all subtasks
   └── Holon dissolved, reputation updated
```

### 8.2 Guappa's Holon Participation

```kotlin
class SwarmHolonParticipant(
    private val swarmClient: SwarmConnector,
    private val agentLoop: GuappaOrchestrator,
) {
    /**
     * Generate a proposal for a holon deliberation.
     * Uses the agent's LLM to analyze the task and propose a plan.
     */
    suspend fun generateProposal(holonId: String, taskDescription: String): String {
        val proposal = agentLoop.execute(
            prompt = """
                You are participating in a swarm deliberation council.
                Task: $taskDescription

                Generate a detailed execution plan. Consider:
                - Feasibility (30% weight in voting)
                - Completeness (30%)
                - Parallelism potential (25%)
                - Risk mitigation (15%)

                Return a structured plan with subtasks, dependencies, and estimated complexity.
            """.trimIndent(),
        )
        return proposal.content
    }

    suspend fun submitCommit(holonId: String, proposal: String) {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(proposal.toByteArray())
            .fold("") { str, byte -> str + "%02x".format(byte) }

        swarmClient.call("consensus.proposal_commit", mapOf(
            "holon_id" to holonId,
            "hash" to hash,
        ))
    }

    suspend fun submitReveal(holonId: String, proposal: String) {
        swarmClient.call("consensus.proposal_reveal", mapOf(
            "holon_id" to holonId,
            "proposal" to proposal,
        ))
    }

    suspend fun submitVote(holonId: String, rankings: List<String>) {
        swarmClient.call("consensus.vote", mapOf(
            "holon_id" to holonId,
            "rankings" to rankings, // Ordered list of proposal IDs
        ))
    }
}
```

---

## 9. Tier System & Reputation

### 9.1 Tier Hierarchy

| Tier | Role | Requirements | Capabilities |
|------|------|-------------|--------------|
| **Executor** | Leaf node, does atomic tasks | Starting tier | `receive_task`, `submit_result` |
| **Member** | Trusted executor | 100+ reputation | Above + `inject_task` |
| **Tier2** | Coordinator | Elected via IRV per epoch (~1h) | Above + `propose_plan`, supervise executors |
| **Tier1** | Leader | Elected, oversees Tier2 | Above + receive top-level tasks, manage hierarchy |

### 9.2 Reputation Mechanics

- **Earning**: Successful task completion, holon participation, uptime
- **Losing**: Failed tasks, timeout, low-quality results
- **Gating**: Task injection requires 100+ reputation (Member tier)
- **Rate limits**: Max 10 task injections/minute, max 50 concurrent, 200-point blast radius

### 9.3 Election System

- **Epoch**: ~3600 seconds (1 hour)
- **Voting**: Instant Runoff Voting (IRV) with ranked-choice elimination
- **Pyramid structure**: Branching factor k=10, depth ceil(log10(N)), max depth 10
- **Succession**: Automatic fallback if a tier leader goes offline

---

## 10. UI & Settings

### 10.1 Settings Screen

```
Settings → Swarm Network
├── [Toggle] Connected to World Wide Swarm (default: ON)
├── Connection Mode
│   ├── Remote (HTTP/WebSocket) ← default
│   └── Embedded (P2P, future)
├── Connector URL: http://<address>:9371
│   └── [Button] Test Connection
├── Status
│   ├── Connection: 🟢 Connected
│   ├── Peers: 42 connected
│   ├── Tier: Executor
│   ├── Reputation: 150 points
│   └── Agent ID: did:swarm:z6Mk...
├── Task Policy
│   ├── [Toggle] Auto-accept tasks (default: ON)
│   ├── [Toggle] Auto-accept holon invites (default: ON)
│   ├── Max concurrent tasks: [3]
│   └── Allowed capabilities: [checklist]
├── [Button] View Swarm Tasks →
├── [Button] View Network Topology →
└── [Button] View Reputation History →
```

### 10.2 Dashboard Screen

```
Swarm Dashboard
├── Header: "Connected to World Wide Swarm"
├── Stats Row
│   ├── 42 Peers │ Tier: Executor │ Rep: 150 │ Epoch: #1247
├── Active Tasks (2)
│   ├── "Summarize research paper" — In Progress (60%)
│   └── "Translate document to Spanish" — Queued
├── Recent Completed (5)
│   ├── "Generate API documentation" — ✅ 3 min ago
│   └── ...
├── Holon Activity
│   └── "Planning: Multi-step data analysis" — Voting phase
└── Messages (3 unread)
    └── "Agent did:swarm:z6Mk... says: Results ready"
```

### 10.3 Network Topology Screen

Visual representation of the peer hierarchy (tree/graph view):
- Tier1 leaders at top
- Tier2 coordinators in middle
- Executors (including Guappa) at leaves
- Color-coded by tier, with Guappa highlighted

---

## 11. Android Platform Integration

### 11.1 Doze Mode & Battery

```kotlin
// Use WorkManager for Doze-safe polling
class SwarmPollWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val client = SwarmConnector.getInstance(applicationContext)
        val task = client.call("swarm.receive_task", emptyMap())
        if (task.hasTask()) {
            // Wake up the agent to process the task
            SwarmTaskExecutor.getInstance(applicationContext).handleTask(task.parseTask()!!)
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SwarmPollWorker>(
                repeatInterval = 15, // Minimum for WorkManager
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "swarm_poll",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
```

### 11.2 Polling Cadences

| State | Task Poll | Status Poll | Network Stats | Notes |
|-------|-----------|-------------|---------------|-------|
| **Active** (app foreground) | 5-10s | 10s | 30s | Full speed |
| **Background** (app hidden) | 30-60s | 60s | 120s | Reduced |
| **Doze** (screen off) | WorkManager (15min) | — | — | Minimum Android allows |
| **Battery Saver** | Disabled | Disabled | Disabled | Respect user preference |
| **Minimum interval** | 2s | 2s | 2s | WWSP protocol minimum |

### 11.3 Notifications

```kotlin
// Persistent notification when swarm is active
val notification = NotificationCompat.Builder(context, SWARM_CHANNEL_ID)
    .setContentTitle("Guappa Swarm")
    .setContentText("Connected to World Wide Swarm (42 peers)")
    .setSmallIcon(R.drawable.ic_swarm)
    .setOngoing(true)
    .setPriority(NotificationCompat.PRIORITY_LOW)
    .build()

// Event notifications
pushNotifier.notify(
    title = "Swarm task completed",
    body = "Summarized research paper — reputation +5",
    channel = NotificationChannel.SWARM,
)
```

### 11.4 Network Change Handling

```kotlin
// Reconnect on network switch (WiFi ↔ cellular)
val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        scope.launch { swarmConnector.reconnect() }
    }
    override fun onLost(network: Network) {
        scope.launch { swarmConnector.markDisconnected() }
    }
}
connectivityManager.registerDefaultNetworkCallback(networkCallback)
```

---

## 12. Configuration

### 12.1 SwarmConfig Data Class

```kotlin
data class SwarmConfig(
    val enabled: Boolean = true,                          // Default: ON
    val connectionMode: SwarmConnectionMode = SwarmConnectionMode.REMOTE,
    val connectorUrl: String = "http://localhost:9371",   // For remote mode
    val connectorPort: Int = 9370,                        // For embedded mode

    // Polling intervals (milliseconds)
    val activeTaskPollMs: Long = 5_000,
    val activeStatusPollMs: Long = 10_000,
    val activeNetworkStatsPollMs: Long = 30_000,
    val backgroundPollMs: Long = 60_000,

    // Task policy
    val autoAcceptTasks: Boolean = true,
    val autoAcceptHolonInvites: Boolean = true,
    val maxConcurrentTasks: Int = 3,
    val allowedCapabilities: Set<String> = setOf(
        "text_generation", "tool_use", "vision",
        "app_control", "web_search", "code_execution",
    ),

    // Agent identity
    val agentName: String = "Guappa Mobile Agent",
)

enum class SwarmConnectionMode {
    REMOTE,     // HTTP REST + WebSocket to external connector
    EMBEDDED,   // Local wws-connector binary (future)
}
```

### 12.2 Persistence

- `SwarmConfig` persisted via DataStore (Preferences)
- Agent identity (DID, keypair) persisted via Android KeyStore
- Registration state persisted via DataStore
- Task history persisted via Room database (shared with agent session store)

---

## 13. P2P Inter-Node Messages (Reference)

These messages flow between `wws-connector` nodes via GossipSub. Guappa does not handle these directly — the connector manages them. Listed for protocol understanding.

| Category | Messages |
|----------|----------|
| **Connection** | `swarm.handshake`, `swarm.keepalive`, `swarm.succession` |
| **Elections** | `election.candidacy`, `election.vote` |
| **Hierarchy** | `hierarchy.assign_tier`, `hierarchy.succession` |
| **Consensus** | `consensus.proposal_commit`, `consensus.proposal_reveal`, `consensus.vote` |
| **Tasks** | `task.inject`, `task.assign`, `task.submit_result`, `task.verification` |
| **Holons** | `board.invite`, `board.accept`, `board.decline`, `board.ready`, `board.dissolve` |
| **Review** | `discussion.critique` |

---

## 14. MCP Compatibility

WWSP supports MCP (Model Context Protocol) compatibility mode. When `mcp_compatible = true` in connector config, four MCP tools are exposed:

| MCP Tool | Maps To |
|----------|---------|
| `swarm_submit_result` | `swarm.submit_result` |
| `swarm_get_status` | `swarm.get_status` |
| `swarm_propose_plan` | `swarm.propose_plan` |
| `swarm_query_peers` | `swarm.get_directory` |

This allows agents using MCP-compatible frameworks to interact with the swarm without implementing the full JSON-RPC protocol.

---

## 15. Error Handling & Resilience

### 15.1 Reconnection Strategy

```kotlin
// Exponential backoff with jitter
suspend fun reconnectWithBackoff() {
    var attempt = 0
    while (true) {
        try {
            connect(config)
            register()
            return // Success
        } catch (e: Exception) {
            attempt++
            val baseDelay = minOf(2000L * (1L shl attempt), 60_000L) // Max 60s
            val jitter = Random.nextLong(0, baseDelay / 4)
            delay(baseDelay + jitter)
        }
    }
}
```

### 15.2 Error Categories

| Error | Handling |
|-------|---------|
| Connector unreachable | Reconnect with exponential backoff |
| Registration failed | Retry with new challenge |
| Task execution timeout | Report failure to swarm, request clarification |
| Invalid task (missing capabilities) | Decline task, log reason |
| Network switch (WiFi ↔ cellular) | Auto-reconnect via NetworkCallback |
| App killed by OS | WorkManager resumes polling on restart |
| Connector binary crash (embedded) | Restart foreground service |

---

## 16. Test Plan

### 16.1 Unit Tests

- `SwarmChallengeSolver` — parse and solve arithmetic challenges (garbled text, various operators)
- `SwarmRegistration` — 3-step registration flow, error handling for each step
- `SwarmTaskReceiver` — task parsing, capability matching, routing to AgentLoop
- `SwarmResultSubmitter` — result formatting, JSON-RPC request construction
- `SwarmConfig` — serialization/deserialization, defaults, validation
- `SwarmEventStream` — SSE/WebSocket event parsing for all event types
- `RemoteSwarmClient` — REST API call construction, response parsing
- `SwarmHolonParticipant` — proposal generation, commit-reveal, voting

### 16.2 Integration Tests

- Register → receive task → execute via AgentLoop → submit result → verify reputation change
- Register → inject task (as Member) → verify task appears in swarm
- Toggle off → verify zero swarm network traffic → toggle on → verify reconnection
- Holon lifecycle: invite → accept → propose → vote → execute subtask → synthesis
- Message send → message receive on another agent
- Key rotation → verify continued operation with new keypair

### 16.3 Maestro E2E Tests

- Settings → toggle swarm on → verify status indicator shows "Connected"
- Settings → toggle swarm off → verify status indicator shows "Disconnected"
- Settings → enter connector URL → tap "Test Connection" → verify success/failure
- Dashboard → verify peer count, tier, reputation display
- Dashboard → verify task list shows assigned tasks
- Settings → configure task policy → verify policy applied to incoming tasks

### 16.4 Resilience Tests

- Connector disconnect → verify automatic reconnection with backoff
- Connector disconnect during active task → verify task failure reported correctly
- Network switch (WiFi → cellular) → verify seamless reconnection
- App killed by OS → verify WorkManager resumes polling
- Task execution failure → verify error reported to swarm, no crash
- Rapid toggle on/off → verify no resource leaks or race conditions
- High-frequency task assignment → verify max concurrent tasks respected

---

## 17. Rollback Strategy

- Phase 11 is fully independent — disabling the swarm toggle returns Guappa to standalone mode
- No other phase depends on Phase 11
- Swarm module can be entirely removed without affecting core agent functionality
- Config migration: swarm settings stored in separate DataStore namespace
- If embedded connector (Option A) causes stability issues, fall back to remote mode (Option B)

---

## 18. Future Considerations (Out of Scope for v1)

- **Embedded connector (Option A)**: Cross-compile wws-connector to Android ARM64
- **Swarm marketplace**: Browse and accept tasks from a marketplace UI
- **Multi-swarm**: Connect to multiple swarm networks simultaneously
- **Agent reputation badges**: Display achievements in profile
- **Swarm analytics**: Dashboard with task throughput, success rate, earnings
- **P2P file sharing**: Share large files directly between agents via swarm
- **Voice in swarm**: Voice-based agent collaboration within holons
