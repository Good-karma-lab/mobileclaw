import React, { useEffect, useState, useCallback } from "react";
import { View, ScrollView, Pressable, TextInput, Alert, ActivityIndicator } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";

import { Screen } from "../../../ui/primitives/Screen";
import { Text } from "../../../ui/primitives/Text";
import { theme } from "../../../ui/theme";
import { useLayoutContext } from "../../state/layout";
import { loadAgentConfig } from "../../state/mobileclaw";
import {
  fetchMemories,
  recallMemories,
  fetchMemoryCount,
  forgetMemory,
  type MemoryEntry,
} from "../../api/mobileclaw";
import { addActivity } from "../../state/activity";

const CATEGORY_COLORS: Record<string, string> = {
  core: theme.colors.base.primary,
  daily: theme.colors.base.secondary,
  conversation: theme.colors.base.accent,
};

function MemoryCard({
  memory,
  onDelete,
}: {
  memory: MemoryEntry;
  onDelete: (key: string) => void;
}) {
  const categoryColor = CATEGORY_COLORS[memory.category] || theme.colors.base.textMuted;

  return (
    <View
      style={{
        padding: theme.spacing.md,
        borderRadius: theme.radii.lg,
        backgroundColor: theme.colors.surface.raised,
        borderWidth: 1,
        borderColor: theme.colors.stroke.subtle,
        gap: 6,
      }}
    >
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start" }}>
        <View style={{ flex: 1, marginRight: theme.spacing.sm }}>
          <View style={{ flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 4 }}>
            <View
              style={{
                paddingHorizontal: 8,
                paddingVertical: 2,
                borderRadius: theme.radii.sm,
                backgroundColor: hexToRgba(categoryColor, 0.2),
                borderWidth: 1,
                borderColor: hexToRgba(categoryColor, 0.4),
              }}
            >
              <Text variant="label" style={{ color: categoryColor, fontSize: 11 }}>
                {memory.category}
              </Text>
            </View>
            {memory.score !== undefined ? (
              <Text variant="muted" style={{ fontSize: 11 }}>
                {Math.round(memory.score * 100)}% match
              </Text>
            ) : null}
          </View>
          <Text variant="bodyMedium" style={{ fontFamily: theme.typography.bodyMedium }}>
            {memory.key}
          </Text>
        </View>
        <Pressable
          onPress={() => {
            Alert.alert(
              "Delete Memory",
              `Delete memory "${memory.key}"?`,
              [
                { text: "Cancel", style: "cancel" },
                {
                  text: "Delete",
                  style: "destructive",
                  onPress: () => onDelete(memory.key),
                },
              ]
            );
          }}
          style={{
            padding: 8,
            borderRadius: theme.radii.sm,
            backgroundColor: theme.colors.surface.panel,
          }}
        >
          <Ionicons name="trash-outline" size={18} color={theme.colors.base.accent} />
        </Pressable>
      </View>
      <Text variant="body" style={{ marginTop: 4 }} numberOfLines={3}>
        {memory.content}
      </Text>
      <Text variant="muted" style={{ fontSize: 11, marginTop: 4 }}>
        {memory.timestamp}
      </Text>
    </View>
  );
}

