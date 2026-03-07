import React, { useState, useCallback, useRef, useEffect } from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { GlassCard, GlassButton } from "../glass";
import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";

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
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  const handleDownload = useCallback(() => {
    setDownloading(true);
    setProgress(0);

    // Fake download progress: 0 -> 100 over ~3 seconds
    const interval = 50;
    const steps = 3000 / interval;
    let step = 0;

    timerRef.current = setInterval(() => {
      step += 1;
      const pct = Math.min(Math.round((step / steps) * 100), 100);
      setProgress(pct);

      if (pct >= 100) {
        if (timerRef.current) clearInterval(timerRef.current);
        timerRef.current = null;
        setDownloading(false);
        setDone(true);
      }
    }, interval);
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
        <Pressable onPress={onSkip} style={styles.skipButton}>
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
