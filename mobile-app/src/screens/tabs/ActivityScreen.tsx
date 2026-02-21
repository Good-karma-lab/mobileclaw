import React, { useEffect, useState } from "react";
import { View, ScrollView, Pressable } from "react-native";
import { useFocusEffect } from "@react-navigation/native";

import { Screen } from "../../../ui/primitives/Screen";
import { Text } from "../../../ui/primitives/Text";
import { useActivity, type ActivityItem } from "../../state/activity";
import { theme } from "../../../ui/theme";
import { getRuntimeSupervisorState, type RuntimeSupervisorState } from "../../runtime/supervisor";
import { getAndroidRuntimeBridgeStatus } from "../../native/androidAgentBridge";
import { useLayoutContext } from "../../state/layout";

function runtimeStatusLabel(state: RuntimeSupervisorState | null): { dot: string; text: string; reason: string } {
  if (!state) return { dot: "⚪", text: "Unknown", reason: "No runtime data" };
  if (state.status === "healthy") return { dot: "🟢", text: "Healthy", reason: state.degradeReason || "" };
  if (state.status === "starting") return { dot: "🟡", text: "Starting", reason: state.degradeReason || "" };
  if (state.status === "degraded") return { dot: "🟡", text: "Degraded", reason: state.degradeReason || "" };
  if (state.status === "stopped") return { dot: "🔴", text: "Stopped", reason: state.degradeReason || "" };
  return { dot: "⚪", text: state.status, reason: state.degradeReason || "" };
}

function activityBorderColor(item: ActivityItem): string {
  if (item.kind === "log" && item.title.toLowerCase().includes("error")) return theme.colors.base.accent;
  if (item.kind === "log") return theme.colors.base.textMuted;
  if (item.kind === "action") return theme.colors.base.secondary;
  if (item.kind === "message") return theme.colors.base.primary;
  return theme.colors.stroke.subtle;
}

