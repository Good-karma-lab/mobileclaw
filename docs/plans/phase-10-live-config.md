# Phase 10: Live Config — Real-Time Configuration via TurboModules

**Date**: 2026-03-06
**Status**: Proposal
**Depends On**: Phase 1 (Foundation), Phase 2 (Provider Router), Phase 3 (Tool Engine)
**Blocks**: —

---

## 1. Objective

Every setting change in the UI applies immediately without restarting the agent service. Uses React Native TurboModules (New Architecture) for type-safe, zero-serialization bridge between JS UI and Kotlin backend, with Kotlin StateFlow for reactive propagation.

---

## 2. Research Checklist

- [ ] React Native New Architecture migration (Hermes, TurboModules, Fabric)
- [ ] Codegen — generate type-safe native stubs from TypeScript specs
- [ ] Native Event Emitters via TurboModule `sendEvent()` — push from Kotlin to JS
- [ ] TurboModule Promise-based calls — JS to Kotlin request-response
- [ ] Kotlin StateFlow for reactive config propagation
- [ ] Android DataStore (Preferences) for persistent reactive storage
- [ ] Provider client lifecycle (connect → reconfigure → reconnect)
- [ ] Tool engine dynamic reconfiguration
- [ ] Channel hot-swap (connect/disconnect without restart)
- [ ] Hermes engine compatibility and debugging

---

## 3. Architecture

### 3.1 Data Flow

```
┌──────────────────────────────────────────────────────────┐
│  React Native (TypeScript / Hermes)                       │
│                                                          │
│  SettingsScreen                                          │
│    │ user changes provider to "Anthropic"                │
│    ▼                                                     │
│  GuappaConfigModule.setTextChatProvider("anthropic")      │
│    │ (TurboModule call — sync, no JSON serialization)    │
└────┼─────────────────────────────────────────────────────┘
     │ JSI (JavaScript Interface) — direct native call
     ▼
┌──────────────────────────────────────────────────────────┐
│  Kotlin Native Module (GuappaConfigModule.kt)            │
│    │                                                     │
│    ▼                                                     │
│  GuappaConfigStore.update { textChat.providerId = "anthropic" }
│    │                                                     │
│    ▼                                                     │
│  DataStore.edit { ... }  ← persistent storage            │
│    │                                                     │
│    ▼                                                     │
│  StateFlow<GuappaConfig> emits new value                 │
│    │                                                     │
│    ├──▶ ProviderRouter.collect → switches to Anthropic   │
│    ├──▶ ToolEngine.collect → updates tool schemas        │
│    ├──▶ ChannelHub.collect → reconnects if needed        │
│    ├──▶ VoicePipeline.collect → swaps STT/TTS engine    │
│    └──▶ MemoryManager.collect → adjusts context budget   │
│                                                          │
│  GuappaConfigModule.sendEvent("configChanged", payload)  │
│    │ (TurboModule event emitter — push to JS)            │
└────┼─────────────────────────────────────────────────────┘
     │ JSI event
     ▼
┌──────────────────────────────────────────────────────────┐
│  React Native                                            │
│  GuappaConfigModule.addListener("configChanged", cb)     │
│    ▼                                                     │
│  UI updates (provider name, model name, status indicator)│
└──────────────────────────────────────────────────────────┘
```

### 3.2 Module Structure

```
android/app/src/main/java/com/guappa/app/
└── config/
    ├── GuappaConfigStore.kt          — reactive config (DataStore + StateFlow)
    ├── GuappaConfig.kt               — full config data class (serializable)
    ├── ConfigChangeDispatcher.kt     — dispatches changes to all subsystems
    ├── ConfigValidator.kt            — validate config values before applying
    ├── ConfigMigrator.kt             — migrate config between app versions
    │
    ├── hotswap/
    │   ├── ProviderHotSwap.kt        — swap provider/model without restart
    │   ├── ChannelHotSwap.kt         — add/remove channels without restart
    │   ├── ToolHotSwap.kt            — enable/disable tools in real-time
    │   ├── VoiceHotSwap.kt           — swap STT/TTS engine
    │   └── MemoryHotSwap.kt          — adjust context budget, toggle features
    │
    └── bridge/
        ├── GuappaConfigSpec.ts       — TypeScript spec for Codegen
        ├── GuappaConfigModule.kt     — TurboModule implementation
        └── NativeGuappaConfig.kt     — Codegen-generated abstract class

// React Native side
src/native/
├── GuappaConfigSpec.ts               — TurboModule TypeScript spec
├── useGuappaConfig.ts                — React hook for config state
└── GuappaConfigContext.tsx            — React context for config
```

### 3.3 TurboModule Spec (TypeScript)

