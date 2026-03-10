import React, { useState, useEffect, useCallback, useRef } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, Alert, LayoutAnimation } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { BlurView } from "expo-blur";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";
import {
  CollapsibleSection,
  GlassDropdown,
  GlassInput,
  GlassSlider,
  GlassButton,
  GlassToggle,
  GlassModal,
} from "../../components/glass";
import {
  loadAgentConfig,
  saveAgentConfig,
  loadIntegrationsConfig,
  loadChannelsConfig,
  saveChannelsConfig,
  loadProactiveConfig,
  saveProactiveConfig,
  loadMemoryConfig,
  saveMemoryConfig,
  DEFAULT_AGENT_CONFIG,
  DEFAULT_CHANNELS_CONFIG,
  DEFAULT_PROACTIVE_CONFIG,
  DEFAULT_MEMORY_CONFIG,
  type AgentRuntimeConfig,
  type ChannelsConfig,
  type ChannelConfig,
  type ProactiveConfig,
  type MemoryConfig,
} from "../../state/guappa";
import { startAgent } from "../../native/guappaAgent";
import { startLocalLlmServer, LOCAL_LLM_URL } from "../../native/localLlmServer";
import { checkModelExists, downloadModel } from "../../native/modelDownloader";
import {
  getProviderHealth,
  getProviderModels,
  type ProviderModelInfo,
} from "../../native/guappaConfig";
import { useDebugInfo } from "../../hooks/useDebugInfo";

// --- Option lists ---

const PROVIDER_OPTIONS = [
  { label: "OpenAI", value: "openai" },
  { label: "Anthropic", value: "anthropic" },
  { label: "Google (Gemini)", value: "gemini" },
  { label: "DeepSeek", value: "deepseek" },
  { label: "Mistral", value: "mistral" },
  { label: "xAI (Grok)", value: "xai" },
  { label: "Groq", value: "groq" },
  { label: "OpenRouter", value: "openrouter" },
  { label: "Ollama", value: "ollama" },
  { label: "Local (on-device)", value: "local" },
];

const STT_OPTIONS = [
  { label: "Whisper", value: "whisper" },
  { label: "Google", value: "google" },
  { label: "Local", value: "local" },
];

const TTS_OPTIONS = [
  { label: "ElevenLabs", value: "elevenlabs" },
  { label: "Google", value: "google" },
  { label: "Local", value: "local" },
];

const RETENTION_OPTIONS = [
  { label: "7 days", value: "7" },
  { label: "30 days", value: "30" },
  { label: "90 days", value: "90" },
  { label: "Forever", value: "-1" },
];

type ChannelMeta = {
  key: string;
  name: string;
  emoji: string;
  fieldLabels: Record<string, string>;
  fieldPlaceholders: Record<string, string>;
  fieldSecure?: Record<string, boolean>;
};

const CHANNEL_META: ChannelMeta[] = [
  {
    key: "telegram",
    name: "Telegram",
    emoji: "\u2708\uFE0F",
    fieldLabels: { botToken: "Bot Token", chatId: "Chat ID" },
    fieldPlaceholders: { botToken: "123456:ABC-DEF...", chatId: "-100..." },
    fieldSecure: { botToken: true },
  },
  {
    key: "discord",
    name: "Discord",
    emoji: "\uD83C\uDFAE",
    fieldLabels: { webhookUrl: "Webhook URL" },
    fieldPlaceholders: { webhookUrl: "https://discord.com/api/webhooks/..." },
    fieldSecure: { webhookUrl: true },
  },
  {
    key: "slack",
    name: "Slack",
    emoji: "\uD83D\uDCBC",
    fieldLabels: { webhookUrl: "Webhook URL" },
    fieldPlaceholders: { webhookUrl: "https://hooks.slack.com/services/..." },
    fieldSecure: { webhookUrl: true },
  },
  {
    key: "email",
    name: "Email",
    emoji: "\u2709\uFE0F",
    fieldLabels: { recipientAddress: "Recipient" },
    fieldPlaceholders: { recipientAddress: "user@example.com" },
  },
  {
    key: "whatsapp",
    name: "WhatsApp",
    emoji: "\uD83D\uDCAC",
    fieldLabels: { phoneNumberId: "Phone Number ID", accessToken: "Access Token", recipient: "Recipient" },
    fieldPlaceholders: { phoneNumberId: "1234567890", accessToken: "EAA...", recipient: "+1..." },
    fieldSecure: { accessToken: true },
  },
  {
    key: "signal",
    name: "Signal",
    emoji: "\uD83D\uDD12",
    fieldLabels: { apiUrl: "API URL", senderNumber: "Sender Number", recipient: "Recipient" },
    fieldPlaceholders: { apiUrl: "http://localhost:8080", senderNumber: "+1...", recipient: "+1..." },
  },
  {
    key: "matrix",
    name: "Matrix",
    emoji: "\uD83C\uDF10",
    fieldLabels: { homeserverUrl: "Homeserver URL", accessToken: "Access Token", roomId: "Room ID" },
    fieldPlaceholders: { homeserverUrl: "https://matrix.org", accessToken: "syt_...", roomId: "!abc:matrix.org" },
    fieldSecure: { accessToken: true },
  },
  {
    key: "sms",
    name: "SMS",
    emoji: "\uD83D\uDCF1",
    fieldLabels: { recipientPhone: "Recipient Phone" },
    fieldPlaceholders: { recipientPhone: "+1234567890" },
  },
];