export function ActivityScreen() {
  const { useSidebar } = useLayoutContext();
  const { items, refresh } = useActivity();
  const [runtimeState, setRuntimeState] = React.useState<RuntimeSupervisorState | null>(null);
  const [bridgeState, setBridgeState] = React.useState<{
    queueSize: number;
    alwaysOn: boolean;
    runtimeReady: boolean;
    daemonUp: boolean;
    telegramSeenCount: number;
    webhookSuccessCount: number;
    webhookFailCount: number;
    lastEventNote: string;
  } | null>(null);
  const [showRaw, setShowRaw] = useState(false);

  useEffect(() => {
    refresh();
    void getRuntimeSupervisorState().then(setRuntimeState);
    void getAndroidRuntimeBridgeStatus().then(setBridgeState);
  }, [refresh]);

  useFocusEffect(
    React.useCallback(() => {
      void refresh();
      void getRuntimeSupervisorState().then(setRuntimeState);
      void getAndroidRuntimeBridgeStatus().then(setBridgeState);
    }, [refresh])
  );

  if (useSidebar) {
    const statusInfo = runtimeStatusLabel(runtimeState);
    return (
      <Screen>
        <ScrollView
          contentContainerStyle={{
            paddingHorizontal: theme.spacing.lg,
            paddingTop: theme.spacing.xl,
            paddingBottom: 40,
            gap: theme.spacing.md,
          }}
        >
          <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
            <Text testID="screen-activity" variant="display">Activity</Text>
            <Pressable
              onPress={() => setShowRaw((v) => !v)}
              style={{
                paddingHorizontal: 10,
                paddingVertical: 6,
                borderRadius: theme.radii.md,
                backgroundColor: theme.colors.surface.panel,
                borderWidth: 1,
                borderColor: theme.colors.stroke.subtle,
              }}
            >
              <Text variant="label">{showRaw ? "Hide raw" : "Show raw"}</Text>
            </Pressable>
          </View>

          {/* Runtime status card */}
          <View
            style={{
              padding: theme.spacing.lg,
              borderRadius: theme.radii.xl,
              backgroundColor: theme.colors.surface.raised,
              borderWidth: 1,
              borderColor: theme.colors.stroke.subtle,
              gap: theme.spacing.xs,
            }}
          >
            <Text variant="label">ZeroClaw Runtime</Text>
            <View style={{ flexDirection: "row", alignItems: "center", gap: theme.spacing.sm }}>
              <Text style={{ fontSize: 18 }}>{statusInfo.dot}</Text>
              <Text variant="title">{statusInfo.text}</Text>
              {runtimeState ? (
                <Text variant="muted" style={{ marginLeft: 4 }}>
                  {runtimeState.restartCount > 0 ? `• ${runtimeState.restartCount} restart${runtimeState.restartCount > 1 ? "s" : ""}` : ""}
                </Text>
              ) : null}
            </View>
            {statusInfo.reason ? <Text variant="muted">{statusInfo.reason}</Text> : null}
            {showRaw && runtimeState ? (
              <Text variant="mono" style={{ marginTop: 4, color: theme.colors.base.textMuted }}>
                {`status=${runtimeState.status} | reason=${runtimeState.degradeReason} | restarts=${runtimeState.restartCount}`}
              </Text>
            ) : null}
            {showRaw && bridgeState ? (
              <Text variant="mono" style={{ marginTop: 4, color: theme.colors.base.textMuted }}>
                {`queue=${bridgeState.queueSize}, daemon=${bridgeState.daemonUp ? "up" : "down"}, tg_seen=${bridgeState.telegramSeenCount}`}
              </Text>
            ) : null}
          </View>

          {/* Activity items */}
          {items.length === 0 ? (
            <View
              style={{
                padding: theme.spacing.lg,
                borderRadius: theme.radii.lg,
                backgroundColor: theme.colors.surface.raised,
              }}
            >
              <Text variant="body">No activity yet.</Text>
            </View>
          ) : (
            items.map((it) => (
              <View
                key={it.id}
                style={{
                  minHeight: 80,
                  paddingVertical: theme.spacing.md,
                  paddingHorizontal: theme.spacing.lg,
                  borderRadius: theme.radii.lg,
                  backgroundColor: theme.colors.surface.raised,
                  borderWidth: 1,
                  borderColor: theme.colors.stroke.subtle,
                  borderLeftWidth: 4,
                  borderLeftColor: activityBorderColor(it),
                  gap: 4,
                  justifyContent: "center",
                }}
              >
                <Text variant="bodyMedium" style={{ fontSize: 18 }}>{it.title}</Text>
                {it.detail && !showRaw ? (
                  <Text variant="muted" numberOfLines={2} style={{ fontSize: 14 }}>{it.detail}</Text>
                ) : null}
                {showRaw ? (
                  <Text variant="mono" style={{ color: theme.colors.base.textMuted, fontSize: 11 }}>
                    {`${it.source} / ${it.kind}${it.detail ? ` | ${it.detail}` : ""}`}
                  </Text>
                ) : null}
                <Text variant="muted" style={{ fontSize: 12 }}>{new Date(it.ts).toLocaleTimeString()}</Text>
              </View>
            ))
          )}
        </ScrollView>
      </Screen>
    );
  }

  return (
    <Screen>
      <ScrollView contentContainerStyle={{ paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.xl, paddingBottom: 140 }}>
        <Text testID="screen-activity" variant="display">Activity</Text>
        <Text variant="muted" style={{ marginTop: theme.spacing.xs }}>
          Agent actions, messages, and runtime logs.
        </Text>

        <View style={{ marginTop: theme.spacing.lg, gap: theme.spacing.sm }}>
          <View
            style={{
              padding: theme.spacing.md,
              borderRadius: theme.radii.lg,
              backgroundColor: theme.colors.surface.raised,
              borderWidth: 1,
              borderColor: theme.colors.stroke.subtle,
            }}
          >
            <Text variant="bodyMedium">ZeroClaw Runtime</Text>
            <Text variant="mono" style={{ marginTop: 6, color: theme.colors.base.textMuted }}>
              {runtimeState
                ? `status=${runtimeState.status} | reason=${runtimeState.degradeReason} | restarts=${runtimeState.restartCount}`
                : "status=unknown"}
            </Text>
            {runtimeState?.components?.length ? (
              <Text variant="muted" style={{ marginTop: 4 }}>
                {runtimeState.components.join(", ")}
              </Text>
            ) : (
              <Text variant="muted" style={{ marginTop: 4 }}>
                No runtime components active.
              </Text>
            )}
            {runtimeState?.missingConfig?.length ? (
              <Text variant="muted" style={{ marginTop: 4 }}>
                Missing config: {runtimeState.missingConfig.join(", ")}
              </Text>
            ) : null}
            {bridgeState ? (
              <Text variant="muted" style={{ marginTop: 4 }}>
                Native bridge: queue={bridgeState.queueSize}, always_on={bridgeState.alwaysOn ? "on" : "off"}, runtime_ready={bridgeState.runtimeReady ? "yes" : "no"}, daemon_up={bridgeState.daemonUp ? "yes" : "no"}, telegram_seen={bridgeState.telegramSeenCount}, handled_ok={bridgeState.webhookSuccessCount}, handled_fail={bridgeState.webhookFailCount}
              </Text>
            ) : null}
            {bridgeState?.lastEventNote ? (
              <Text variant="muted" style={{ marginTop: 4 }}>
                Last bridge event: {bridgeState.lastEventNote}
              </Text>
            ) : null}
          </View>

          {items.length === 0 ? (
            <View style={{ padding: theme.spacing.lg, borderRadius: theme.radii.lg, backgroundColor: theme.colors.surface.raised }}>
              <Text variant="body">No activity yet.</Text>
              <Text variant="muted" style={{ marginTop: theme.spacing.xs }}>
                Open chat or settings to generate activity.
              </Text>
            </View>
          ) : (
            items.map((it) => (
              <View
                key={it.id}
                style={{
                  padding: theme.spacing.md,
                  borderRadius: theme.radii.lg,
                  backgroundColor: theme.colors.surface.raised,
                  borderWidth: 1,
                  borderColor: theme.colors.stroke.subtle,
                }}
              >
                <Text variant="bodyMedium">{it.title}</Text>
                <Text variant="mono" style={{ marginTop: 6, color: theme.colors.base.textMuted }}>
                  {`${it.source} / ${it.kind}`}
                </Text>
                {it.detail ? (
                  <Text variant="muted" style={{ marginTop: 4 }}>
                    {it.detail}
                  </Text>
                ) : null}
                <Text variant="mono" style={{ marginTop: 10, color: theme.colors.base.textMuted }}>
                  {new Date(it.ts).toLocaleString()}
                </Text>
              </View>
            ))
          )}
        </View>
      </ScrollView>
    </Screen>
  );
}