```typescript
// GuappaConfigSpec.ts — input for Codegen
import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  // ── Getters ──
  getConfig(): Promise<string>;  // JSON string of full config
  getProviderKeys(): Promise<string>;  // JSON map of provider → has_key

  // ── Capability-based setters ──
  setCapabilityProvider(capability: string, providerId: string, modelId: string): Promise<void>;
  setCapabilityFallback(capability: string, providerId: string, modelId: string): Promise<void>;

  // ── Provider keys ──
  setProviderKey(providerId: string, apiKey: string): Promise<boolean>;  // returns validation result
  removeProviderKey(providerId: string): Promise<void>;

  // ── Model discovery ──
  refreshModels(providerId: string): Promise<string>;  // JSON array of ModelInfo
  getModelsForCapability(capability: string): Promise<string>;  // JSON array

  // ── Global settings ──
  setTemperature(value: number): Promise<void>;
  setMaxOutputTokens(value: number): Promise<void>;
  setAutoFallback(enabled: boolean): Promise<void>;
  setDailyCostBudget(amount: number): Promise<void>;

  // ── Tool management ──
  setToolEnabled(toolName: string, enabled: boolean): Promise<void>;
  getToolList(): Promise<string>;  // JSON array of tool statuses

  // ── Channel management ──
  setChannelConfig(channelId: string, config: string): Promise<boolean>;
  getChannelStatus(): Promise<string>;  // JSON map of channel → status

  // ── Voice settings ──
  setVoiceConfig(config: string): Promise<void>;

  // ── Memory settings ──
  setMemoryConfig(config: string): Promise<void>;
  exportMemory(): Promise<string>;  // file path
  importMemory(filePath: string): Promise<boolean>;
  clearMemory(): Promise<void>;

  // ── Proactive settings ──
  setProactiveConfig(config: string): Promise<void>;

  // ── Events (Kotlin → JS) ──
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('GuappaConfig');
```

### 3.4 TurboModule Implementation (Kotlin)

```kotlin
class GuappaConfigModule(
    reactContext: ReactApplicationContext,
    private val configStore: GuappaConfigStore,
    private val providerRouter: ProviderRouter,
    private val modelDiscovery: ModelDiscoveryService,
    private val channelHub: ChannelHub,
    private val toolEngine: ToolEngine,
) : NativeGuappaConfigSpec(reactContext) {  // Codegen-generated base class

    // ── Capability-based provider/model selection ──

    override fun setCapabilityProvider(
        capability: String, providerId: String, modelId: String, promise: Promise
    ) {
        scope.launch {
            try {
                val cap = ModelCapability.valueOf(capability)
                configStore.update { config ->
                    config.updateCapability(cap) {
                        it.copy(providerId = providerId, modelId = modelId)
                    }
                }
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("CONFIG_ERROR", e.message)
            }
        }
    }

    // ── Model discovery ──

    override fun refreshModels(providerId: String, promise: Promise) {
        scope.launch {
            val result = modelDiscovery.refreshProvider(
                providerId,
                configStore.value.providerKeys[providerId] ?: return@launch promise.reject("NO_KEY", "No API key")
            )
            result.onSuccess { models ->
                promise.resolve(Json.encodeToString(models))
            }
            result.onFailure { error ->
                promise.reject("FETCH_ERROR", error.message)
            }
        }
    }

    override fun getModelsForCapability(capability: String, promise: Promise) {
        scope.launch {
            val cap = ModelCapability.valueOf(capability)
            val models = modelDiscovery.getModelsForCapability(cap)
            promise.resolve(Json.encodeToString(models))
        }
    }

    // ── Provider key management ──

    override fun setProviderKey(providerId: String, apiKey: String, promise: Promise) {
        scope.launch {
            // 1. Store key securely (Android Keystore)
            secureKeyStore.store(providerId, apiKey)

            // 2. Validate key by calling health check
            val provider = providerRouter.getProvider(providerId)
            val health = provider?.healthCheck(apiKey)

            if (health?.isSuccess == true) {
                // 3. Update config
                configStore.update { it.updateProviderKey(providerId, apiKey) }
                // 4. Trigger model refresh
                modelDiscovery.refreshProvider(providerId, ProviderConfig(apiKey = apiKey))
                // 5. Notify JS
                sendConfigEvent("providerKeySet", mapOf("providerId" to providerId, "valid" to true))
                promise.resolve(true)
            } else {
                promise.resolve(false)  // key invalid
            }
        }
    }

    // ── Event emission (Kotlin → JS) ──

    private fun sendConfigEvent(eventName: String, data: Map<String, Any>) {
        val params = Arguments.createMap().apply {
            putString("event", eventName)
            for ((key, value) in data) {
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Double -> putDouble(key, value)
                }
            }
        }
        sendEvent("configChanged", params)
    }
}
```

---

## 4. Reactive Config Store

```kotlin
class GuappaConfigStore(private val context: Context) {
    private val dataStore = context.dataStore  // DataStore<Preferences>

    private val _config = MutableStateFlow(GuappaConfig.DEFAULT)
    val config: StateFlow<GuappaConfig> = _config.asStateFlow()

    init {
        // Load persisted config on init
        CoroutineScope(Dispatchers.IO).launch {
            val saved = loadFromDataStore()
            _config.value = saved
        }
    }

    suspend fun update(transform: (GuappaConfig) -> GuappaConfig) {
        val newConfig = transform(_config.value)
        val validated = ConfigValidator.validate(newConfig)
        _config.value = validated
        persistToDataStore(validated)
    }

    val value: GuappaConfig get() = _config.value
}
```