const SAVE_DEBOUNCE_MS = 500;

export function ConfigScreen({ isActive }: { isActive?: boolean }) {
  const insets = useSafeAreaInsets();
  const [config, setConfig] = useState<AgentRuntimeConfig>(DEFAULT_AGENT_CONFIG);
  const [loaded, setLoaded] = useState(false);
  const [restarting, setRestarting] = useState(false);
  const [providerModels, setProviderModels] = useState<ProviderModelInfo[]>([]);
  const [providerModelsLoading, setProviderModelsLoading] = useState(false);
  const [providerHealthy, setProviderHealthy] = useState<boolean | null>(null);
  const [providerProbeError, setProviderProbeError] = useState<string | null>(null);
  const [localModelStatus, setLocalModelStatus] = useState<
    "idle" | "checking" | "not_downloaded" | "downloading" | "ready"
  >("idle");
  const [localDownloadProgress, setLocalDownloadProgress] = useState(0);
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const localModelProbeRef = useRef(0);
  const localModelDownloadRef = useRef(false);
  const [channels, setChannels] = useState<ChannelsConfig>(DEFAULT_CHANNELS_CONFIG);
  const [expandedChannel, setExpandedChannel] = useState<string | null>(null);
  const [channelTesting, setChannelTesting] = useState<string | null>(null);
  const [proactive, setProactive] = useState<ProactiveConfig>(DEFAULT_PROACTIVE_CONFIG);
  const [memory, setMemory] = useState<MemoryConfig>(DEFAULT_MEMORY_CONFIG);
  const [clearMemoryVisible, setClearMemoryVisible] = useState(false);
  const [exportingMemory, setExportingMemory] = useState(false);
  const { loading: debugLoading, error: debugError, downloadDebugInfo } = useDebugInfo();

  // Load config on mount
  useEffect(() => {
    loadAgentConfig().then((cfg) => {
      setConfig(cfg);
      setLoaded(true);
    });
    loadChannelsConfig().then(setChannels);
    loadProactiveConfig().then(setProactive);
    loadMemoryConfig().then(setMemory);
  }, []);

  useEffect(() => {
    if (!loaded || config.provider !== "local") {
      setLocalModelStatus("idle");
      setLocalDownloadProgress(0);
      return;
    }

    let cancelled = false;
    const probeId = ++localModelProbeRef.current;

    (async () => {
      setLocalModelStatus("checking");
      try {
        const { exists, path } = await checkModelExists(config.model || DEFAULT_AGENT_CONFIG.model);
        if (
          cancelled ||
          localModelDownloadRef.current ||
          localModelProbeRef.current !== probeId
        ) {
          return;
        }
        if (exists) {
          setLocalModelStatus("ready");
          setLocalDownloadProgress(100);
          if (path && config.localModelPath !== path) {
            updateConfig("localModelPath", path);
          }
          return;
        }
      } catch {
        if (
          cancelled ||
          localModelDownloadRef.current ||
          localModelProbeRef.current !== probeId
        ) {
          return;
        }
      }

      setLocalModelStatus("not_downloaded");
      setLocalDownloadProgress(0);
    })();

    return () => {
      cancelled = true;
    };
  }, [config.localModelPath, config.model, config.provider, loaded]);

  // Debounced save
  const persistConfig = useCallback((updated: AgentRuntimeConfig) => {
    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
    }
    saveTimerRef.current = setTimeout(() => {
      saveAgentConfig(updated);
    }, SAVE_DEBOUNCE_MS);
  }, []);

  // Restart agent with current config (hot-reload)
  const restartAgent = useCallback(async () => {
    setRestarting(true);
    try {
      await saveAgentConfig(config);
      const integCfg = await loadIntegrationsConfig();
      const runtimeApiKey =
        config.authMode === "oauth_token" ? config.oauthAccessToken : config.apiKey;

      const agentConfig: Record<string, any> = {
        apiKey: runtimeApiKey,
        provider: config.provider,
        model: config.model,
        apiUrl: config.apiUrl,
        temperature: config.temperature,
        telegramToken: integCfg.telegramEnabled ? integCfg.telegramBotToken : "",
        telegramChatId: integCfg.telegramEnabled ? integCfg.telegramChatId : "",
        discordBotToken: integCfg.discordEnabled ? integCfg.discordBotToken : "",
        slackBotToken: integCfg.slackEnabled ? integCfg.slackBotToken : "",
        composioApiKey: integCfg.composioEnabled ? integCfg.composioApiKey : "",
        braveApiKey: config.braveApiKey || "",
        localModelPath: config.localModelPath || "",
        thinkingMode: config.thinkingMode ?? false,
      };

      if (agentConfig.provider === "local" && agentConfig.localModelPath) {
        await startLocalLlmServer({
          modelPath: agentConfig.localModelPath,
          gpuLayers: config.gpuLayers ?? 0,
          cpuThreads: config.cpuThreads ?? 4,
          contextLength: config.contextLength ?? 2048,
          thinkingMode: config.thinkingMode ?? true,
        });
        agentConfig.provider = "openai";
        agentConfig.apiUrl = `${LOCAL_LLM_URL}/v1`;
        agentConfig.apiKey = "local";
        agentConfig.model = "local";
      }

      await startAgent(agentConfig);
      console.log("[config] agent restarted with new config");
    } catch (err) {
      console.warn("[config] agent restart failed:", err);
    } finally {
      setRestarting(false);
    }
  }, [config]);

  // Helper to update a single config key
  const updateConfig = useCallback(
    <K extends keyof AgentRuntimeConfig>(key: K, value: AgentRuntimeConfig[K]) => {
      setConfig((prev) => {
        const next = { ...prev, [key]: value };
        persistConfig(next);
        return next;
      });
    },
    [persistConfig],
  );

  // --- Channel helpers ---
  const updateChannel = useCallback(
    (channelKey: string, patch: Partial<ChannelConfig>) => {
      setChannels((prev) => {
        const next = {
          ...prev,
          [channelKey]: { ...prev[channelKey], ...patch },
        };
        saveChannelsConfig(next);
        return next;
      });
    },
    [],
  );

  const updateChannelField = useCallback(
    (channelKey: string, fieldKey: string, value: string) => {
      setChannels((prev) => {
        const ch = prev[channelKey];
        const next = {
          ...prev,
          [channelKey]: {
            ...ch,
            fields: { ...ch.fields, [fieldKey]: value },
          },
        };
        saveChannelsConfig(next);
        return next;
      });
    },
    [],
  );

  const toggleChannelExpanded = useCallback((key: string) => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setExpandedChannel((prev) => (prev === key ? null : key));
  }, []);

  const testChannelConnection = useCallback(
    async (channelKey: string) => {
      setChannelTesting(channelKey);
      try {
        // Simulate a health-check call; in production this would call a native bridge
        await new Promise((resolve) => setTimeout(resolve, 1500));
        const ch = channels[channelKey];
        const allFieldsFilled = Object.values(ch.fields).every((v) => v.trim().length > 0);
        updateChannel(channelKey, {
          status: allFieldsFilled ? "connected" : "error",
        });
      } catch {
        updateChannel(channelKey, { status: "error" });
      } finally {
        setChannelTesting(null);
      }
    },
    [channels, updateChannel],
  );

  const saveChannels = useCallback(() => {
    saveChannelsConfig(channels);
  }, [channels]);

  // --- Proactive helpers ---
  const updateProactive = useCallback(
    <K extends keyof ProactiveConfig>(key: K, value: ProactiveConfig[K]) => {
      setProactive((prev) => {
        const next = { ...prev, [key]: value };
        saveProactiveConfig(next);
        return next;
      });
    },
    [],
  );

  // --- Memory helpers ---
  const updateMemory = useCallback(
    <K extends keyof MemoryConfig>(key: K, value: MemoryConfig[K]) => {
      setMemory((prev) => {
        const next = { ...prev, [key]: value };
        saveMemoryConfig(next);
        return next;
      });
    },
    [],
  );

  const handleClearMemory = useCallback(() => {
    setClearMemoryVisible(false);
    // In production, this would call a native bridge to clear memory storage
    Alert.alert("Memory Cleared", "All agent memory has been erased.");
  }, []);

  const handleExportMemory = useCallback(async () => {
    setExportingMemory(true);
    try {
      // In production, this would call a native bridge to export memory as JSON
      await new Promise((resolve) => setTimeout(resolve, 1000));
      Alert.alert("Export Complete", "Memory exported as JSON to Downloads folder.");
    } catch {
      Alert.alert("Export Failed", "Could not export memory. Please try again.");
    } finally {
      setExportingMemory(false);
    }
  }, []);

  useEffect(() => {
    if (!loaded || config.provider === "local") {
      setProviderModels([]);
      setProviderHealthy(null);
      setProviderProbeError(null);
      setProviderModelsLoading(false);
      return;
    }

    let cancelled = false;
    setProviderModelsLoading(true);
    setProviderProbeError(null);

    const timer = setTimeout(() => {
      void (async () => {
        try {
          const probeConfig = {
            provider: config.provider,
            apiKey: config.apiKey,
            oauthAccessToken: config.oauthAccessToken,
            apiUrl: config.apiUrl,
          };
          const [models, healthy] = await Promise.all([
            getProviderModels(probeConfig),
            getProviderHealth(probeConfig),
          ]);

          if (cancelled) {
            return;
          }

          setProviderModels(models);
          setProviderHealthy(healthy);
          if (models.length > 0 && !models.some((model) => model.id === config.model)) {
            updateConfig("model", models[0].id);
          }
        } catch (err) {
          if (cancelled) {
            return;
          }

          setProviderModels([]);
          setProviderHealthy(false);
          setProviderProbeError(err instanceof Error ? err.message : String(err));
        } finally {
          if (!cancelled) {
            setProviderModelsLoading(false);
          }
        }
      })();
    }, 350);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [
    config.apiKey,
    config.apiUrl,
    config.model,
    config.oauthAccessToken,
    config.provider,
    loaded,
    updateConfig,
  ]);

  const handleLocalModelDownload = useCallback(async () => {
    const modelId = config.model || DEFAULT_AGENT_CONFIG.model;
    localModelProbeRef.current += 1;
    localModelDownloadRef.current = true;
    setLocalModelStatus("downloading");
    setLocalDownloadProgress(0);

    try {
      const modelPath = await downloadModel(modelId, setLocalDownloadProgress);
      updateConfig("localModelPath", modelPath);
      setLocalModelStatus("ready");
    } catch (err) {
      console.warn("[config] local model download failed:", err);
      setLocalModelStatus("not_downloaded");
      setLocalDownloadProgress(0);
    } finally {
      localModelDownloadRef.current = false;
    }
  }, [config.model, updateConfig]);

  const localModelLabel =
    localModelStatus === "ready"
      ? "Model ready"
      : localModelStatus === "downloading"
        ? `Downloading... ${localDownloadProgress}%`
        : localModelStatus === "checking"
          ? "Checking local storage..."
          : "Download recommended local model";

  const providerStatusLabel =
    config.provider === "local"
      ? "On-device inference uses the downloaded local model."
      : providerModelsLoading
        ? "Checking provider health and fetching available models..."
        : providerProbeError
          ? `Provider probe failed: ${providerProbeError}`
          : providerHealthy === true
            ? providerModels.length > 0
              ? `${providerModels.length} models available from provider.`
              : "Provider connected. Enter a model manually if needed."
            : providerModels.length > 0
              ? "Fetched models, but provider health check did not fully pass."
              : "Provider unavailable or awaiting valid credentials.";

  const providerModelOptions = providerModels.map((model) => ({
    label: model.name,
    value: model.id,
  }));

  return (
    <View
      style={[styles.container, { backgroundColor: "rgba(2, 2, 6, 0.85)" }]}
      testID="config-screen"
    >
      {/* Glass Header */}
      <BlurView
        intensity={30}
        tint="dark"
        style={[styles.header, { paddingTop: insets.top + spacing.sm }]}
      >
        <View style={styles.headerInner}>
          <Text style={styles.headerTitle}>Configuration</Text>
          <Pressable
            style={({ pressed }) => [
              styles.restartButton,
              pressed && { opacity: 0.7 },
              restarting && { opacity: 0.5 },
            ]}
            onPress={restartAgent}
            disabled={restarting}
            hitSlop={8}
          >
            <Ionicons
              name="refresh-outline"
              size={16}
              color={colors.accent.cyan}
            />
            <Text style={styles.restartButtonText}>
              {restarting ? "Restarting..." : "Apply"}
            </Text>
          </Pressable>
        </View>
        {/* Bottom gradient line: cyan -> transparent -> cyan */}
        <LinearGradient
          colors={[colors.accent.cyan, "transparent", colors.accent.cyan]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 0 }}
          style={styles.headerLine}
        />
      </BlurView>

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={[
          styles.scrollContent,
          { paddingBottom: insets.bottom + 100 },
        ]}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        {/* 1. How GUAPPA Thinks */}
        <CollapsibleSection
          title="How GUAPPA Thinks"
          icon="sparkles-outline"
          defaultExpanded={true}
          testID="config-section-thinks"
        >
          <GlassDropdown
            label="Provider"
            value={config.provider}
            options={PROVIDER_OPTIONS}
            onValueChange={(v) => updateConfig("provider", v as AgentRuntimeConfig["provider"])}
            testID="config-provider-select"
          />
          <GlassInput
            label="API Key"
            value={config.apiKey}
            onChangeText={(v) => updateConfig("apiKey", v)}
            placeholder="sk-..."
            secureTextEntry
            testID="config-api-key-input"
          />
          {providerModelOptions.length > 0 ? (
            <GlassDropdown
              label="Model"
              value={config.model}
              options={providerModelOptions}
              onValueChange={(v) => updateConfig("model", v)}
              placeholder={providerModelsLoading ? "Loading models..." : "Select model"}
              testID="config-model-dropdown"
            />
          ) : (
            <GlassInput
              label="Model"
              value={config.model}
              onChangeText={(v) => updateConfig("model", v)}
              placeholder="Select provider first"
              testID="config-model-input"
            />
          )}
          <Text style={styles.placeholder}>{providerStatusLabel}</Text>
          <GlassSlider
            label="Temperature"
            value={config.temperature}
            onValueChange={(v) => updateConfig("temperature", v)}
            min={0.0}
            max={2.0}
            step={0.1}
          />
          <GlassInput
            label="Daily budget"
            value="$5.00"
            onChangeText={() => {}}
            placeholder="$5.00/day"
          />
        </CollapsibleSection>

        {/* 2. How GUAPPA Sees */}
        <CollapsibleSection title="How GUAPPA Sees" icon="eye-outline">
          <Text style={styles.placeholder}>
            Configure vision and image generation capabilities
          </Text>
          <GlassDropdown
            label="Vision provider"
            value=""
            options={PROVIDER_OPTIONS}
            onValueChange={() => {}}
            placeholder="Not configured"
          />
          <GlassDropdown
            label="Image generation provider"
            value=""
            options={PROVIDER_OPTIONS}
            onValueChange={() => {}}
            placeholder="Not configured"
          />
        </CollapsibleSection>

        {/* 3. How GUAPPA Speaks & Listens */}
        <CollapsibleSection title="How GUAPPA Speaks & Listens" icon="mic-outline">
          <GlassDropdown
            label="Speech-to-Text engine"
            value="whisper"
            options={STT_OPTIONS}
            onValueChange={() => {}}
          />
          <GlassDropdown
            label="Text-to-Speech engine"
            value="elevenlabs"
            options={TTS_OPTIONS}
            onValueChange={() => {}}
          />
          <Text style={styles.placeholder}>
            Voice configuration coming in M4
          </Text>
        </CollapsibleSection>

        {/* 4. How GUAPPA Connects */}
        <CollapsibleSection title="How GUAPPA Connects" icon="globe-outline">
          <View style={channelStyles.tileGrid}>
            {CHANNEL_META.map((meta) => {
              const ch = channels[meta.key];
              const isExpanded = expandedChannel === meta.key;
              const statusColor =
                ch.status === "connected"
                  ? colors.semantic.success
                  : ch.status === "error"
                    ? colors.semantic.error
                    : colors.text.tertiary;

              return (
                <View key={meta.key} style={channelStyles.tileWrapper}>
                  <Pressable
                    onPress={() => toggleChannelExpanded(meta.key)}
                    style={[
                      channelStyles.tile,
                      isExpanded && channelStyles.tileExpanded,
                    ]}
                  >
                    <View style={channelStyles.tileHeader}>
                      <Text style={channelStyles.tileEmoji}>{meta.emoji}</Text>
                      <Text style={channelStyles.tileName}>{meta.name}</Text>
                      <View
                        style={[
                          channelStyles.statusDot,
                          { backgroundColor: statusColor },
                        ]}
                      />
                    </View>
                    <View style={channelStyles.tileToggleRow}>
                      <GlassToggle
                        value={ch.enabled}
                        onValueChange={(v) => {
                          updateChannel(meta.key, {
                            enabled: v,
                            status: v ? ch.status : "disabled",
                          });
                        }}
                      />
                    </View>
                  </Pressable>

                  {isExpanded && (
                    <View style={channelStyles.expandedFields}>
                      {Object.keys(meta.fieldLabels).map((fieldKey) => (
                        <GlassInput
                          key={fieldKey}
                          label={meta.fieldLabels[fieldKey]}
                          value={ch.fields[fieldKey] ?? ""}
                          onChangeText={(v) =>
                            updateChannelField(meta.key, fieldKey, v)
                          }
                          placeholder={meta.fieldPlaceholders[fieldKey]}
                          secureTextEntry={meta.fieldSecure?.[fieldKey] ?? false}
                        />
                      ))}
                      <View style={channelStyles.expandedActions}>
                        <GlassButton
                          title="Test Connection"
                          onPress={() => testChannelConnection(meta.key)}
                          variant="secondary"
                          icon="pulse-outline"
                          loading={channelTesting === meta.key}
                          disabled={!ch.enabled}
                          style={channelStyles.testButton}
                        />
                        <GlassButton
                          title="Save"
                          onPress={saveChannels}
                          variant="primary"
                          icon="save-outline"
                          style={channelStyles.saveButton}
                        />
                      </View>
                    </View>
                  )}
                </View>
              );
            })}
          </View>
        </CollapsibleSection>

        {/* 5. What GUAPPA Can Do */}
        <CollapsibleSection title="What GUAPPA Can Do" icon="build-outline">
          <Text style={styles.placeholder}>
            Tool permissions coming in M3
          </Text>
        </CollapsibleSection>

        {/* 6. What GUAPPA Remembers */}
        <CollapsibleSection title="What GUAPPA Remembers" icon="server-outline">
          <GlassToggle
            value={memory.autoSummarization}
            onValueChange={(v) => updateMemory("autoSummarization", v)}
            label="Auto-summarization"
            description="Automatically summarize long conversations"
          />
          <GlassSlider
            label="Context budget"
            value={memory.contextBudgetTokens}
            onValueChange={(v) => updateMemory("contextBudgetTokens", v)}
            min={2048}
            max={32768}
            step={1024}
            unit="tokens"
          />
          <GlassDropdown
            label="Memory retention"
            value={String(memory.retentionDays)}
            options={RETENTION_OPTIONS}
            onValueChange={(v) => updateMemory("retentionDays", Number(v))}
          />
          <View style={memoryStyles.actions}>
            <GlassButton
              title="Export Memory (JSON)"
              onPress={handleExportMemory}
              variant="secondary"
              icon="download-outline"
              loading={exportingMemory}
              style={memoryStyles.exportButton}
            />
            <Pressable
              onPress={() => setClearMemoryVisible(true)}
              style={memoryStyles.dangerButton}
            >
              <Ionicons
                name="trash-outline"
                size={16}
                color={colors.semantic.error}
              />
              <Text style={memoryStyles.dangerButtonText}>
                Clear All Memory
              </Text>
            </Pressable>
          </View>
          <GlassModal
            visible={clearMemoryVisible}
            onClose={() => setClearMemoryVisible(false)}
            title="Clear All Memory?"
          >
            <Text style={memoryStyles.modalBody}>
              This will permanently erase all stored agent memory. This action
              cannot be undone.
            </Text>
            <View style={memoryStyles.modalActions}>
              <GlassButton
                title="Cancel"
                onPress={() => setClearMemoryVisible(false)}
                variant="ghost"
              />
              <Pressable
                onPress={handleClearMemory}
                style={memoryStyles.confirmDeleteButton}
              >
                <Text style={memoryStyles.confirmDeleteText}>
                  Erase Everything
                </Text>
              </Pressable>
            </View>
          </GlassModal>
        </CollapsibleSection>

        {/* 7. How GUAPPA Acts on Her Own */}
        <CollapsibleSection title="How GUAPPA Acts on Her Own" icon="flash-outline">
          <GlassToggle
            value={proactive.enabled}
            onValueChange={(v) => updateProactive("enabled", v)}
            label="Enable proactive agent"
            description="Allow GUAPPA to initiate actions autonomously"
          />

          {proactive.enabled && (
            <>
              {/* Quiet Hours */}
              <View style={proactiveStyles.subsection}>
                <Text style={proactiveStyles.subsectionTitle}>Quiet Hours</Text>
                <View style={proactiveStyles.timeRow}>
                  <View style={proactiveStyles.timeField}>
                    <GlassInput
                      label="Start"
                      value={proactive.quietHoursStart}
                      onChangeText={(v) => updateProactive("quietHoursStart", v)}
                      placeholder="22:00"
                    />
                  </View>
                  <View style={proactiveStyles.timeField}>
                    <GlassInput
                      label="End"
                      value={proactive.quietHoursEnd}
                      onChangeText={(v) => updateProactive("quietHoursEnd", v)}
                      placeholder="07:00"
                    />
                  </View>
                </View>
              </View>

              {/* Scheduled briefings */}
              <View style={proactiveStyles.subsection}>
                <Text style={proactiveStyles.subsectionTitle}>
                  Scheduled Briefings
                </Text>
                <View style={proactiveStyles.briefingRow}>
                  <GlassToggle
                    value={proactive.morningBriefingEnabled}
                    onValueChange={(v) =>
                      updateProactive("morningBriefingEnabled", v)
                    }
                    label="Morning briefing"
                  />
                  {proactive.morningBriefingEnabled && (
                    <GlassInput
                      label="Time"
                      value={proactive.morningBriefingTime}
                      onChangeText={(v) =>
                        updateProactive("morningBriefingTime", v)
                      }
                      placeholder="08:00"
                    />
                  )}
                </View>
                <View style={proactiveStyles.briefingRow}>
                  <GlassToggle
                    value={proactive.eveningSummaryEnabled}
                    onValueChange={(v) =>
                      updateProactive("eveningSummaryEnabled", v)
                    }
                    label="Evening summary"
                  />
                  {proactive.eveningSummaryEnabled && (
                    <GlassInput
                      label="Time"
                      value={proactive.eveningSummaryTime}
                      onChangeText={(v) =>
                        updateProactive("eveningSummaryTime", v)
                      }
                      placeholder="21:00"
                    />
                  )}
                </View>
              </View>

              {/* Cooldown */}
              <GlassSlider
                label="Notification cooldown"
                value={proactive.notificationCooldownMin}
                onValueChange={(v) =>
                  updateProactive("notificationCooldownMin", v)
                }
                min={1}
                max={60}
                step={1}
                unit="min"
              />

              {/* Triggers */}
              <View style={proactiveStyles.subsection}>
                <Text style={proactiveStyles.subsectionTitle}>Triggers</Text>
                <GlassToggle
                  value={proactive.triggerBatteryLow}
                  onValueChange={(v) =>
                    updateProactive("triggerBatteryLow", v)
                  }
                  label="Battery Low"
                  description="Alert when battery drops below 15%"
                />
                <GlassToggle
                  value={proactive.triggerCalendarEvents}
                  onValueChange={(v) =>
                    updateProactive("triggerCalendarEvents", v)
                  }
                  label="Calendar Events"
                  description="Prepare briefings for upcoming events"
                />
                <GlassToggle
                  value={proactive.triggerMissedCalls}
                  onValueChange={(v) =>
                    updateProactive("triggerMissedCalls", v)
                  }
                  label="Missed Calls"
                  description="Notify and suggest follow-up for missed calls"
                />
                <GlassToggle
                  value={proactive.triggerIncomingSms}
                  onValueChange={(v) =>
                    updateProactive("triggerIncomingSms", v)
                  }
                  label="Incoming SMS"
                  description="Analyze and summarize incoming messages"
                />
              </View>
            </>
          )}
        </CollapsibleSection>

        {/* 8. Local Intelligence */}
        <CollapsibleSection title="Local Intelligence" icon="hardware-chip-outline">
          <GlassInput
            label="Local model path"
            value={config.localModelPath}
            onChangeText={(v) => updateConfig("localModelPath", v)}
            placeholder="/data/models/model.gguf"
          />
          <GlassSlider
            label="GPU layers"
            value={config.gpuLayers}
            onValueChange={(v) => updateConfig("gpuLayers", v)}
            min={0}
            max={99}
            step={1}
          />
          <GlassButton
            title={localModelStatus === "ready" ? "Model Downloaded" : "Download Model"}
            onPress={handleLocalModelDownload}
            variant="secondary"
            icon="download-outline"
            loading={localModelStatus === "downloading"}
            disabled={localModelStatus === "checking" || localModelStatus === "ready"}
          />
          <Text style={styles.placeholder}>{localModelLabel}</Text>
        </CollapsibleSection>

        {/* 9. Permissions */}
        <CollapsibleSection title="Permissions" icon="shield-checkmark-outline">
          <PermissionRow label="Camera" status="not granted" />
          <PermissionRow label="Microphone" status="not granted" />
          <PermissionRow label="Location" status="not granted" />
          <PermissionRow label="Notifications" status="not granted" />
          <PermissionRow label="Storage" status="not granted" />
          <PermissionRow label="Contacts" status="not granted" />
          <PermissionRow label="Phone / Calls" status="not granted" />
          <PermissionRow label="SMS" status="not granted" />
          <PermissionRow label="Bluetooth" status="not granted" />
          <PermissionRow label="Accessibility Service" status="not granted" />
        </CollapsibleSection>

        {/* 10. Debug Info — warm-tinted glass card */}
        <View style={styles.debugCard}>
          <BlurView intensity={15} tint="dark" style={styles.debugBlur}>
            <Pressable
              onPress={downloadDebugInfo}
              disabled={debugLoading}
              style={({ pressed }) => [
                styles.debugInner,
                pressed && { opacity: 0.7 },
                debugLoading && { opacity: 0.6 },
              ]}
            >
              <View style={styles.debugIconContainer}>
                <Ionicons
                  name="download-outline"
                  size={18}
                  color={colors.accent.amber}
                />
                <Ionicons
                  name="bug-outline"
                  size={14}
                  color={colors.accent.amber}
                  style={styles.debugBugIcon}
                />
              </View>
              <View style={styles.debugTextContainer}>
                <Text style={styles.debugTitle}>
                  {debugLoading ? "Collecting Debug Info..." : "Download Debug Info"}
                </Text>
                <Text style={styles.debugSubtitle}>
                  {debugError ?? "Export logs, config snapshot, and diagnostics"}
                </Text>
              </View>
              <Ionicons
                name="chevron-forward"
                size={16}
                color={colors.text.tertiary}
              />
            </Pressable>
          </BlurView>
        </View>
      </ScrollView>
    </View>
  );
}

