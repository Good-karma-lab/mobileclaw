import React, { useState } from "react";
import { View, Text, ScrollView, StyleSheet, Pressable } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { colors } from "../../theme/colors";

type SwarmTab = "feed" | "peers" | "identity";

export function SwarmScreen() {
  const insets = useSafeAreaInsets();
  const [activeTab, setActiveTab] = useState<SwarmTab>("feed");

  return (
    <LinearGradient
      colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
      style={styles.container}
      testID="swarm-screen"
    >
      {/* Header */}
      <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
        <Text style={styles.title}>World Wide Swarm</Text>
        <View style={styles.statusPill}>
          <View style={styles.offlineDot} />
          <Text style={styles.statusText}>Offline</Text>
        </View>
      </View>

      {/* Tab bar */}
      <View style={styles.tabBar}>
        {(
          [
            { key: "feed", label: "Live Feed", icon: "pulse-outline" },
            { key: "peers", label: "Peers", icon: "people-outline" },
            { key: "identity", label: "Identity", icon: "finger-print-outline" },
          ] as const
        ).map((tab) => {
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
                color={isActive ? "#8B5CF6" : colors.text.tertiary}
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

      <ScrollView
        style={styles.content}
        contentContainerStyle={styles.contentInner}
      >
        {activeTab === "feed" && (
          <View style={styles.emptyState}>
            <View style={styles.emptyIcon}>
              <Ionicons name="globe-outline" size={56} color={colors.text.tertiary} />
            </View>
            <Text style={styles.emptyTitle}>Not connected</Text>
            <Text style={styles.emptySubtitle}>
              Join the World Wide Swarm to discover and collaborate with other GUAPPA agents
            </Text>
            <Pressable style={styles.connectButton}>
              <Ionicons name="link-outline" size={20} color={colors.base.spaceBlack} />
              <Text style={styles.connectButtonText}>Connect to Swarm</Text>
            </Pressable>
          </View>
        )}

        {activeTab === "peers" && (
          <View style={styles.emptyState}>
            <View style={styles.emptyIcon}>
              <Ionicons name="people-outline" size={56} color={colors.text.tertiary} />
            </View>
            <Text style={styles.emptyTitle}>No peers discovered</Text>
            <Text style={styles.emptySubtitle}>
              Connect to the swarm to discover nearby agents
            </Text>
          </View>
        )}

        {activeTab === "identity" && (
          <View style={styles.identitySection}>
            <View style={styles.identityCard}>
              <Ionicons name="finger-print-outline" size={40} color="#8B5CF6" />
              <Text style={styles.identityLabel}>Agent Identity</Text>
              <Text style={styles.identityStatus}>Not configured</Text>
              <Text style={styles.identityHint}>
                Generate an Ed25519 keypair to join the swarm with a unique identity
              </Text>
              <Pressable style={styles.generateButton}>
                <Text style={styles.generateButtonText}>Generate Identity</Text>
              </Pressable>
            </View>

            <View style={styles.statsCard}>
              <Text style={styles.statsTitle}>Swarm Stats</Text>
              {[
                { label: "Messages sent", value: "0" },
                { label: "Messages received", value: "0" },
                { label: "Tasks delegated", value: "0" },
                { label: "Tasks completed", value: "0" },
                { label: "Uptime", value: "—" },
              ].map((stat) => (
                <View key={stat.label} style={styles.statRow}>
                  <Text style={styles.statLabel}>{stat.label}</Text>
                  <Text style={styles.statValue}>{stat.value}</Text>
                </View>
              ))}
            </View>
          </View>
        )}
      </ScrollView>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 20,
    paddingBottom: 12,
  },
  title: {
    color: colors.text.primary,
    fontSize: 24,
    fontWeight: "700",
  },
  statusPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    backgroundColor: "rgba(255,255,255,0.05)",
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 12,
  },
  offlineDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.text.tertiary,
  },
  statusText: {
    color: colors.text.tertiary,
    fontSize: 12,
    fontWeight: "600",
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
    backgroundColor: "rgba(139, 92, 246, 0.08)",
    borderWidth: 1,
    borderColor: "rgba(139, 92, 246, 0.15)",
  },
  tabLabel: {
    color: colors.text.tertiary,
    fontSize: 12,
    fontWeight: "600",
  },
  tabLabelActive: {
    color: "#8B5CF6",
  },
  content: { flex: 1 },
  contentInner: {
    padding: 16,
    paddingBottom: 100,
  },
  emptyState: {
    alignItems: "center",
    paddingTop: 60,
  },
  emptyIcon: {
    width: 112,
    height: 112,
    borderRadius: 56,
    backgroundColor: "rgba(139, 92, 246, 0.05)",
    borderWidth: 1,
    borderColor: "rgba(139, 92, 246, 0.1)",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 20,
  },
  emptyTitle: {
    color: colors.text.primary,
    fontSize: 20,
    fontWeight: "600",
    marginBottom: 8,
  },
  emptySubtitle: {
    color: colors.text.tertiary,
    fontSize: 14,
    textAlign: "center",
    maxWidth: 300,
    lineHeight: 20,
    marginBottom: 24,
  },
  connectButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    backgroundColor: "#8B5CF6",
    paddingHorizontal: 24,
    paddingVertical: 14,
    borderRadius: 16,
  },
  connectButtonText: {
    color: colors.base.spaceBlack,
    fontSize: 16,
    fontWeight: "700",
  },
  identitySection: { gap: 16 },
  identityCard: {
    backgroundColor: "rgba(255,255,255,0.04)",
    borderRadius: 20,
    padding: 24,
    alignItems: "center",
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.06)",
  },
  identityLabel: {
    color: colors.text.primary,
    fontSize: 18,
    fontWeight: "600",
    marginTop: 12,
  },
  identityStatus: {
    color: colors.text.tertiary,
    fontSize: 14,
    marginTop: 4,
    marginBottom: 12,
  },
  identityHint: {
    color: colors.text.tertiary,
    fontSize: 13,
    textAlign: "center",
    lineHeight: 18,
    marginBottom: 16,
    maxWidth: 280,
  },
  generateButton: {
    backgroundColor: "rgba(139, 92, 246, 0.15)",
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: "rgba(139, 92, 246, 0.3)",
  },
  generateButtonText: {
    color: "#8B5CF6",
    fontSize: 14,
    fontWeight: "600",
  },
  statsCard: {
    backgroundColor: "rgba(255,255,255,0.04)",
    borderRadius: 20,
    padding: 20,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.06)",
  },
  statsTitle: {
    color: colors.text.primary,
    fontSize: 16,
    fontWeight: "600",
    marginBottom: 16,
  },
  statRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: "rgba(255,255,255,0.04)",
  },
  statLabel: {
    color: colors.text.secondary,
    fontSize: 14,
  },
  statValue: {
    color: colors.text.primary,
    fontSize: 14,
    fontWeight: "600",
  },
});
