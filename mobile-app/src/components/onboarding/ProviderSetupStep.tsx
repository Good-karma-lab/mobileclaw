import React, { useState, useCallback } from "react";
import { View, Text, Pressable, StyleSheet, ScrollView } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { GlassCard, GlassButton, GlassDropdown, GlassInput } from "../glass";
import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";
import {
  saveAgentConfig,
  DEFAULT_AGENT_CONFIG,
  type ProviderId,
} from "../../state/guappa";

type Props = {
  onNext: () => void;
  onSkip: () => void;
};

type SetupMode = null | "cloud" | "local";

const PROVIDER_OPTIONS = [
  { label: "OpenRouter", value: "openrouter" },
  { label: "OpenAI", value: "openai" },
  { label: "Anthropic", value: "anthropic" },
  { label: "Google Gemini", value: "gemini" },
  { label: "Groq", value: "groq" },
  { label: "DeepSeek", value: "deepseek" },
];

export function ProviderSetupStep({ onNext, onSkip }: Props) {
  const [mode, setMode] = useState<SetupMode>(null);
  const [provider, setProvider] = useState("openrouter");
  const [apiKey, setApiKey] = useState("");
  const [saving, setSaving] = useState(false);

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      if (mode === "cloud") {
        await saveAgentConfig({
          ...DEFAULT_AGENT_CONFIG,
          provider: provider as ProviderId,
          apiKey,
        });
      } else {
        await saveAgentConfig({
          ...DEFAULT_AGENT_CONFIG,
          provider: "local",
        });
      }
      onNext();
    } finally {
      setSaving(false);
    }
  }, [mode, provider, apiKey, onNext]);

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.container}
      keyboardShouldPersistTaps="handled"
    >
      <Text style={styles.heading}>Choose how GUAPPA thinks</Text>

      <GlassCard
        onPress={() => setMode("cloud")}
        style={mode === "cloud" ? styles.cardSelected : undefined}
      >
        <View style={styles.cardHeader}>
          <Ionicons
            name="cloud-outline"
            size={28}
            color={colors.accent.cyan}
            style={styles.cardIcon}
          />
          <View style={styles.cardTextWrap}>
            <Text style={styles.cardTitle}>Cloud AI</Text>
            <Text style={styles.cardDesc}>
              Fast, powerful, requires API key
            </Text>
          </View>
        </View>
      </GlassCard>

      {mode === "cloud" && (
        <View style={styles.expandedSection}>
          <GlassDropdown
            label="Provider"
            value={provider}
            options={PROVIDER_OPTIONS}
            onValueChange={setProvider}
            placeholder="Select provider"
          />
          <GlassInput
            label="API Key"
            value={apiKey}
            onChangeText={setApiKey}
            placeholder="sk-..."
            secureTextEntry
            testID="wizard-api-key-input"
          />
          <GlassButton
            title="Continue"
            onPress={handleSave}
            loading={saving}
            disabled={!apiKey.trim()}
            icon="checkmark-circle-outline"
          />
        </View>
      )}

      <GlassCard
        onPress={() => setMode("local")}
        style={mode === "local" ? styles.cardSelected : undefined}
      >
        <View style={styles.cardHeader}>
          <Ionicons
            name="phone-portrait-outline"
            size={28}
            color={colors.accent.violet}
            style={styles.cardIcon}
          />
          <View style={styles.cardTextWrap}>
            <Text style={styles.cardTitle}>On-Device AI</Text>
            <Text style={styles.cardDesc}>
              Free, private, runs locally
            </Text>
          </View>
        </View>
      </GlassCard>

      {mode === "local" && (
        <View style={styles.expandedSection}>
          <GlassCard>
            <Text style={styles.modelName}>Qwen3.5 0.8B</Text>
            <Text style={styles.modelInfo}>
              Compact model optimized for on-device inference. Supports thinking
              mode for step-by-step reasoning.
            </Text>
          </GlassCard>
          <GlassButton
            title="Continue"
            onPress={handleSave}
            loading={saving}
            icon="checkmark-circle-outline"
          />
        </View>
      )}

      <Pressable onPress={onSkip} style={styles.skipButton} testID="onboarding-skip">
        <Text style={styles.skipText}>Skip</Text>
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {
    flex: 1,
  },
  container: {
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.xxl,
    gap: spacing.md,
  },
  heading: {
    fontFamily: typography.display.fontFamily,
    fontSize: 22,
    color: colors.text.primary,
    textAlign: "center",
    marginBottom: spacing.md,
  },
  cardSelected: {
    borderColor: colors.accent.cyan,
  },
  cardHeader: {
    flexDirection: "row",
    alignItems: "center",
  },
  cardIcon: {
    marginRight: spacing.md,
  },
  cardTextWrap: {
    flex: 1,
  },
  cardTitle: {
    fontFamily: typography.bodySemiBold.fontFamily,
    fontSize: 17,
    color: colors.text.primary,
    marginBottom: 2,
  },
  cardDesc: {
    fontFamily: typography.body.fontFamily,
    fontSize: 14,
    color: colors.text.secondary,
  },
  expandedSection: {
    gap: spacing.md,
    paddingLeft: spacing.xs,
    paddingRight: spacing.xs,
  },
  modelName: {
    fontFamily: typography.bodySemiBold.fontFamily,
    fontSize: 16,
    color: colors.accent.violet,
    marginBottom: spacing.xs,
  },
  modelInfo: {
    fontFamily: typography.body.fontFamily,
    fontSize: 14,
    color: colors.text.secondary,
    lineHeight: 20,
  },
  skipButton: {
    alignSelf: "center",
    paddingVertical: spacing.md,
  },
  skipText: {
    fontFamily: typography.bodyMedium.fontFamily,
    fontSize: 15,
    color: colors.text.tertiary,
  },
});
