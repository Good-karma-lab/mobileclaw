# Guappa API Reference

## Native Modules (React Native Bridge)

### GuappaAgent

Bridge: `NativeModules.GuappaAgent`

| Method | Params | Returns | Description |
|--------|--------|---------|-------------|
| `sendMessage(text, imageUris?)` | `string, string[]?` | `Promise<string>` | Send message to agent, get response |
| `streamMessage(text, imageUris?)` | `string, string[]?` | `Promise<void>` | Stream message (events via emitter) |
| `cancelStream()` | — | `Promise<void>` | Cancel active stream |
| `getSessionId()` | — | `Promise<string>` | Get current session ID |
| `isAgentRunning()` | — | `Promise<boolean>` | Check if agent service is active |

Events: `agentStreamChunk`, `agentStreamComplete`, `agentStreamError`

### GuappaMemory

Bridge: `NativeModules.GuappaMemory`

| Method | Params | Returns | Description |
|--------|--------|---------|-------------|
| `getMemories(category?, tier?)` | `string?, string?` | `Promise<Fact[]>` | List stored facts |
| `addMemory(key, value, category, tier?, importance?)` | `string, string, string, string?, number` | `Promise<Fact>` | Store a fact |
| `searchMemories(query)` | `string` | `Promise<Fact[]>` | Full-text search |
| `semanticSearch(query, limit)` | `string, number` | `Promise<SemanticResult[]>` | Vector similarity search |
| `deleteMemory(id)` | `string` | `Promise<boolean>` | Delete a fact |
| `getSessionHistory(limit?)` | `number` | `Promise<Session[]>` | Recent sessions |
| `createSession(title?)` | `string?` | `Promise<Session>` | Create new session |
| `endSession(sessionId, summary?)` | `string, string?` | `Promise<boolean>` | End session |
| `getSessionMessages(sessionId)` | `string` | `Promise<Message[]>` | Messages in session |
| `getTasks()` | — | `Promise<Task[]>` | All tasks |
| `addTask(title, desc?, priority?, dueDate?)` | `string, string?, number, number` | `Promise<Task>` | Create task |
| `updateTaskStatus(taskId, status)` | `string, string` | `Promise<boolean>` | Update task |
| `deleteTask(taskId)` | `string` | `Promise<boolean>` | Delete task |
| `getMemoryStats()` | — | `Promise<MemoryStats>` | Memory statistics |
| `exportMemories()` | — | `Promise<ExportResult>` | Export all data to JSON |
| `importMemories(jsonString)` | `string` | `Promise<ImportResult>` | Import data from JSON |

### GuappaConfig (ConfigBridge)

Bridge: `NativeModules.GuappaConfig`

| Method | Params | Returns | Description |
|--------|--------|---------|-------------|
| `getConfig()` | — | `Promise<Config>` | Full config object |
| `setConfig(key, value)` | `string, string` | `Promise<void>` | Set config value |
| `getProviderConfig()` | — | `Promise<ProviderConfig>` | Provider settings |
| `setProviderConfig(config)` | `object` | `Promise<void>` | Update provider |
| `listModels()` | — | `Promise<string[]>` | Fetch available models |

### GuappaSwarm

Bridge: `NativeModules.GuappaSwarm`

| Method | Params | Returns | Description |
|--------|--------|---------|-------------|
| `connect(connectorUrl)` | `string` | `Promise<boolean>` | Connect to swarm |
| `disconnect()` | — | `Promise<void>` | Disconnect |
| `getIdentity()` | — | `Promise<Identity>` | DID + public key |
| `generateIdentity()` | — | `Promise<Identity>` | Create new identity |
| `getPeers()` | — | `Promise<Peer[]>` | Connected peers |
| `sendMessage(peerId, text)` | `string, string` | `Promise<boolean>` | Send to peer |

### GuappaChannels

Bridge: `NativeModules.GuappaChannels`

| Method | Params | Returns | Description |
|--------|--------|---------|-------------|
| `send(channelId, message, metadata?)` | `string, string, object?` | `Promise<boolean>` | Send via channel |
| `broadcast(message, channelIds?)` | `string, string[]?` | `Promise<void>` | Broadcast to channels |
| `getChannelStatus()` | — | `Promise<ChannelStatus[]>` | Health of all channels |
| `configureChannel(channelId, config)` | `string, object` | `Promise<void>` | Configure channel |

### GuappaProactive

Bridge: `NativeModules.GuappaProactive`

| Method | Params | Returns | Description |
|--------|--------|---------|-------------|
| `getTriggers()` | — | `Promise<Trigger[]>` | All triggers |
| `addTrigger(type, config)` | `string, object` | `Promise<Trigger>` | Create trigger |
| `removeTrigger(id)` | `string` | `Promise<boolean>` | Delete trigger |
| `getNotificationHistory()` | — | `Promise<Notification[]>` | Recent notifications |

### AndroidSTT

Bridge: `NativeModules.AndroidSTT`

| Method | Params | Returns | Description |
|--------|--------|---------|-------------|
| `isAvailable()` | — | `Promise<boolean>` | SpeechRecognizer available? |
| `startListening(options)` | `{language, offline}` | `Promise<boolean>` | Start recognition |
| `stopListening()` | — | `Promise<boolean>` | Stop recognition |
| `cancel()` | — | `Promise<boolean>` | Cancel recognition |
| `destroy()` | — | `Promise<boolean>` | Destroy recognizer |

Events: `androidSTT_ready`, `androidSTT_speechStart`, `androidSTT_speechEnd`, `androidSTT_result`, `androidSTT_error`, `androidSTT_rmsChanged`

## Kotlin Internal API

### ProviderRouter

```kotlin
class ProviderRouter {
    fun route(capability: CapabilityType): Provider
    fun getProvider(name: String): Provider?
    fun listProviders(): List<Provider>
}
```

### ToolEngine

```kotlin
class ToolEngine {
    suspend fun execute(toolName: String, args: JSONObject): ToolResult
    fun listTools(): List<Tool>
    fun isToolEnabled(name: String): Boolean
}
```

### MemoryManager

```kotlin
class MemoryManager {
    suspend fun addFact(key, value, category, tier, importance): MemoryFactEntity
    suspend fun searchMemories(query): List<MemoryFactEntity>
    suspend fun semanticSearch(query, limit): List<SemanticResult>
    suspend fun getStats(): MemoryStats
    suspend fun runCleanup(): Int
    suspend fun runPromotion(): Int
}
```

### TokenCounter

```kotlin
object TokenCounter {
    fun count(text: String): Int
    fun countMessages(messages: List<Map<String, String>>): Int
    fun estimateFast(text: String): Int
    fun exceedsBudget(text: String, budget: Int): Boolean
}
```
