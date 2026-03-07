import React, { useState } from "react";
import { View, Text, ScrollView, StyleSheet, Pressable } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";

type Tab = "tasks" | "schedules" | "triggers" | "memory";

const TABS: { key: Tab; label: string; icon: keyof typeof Ionicons.glyphMap }[] = [
  { key: "tasks", label: "Tasks", icon: "list-outline" },
  { key: "schedules", label: "Schedules", icon: "calendar-outline" },
  { key: "triggers", label: "Triggers", icon: "flash-outline" },
  { key: "memory", label: "Memory", icon: "server-outline" },
];

function EmptyState({ icon, title, subtitle }: {
  icon: keyof typeof Ionicons.glyphMap;
  title: string;
  subtitle: string;
}) {
  return (
    <View style={styles.emptyState}>
      <View style={styles.emptyIcon}>
        <Ionicons name={icon} size={48} color={colors.text.tertiary} />
      </View>
      <Text style={styles.emptyTitle}>{title}</Text>
      <Text style={styles.emptySubtitle}>{subtitle}</Text>
    </View>
  );
}

function TasksPanel() {
  return (
    <EmptyState
      icon="list-outline"
      title="No active tasks"
      subtitle="Ask GUAPPA to do something and tasks will appear here"
    />
  );
}

function SchedulesPanel() {
  return (
    <EmptyState
      icon="calendar-outline"
      title="No schedules"
      subtitle="Create recurring actions like 'Every morning, check my calendar'"
    />
  );
}

function TriggersPanel() {
  return (
    <EmptyState
      icon="flash-outline"
      title="No triggers"
      subtitle="Set up event-based actions like 'When I get a call, log it'"
    />
  );
}

function MemoryPanel() {
  return (
    <View style={styles.memoryPanel}>
      {["Working", "Short-term", "Long-term", "Episodic", "Semantic"].map(
        (tier, i) => (
          <View key={tier} style={styles.memoryTier}>
            <View style={styles.memoryTierHeader}>
              <View
                style={[
                  styles.memoryDot,
                  {
                    backgroundColor: [
                      colors.accent.cyan,
                      colors.accent.lime,
                      "#8B5CF6",
                      "#F59E0B",
                      "#EC4899",
                    ][i],
                  },
                ]}
              />
              <Text style={styles.memoryTierName}>{tier}</Text>
            </View>
            <View style={styles.memoryBar}>
              <View
                style={[
                  styles.memoryFill,
                  {
                    width: `${[15, 5, 0, 0, 0][i]}%`,
                    backgroundColor: [
                      colors.accent.cyan,
                      colors.accent.lime,
                      "#8B5CF6",
                      "#F59E0B",
                      "#EC4899",
                    ][i],
                  },
                ]}
              />
            </View>
            <Text style={styles.memorySize}>
              {["1.2 KB", "0.3 KB", "0 KB", "0 KB", "0 KB"][i]}
            </Text>
          </View>
        )
      )}
    </View>
  );
}

export function CommandScreen() {
  const insets = useSafeAreaInsets();
  const [activeTab, setActiveTab] = useState<Tab>("tasks");

  return (
    <LinearGradient
      colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
      style={styles.container}
      testID="command-screen"
    >
      {/* Header */}
      <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
        <Text style={styles.title}>Command Center</Text>
      </View>

      {/* Tab bar */}
      <View style={styles.tabBar}>
        {TABS.map((tab) => {
          const isActive = activeTab === tab.key;
          return (
            <Pressable
              key={tab.key}
              style={[styles.tab, isActive && styles.tabActive]}
              onPress={() => setActiveTab(tab.key)}
            >
              <Ionicons
                name={tab.icon}
                size={18}
                color={isActive ? colors.accent.cyan : colors.text.tertiary}
              />
              <Text
                style={[styles.tabLabel, isActive && styles.tabLabelActive]}
              >
                {tab.label}
              </Text>
            </Pressable>
          );
        })}
      </View>

      {/* Content */}
      <ScrollView
        style={styles.content}
        contentContainerStyle={styles.contentInner}
      >
        {activeTab === "tasks" && <TasksPanel />}
        {activeTab === "schedules" && <SchedulesPanel />}
        {activeTab === "triggers" && <TriggersPanel />}
        {activeTab === "memory" && <MemoryPanel />}
      </ScrollView>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    paddingHorizontal: 20,
    paddingBottom: 12,
  },
  title: {
    color: colors.text.primary,
    fontSize: 28,
    fontWeight: "700",
    letterSpacing: 0.5,
  },
  tabBar: {
    flexDirection: "row",
    paddingHorizontal: 12,
    gap: 4,
    marginBottom: 4,
  },
  tab: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    paddingVertical: 10,
    borderRadius: 12,
    backgroundColor: "rgba(255,255,255,0.03)",
  },
  tabActive: {
    backgroundColor: "rgba(0, 240, 255, 0.08)",
    borderWidth: 1,
    borderColor: "rgba(0, 240, 255, 0.15)",
  },
  tabLabel: {
    color: colors.text.tertiary,
    fontSize: 12,
    fontWeight: "600",
  },
  tabLabelActive: {
    color: colors.accent.cyan,
  },
  content: {
    flex: 1,
  },
  contentInner: {
    padding: 16,
    paddingBottom: 100,
  },
  emptyState: {
    alignItems: "center",
    paddingTop: 60,
  },
  emptyIcon: {
    width: 96,
    height: 96,
    borderRadius: 48,
    backgroundColor: "rgba(255,255,255,0.03)",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 16,
  },
  emptyTitle: {
    color: colors.text.primary,
    fontSize: 18,
    fontWeight: "600",
    marginBottom: 8,
  },
  emptySubtitle: {
    color: colors.text.tertiary,
    fontSize: 14,
    textAlign: "center",
    maxWidth: 280,
    lineHeight: 20,
  },
  memoryPanel: {
    gap: 16,
  },
  memoryTier: {
    backgroundColor: "rgba(255,255,255,0.04)",
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.06)",
  },
  memoryTierHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginBottom: 10,
  },
  memoryDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  memoryTierName: {
    color: colors.text.primary,
    fontSize: 15,
    fontWeight: "600",
  },
  memoryBar: {
    height: 6,
    borderRadius: 3,
    backgroundColor: "rgba(255,255,255,0.06)",
    marginBottom: 6,
  },
  memoryFill: {
    height: "100%",
    borderRadius: 3,
  },
  memorySize: {
    color: colors.text.tertiary,
    fontSize: 12,
  },
});
