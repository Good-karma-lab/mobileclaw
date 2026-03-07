import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet, Pressable } from "react-native";
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withRepeat,
  withTiming,
  withSpring,
  Easing,
} from "react-native-reanimated";
import { BlurView } from "expo-blur";
import { LinearGradient } from "expo-linear-gradient";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";
import { springs } from "../../theme/animations";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type FilterKey = "all" | "tasks" | "messages" | "reputation" | "network";

const FILTERS: { key: FilterKey; label: string }[] = [
  { key: "all", label: "All" },
  { key: "tasks", label: "Tasks" },
  { key: "messages", label: "Messages" },
  { key: "reputation", label: "Reputation" },
  { key: "network", label: "Network" },
];

// ---------------------------------------------------------------------------
// Animated filter pill
// ---------------------------------------------------------------------------

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

function FilterPill({
  label,
  isActive,
  onPress,
}: {
  label: string;
  isActive: boolean;
  onPress: () => void;
}) {
  const scale = useSharedValue(1);
  const bg = useSharedValue(isActive ? 1 : 0);

  useEffect(() => {
    bg.value = withSpring(isActive ? 1 : 0, springs.snappy);
  }, [isActive, bg]);

  const pillStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
    backgroundColor:
      bg.value > 0.5
        ? "rgba(139, 92, 246, 0.20)"
        : "rgba(255, 255, 255, 0.06)",
    borderColor:
      bg.value > 0.5
        ? "rgba(139, 92, 246, 0.40)"
        : "rgba(255, 255, 255, 0.12)",
  }));

  const handlePressIn = useCallback(() => {
    scale.value = withSpring(0.93, springs.snappy);
  }, [scale]);

  const handlePressOut = useCallback(() => {
    scale.value = withSpring(1, springs.snappy);
  }, [scale]);

  return (
    <AnimatedPressable
      onPress={onPress}
      onPressIn={handlePressIn}
      onPressOut={handlePressOut}
      style={[filterStyles.pill, pillStyle]}
    >
      <Text
        style={[
          filterStyles.pillText,
          isActive && filterStyles.pillTextActive,
        ]}
      >
        {label}
      </Text>
    </AnimatedPressable>
  );
}

const filterStyles = StyleSheet.create({
  pill: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 16,
    borderWidth: 1,
  },
  pillText: {
    color: colors.text.secondary,
    fontSize: 13,
    fontFamily: typography.bodySemiBold.fontFamily,
  },
  pillTextActive: {
    color: colors.accent.violet,
  },
});

// ---------------------------------------------------------------------------
// Rotating globe for empty / disconnected state
// ---------------------------------------------------------------------------