function hexToRgba(hex: string, alpha: number): string {
  const h = hex.trim();
  if (!h.startsWith("#") || h.length !== 7) return h;
  const r = parseInt(h.slice(1, 3), 16);
  const g = parseInt(h.slice(3, 5), 16);
  const b = parseInt(h.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

export function MemoryScreen() {
  const { useSidebar } = useLayoutContext();
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [count, setCount] = useState(0);
  const [searchQuery, setSearchQuery] = useState("");
  const [categoryFilter, setCategoryFilter] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadMemories = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const config = await loadAgentConfig();
      const [memoriesData, countData] = await Promise.all([
        searchQuery.trim()
          ? recallMemories(config.platformUrl, searchQuery, 50)
          : fetchMemories(config.platformUrl, categoryFilter || undefined),
        fetchMemoryCount(config.platformUrl),
      ]);
      setMemories(memoriesData);
      setCount(countData);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Failed to load memories";
      setError(msg);
      void addActivity({ kind: "log", source: "memory", title: "Memory load error", detail: msg });
    } finally {
      setLoading(false);
    }
  }, [searchQuery, categoryFilter]);

  useEffect(() => {
    void loadMemories();
  }, [loadMemories]);

  useFocusEffect(
    useCallback(() => {
      void loadMemories();
    }, [loadMemories])
  );

  const handleDelete = async (key: string) => {
    try {
      const config = await loadAgentConfig();
      const success = await forgetMemory(config.platformUrl, key);
      if (success) {
        setMemories((prev) => prev.filter((m) => m.key !== key));
        setCount((prev) => Math.max(0, prev - 1));
        void addActivity({ kind: "action", source: "memory", title: "Memory deleted", detail: key });
      } else {
        Alert.alert("Not Found", `Memory "${key}" was not found.`);
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Failed to delete memory";
      Alert.alert("Error", msg);
    }
  };

  const categories = ["core", "daily", "conversation"];

  const content = (
    <>
      <View
        style={{
          padding: theme.spacing.md,
          borderRadius: theme.radii.lg,
          backgroundColor: theme.colors.surface.raised,
          borderWidth: 1,
          borderColor: theme.colors.stroke.subtle,
          gap: theme.spacing.sm,
        }}
      >
        <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
          <Text variant="title">Memory Store</Text>
          <View
            style={{
              paddingHorizontal: 12,
              paddingVertical: 6,
              borderRadius: theme.radii.md,
              backgroundColor: theme.colors.surface.panel,
            }}
          >
            <Text variant="mono" style={{ fontSize: 14 }}>
              {count} entries
            </Text>
          </View>
        </View>

        <TextInput
          testID="memory-search"
          placeholder="Search memories..."
          placeholderTextColor={theme.colors.alpha.textPlaceholder}
          value={searchQuery}
          onChangeText={setSearchQuery}
          style={{
            minHeight: 48,
            borderRadius: theme.radii.lg,
            padding: theme.spacing.md,
            backgroundColor: theme.colors.surface.panel,
            borderWidth: 1,
            borderColor: theme.colors.stroke.subtle,
            color: theme.colors.base.text,
            fontFamily: theme.typography.body,
          }}
        />

        <View style={{ flexDirection: "row", gap: theme.spacing.sm, flexWrap: "wrap" }}>
          <Pressable
            onPress={() => setCategoryFilter(null)}
            style={{
              paddingHorizontal: 12,
              paddingVertical: 6,
              borderRadius: theme.radii.md,
              backgroundColor: categoryFilter === null ? theme.colors.base.primary : theme.colors.surface.panel,
              borderWidth: 1,
              borderColor: categoryFilter === null ? theme.colors.base.primary : theme.colors.stroke.subtle,
            }}
          >
            <Text variant="label" style={{ color: categoryFilter === null ? theme.colors.base.background : theme.colors.base.text }}>
              All
            </Text>
          </Pressable>
          {categories.map((cat) => (
            <Pressable
              key={cat}
              onPress={() => setCategoryFilter(cat)}
              style={{
                paddingHorizontal: 12,
                paddingVertical: 6,
                borderRadius: theme.radii.md,
                backgroundColor: categoryFilter === cat ? CATEGORY_COLORS[cat] : theme.colors.surface.panel,
                borderWidth: 1,
                borderColor: categoryFilter === cat ? CATEGORY_COLORS[cat] : theme.colors.stroke.subtle,
              }}
            >
              <Text
                variant="label"
                style={{ color: categoryFilter === cat ? theme.colors.base.background : theme.colors.base.text }}
              >
                {cat}
              </Text>
            </Pressable>
          ))}
        </View>
      </View>

      {loading ? (
        <View style={{ padding: theme.spacing.xl, alignItems: "center" }}>
          <ActivityIndicator size="large" color={theme.colors.base.primary} />
          <Text variant="muted" style={{ marginTop: theme.spacing.md }}>
            Loading memories...
          </Text>
        </View>
      ) : error ? (
        <View
          style={{
            padding: theme.spacing.lg,
            borderRadius: theme.radii.lg,
            backgroundColor: theme.colors.surface.raised,
            borderWidth: 1,
            borderColor: theme.colors.base.accent,
          }}
        >
          <Text variant="bodyMedium" style={{ color: theme.colors.base.accent }}>
            {error}
          </Text>
          <Pressable
            onPress={loadMemories}
            style={{
              marginTop: theme.spacing.md,
              paddingVertical: 10,
              paddingHorizontal: 16,
              borderRadius: theme.radii.md,
              backgroundColor: theme.colors.surface.panel,
              alignSelf: "flex-start",
            }}
          >
            <Text variant="label">Retry</Text>
          </Pressable>
        </View>
      ) : memories.length === 0 ? (
        <View
          style={{
            padding: theme.spacing.lg,
            borderRadius: theme.radii.lg,
            backgroundColor: theme.colors.surface.raised,
            alignItems: "center",
          }}
        >
          <Ionicons name="cube-outline" size={48} color={theme.colors.base.textMuted} />
          <Text variant="bodyMedium" style={{ marginTop: theme.spacing.md, textAlign: "center" }}>
            {searchQuery ? "No memories match your search." : "No memories stored yet."}
          </Text>
          <Text variant="muted" style={{ marginTop: theme.spacing.xs, textAlign: "center" }}>
            Chat with the agent to store memories.
          </Text>
        </View>
      ) : (
        memories.map((memory) => (
          <MemoryCard key={memory.id} memory={memory} onDelete={handleDelete} />
        ))
      )}
    </>
  );

  if (useSidebar) {
    return (
      <Screen testID="screen-memory">
        <ScrollView
          contentContainerStyle={{
            paddingHorizontal: theme.spacing.lg,
            paddingTop: theme.spacing.xl,
            paddingBottom: 40,
            gap: theme.spacing.md,
          }}
        >
          <Text variant="display">
            Memory
          </Text>
          {content}
        </ScrollView>
      </Screen>
    );
  }

  return (
    <Screen testID="screen-memory">
      <ScrollView
        contentContainerStyle={{
          paddingHorizontal: theme.spacing.lg,
          paddingTop: theme.spacing.xl,
          paddingBottom: 140,
          gap: theme.spacing.md,
        }}
      >
        <View>
          <Text variant="display">
            Memory
          </Text>
          <Text variant="muted" style={{ marginTop: theme.spacing.xs }}>
            View, search, and delete stored memories.
          </Text>
        </View>
        {content}
      </ScrollView>
    </Screen>
  );
}