---

## 5. Hot-Swap Implementations

### 5.1 Provider Hot-Swap

```kotlin
class ProviderHotSwap(
    private val configStore: GuappaConfigStore,
    private val providerRouter: ProviderRouter,
) {
    init {
        // React to config changes
        scope.launch {
            configStore.config
                .map { it.providerRouterConfig }
                .distinctUntilChanged()
                .collect { newConfig ->
                    providerRouter.reconfigure(newConfig)
                    // Current request (if any) completes with old provider
                    // Next request uses new provider
                    // No conversation loss
                }
        }
    }
}
```

### 5.2 Channel Hot-Swap

```kotlin
class ChannelHotSwap(
    private val configStore: GuappaConfigStore,
    private val channelHub: ChannelHub,
) {
    init {
        scope.launch {
            configStore.config
                .map { it.channelConfigs }
                .distinctUntilChanged()
                .collect { newConfigs ->
                    for ((channelId, config) in newConfigs) {
                        if (config.enabled && !channelHub.isConnected(channelId)) {
                            channelHub.connect(channelId, config)
                        } else if (!config.enabled && channelHub.isConnected(channelId)) {
                            channelHub.disconnect(channelId)
                        }
                    }
                }
        }
    }
}
```

---

## 6. What Changes Immediately

| Setting | Mechanism | Latency |
|---------|-----------|---------|
| Provider | ProviderRouter reconfigure | Next request |
| Model | ProviderRouter reconfigure | Next request |
| API key | Secure store + health check | ~2 seconds |
| Temperature | Config propagation | Next request |
| Max tokens | Config propagation | Next request |
| Tool enable/disable | ToolEngine reconfigure | Next request |
| Channel token | Channel reconnect | ~5 seconds |
| Voice engine | VoicePipeline swap | Next voice interaction |
| Local model | ModelManager load/unload | 10-30 seconds |
| Proactive settings | ProactiveEngine reconfigure | Immediate |
| Memory settings | MemoryManager reconfigure | Immediate |
| Context budget | ContextBudgetAllocator | Next request |
| Cost budget | CostTracker reconfigure | Immediate |

---

## 7. React Native Migration Checklist

```
□ Enable Hermes engine in gradle.properties
□ Enable New Architecture flags (newArchEnabled=true)
□ Run Codegen: npx react-native codegen
□ Migrate existing NativeModules to TurboModule specs
□ Replace NativeEventEmitter with TurboModule event emitters
□ Migrate views to Fabric (if applicable)
□ Test all existing functionality still works
□ Profile: verify no performance regression
```

---

## 8. Configuration Schema (Full)

```kotlin
@Serializable
data class GuappaConfig(
    val foundation: FoundationConfig = FoundationConfig(),
    val providerRouter: ProviderRouterConfig = ProviderRouterConfig(),
    val toolEngine: ToolEngineConfig = ToolEngineConfig(),
    val proactive: ProactiveConfig = ProactiveConfig(),
    val channels: ChannelHubConfig = ChannelHubConfig(),
    val voice: VoiceConfig = VoiceConfig(),
    val memory: MemoryConfig = MemoryConfig(),
    val persona: PersonaConfig = PersonaConfig(),

    // Meta
    val version: Int = 1,
    val migratedFrom: Int? = null,
) {
    companion object {
        val DEFAULT = GuappaConfig()
    }
}
```

---

## 9. Test Plan

| Test | Description |
|------|-------------|
| `ConfigStore_PersistReload` | Set value → restart → value persisted |
| `ConfigStore_StateFlowEmits` | Update config → collectors receive new value |
| `ProviderHotSwap_SwitchProvider` | Change provider → next request uses new |
| `ChannelHotSwap_Connect` | Enable Telegram → channel connects |
| `ChannelHotSwap_Disconnect` | Disable Telegram → channel disconnects |
| `ToolHotSwap_Disable` | Disable tool → not in LLM tool list |
| `TurboModule_SetCapability` | JS call → Kotlin config updated |
| `TurboModule_Event` | Kotlin change → JS receives event |
| `ModelRefresh_FromSettings` | Open settings → models refreshed from API |
| `ConfigMigration_V1ToV2` | Old config → migrated to new schema |

---

## 10. Acceptance Criteria

- [ ] All config changes apply without service restart
- [ ] Provider switch takes effect on next request (< 1s)
- [ ] Channel connect/disconnect works from Settings UI
- [ ] Tool enable/disable reflected in next LLM call
- [ ] Model list refreshes when opening Settings
- [ ] API key validation shows real-time result
- [ ] Config persists across app restart
- [ ] Config migration works on app update
- [ ] TurboModule bridge has zero JSON serialization overhead
- [ ] React Native New Architecture fully enabled (Hermes + TurboModules)