function RotatingGlobe() {
  const rotation = useSharedValue(0);

  useEffect(() => {
    rotation.value = withRepeat(
      withTiming(360, { duration: 12000, easing: Easing.linear }),
      -1,
      false,
    );
  }, [rotation]);

  const globeStyle = useAnimatedStyle(() => ({
    transform: [{ rotate: `${rotation.value}deg` }],
  }));

  return (
    <View style={emptyStyles.iconContainer}>
      <Animated.View style={globeStyle}>
        <Ionicons name="globe-outline" size={56} color={colors.accent.violet} />
      </Animated.View>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

export function SwarmScreen() {
  const insets = useSafeAreaInsets();
  const [connected, setConnected] = useState(false);
  const [activeFilter, setActiveFilter] = useState<FilterKey>("all");

  return (
    <LinearGradient
      colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
      style={styles.container}
      testID="swarm-screen"
    >
      {/* ---- Glass header ---- */}
      <BlurView
        intensity={24}
        tint="dark"
        style={[styles.header, { paddingTop: insets.top + spacing.sm }]}
      >
        <View style={styles.headerInner}>
          <Text style={styles.headerTitle}>Swarm</Text>
          <Pressable style={styles.statsButton}>
            <Ionicons
              name="stats-chart-outline"
              size={16}
              color={colors.text.secondary}
            />
            <Text style={styles.statsButtonText}>Stats</Text>
          </Pressable>
        </View>
        {/* Bottom gradient line: violet -> transparent -> violet */}
        <LinearGradient
          colors={[
            colors.accent.violet,
            "transparent",
            colors.accent.violet,
          ]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 0 }}
          style={styles.headerLine}
        />
      </BlurView>

      {/* ---- Glass status bar ---- */}
      <View style={styles.statusBar}>
        <BlurView intensity={16} tint="dark" style={styles.statusBarBlur}>
          <View style={styles.statusBarInner}>
            {/* Connection toggle */}
            <Pressable
              style={[
                styles.togglePill,
                connected && styles.togglePillActive,
              ]}
              onPress={() => setConnected((v) => !v)}
            >
              <View
                style={[
                  styles.toggleDot,
                  connected ? styles.toggleDotOn : styles.toggleDotOff,
                ]}
              />
              <Text
                style={[
                  styles.toggleLabel,
                  connected && styles.toggleLabelActive,
                ]}
              >
                {connected ? "ON" : "OFF"}
              </Text>
            </Pressable>

            {/* Peer count */}
            <Text style={styles.peerCount}>0 Peers</Text>

            {/* Tier badge */}
            <View style={styles.tierBadge}>
              <Text style={styles.tierBadgeText}>EXECUTOR</Text>
            </View>

            {/* Reputation placeholder */}
            <View style={styles.reputationContainer}>
              <Ionicons
                name="star-outline"
                size={14}
                color={colors.text.tertiary}
              />
              <Text style={styles.reputationText}>--</Text>
            </View>
          </View>
        </BlurView>
      </View>

      {/* ---- Filter pills ---- */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.filterRow}
        style={styles.filterScroll}
      >
        {FILTERS.map((f) => (
          <FilterPill
            key={f.key}
            label={f.label}
            isActive={activeFilter === f.key}
            onPress={() => setActiveFilter(f.key)}
          />
        ))}
      </ScrollView>

      {/* ---- Scrollable feed ---- */}
      <ScrollView
        style={styles.feed}
        contentContainerStyle={styles.feedContent}
      >
        {!connected ? (
          /* ---- Disconnected empty state ---- */
          <View style={emptyStyles.container}>
            <RotatingGlobe />
            <Text style={emptyStyles.title}>Connect to the Swarm</Text>
            <Text style={emptyStyles.subtitle}>
              Join the World Wide Swarm to discover and collaborate with other
              agents across the network
            </Text>
            <Pressable
              style={styles.enableButton}
              onPress={() => setConnected(true)}
            >
              <Ionicons name="flash-outline" size={18} color="#FFFFFF" />
              <Text style={styles.enableButtonText}>Enable</Text>
            </Pressable>
          </View>
        ) : (
          /* ---- Connected placeholder ---- */
          <View style={emptyStyles.container}>
            <Ionicons
              name="radio-outline"
              size={48}
              color={colors.accent.violet}
            />
            <Text style={emptyStyles.title}>Listening...</Text>
            <Text style={emptyStyles.subtitle}>
              No swarm events yet. Activity will appear here as peers connect
              and tasks are distributed.
            </Text>
          </View>
        )}
      </ScrollView>
    </LinearGradient>
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },

  // Glass header
  header: {
    backgroundColor: "rgba(255, 255, 255, 0.06)",
    borderBottomWidth: 0,
  },
  headerInner: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.md,
    paddingBottom: spacing.sm,
  },
  headerTitle: {
    color: colors.text.primary,
    fontFamily: typography.display.fontFamily,
    fontSize: 18,
    letterSpacing: 2,
    fontWeight: "700",
  },
  headerLine: {
    height: 1,
    width: "100%",
  },
  statsButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    backgroundColor: "rgba(255, 255, 255, 0.06)",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.12)",
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  statsButtonText: {
    color: colors.text.secondary,
    fontSize: 13,
    fontFamily: typography.bodySemiBold.fontFamily,
  },

  // Glass status bar
  statusBar: {
    marginHorizontal: spacing.md,
    marginTop: spacing.sm,
    borderRadius: 20,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.12)",
  },
  statusBarBlur: {
    backgroundColor: "rgba(255, 255, 255, 0.06)",
  },
  statusBarInner: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: spacing.md,
    paddingVertical: 12,
    gap: 12,
  },

  // Toggle pill
  togglePill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    backgroundColor: "rgba(255, 255, 255, 0.06)",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.12)",
    borderRadius: 16,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  togglePillActive: {
    backgroundColor: "rgba(139, 92, 246, 0.15)",
    borderColor: "rgba(139, 92, 246, 0.35)",
  },
  toggleDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  toggleDotOff: {
    backgroundColor: colors.text.tertiary,
  },
  toggleDotOn: {
    backgroundColor: colors.accent.violet,
  },
  toggleLabel: {
    color: colors.text.tertiary,
    fontSize: 12,
    fontFamily: typography.mono.fontFamily,
    fontWeight: "600",
  },
  toggleLabelActive: {
    color: colors.accent.violet,
  },

  // Peer count
  peerCount: {
    color: colors.text.secondary,
    fontSize: 13,
    fontFamily: typography.mono.fontFamily,
  },

  // Tier badge
  tierBadge: {
    backgroundColor: "rgba(255, 255, 255, 0.06)",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.12)",
    borderRadius: 16,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  tierBadgeText: {
    color: colors.text.tertiary,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
    letterSpacing: 1,
  },

  // Reputation
  reputationContainer: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    marginLeft: "auto",
  },
  reputationText: {
    color: colors.text.tertiary,
    fontSize: 13,
    fontFamily: typography.mono.fontFamily,
  },

  // Filter pills row
  filterScroll: {
    flexGrow: 0,
    marginTop: spacing.sm,
  },
  filterRow: {
    paddingHorizontal: spacing.md,
    gap: spacing.sm,
  },

  // Feed
  feed: {
    flex: 1,
  },
  feedContent: {
    padding: spacing.md,
    paddingBottom: 120,
  },

  // Enable button (violet accent)
  enableButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    backgroundColor: "rgba(139, 92, 246, 0.25)",
    borderWidth: 1,
    borderColor: "rgba(139, 92, 246, 0.40)",
    borderRadius: 16,
    paddingHorizontal: 24,
    paddingVertical: 14,
  },
  enableButtonText: {
    color: "#FFFFFF",
    fontSize: 15,
    fontFamily: typography.bodySemiBold.fontFamily,
  },
});

// Empty / disconnected state styles
const emptyStyles = StyleSheet.create({
  container: {
    alignItems: "center",
    paddingTop: 60,
    gap: 12,
  },
  iconContainer: {
    width: 112,
    height: 112,
    borderRadius: 56,
    backgroundColor: "rgba(139, 92, 246, 0.06)",
    borderWidth: 1,
    borderColor: "rgba(139, 92, 246, 0.15)",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 8,
  },
  title: {
    color: colors.text.primary,
    fontFamily: typography.body.fontFamily,
    fontSize: 18,
    opacity: 0.5,
  },
  subtitle: {
    color: colors.text.tertiary,
    fontFamily: typography.body.fontFamily,
    fontSize: 14,
    textAlign: "center",
    maxWidth: 300,
    lineHeight: 20,
    marginBottom: 12,
  },
});
