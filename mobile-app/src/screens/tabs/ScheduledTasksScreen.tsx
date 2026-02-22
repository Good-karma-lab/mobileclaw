import React, { useCallback, useEffect, useState } from "react";
import { Alert, Pressable, ScrollView, View } from "react-native";
import { useNavigation } from "@react-navigation/native";

import { Screen } from "../../../ui/primitives/Screen";
import { Text } from "../../../ui/primitives/Text";
import { theme } from "../../../ui/theme";
import { loadSecurityConfig, loadAgentConfig } from "../../state/mobileclaw";
import { useActivity, type ActivityItem } from "../../state/activity";

type GatewayHealth = "checking" | "ok" | "error";

interface CronJob {
  id: string;
  name: string | null;
  expression: string;
  prompt: string | null;
  command: string;
  enabled: boolean;
  created_at: string;
  last_run: string | null;
  next_run: string;
  last_status: string | null;
}

export function ScheduledTasksScreen() {
  const navigation = useNavigation<any>();
  const { items: allItems, refresh } = useActivity();
  const [callHooksEnabled, setCallHooksEnabled] = useState(false);
  const [smsHooksEnabled, setSmsHooksEnabled] = useState(false);
  const [gatewayHealth, setGatewayHealth] = useState<GatewayHealth>("checking");
  const [cronJobs, setCronJobs] = useState<CronJob[]>([]);
  const [cronLoading, setCronLoading] = useState(false);
  const [gatewayUrl, setGatewayUrl] = useState("http://127.0.0.1:8000");

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const [security, agentCfg] = await Promise.all([
        loadSecurityConfig(),
        loadAgentConfig(),
      ]);
      if (cancelled) return;
      setCallHooksEnabled(security.incomingCallHooks);
      setSmsHooksEnabled(security.incomingSmsHooks);
      if (agentCfg.platformUrl) setGatewayUrl(agentCfg.platformUrl);
    })();
    return () => { cancelled = true; };
  }, []);

  const checkGateway = useCallback(() => {
    setGatewayHealth("checking");
    (async () => {
      try {
        const res = await Promise.race([
          fetch(`${gatewayUrl}/health`),
          new Promise<never>((_, reject) => setTimeout(() => reject(new Error("timeout")), 3000)),
        ]);
        setGatewayHealth(res.ok ? "ok" : "error");
      } catch {
        setGatewayHealth("error");
      }
    })();
  }, [gatewayUrl]);

  useEffect(() => {
    checkGateway();
  }, [checkGateway]);

  const fetchCronJobs = useCallback(() => {
    setCronLoading(true);
    (async () => {
      try {
        const res = await Promise.race([
          fetch(`${gatewayUrl}/cron/jobs`),
          new Promise<never>((_, reject) => setTimeout(() => reject(new Error("timeout")), 5000)),
        ]);
        if (res.ok) {
          const data = (await res.json()) as { jobs: CronJob[] };
          setCronJobs(data.jobs ?? []);
        }
      } catch {
        // gateway offline — leave existing list
      } finally {
        setCronLoading(false);
      }
    })();
  }, [gatewayUrl]);

  useEffect(() => {
    fetchCronJobs();
  }, [fetchCronJobs]);

  const deleteJob = useCallback((job: CronJob) => {
    const label = job.name ?? job.id;
    Alert.alert(
      "Delete scheduled task?",
      `Remove "${label}"? It will no longer run.`,
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Delete",
          style: "destructive",
          onPress: () => {
            (async () => {
              try {
                await fetch(`${gatewayUrl}/cron/jobs/${job.id}`, { method: "DELETE" });
                setCronJobs((prev) => prev.filter((j) => j.id !== job.id));
              } catch {
                Alert.alert("Error", "Failed to delete task. Is the gateway online?");
              }
            })();
          },
        },
      ],
    );
  }, [gatewayUrl]);

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
            <Text variant="label">Gateway ({gatewayUrl})</Text>
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

        {/* Scheduled tasks (cron jobs) */}
        <View>
          <View
            style={{
              flexDirection: "row",
              alignItems: "center",
              justifyContent: "space-between",
              marginBottom: theme.spacing.sm,
            }}
          >
            <Text variant="heading">Scheduled Tasks</Text>
            <Pressable
              onPress={fetchCronJobs}
              style={{
                paddingHorizontal: 8,
                paddingVertical: 4,
                borderRadius: theme.radii.sm,
                backgroundColor: theme.colors.surface.raised,
                borderWidth: 1,
                borderColor: theme.colors.stroke.subtle,
              }}
            >
              <Text variant="caption">{cronLoading ? "Loading…" : "Refresh"}</Text>
            </Pressable>
          </View>
          {cronJobs.length === 0 ? (
            <View
              style={{
                backgroundColor: theme.colors.surface.panel,
                borderRadius: theme.radii.md,
                borderWidth: 1,
                borderColor: theme.colors.stroke.subtle,
                padding: theme.spacing.md,
              }}
            >
              <Text variant="muted">
                {cronLoading ? "Loading scheduled tasks…" : "No scheduled tasks. Ask the agent to set up reminders or automations."}
              </Text>
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
              {cronJobs.map((job, idx) => (
                <View key={job.id}>
                  {idx > 0 && <View style={{ height: 1, backgroundColor: theme.colors.stroke.subtle }} />}
                  <View style={{ padding: theme.spacing.md }}>
                    <View style={{ flexDirection: "row", alignItems: "flex-start", justifyContent: "space-between" }}>
                      <View style={{ flex: 1, marginRight: 8 }}>
                        <Text variant="label">{job.name ?? job.id}</Text>
                        <Text variant="caption" style={{ color: theme.colors.base.textMuted, marginTop: 2 }}>
                          {job.expression}
                        </Text>
                        {(job.prompt ?? job.command) ? (
                          <Text variant="muted" style={{ marginTop: 4 }} numberOfLines={2}>
                            {job.prompt ?? job.command}
                          </Text>
                        ) : null}
                        {job.next_run ? (
                          <Text variant="caption" style={{ color: theme.colors.base.textMuted, marginTop: 4 }}>
                            Next: {new Date(job.next_run).toLocaleString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })}
                          </Text>
                        ) : null}
                        {job.last_status ? (
                          <Text
                            variant="caption"
                            style={{
                              color: job.last_status === "ok" ? theme.colors.base.primary : "#e05252",
                              marginTop: 2,
                            }}
                          >
                            Last: {job.last_status}
                          </Text>
                        ) : null}
                      </View>
                      <Pressable
                        onPress={() => deleteJob(job)}
                        style={{
                          paddingHorizontal: 10,
                          paddingVertical: 6,
                          borderRadius: theme.radii.sm,
                          backgroundColor: theme.colors.surface.raised,
                          borderWidth: 1,
                          borderColor: "#e05252",
                        }}
                      >
                        <Text variant="caption" style={{ color: "#e05252" }}>Delete</Text>
                      </Pressable>
                    </View>
                  </View>
                </View>
              ))}
            </View>
          )}
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