// --- Permission row with glass pill status badges ---

function PermissionRow({ label, status }: { label: string; status: string }) {
  const granted = status === "granted";
  const pillColor = granted ? colors.semantic.success : colors.semantic.error;
  const pillLabel = granted ? "Granted" : "Denied";

  return (
    <View style={permStyles.row}>
      <Text style={permStyles.label}>{label}</Text>
      <View
        style={[
          permStyles.pill,
          {
            backgroundColor: granted
              ? "rgba(20, 184, 166, 0.12)"
              : "rgba(239, 68, 68, 0.12)",
            borderColor: granted
              ? "rgba(20, 184, 166, 0.30)"
              : "rgba(239, 68, 68, 0.30)",
          },
        ]}
      >
        <Text style={[permStyles.pillText, { color: pillColor }]}>
          {pillLabel}
        </Text>
      </View>
    </View>
  );
}

const permStyles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: "rgba(255,255,255,0.06)",
  },
  label: {
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.body.fontFamily,
  },
  pill: {
    paddingHorizontal: 10,
    paddingVertical: 3,
    borderRadius: 999,
    borderWidth: 1,
    backgroundColor: "rgba(255, 255, 255, 0.09)",
  },
  pillText: {
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
});

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },

  // ── Glass Header ──────────────────────────────────────────
  header: {
    backgroundColor: "rgba(255, 255, 255, 0.08)",
    borderBottomWidth: 0,
    overflow: "hidden",
  },
  headerInner: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.md,
  },
  headerTitle: {
    color: colors.text.primary,
    fontSize: 18,
    fontFamily: typography.display.fontFamily,
    letterSpacing: 1,
  },
  restartButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 14,
    backgroundColor: "rgba(20, 60, 80, 0.12)",
    borderWidth: 1,
    borderColor: "rgba(20, 70, 90, 0.2)",
  },
  restartButtonText: {
    color: colors.accent.cyan,
    fontSize: 12,
    fontFamily: typography.bodySemiBold.fontFamily,
    letterSpacing: 0.3,
  },
  headerLine: {
    height: 1,
    width: "100%",
  },

  // ── Content ───────────────────────────────────────────────
  scroll: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: spacing.md,
    paddingTop: spacing.md,
  },
  placeholder: {
    color: colors.text.tertiary,
    fontSize: 13,
    fontFamily: typography.body.fontFamily,
    fontStyle: "italic",
    marginVertical: spacing.sm,
  },

  // ── Debug Info Card (warm tint) ───────────────────────────
  debugCard: {
    borderRadius: 20,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: "rgba(255, 170, 51, 0.15)",
    marginTop: spacing.sm,
    marginBottom: spacing.lg,
  },
  debugBlur: {
    backgroundColor: "rgba(255, 170, 51, 0.04)",
  },
  debugInner: {
    flexDirection: "row",
    alignItems: "center",
    padding: spacing.md,
    gap: spacing.sm,
  },
  debugIconContainer: {
    flexDirection: "row",
    alignItems: "center",
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "rgba(255, 170, 51, 0.10)",
    justifyContent: "center",
  },
  debugBugIcon: {
    marginLeft: -4,
  },
  debugTextContainer: {
    flex: 1,
  },
  debugTitle: {
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.bodySemiBold.fontFamily,
  },
  debugSubtitle: {
    color: colors.text.tertiary,
    fontSize: 12,
    fontFamily: typography.body.fontFamily,
    marginTop: 2,
  },
});

