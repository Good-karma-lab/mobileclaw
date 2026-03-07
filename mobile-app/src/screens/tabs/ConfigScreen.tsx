import React, { useState, useEffect, useCallback, useRef } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet } from "react-native";
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
} from "../../components/glass";
import {
  loadAgentConfig,
  saveAgentConfig,
  loadIntegrationsConfig,
  DEFAULT_AGENT_CONFIG,
  type AgentRuntimeConfig,
} from "../../state/guappa";
import { startAgent } from "../../native/guappaAgent";
import { startLocalLlmServer, LOCAL_LLM_URL } from "../../native/localLlmServer";

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

const SAVE_DEBOUNCE_MS = 500;

export function ConfigScreen() {
  const insets = useSafeAreaInsets();
  const [config, setConfig] = useState<AgentRuntimeConfig>(DEFAULT_AGENT_CONFIG);
  const [loaded, setLoaded] = useState(false);
  const [restarting, setRestarting] = useState(false);
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Load config on mount
  useEffect(() => {
    loadAgentConfig().then((cfg) => {
      setConfig(cfg);
      setLoaded(true);
    });
  }, []);

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

  return (
    <LinearGradient
      colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
      style={styles.container}
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
          icon="brain-outline"
          defaultExpanded={true}
        >
          <GlassDropdown
            label="Provider"
            value={config.provider}
            options={PROVIDER_OPTIONS}
            onValueChange={(v) => updateConfig("provider", v as AgentRuntimeConfig["provider"])}
          />
          <GlassInput
            label="API Key"
            value={config.apiKey}
            onChangeText={(v) => updateConfig("apiKey", v)}
            placeholder="sk-..."
            secureTextEntry
          />
          <GlassInput
            label="Model"
            value={config.model}
            onChangeText={(v) => updateConfig("model", v)}
            placeholder="Select provider first"
          />
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
          <Text style={styles.placeholder}>
            Channel configuration coming in M7
          </Text>
        </CollapsibleSection>

        {/* 5. What GUAPPA Can Do */}
        <CollapsibleSection title="What GUAPPA Can Do" icon="build-outline">
          <Text style={styles.placeholder}>
            Tool permissions coming in M3
          </Text>
        </CollapsibleSection>

        {/* 6. What GUAPPA Remembers */}
        <CollapsibleSection title="What GUAPPA Remembers" icon="server-outline">
          <Text style={styles.placeholder}>
            Memory configuration coming in M5
          </Text>
        </CollapsibleSection>

        {/* 7. How GUAPPA Acts on Her Own */}
        <CollapsibleSection title="How GUAPPA Acts on Her Own" icon="flash-outline">
          <Text style={styles.placeholder}>
            Proactive agent configuration coming in M6
          </Text>
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
            title="Download Model"
            onPress={() => console.log("Download model placeholder")}
            variant="secondary"
            icon="download-outline"
          />
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
              onPress={() => console.log("Download debug info placeholder")}
              style={({ pressed }) => [
                styles.debugInner,
                pressed && { opacity: 0.7 },
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
                <Text style={styles.debugTitle}>Download Debug Info</Text>
                <Text style={styles.debugSubtitle}>
                  Export logs, config snapshot, and diagnostics
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
    </LinearGradient>
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
    backgroundColor: "rgba(255,255,255,0.05)",
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
    backgroundColor: "rgba(255, 255, 255, 0.04)",
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
    backgroundColor: "rgba(0, 240, 255, 0.08)",
    borderWidth: 1,
    borderColor: "rgba(0, 240, 255, 0.20)",
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
