import React, { useState, useEffect, useCallback, useRef } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
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
  DEFAULT_AGENT_CONFIG,
  type AgentRuntimeConfig,
} from "../../state/guappa";

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
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={[
          styles.scrollContent,
          { paddingTop: insets.top + spacing.md, paddingBottom: insets.bottom + 100 },
        ]}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        <Text style={styles.screenTitle}>Configuration</Text>

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

        {/* 10. Download Debug Info */}
        <View style={styles.debugButtonWrapper}>
          <GlassButton
            title="Download Debug Info"
            onPress={() => console.log("Download debug info placeholder")}
            variant="secondary"
            icon="download-outline"
          />
        </View>
      </ScrollView>
    </LinearGradient>
  );
}

// --- Small helper component for permission rows ---

function PermissionRow({ label, status }: { label: string; status: string }) {
  const granted = status === "granted";
  return (
    <View style={permStyles.row}>
      <View style={permStyles.labelRow}>
        <View
          style={[
            permStyles.dot,
            { backgroundColor: granted ? colors.semantic.success : colors.text.tertiary },
          ]}
        />
        <Text style={permStyles.label}>{label}</Text>
      </View>
      <Text
        style={[
          permStyles.status,
          { color: granted ? colors.semantic.success : colors.text.tertiary },
        ]}
      >
        {status}
      </Text>
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
  labelRow: {
    flexDirection: "row",
    alignItems: "center",
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: spacing.sm,
  },
  label: {
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.body.fontFamily,
  },
  status: {
    fontSize: 12,
    fontFamily: typography.mono.fontFamily,
    textTransform: "uppercase",
  },
});

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scroll: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: spacing.md,
  },
  screenTitle: {
    color: colors.text.primary,
    fontSize: 28,
    fontFamily: typography.display.fontFamily,
    marginBottom: spacing.lg,
  },
  placeholder: {
    color: colors.text.tertiary,
    fontSize: 13,
    fontFamily: typography.body.fontFamily,
    fontStyle: "italic",
    marginVertical: spacing.sm,
  },
  debugButtonWrapper: {
    marginTop: spacing.sm,
    marginBottom: spacing.lg,
  },
});