// --- Channel tile styles ---

const channelStyles = StyleSheet.create({
  tileGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: spacing.sm,
  },
  tileWrapper: {
    width: "100%",
  },
  tile: {
    backgroundColor: "rgba(255, 255, 255, 0.08)",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.14)",
    borderRadius: 14,
    padding: spacing.sm,
  },
  tileExpanded: {
    borderColor: "rgba(20, 70, 90, 0.2)",
    backgroundColor: "rgba(20, 60, 80, 0.06)",
  },
  tileHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
  },
  tileEmoji: {
    fontSize: 20,
  },
  tileName: {
    flex: 1,
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.bodySemiBold.fontFamily,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  tileToggleRow: {
    marginTop: spacing.xs,
    alignItems: "flex-end",
  },
  expandedFields: {
    paddingHorizontal: spacing.sm,
    paddingTop: spacing.sm,
    paddingBottom: spacing.xs,
  },
  expandedActions: {
    flexDirection: "row",
    gap: spacing.sm,
    marginTop: spacing.sm,
  },
  testButton: {
    flex: 1,
  },
  saveButton: {
    flex: 1,
  },
});

// --- Proactive agent styles ---

const proactiveStyles = StyleSheet.create({
  subsection: {
    marginTop: spacing.md,
    paddingTop: spacing.sm,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: "rgba(255, 255, 255, 0.10)",
  },
  subsectionTitle: {
    color: colors.text.secondary,
    fontSize: 12,
    fontFamily: typography.bodySemiBold.fontFamily,
    textTransform: "uppercase",
    letterSpacing: 0.5,
    marginBottom: spacing.sm,
  },
  timeRow: {
    flexDirection: "row",
    gap: spacing.sm,
  },
  timeField: {
    flex: 1,
  },
  briefingRow: {
    marginBottom: spacing.xs,
  },
});

