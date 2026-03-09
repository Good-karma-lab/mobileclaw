import React, { useState, useCallback, useEffect, useRef } from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { GlassCard, GlassButton } from "../glass";
import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";
import { checkModelExists, downloadModel } from "../../native/modelDownloader";
import { DEFAULT_AGENT_CONFIG, loadAgentConfig, saveAgentConfig } from "../../state/guappa";
import { isModelDownloaded as isWhisperDownloaded, downloadModel as downloadWhisperModel } from "../../voice/whisperModelManager";

type Props = {
  onNext: () => void;
  onSkip: () => void;
};

const RECOMMENDED_MODEL = {
  name: "Qwen3.5 0.8B (Q4_K_M)",
  size: "~530 MB",
  description:
    "A compact yet capable language model optimized for mobile devices. Supports thinking mode for step-by-step reasoning.",
};

export function ModelDownloadStep({ onNext, onSkip }: Props) {
  const [downloading, setDownloading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const mountedRef = useRef(true);

  // Voice model (Whisper) state
  const [whisperDownloading, setWhisperDownloading] = useState(false);
  const [whisperProgress, setWhisperProgress] = useState(0);
  const [whisperDone, setWhisperDone] = useState(false);
  const [whisperError, setWhisperError] = useState<string | null>(null);

  useEffect(() => {
    mountedRef.current = true;
    let cancelled = false;

    (async () => {
      try {
        const { exists, path } = await checkModelExists(DEFAULT_AGENT_CONFIG.model);
        if (cancelled || !exists) {
          return;
        }

        const config = await loadAgentConfig();
        await saveAgentConfig({
          ...config,
          provider: "local",
          model: DEFAULT_AGENT_CONFIG.model,
          localModelPath: path,
        });

        setProgress(100);
        setDone(true);
      } catch {
        // Ignore status probe failures and let manual download handle the rest.
      }
    })();

    return () => {
      cancelled = true;
      mountedRef.current = false;
    };
  }, []);

  // Check Whisper model on mount
  useEffect(() => {
    (async () => {
      const downloaded = await isWhisperDownloaded("base.en");
      if (downloaded && mountedRef.current) {
        setWhisperDone(true);
        setWhisperProgress(100);
      }
    })();
  }, []);

  const handleWhisperDownload = useCallback(async () => {
    setWhisperDownloading(true);
    setWhisperProgress(0);
    setWhisperError(null);
    try {
      await downloadWhisperModel("base.en", (p) => {
        if (mountedRef.current) setWhisperProgress(Math.round(p * 100));
      });
      if (mountedRef.current) setWhisperDone(true);
    } catch (err) {
      if (mountedRef.current) {
        setWhisperError(err instanceof Error ? err.message : String(err));
        setWhisperProgress(0);
      }
    } finally {
      if (mountedRef.current) setWhisperDownloading(false);
    }
  }, []);

  const handleDownload = useCallback(async () => {
    setDownloading(true);
    setProgress(0);
    setError(null);

    try {
      const modelPath = await downloadModel(DEFAULT_AGENT_CONFIG.model, setProgress);
      if (!mountedRef.current) {
        return;
      }
      const config = await loadAgentConfig();
      await saveAgentConfig({
        ...config,
        provider: "local",
        model: DEFAULT_AGENT_CONFIG.model,
        localModelPath: modelPath,
      });
      if (mountedRef.current) {
        setDone(true);
      }
    } catch (err) {
      if (mountedRef.current) {
        setError(err instanceof Error ? err.message : String(err));
        setProgress(0);
      }
    } finally {
      if (mountedRef.current) {
        setDownloading(false);
      }
    }
  }, []);

  return (
    <View style={styles.container}>
      <Text style={styles.heading}>Download a model</Text>

      <GlassCard>
        <View style={styles.modelHeader}>
          <Ionicons
            name="cube-outline"
            size={28}
            color={colors.accent.violet}
            style={styles.modelIcon}
          />
          <View style={styles.modelTextWrap}>
            <Text style={styles.modelName}>{RECOMMENDED_MODEL.name}</Text>
            <Text style={styles.modelSize}>{RECOMMENDED_MODEL.size}</Text>
          </View>
        </View>
        <Text style={styles.modelDesc}>{RECOMMENDED_MODEL.description}</Text>

        {(downloading || done) && (
          <View style={styles.progressContainer}>
            <View style={styles.progressTrack}>
              <View
                style={[styles.progressFill, { width: `${progress}%` }]}
              />
            </View>
            <Text style={styles.progressLabel}>
              {done ? "Download complete" : `${progress}%`}
            </Text>
          </View>
        )}
        {error ? <Text style={styles.errorText}>{error}</Text> : null}
      </GlassCard>

      {/* Voice Model (Whisper STT) */}
      <GlassCard style={{ marginTop: spacing.md }}>
        <View style={styles.modelHeader}>
          <Ionicons
            name="mic-outline"
            size={28}
            color={colors.accent.cyan}
            style={styles.modelIcon}
          />
          <View style={styles.modelTextWrap}>
            <Text style={styles.modelName}>Whisper Base (English)</Text>
            <Text style={styles.modelSize}>~148 MB — Voice Recognition</Text>
          </View>
        </View>
        <Text style={styles.modelDesc}>
          On-device speech-to-text. No cloud needed. Required for voice interaction.
        </Text>

        {(whisperDownloading || whisperDone) && (
          <View style={styles.progressContainer}>
            <View style={styles.progressTrack}>
              <View style={[styles.progressFill, { width: `${whisperProgress}%`, backgroundColor: colors.accent.cyan }]} />
            </View>
            <Text style={styles.progressLabel}>
              {whisperDone ? "Download complete" : `${whisperProgress}%`}
            </Text>
          </View>
        )}
        {whisperError ? <Text style={styles.errorText}>{whisperError}</Text> : null}

        {!whisperDone && (
          <GlassButton
            title={whisperDownloading ? "Downloading..." : "Download Voice Model"}
            onPress={handleWhisperDownload}
            loading={whisperDownloading}
            disabled={whisperDownloading}
            icon="download-outline"
            style={{ marginTop: spacing.sm }}
          />
        )}
      </GlassCard>

      <View style={styles.actions}>
        {done ? (
          <GlassButton
            title="Continue"
            onPress={onNext}
            icon="checkmark-circle-outline"
          />
        ) : (
          <GlassButton
            title={downloading ? "Downloading..." : "Download"}
            onPress={handleDownload}
            loading={downloading}
            disabled={downloading}
            icon="download-outline"
          />
        )}
      </View>

      {!done && (
        <Pressable onPress={onSkip} style={styles.skipButton} disabled={downloading} testID="onboarding-skip-model">
          <Text style={styles.skipText}>Skip for now</Text>
          <Text style={styles.warningText}>
            GUAPPA needs a model to chat
          </Text>
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.xxl,
  },
  heading: {
    fontFamily: typography.display.fontFamily,
    fontSize: 22,
    color: colors.text.primary,
    textAlign: "center",
    marginBottom: spacing.lg,
  },
  modelHeader: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: spacing.sm,
  },
  modelIcon: {
    marginRight: spacing.md,
  },
  modelTextWrap: {
    flex: 1,
  },
  modelName: {
    fontFamily: typography.bodySemiBold.fontFamily,
    fontSize: 16,
    color: colors.text.primary,
  },
  modelSize: {
    fontFamily: typography.mono.fontFamily,
    fontSize: 13,
    color: colors.text.tertiary,
    marginTop: 2,
  },
  modelDesc: {
    fontFamily: typography.body.fontFamily,
    fontSize: 14,
    color: colors.text.secondary,
    lineHeight: 20,
  },
  progressContainer: {
    marginTop: spacing.md,
  },
  progressTrack: {
    height: 6,
    borderRadius: 3,
    backgroundColor: "rgba(255, 255, 255, 0.08)",
    overflow: "hidden",
  },
  progressFill: {
    height: "100%",
    borderRadius: 3,
    backgroundColor: colors.accent.cyan,
  },
  progressLabel: {
    fontFamily: typography.mono.fontFamily,
    fontSize: 12,
    color: colors.text.secondary,
    textAlign: "right",
    marginTop: spacing.xs,
  },
  errorText: {
    fontFamily: typography.body.fontFamily,
    fontSize: 12,
    color: colors.semantic.error,
    marginTop: spacing.sm,
  },
  actions: {
    marginTop: spacing.lg,
  },
  skipButton: {
    alignSelf: "center",
    alignItems: "center",
    paddingVertical: spacing.lg,
  },
  skipText: {
    fontFamily: typography.bodyMedium.fontFamily,
    fontSize: 15,
    color: colors.text.tertiary,
  },
  warningText: {
    fontFamily: typography.body.fontFamily,
    fontSize: 12,
    color: colors.semantic.warning,
    marginTop: spacing.xs,
  },
});
