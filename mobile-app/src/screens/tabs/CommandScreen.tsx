import React, { useEffect } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { BlurView } from "expo-blur";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withRepeat,
  withSpring,
} from "react-native-reanimated";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";
import { CollapsibleSection } from "../../components/glass";

const AnimatedView = Animated.createAnimatedComponent(View);

function StatusPill() {
  const dotOpacity = useSharedValue(0.3);

  useEffect(() => {
    dotOpacity.value = withRepeat(
      withSpring(1, { damping: 4, stiffness: 40 }),
      -1,
      true
    );
  }, [dotOpacity]);

  const dotStyle = useAnimatedStyle(() => ({
    opacity: dotOpacity.value,
  }));

  return (
    <BlurView intensity={25} tint="dark" style={styles.statusPill}>
      <AnimatedView style={[styles.statusDot, dotStyle]} />
      <Text style={styles.statusLabel}>Idle</Text>
    </BlurView>
  );
}

function AnimatedEmptyIcon({
  icon,
}: {
  icon: keyof typeof Ionicons.glyphMap;
}) {
  const scale = useSharedValue(1);

  useEffect(() => {
    scale.value = withRepeat(
      withSpring(1.06, { damping: 3, stiffness: 30 }),
      -1,
      true
    );
  }, [scale]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  return (
    <AnimatedView style={[styles.emptyIcon, animatedStyle]}>
      <Ionicons name={icon} size={36} color={colors.text.tertiary} />
    </AnimatedView>
  );
}

function ActiveTasksContent() {
  return (
    <View style={styles.sectionContent}>
      <AnimatedEmptyIcon icon="list-outline" />
      <Text style={styles.emptyTitle}>No active tasks</Text>
      <Text style={styles.emptySubtitle}>
        Ask GUAPPA to do something and tasks will appear here
      </Text>
    </View>
  );
}

function ScheduledContent() {
  return (
    <View style={styles.sectionContent}>
      <AnimatedEmptyIcon icon="calendar-outline" />
      <Text style={styles.emptyTitle}>No schedules</Text>
      <Text style={styles.emptySubtitle}>
        Create recurring actions like "Every morning, check my calendar"
      </Text>
      <View style={styles.nextInRow}>
        <Text style={styles.nextInLabel}>Next in</Text>
        <Text style={styles.nextInValue}>{"\u2014"}</Text>
      </View>
    </View>
  );
}

function TriggersContent() {
  return (
    <View style={styles.sectionContent}>
      <AnimatedEmptyIcon icon="flash-outline" />
      <Text style={styles.emptyTitle}>No triggers</Text>
      <Text style={styles.emptySubtitle}>
        Set up event-based actions like "When I get a call, log it"
      </Text>
      <View style={styles.triggersInfoPill}>
        <Ionicons
          name="information-circle-outline"
          size={14}
          color={colors.text.secondary}
        />
        <Text style={styles.triggersInfoText}>
          4 built-in triggers available
        </Text>
      </View>
    </View>
  );
}

const MEMORY_TIERS = [
  { name: "Working", color: colors.accent.cyan, pct: 15, size: "1.2 KB" },
  { name: "Short-term", color: colors.accent.lime, pct: 5, size: "0.3 KB" },
  { name: "Long-term", color: "#8B5CF6", pct: 0, size: "0 KB" },
  { name: "Episodic", color: "#F59E0B", pct: 0, size: "0 KB" },
  { name: "Semantic", color: "#EC4899", pct: 0, size: "0 KB" },
] as const;

function MemoryContent() {
  return (
    <View style={styles.memoryPanel}>
      {MEMORY_TIERS.map((tier) => (
        <View key={tier.name} style={styles.memoryTier}>
          <View style={styles.memoryTierHeader}>
            <View
              style={[styles.memoryDot, { backgroundColor: tier.color }]}
            />
            <Text style={styles.memoryTierName}>{tier.name}</Text>
          </View>
          <View style={styles.memoryBar}>
            <View
              style={[
                styles.memoryFill,
                {
                  width: `${tier.pct}%`,
                  backgroundColor: tier.color,
                },
              ]}
            />
          </View>
          <Text style={styles.memorySize}>{tier.size}</Text>
        </View>
      ))}
    </View>
  );
}

export function CommandScreen() {
  const insets = useSafeAreaInsets();

  return (
    <LinearGradient
      colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
      style={styles.container}
      testID="command-screen"
    >
      {/* Glass Header */}
      <BlurView
        intensity={30}
        tint="dark"
        style={[styles.header, { paddingTop: insets.top + spacing.sm }]}
      >
        <View style={styles.headerInner}>
          <Text style={styles.title}>Command Center</Text>
          <StatusPill />
        </View>
        {/* Bottom gradient line: cyan -> transparent -> cyan */}
        <LinearGradient
          colors={[colors.accent.cyan, "transparent", colors.accent.cyan]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 0 }}
          style={styles.headerLine}
        />
      </BlurView>

      {/* Collapsible Sections */}
      <ScrollView
        style={styles.content}
        contentContainerStyle={styles.contentInner}
        showsVerticalScrollIndicator={false}
      >
        <CollapsibleSection
          title="Active Tasks"
          icon="list-outline"
          defaultExpanded
        >
          <ActiveTasksContent />
        </CollapsibleSection>

        <CollapsibleSection title="Scheduled" icon="calendar-outline">
          <ScheduledContent />
        </CollapsibleSection>

        <CollapsibleSection title="Triggers" icon="flash-outline">
          <TriggersContent />
        </CollapsibleSection>

        <CollapsibleSection
          title="Memory"
          icon="server-outline"
          defaultExpanded
        >
          <MemoryContent />
        </CollapsibleSection>
      </ScrollView>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },

  // ── Header ──────────────────────────────────────────────
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
  title: {
    color: colors.text.primary,
    fontSize: 18,
    fontFamily: typography.display.fontFamily,
    letterSpacing: 1,
  },
  headerLine: {
    height: 1,
    width: "100%",
  },

  // ── Status Pill ─────────────────────────────────────────
  statusPill: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
    overflow: "hidden",
    backgroundColor: "rgba(255, 255, 255, 0.06)",
    borderWidth: 1,
    borderColor: colors.glass.borderSubtle,
    gap: 6,
  },
  statusDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: colors.text.tertiary,
  },
  statusLabel: {
    color: colors.text.tertiary,
    fontSize: 12,
    fontFamily: typography.mono.fontFamily,
    letterSpacing: 0.5,
  },

  // ── Content ─────────────────────────────────────────────
  content: {
    flex: 1,
  },
  contentInner: {
    padding: spacing.md,
    paddingBottom: 120,
  },

  // ── Section Empty States ────────────────────────────────
  sectionContent: {
    alignItems: "center",
    paddingVertical: spacing.lg,
  },
  emptyIcon: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: "rgba(255, 255, 255, 0.04)",
    borderWidth: 1,
    borderColor: colors.glass.borderSubtle,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: spacing.md,
  },
  emptyTitle: {
    color: colors.text.primary,
    fontSize: 16,
    fontFamily: typography.bodySemiBold.fontFamily,
    marginBottom: spacing.xs,
  },
  emptySubtitle: {
    color: colors.text.tertiary,
    fontSize: 13,
    fontFamily: typography.body.fontFamily,
    textAlign: "center",
    maxWidth: 260,
    lineHeight: 19,
  },

  // ── Scheduled: "Next in" ────────────────────────────────
  nextInRow: {
    flexDirection: "row",
    alignItems: "center",
    marginTop: spacing.md,
    gap: spacing.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: 12,
    backgroundColor: "rgba(255, 255, 255, 0.04)",
    borderWidth: 1,
    borderColor: colors.glass.borderSubtle,
  },
  nextInLabel: {
    color: colors.text.secondary,
    fontSize: 13,
    fontFamily: typography.bodySemiBold.fontFamily,
  },
  nextInValue: {
    color: colors.text.tertiary,
    fontSize: 13,
    fontFamily: typography.mono.fontFamily,
  },

  // ── Triggers: info pill ─────────────────────────────────
  triggersInfoPill: {
    flexDirection: "row",
    alignItems: "center",
    marginTop: spacing.md,
    gap: 6,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: 12,
    backgroundColor: "rgba(255, 255, 255, 0.04)",
    borderWidth: 1,
    borderColor: colors.glass.borderSubtle,
  },
  triggersInfoText: {
    color: colors.text.secondary,
    fontSize: 12,
    fontFamily: typography.body.fontFamily,
  },

  // ── Memory ──────────────────────────────────────────────
  memoryPanel: {
    gap: spacing.sm,
  },
  memoryTier: {
    backgroundColor: "rgba(255, 255, 255, 0.04)",
    borderRadius: 14,
    padding: spacing.md,
    borderWidth: 1,
    borderColor: colors.glass.borderSubtle,
  },
  memoryTierHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    marginBottom: 10,
  },
  memoryDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  memoryTierName: {
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.bodySemiBold.fontFamily,
  },
  memoryBar: {
    height: 5,
    borderRadius: 3,
    backgroundColor: "rgba(255, 255, 255, 0.06)",
    marginBottom: 6,
  },
  memoryFill: {
    height: "100%",
    borderRadius: 3,
  },
  memorySize: {
    color: colors.text.secondary,
    fontSize: 12,
    fontFamily: typography.mono.fontFamily,
  },
});
