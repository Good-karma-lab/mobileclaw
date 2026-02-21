import React, { useEffect, useRef, useState } from "react";
import { FlatList, Modal, Pressable, ScrollView, Switch, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { Screen } from "../../../ui/primitives/Screen";
import { Text } from "../../../ui/primitives/Text";
import { theme } from "../../../ui/theme";
import { addActivity } from "../../state/activity";
import {
  DEFAULT_INTEGRATIONS,
  type IntegrationsConfig,
  loadIntegrationsConfig,
  saveIntegrationsConfig,
} from "../../state/mobileclaw";
import { applyRuntimeSupervisorConfig } from "../../runtime/supervisor";
import { useLayoutContext } from "../../state/layout";

type TileId = "telegram" | "discord" | "slack" | "whatsapp" | "composio";

function IntegrationCard(props: {
  name: string;
  enabled: boolean;
  expanded: boolean;
  onExpandToggle: () => void;
  onToggle: (next: boolean) => void;
  instructions: string[];
  children: React.ReactNode;
}) {
  return (
    <View
      style={{
        padding: theme.spacing.lg,
        borderRadius: theme.radii.xl,
        backgroundColor: theme.colors.surface.raised,
        borderWidth: 1,
        borderColor: theme.colors.stroke.subtle,
        gap: theme.spacing.sm,
      }}
    >
      <Pressable onPress={props.onExpandToggle} style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
        <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
          <Text variant="title">{props.name}</Text>
          {props.enabled ? <Ionicons name="checkmark-circle" size={16} color={theme.colors.base.secondary} /> : null}
        </View>
        <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
          <Switch value={props.enabled} onValueChange={props.onToggle} />
          <Ionicons name={props.expanded ? "chevron-up" : "chevron-down"} size={16} color={theme.colors.base.textMuted} />
        </View>
      </Pressable>
      {props.enabled ? (
        <>
          {props.children}
          {props.expanded ? (
            <View style={{ marginTop: 4, gap: 4 }}>
              <Text variant="label">Setup guide</Text>
              {props.instructions.map((step) => (
                <Text key={`${props.name}-${step}`} variant="muted">
                  - {step}
                </Text>
              ))}
            </View>
          ) : null}
        </>
      ) : (
        <Text variant="muted">Disabled</Text>
      )}
    </View>
  );
}

function SecretField(props: { label: string; value: string; onChangeText: (value: string) => void }) {
  return (
    <View style={{ gap: 6 }}>
      <Text variant="label">{props.label}</Text>
      <TextInput
        value={props.value}
        onChangeText={props.onChangeText}
        secureTextEntry
        style={{
          minHeight: 56,
          borderRadius: theme.radii.lg,
          padding: theme.spacing.md,
          backgroundColor: theme.colors.surface.panel,
          borderWidth: 1,
          borderColor: theme.colors.stroke.subtle,
          color: theme.colors.base.text,
          fontFamily: theme.typography.body,
        }}
      />
    </View>
  );
}

export function IntegrationsScreen() {
  const { useSidebar, width, sidebarWidth } = useLayoutContext();
  const [form, setForm] = useState<IntegrationsConfig>(DEFAULT_INTEGRATIONS);
  const [saveStatus, setSaveStatus] = useState("Loading...");
  const [telegramLookupStatus, setTelegramLookupStatus] = useState("");
  const [telegramLookupBusy, setTelegramLookupBusy] = useState(false);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [sheetId, setSheetId] = useState<TileId | null>(null);
  const hydratedRef = useRef(false);

  const toggleExpanded = (key: string) => {
    setExpanded((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const setEnabled = (key: string, next: boolean) => {
    if (next) {
      setExpanded((prev) => ({ ...prev, [key]: true }));
    }
  };

  const guides: Record<string, string[]> = {
    telegram: [
      "Create a bot via @BotFather, then paste your bot token below.",
      "Open Telegram chat with your bot and send any message (for example: /start).",
      "Tap Detect chat from Telegram updates to auto-fill Chat ID.",
      "Keep Telegram toggle ON. MobileClaw saves and applies runtime config automatically.",
    ],
    discord: [
      "Create a Discord app and bot in the Developer Portal.",
      "Enable Message Content intent and invite the bot to your server.",
      "Paste Bot token here and keep Discord toggle ON.",
      "Send a test message to verify the integration is working.",
    ],
    slack: [
      "Create a Slack app and add Bot token scopes.",
      "Install the app to workspace and copy Bot User OAuth token.",
      "Paste token here and keep Slack toggle ON.",
      "Invite the bot to a channel and send a quick test message.",
    ],
    whatsapp: [
      "Create a Meta app with WhatsApp Business integration.",
      "Set webhook endpoint to your ZeroClaw gateway URL.",
      "Paste access token here and keep WhatsApp toggle ON.",
      "Use Meta dashboard webhook test to confirm delivery.",
    ],
    composio: [
      "Create an account at app.composio.dev and generate API key.",
      "Paste key here and keep Composio toggle ON.",
      "Connect target SaaS tools in Composio dashboard.",
      "MobileClaw auto-saves and reloads runtime tool configuration.",
    ],
  };

  const detectTelegramChatId = async () => {
    const token = form.telegramBotToken.trim();
    if (!token) {
      setTelegramLookupStatus("Paste Bot token first.");
      return;
    }

    setTelegramLookupBusy(true);
    setTelegramLookupStatus("Checking latest Telegram updates...");

    try {
      const response = await fetch(`https://api.telegram.org/bot${token}/getUpdates`);
      const payload = (await response.json()) as {
        ok?: boolean;
        description?: string;
        result?: Array<{
          message?: { chat?: { id?: number | string } };
          edited_message?: { chat?: { id?: number | string } };
          channel_post?: { chat?: { id?: number | string } };
        }>;
      };

      if (!response.ok || !payload.ok) {
        const detail = payload.description ? `: ${payload.description}` : "";
        setTelegramLookupStatus(`Telegram API request failed${detail}`);
        return;
      }

      const updates = Array.isArray(payload.result) ? payload.result : [];
      const match = [...updates]
        .reverse()
        .map((update) => update.message?.chat?.id ?? update.edited_message?.chat?.id ?? update.channel_post?.chat?.id)
        .find((chatId) => chatId !== undefined && chatId !== null);

      if (match === undefined) {
        setTelegramLookupStatus("No chat found yet. Send any message to your bot and try again.");
        return;
      }

      const detectedChatId = String(match);
      setForm((prev) => ({ ...prev, telegramChatId: detectedChatId }));
      setTelegramLookupStatus(`Detected chat ID: ${detectedChatId}`);
      await addActivity({
        kind: "action",
        source: "integrations",
        title: "Telegram chat ID detected",
        detail: `chat_id=${detectedChatId}`,
      });
    } catch (error) {
      setTelegramLookupStatus(error instanceof Error ? `Failed to detect chat ID: ${error.message}` : "Failed to detect chat ID.");
    } finally {
      setTelegramLookupBusy(false);
    }
  };

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const loaded = await loadIntegrationsConfig();
      if (!cancelled) {
        setForm(loaded);
        hydratedRef.current = true;
        setSaveStatus("Autosave enabled");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!hydratedRef.current) return;
    const timer = setTimeout(() => {
      void saveIntegrationsConfig(form);
      void applyRuntimeSupervisorConfig("integrations_saved");
      void addActivity({
        kind: "action",
        source: "integrations",
        title: "Integrations updated",
        detail: "Runtime supervisor applied latest integration config",
      });
      setSaveStatus("Saved locally");
    }, 300);
    return () => clearTimeout(timer);
  }, [form]);

  if (useSidebar) {
    const tiles: Array<{ id: TileId; title: string; enabled: boolean }> = [
      { id: "telegram", title: "Telegram", enabled: form.telegramEnabled },
      { id: "discord", title: "Discord", enabled: form.discordEnabled },
      { id: "slack", title: "Slack", enabled: form.slackEnabled },
      { id: "whatsapp", title: "WhatsApp", enabled: form.whatsappEnabled },
      { id: "composio", title: "Composio", enabled: form.composioEnabled },
    ];

    const availableWidth = width - sidebarWidth - 32;
    const numColumns = Math.min(4, Math.max(2, Math.floor(availableWidth / 200)));
    const tileSize = Math.floor((availableWidth - (numColumns - 1) * theme.spacing.md) / numColumns);

    const toggleTile = (id: TileId, next?: boolean) => {
      setForm((prev) => {
        const val = (field: boolean) => next !== undefined ? next : !field;
        if (id === "telegram") return { ...prev, telegramEnabled: val(prev.telegramEnabled) };
        if (id === "discord") return { ...prev, discordEnabled: val(prev.discordEnabled) };
        if (id === "slack") return { ...prev, slackEnabled: val(prev.slackEnabled) };
        if (id === "whatsapp") return { ...prev, whatsappEnabled: val(prev.whatsappEnabled) };
        return { ...prev, composioEnabled: val(prev.composioEnabled) };
      });
    };

    return (
      <Screen>
        <ScrollView contentContainerStyle={{ paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.xl, paddingBottom: 40, gap: theme.spacing.lg }}>
          <Text testID="screen-integrations" variant="display">Integrations</Text>
          <Text variant="muted">Tap a tile to configure and enable each integration.</Text>

          <FlatList
            data={tiles}
            numColumns={numColumns}
            key={String(numColumns)}
            scrollEnabled={false}
            keyExtractor={(tile) => tile.id}
            columnWrapperStyle={numColumns > 1 ? { gap: theme.spacing.md } : undefined}
            contentContainerStyle={{ gap: theme.spacing.md }}
            renderItem={({ item: tile }) => (
              <Pressable
                onPress={() => setSheetId(tile.id)}
                style={{
                  width: tileSize,
                  height: tileSize,
                  borderRadius: theme.radii.xl,
                  borderWidth: 1,
                  borderColor: tile.enabled ? theme.colors.base.secondary : theme.colors.stroke.subtle,
                  backgroundColor: tile.enabled ? theme.colors.surface.glass : theme.colors.surface.raised,
                  padding: theme.spacing.lg,
                  justifyContent: "space-between",
                }}
              >
                <Text variant="title" style={{ fontSize: 16 }}>{tile.title}</Text>
                <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
                  <Ionicons
                    name={tile.enabled ? "checkmark-circle" : "radio-button-off"}
                    size={22}
                    color={tile.enabled ? theme.colors.base.secondary : theme.colors.base.textMuted}
                  />
                  <Text variant="bodyMedium">{tile.enabled ? "Connected" : "Off"}</Text>
                </View>
              </Pressable>
            )}
          />
        </ScrollView>

        {/* Config bottom sheet */}
        <Modal
          animationType="slide"
          transparent
          visible={sheetId !== null}
          onRequestClose={() => setSheetId(null)}
        >
          <View style={{ flex: 1, justifyContent: "flex-end", backgroundColor: theme.colors.alpha.scrim }}>
            <View
              style={{
                maxHeight: "60%",
                padding: theme.spacing.lg,
                borderTopLeftRadius: theme.radii.xl,
                borderTopRightRadius: theme.radii.xl,
                backgroundColor: theme.colors.base.background,
                gap: theme.spacing.sm,
              }}
            >
              {/* Sheet header: title + enable toggle + close */}
              <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
                <Text variant="title">{tiles.find((t) => t.id === sheetId)?.title ?? ""} Setup</Text>
                <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
                  <Switch
                    value={tiles.find((t) => t.id === sheetId)?.enabled ?? false}
                    onValueChange={(next) => sheetId && toggleTile(sheetId, next)}
                  />
                  <Pressable onPress={() => setSheetId(null)}>
                    <Ionicons name="close" size={22} color={theme.colors.base.textMuted} />
                  </Pressable>
                </View>
              </View>
              <ScrollView contentContainerStyle={{ gap: theme.spacing.sm }}>
                {sheetId === "telegram" ? (
                  <>
                    <SecretField label="Bot token" value={form.telegramBotToken} onChangeText={(v) => setForm((p) => ({ ...p, telegramBotToken: v }))} />
                    <View style={{ gap: 6 }}>
                      <Text variant="label">Chat ID</Text>
                      <TextInput
                        value={form.telegramChatId}
                        onChangeText={(v) => setForm((p) => ({ ...p, telegramChatId: v }))}
                        placeholder="Auto-detect or enter manually"
                        placeholderTextColor={theme.colors.alpha.textPlaceholder}
                        keyboardType="numeric"
                        style={{
                          minHeight: 56,
                          borderRadius: theme.radii.lg,
                          padding: theme.spacing.md,
                          backgroundColor: theme.colors.surface.panel,
                          borderWidth: 1,
                          borderColor: theme.colors.stroke.subtle,
                          color: theme.colors.base.text,
                          fontFamily: theme.typography.body,
                        }}
                      />
                    </View>
                    <Pressable
                      onPress={() => { void detectTelegramChatId(); }}
                      disabled={telegramLookupBusy}
                      style={{
                        paddingVertical: 14,
                        borderRadius: theme.radii.lg,
                        alignItems: "center",
                        borderWidth: 1,
                        borderColor: theme.colors.stroke.subtle,
                        backgroundColor: telegramLookupBusy ? theme.colors.surface.panel : theme.colors.surface.raised,
                      }}
                    >
                      <Text variant="bodyMedium">{telegramLookupBusy ? "Detecting chat..." : "Detect Chat ID from updates"}</Text>
                    </Pressable>
                    {telegramLookupStatus ? <Text variant="muted">{telegramLookupStatus}</Text> : null}
                  </>
                ) : null}
                {sheetId === "discord" ? (
                  <SecretField label="Discord Bot token" value={form.discordBotToken} onChangeText={(v) => setForm((p) => ({ ...p, discordBotToken: v }))} />
                ) : null}
                {sheetId === "slack" ? (
                  <SecretField label="Slack Bot token" value={form.slackBotToken} onChangeText={(v) => setForm((p) => ({ ...p, slackBotToken: v }))} />
                ) : null}
                {sheetId === "whatsapp" ? (
                  <SecretField label="WhatsApp access token" value={form.whatsappAccessToken} onChangeText={(v) => setForm((p) => ({ ...p, whatsappAccessToken: v }))} />
                ) : null}
                {sheetId === "composio" ? (
                  <SecretField label="Composio API key" value={form.composioApiKey} onChangeText={(v) => setForm((p) => ({ ...p, composioApiKey: v }))} />
                ) : null}
                {/* Setup guide */}
                {sheetId && guides[sheetId] ? (
                  <View style={{ marginTop: theme.spacing.sm, gap: 6, padding: theme.spacing.md, borderRadius: theme.radii.lg, backgroundColor: theme.colors.surface.panel }}>
                    <Text variant="label">Setup guide</Text>
                    {guides[sheetId].map((step) => (
                      <Text key={step} variant="muted">• {step}</Text>
                    ))}
                  </View>
                ) : null}
              </ScrollView>
            </View>
          </View>
        </Modal>
      </Screen>
    );
  }

  return (
    <Screen>
      <ScrollView contentContainerStyle={{ paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.xl, paddingBottom: 140, gap: theme.spacing.lg }}>
        <View>
          <Text testID="screen-integrations" variant="display">Integrations</Text>
          <Text variant="muted" style={{ marginTop: theme.spacing.xs }}>
            Configure Telegram, Discord, Slack, WhatsApp, and Composio.
          </Text>
          <Text variant="mono" style={{ marginTop: theme.spacing.sm, color: theme.colors.base.textMuted }}>
            {saveStatus}
          </Text>
        </View>

        <IntegrationCard
          name="Telegram"
          enabled={form.telegramEnabled}
          expanded={!!expanded.telegram}
          onExpandToggle={() => toggleExpanded("telegram")}
          onToggle={(next) => {
            setForm((prev) => ({ ...prev, telegramEnabled: next }));
            setEnabled("telegram", next);
          }}
          instructions={guides.telegram}
        >
          <SecretField label="Bot token" value={form.telegramBotToken} onChangeText={(value) => setForm((prev) => ({ ...prev, telegramBotToken: value }))} />
          <Pressable
            onPress={() => { void detectTelegramChatId(); }}
            disabled={telegramLookupBusy}
            style={{
              paddingVertical: 10,
              borderRadius: theme.radii.lg,
              alignItems: "center",
              borderWidth: 1,
              borderColor: theme.colors.stroke.subtle,
              backgroundColor: telegramLookupBusy ? theme.colors.surface.panel : theme.colors.surface.raised,
            }}
          >
            <Text variant="bodyMedium">{telegramLookupBusy ? "Detecting chat..." : "Detect chat from Telegram updates"}</Text>
          </Pressable>
          {telegramLookupStatus ? (
            <Text variant="muted" style={{ marginTop: 2 }}>
              {telegramLookupStatus}
            </Text>
          ) : null}
        </IntegrationCard>

        <IntegrationCard
          name="Discord"
          enabled={form.discordEnabled}
          expanded={!!expanded.discord}
          onExpandToggle={() => toggleExpanded("discord")}
          onToggle={(next) => {
            setForm((prev) => ({ ...prev, discordEnabled: next }));
            setEnabled("discord", next);
          }}
          instructions={guides.discord}
        >
          <SecretField label="Bot token" value={form.discordBotToken} onChangeText={(value) => setForm((prev) => ({ ...prev, discordBotToken: value }))} />
        </IntegrationCard>

        <IntegrationCard
          name="Slack"
          enabled={form.slackEnabled}
          expanded={!!expanded.slack}
          onExpandToggle={() => toggleExpanded("slack")}
          onToggle={(next) => {
            setForm((prev) => ({ ...prev, slackEnabled: next }));
            setEnabled("slack", next);
          }}
          instructions={guides.slack}
        >
          <SecretField label="Bot token" value={form.slackBotToken} onChangeText={(value) => setForm((prev) => ({ ...prev, slackBotToken: value }))} />
        </IntegrationCard>

        <IntegrationCard
          name="WhatsApp"
          enabled={form.whatsappEnabled}
          expanded={!!expanded.whatsapp}
          onExpandToggle={() => toggleExpanded("whatsapp")}
          onToggle={(next) => {
            setForm((prev) => ({ ...prev, whatsappEnabled: next }));
            setEnabled("whatsapp", next);
          }}
          instructions={guides.whatsapp}
        >
          <SecretField label="Access token" value={form.whatsappAccessToken} onChangeText={(value) => setForm((prev) => ({ ...prev, whatsappAccessToken: value }))} />
        </IntegrationCard>

        <IntegrationCard
          name="Composio"
          enabled={form.composioEnabled}
          expanded={!!expanded.composio}
          onExpandToggle={() => toggleExpanded("composio")}
          onToggle={(next) => {
            setForm((prev) => ({ ...prev, composioEnabled: next }));
            setEnabled("composio", next);
          }}
          instructions={guides.composio}
        >
          <SecretField label="API key" value={form.composioApiKey} onChangeText={(value) => setForm((prev) => ({ ...prev, composioApiKey: value }))} />
        </IntegrationCard>
      </ScrollView>
    </Screen>
  );
}
