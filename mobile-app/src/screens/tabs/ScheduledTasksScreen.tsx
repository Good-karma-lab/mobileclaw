import React, { useCallback, useEffect, useState } from "react";
import { Pressable, ScrollView, View } from "react-native";
import { useNavigation } from "@react-navigation/native";

import { Screen } from "../../../ui/primitives/Screen";
import { Text } from "../../../ui/primitives/Text";
import { theme } from "../../../ui/theme";
import { loadSecurityConfig } from "../../state/mobileclaw";
import { useActivity, type ActivityItem } from "../../state/activity";

type GatewayHealth = "checking" | "ok" | "error";

export function ScheduledTasksScreen() {
  const navigation = useNavigation<any>();
  const { items: allItems, refresh } = useActivity();
  const [callHooksEnabled, setCallHooksEnabled] = useState(false);
  const [smsHooksEnabled, setSmsHooksEnabled] = useState(false);
  const [gatewayHealth, setGatewayHealth] = useState<GatewayHealth>("checking");

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const security = await loadSecurityConfig();
      if (cancelled) return;
      setCallHooksEnabled(security.incomingCallHooks);
      setSmsHooksEnabled(security.incomingSmsHooks);
    })();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await Promise.race([
          fetch("http://127.0.0.1:8000/health"),
          new Promise<never>((_, reject) => setTimeout(() => reject(new Error("timeout")), 3000)),
        ]);
        if (!cancelled) setGatewayHealth(res.ok ? "ok" : "error");
      } catch {
        if (!cancelled) setGatewayHealth("error");
      }
    })();
    return () => { cancelled = true; };
  }, []);

  const checkGateway = useCallback(() => {
    setGatewayHealth("checking");
    (async () => {
      try {
        const res = await Promise.race([
          fetch("http://127.0.0.1:8000/health"),
          new Promise<never>((_, reject) => setTimeout(() => reject(new Error("timeout")), 3000)),
        ]);
        setGatewayHealth(res.ok ? "ok" : "error");
      } catch {
        setGatewayHealth("error");
      }
    })();
  }, []);

  const runtimeTasks: ActivityItem[] = allItems
    .filter((item) => item.source === "runtime")
    .slice(0, 20);

  const healthColor =
    gatewayHealth === "ok"
      ? theme.colors.base.primary
      : gatewayHealth === "error"
      ? "#e05252"
      : theme.colors.base.textMuted;

  const healthLabel =
    gatewayHealth === "ok" ? "Online" : gatewayHealth === "error" ? "Offline" : "Checking…";

  return (
    <Screen testID="screen-tasks">
      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={{
          paddingHorizontal: theme.spacing.lg,
          paddingTop: theme.spacing.xl,
          paddingBottom: 40,
          gap: theme.spacing.lg,
        }}
        showsVerticalScrollIndicator={false}
      >
        <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
          <Text variant="display">Tasks &amp; Hooks</Text>
          <Pressable
            onPress={() => navigation.goBack()}
            style={{
              paddingHorizontal: 10,
              paddingVertical: 6,
              borderRadius: theme.radii.md,
              backgroundColor: theme.colors.surface.panel,
              borderWidth: 1,
              borderColor: theme.colors.stroke.subtle,
            }}
          >
            <Text variant="label">Back</Text>
          </Pressable>
        </View>

        {/* Gateway health */}
        <View
          style={{
            backgroundColor: theme.colors.surface.panel,
            borderRadius: theme.radii.md,
            borderWidth: 1,
            borderColor: theme.colors.stroke.subtle,
            padding: theme.spacing.md,
          }}
        >
          <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
            <Text variant="label">Gateway (127.0.0.1:8000)</Text>
            <Pressable onPress={checkGateway}>
              <View
                style={{
                  flexDirection: "row",
                  alignItems: "center",
                  gap: 6,
                  paddingHorizontal: 8,
                  paddingVertical: 4,
                  borderRadius: theme.radii.sm,
                  backgroundColor: theme.colors.surface.raised,
                }}
              >
                <View
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: 4,
                    backgroundColor: healthColor,
                  }}
                />
                <Text variant="caption" style={{ color: healthColor }}>
                  {healthLabel}
                </Text>
              </View>
            </Pressable>
          </View>
          <Text variant="muted" style={{ marginTop: 4 }}>
            Tap the badge to refresh. Agent gateway must be online for tool execution.
          </Text>
        </View>

        {/* Active hooks */}
        <View>
          <Text variant="heading" style={{ marginBottom: theme.spacing.sm }}>
            Active Hooks
          </Text>
          <View
            style={{
              backgroundColor: theme.colors.surface.panel,
              borderRadius: theme.radii.md,
              borderWidth: 1,
              borderColor: theme.colors.stroke.subtle,
              overflow: "hidden",
            }}
          >
            <HookRow
              label="Incoming Call Hook"
              description="Agent reacts to incoming phone calls"
              enabled={callHooksEnabled}
            />
            <View style={{ height: 1, backgroundColor: theme.colors.stroke.subtle }} />
            <HookRow
              label="Incoming SMS Hook"
              description="Agent reacts to incoming SMS messages"
              enabled={smsHooksEnabled}
            />
          </View>
          <Text variant="muted" style={{ marginTop: 6 }}>
            Configure hooks in Security settings.
          </Text>
        </View>

        {/* Recent runtime tasks */}
        <View>
          <View
            style={{
              flexDirection: "row",
              alignItems: "center",
              justifyContent: "space-between",
              marginBottom: theme.spacing.sm,
            }}
          >
            <Text variant="heading">Recent Agent Tasks</Text>
            <Pressable
              onPress={() => { void refresh(); }}
              style={{
                paddingHorizontal: 8,
                paddingVertical: 4,
                borderRadius: theme.radii.sm,
                backgroundColor: theme.colors.surface.raised,
                borderWidth: 1,
                borderColor: theme.colors.stroke.subtle,
              }}
            >
              <Text variant="caption">Refresh</Text>
            </Pressable>
          </View>
          {runtimeTasks.length === 0 ? (
            <View
              style={{
                backgroundColor: theme.colors.surface.panel,
                borderRadius: theme.radii.md,
                borderWidth: 1,
                borderColor: theme.colors.stroke.subtle,
                padding: theme.spacing.md,
              }}
            >
              <Text variant="muted">No runtime tasks yet. Agent activity will appear here.</Text>
            </View>
          ) : (
            <View
              style={{
                backgroundColor: theme.colors.surface.panel,
                borderRadius: theme.radii.md,
                borderWidth: 1,
                borderColor: theme.colors.stroke.subtle,
                overflow: "hidden",
              }}
            >
              {runtimeTasks.map((item, idx) => (
                <View key={item.id}>
                  {idx > 0 && <View style={{ height: 1, backgroundColor: theme.colors.stroke.subtle }} />}
                  <View style={{ padding: theme.spacing.md }}>
                    <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start" }}>
                      <Text variant="label" style={{ flex: 1 }}>{item.title}</Text>
                      <Text variant="caption" style={{ color: theme.colors.base.textMuted, marginLeft: 8 }}>
                        {new Date(item.ts).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                      </Text>
                    </View>
                    {item.detail ? (
                      <Text variant="muted" style={{ marginTop: 2 }} numberOfLines={2}>
                        {item.detail}
                      </Text>
                    ) : null}
                  </View>
                </View>
              ))}
            </View>
          )}
        </View>
      </ScrollView>
    </Screen>
  );
}

function HookRow({
  label,
  description,
  enabled,
}: {
  label: string;
  description: string;
  enabled: boolean;
}) {
  return (
    <View style={{ padding: theme.spacing.md, flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
      <View style={{ flex: 1 }}>
        <Text variant="label">{label}</Text>
        <Text variant="muted" style={{ marginTop: 2 }}>{description}</Text>
      </View>
      <View
        style={{
          paddingHorizontal: 8,
          paddingVertical: 3,
          borderRadius: theme.radii.sm,
          backgroundColor: enabled ? theme.colors.base.primary : theme.colors.surface.raised,
        }}
      >
        <Text
          variant="caption"
          style={{ color: enabled ? "#fff" : theme.colors.base.textMuted }}
        >
          {enabled ? "On" : "Off"}
        </Text>
      </View>
    </View>
  );
}