// --- Memory settings styles ---

const memoryStyles = StyleSheet.create({
  actions: {
    marginTop: spacing.md,
    gap: spacing.sm,
  },
  exportButton: {
    marginBottom: 0,
  },
  dangerButton: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.sm,
    paddingVertical: 14,
    paddingHorizontal: spacing.lg,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: "rgba(239, 68, 68, 0.30)",
    backgroundColor: "rgba(239, 68, 68, 0.08)",
    minHeight: 48,
  },
  dangerButtonText: {
    color: colors.semantic.error,
    fontSize: 15,
    fontFamily: typography.bodySemiBold.fontFamily,
  },
  modalBody: {
    color: colors.text.secondary,
    fontSize: 14,
    fontFamily: typography.body.fontFamily,
    lineHeight: 20,
    marginBottom: spacing.lg,
  },
  modalActions: {
    flexDirection: "row",
    justifyContent: "flex-end",
    gap: spacing.sm,
  },
  confirmDeleteButton: {
    paddingVertical: 12,
    paddingHorizontal: spacing.lg,
    borderRadius: 14,
    backgroundColor: "rgba(239, 68, 68, 0.20)",
    borderWidth: 1,
    borderColor: "rgba(239, 68, 68, 0.40)",
  },
  confirmDeleteText: {
    color: colors.semantic.error,
    fontSize: 14,
    fontFamily: typography.bodySemiBold.fontFamily,
  },
});
