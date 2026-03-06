import React, { useEffect, useMemo, useRef, useState } from "react";
import { ScrollView, TextInput, View, Pressable, Modal, Switch } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";

import { Screen } from "../../../ui/primitives/Screen";
import { Text } from "../../../ui/primitives/Text";
import { theme } from "../../../ui/theme";
import { addActivity } from "../../state/activity";
import { fetchOpenRouterModels } from "../../api/mobileclaw";
import {
  type AgentRuntimeConfig,
  type ProviderId,
  loadAgentConfig,
  saveAgentConfig,
  DEFAULT_AGENT_CONFIG,
} from "../../state/mobileclaw";
import { applyRuntimeSupervisorConfig } from "../../runtime/supervisor";
import { useLayoutContext } from "../../state/layout";
import { getModelDownloadUrl, getModelFileName, checkModelExists, downloadModel, downloadWhisperModel, checkWhisperModelExists } from "../../native/modelDownloader";

type ProviderPreset = {
  id: ProviderId;
  title: string;
  endpoint: string;
  model: string;
  supportsOauthToken: boolean;
  docsHint: string;
};

const MODELS_BY_PROVIDER: Record<ProviderId, string[]> = {
  local: ["Qwen/Qwen3.5-0.8B", "Qwen/Qwen3.5-2B"],
  ollama: ["gpt-oss:20b", "qwen2.5-coder:14b", "llama3.1:8b"],
  openrouter: ["minimax/minimax-m2.5"],
  openai: ["gpt-4.1-mini", "gpt-4.1", "gpt-4o-mini"],
  anthropic: ["claude-3-5-sonnet-latest", "claude-3-5-haiku-latest", "claude-3-opus-latest"],
  gemini: ["gemini-1.5-pro", "gemini-1.5-flash", "gemini-2.0-flash-exp"],
  copilot: ["gpt-4o-mini", "gpt-4.1", "claude-3-5-sonnet"],
  mistral: ["mistral-large-latest", "mistral-medium-latest", "mistral-small-latest"],
  deepseek: ["deepseek-chat", "deepseek-reasoner"],
  xai: ["grok-beta", "grok-vision-beta"],
  groq: ["llama-3.1-70b-versatile", "llama-3.1-8b-instant", "gemma2-9b-it"],
  together: ["meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo", "mistralai/Mixtral-8x7B-Instruct-v0.1"],
  fireworks: ["accounts/fireworks/models/llama-v3p1-70b-instruct"],
  perplexity: ["llama-3.1-sonar-large-128k-online", "llama-3.1-sonar-small-128k-online"],
  cohere: ["command-r-plus", "command-r"],
  minimax: ["MiniMax-Text-01", "abab6.5s-chat"],
  venice: ["llama-3.3-70b", "mistral-31-24b"],
  moonshot: ["moonshot-v1-8k", "moonshot-v1-32k"],
  glm: ["glm-4", "glm-4-air"],
  qwen: ["qwen-max", "qwen-plus", "qwen-turbo"],
  "lm-studio": ["local-model"],
};

const PROVIDERS: ProviderPreset[] = [
  { id: "local", title: "Local Inference (on-device)", endpoint: "", model: "Qwen/Qwen3.5-2B", supportsOauthToken: false, docsHint: "Runs AI model directly on your device. No API key or internet needed." },
  { id: "ollama", title: "Ollama (local)", endpoint: "http://10.0.2.2:11434", model: "gpt-oss:20b", supportsOauthToken: false, docsHint: "Local Ollama on host machine." },
  { id: "openrouter", title: "OpenRouter", endpoint: "https://openrouter.ai/api/v1", model: "minimax/minimax-m2.5", supportsOauthToken: false, docsHint: "Use OpenRouter API key." },
  { id: "openai", title: "OpenAI", endpoint: "https://api.openai.com/v1", model: "gpt-4.1-mini", supportsOauthToken: true, docsHint: "API key or OAuth access token." },
  { id: "anthropic", title: "Anthropic", endpoint: "https://api.anthropic.com/v1", model: "claude-3-5-sonnet-latest", supportsOauthToken: true, docsHint: "Anthropic key or supported OAuth token." },
  { id: "gemini", title: "Google Gemini", endpoint: "https://generativelanguage.googleapis.com/v1beta", model: "gemini-1.5-pro", supportsOauthToken: true, docsHint: "Gemini API key or OAuth token." },
  { id: "copilot", title: "GitHub Copilot", endpoint: "https://api.githubcopilot.com", model: "gpt-4o-mini", supportsOauthToken: true, docsHint: "Token for Copilot-enabled account." },
  { id: "mistral", title: "Mistral AI", endpoint: "https://api.mistral.ai/v1", model: "mistral-large-latest", supportsOauthToken: false, docsHint: "Mistral API key from console.mistral.ai." },
  { id: "deepseek", title: "DeepSeek", endpoint: "https://api.deepseek.com", model: "deepseek-chat", supportsOauthToken: false, docsHint: "DeepSeek API key from platform.deepseek.com." },
  { id: "xai", title: "xAI / Grok", endpoint: "https://api.x.ai", model: "grok-beta", supportsOauthToken: false, docsHint: "xAI API key from console.x.ai." },
  { id: "groq", title: "Groq", endpoint: "https://api.groq.com/openai", model: "llama-3.1-70b-versatile", supportsOauthToken: false, docsHint: "Groq API key from console.groq.com." },
  { id: "together", title: "Together AI", endpoint: "https://api.together.xyz", model: "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo", supportsOauthToken: false, docsHint: "Together API key from api.together.ai." },
  { id: "fireworks", title: "Fireworks AI", endpoint: "https://api.fireworks.ai/inference/v1", model: "accounts/fireworks/models/llama-v3p1-70b-instruct", supportsOauthToken: false, docsHint: "Fireworks API key from fireworks.ai." },
  { id: "perplexity", title: "Perplexity", endpoint: "https://api.perplexity.ai", model: "llama-3.1-sonar-large-128k-online", supportsOauthToken: false, docsHint: "Perplexity API key from perplexity.ai/settings." },
  { id: "cohere", title: "Cohere", endpoint: "https://api.cohere.com/compatibility", model: "command-r-plus", supportsOauthToken: false, docsHint: "Cohere API key from dashboard.cohere.com." },
  { id: "minimax", title: "MiniMax", endpoint: "https://api.minimaxi.com/v1", model: "MiniMax-Text-01", supportsOauthToken: false, docsHint: "MiniMax API key from platform.minimaxi.com." },
  { id: "venice", title: "Venice AI", endpoint: "https://api.venice.ai", model: "llama-3.3-70b", supportsOauthToken: false, docsHint: "Venice API key from venice.ai." },
  { id: "moonshot", title: "Moonshot / Kimi", endpoint: "https://api.moonshot.cn/v1", model: "moonshot-v1-8k", supportsOauthToken: false, docsHint: "Moonshot API key from platform.moonshot.cn." },
  { id: "glm", title: "GLM / Zhipu AI", endpoint: "https://open.bigmodel.cn/api/paas/v4", model: "glm-4", supportsOauthToken: false, docsHint: "Zhipu AI API key from bigmodel.cn." },
  { id: "qwen", title: "Qwen / Dashscope", endpoint: "https://dashscope.aliyuncs.com/compatible-mode/v1", model: "qwen-max", supportsOauthToken: false, docsHint: "Alibaba Cloud API key from dashscope.aliyuncs.com." },
  { id: "lm-studio", title: "LM Studio (local)", endpoint: "http://10.0.2.2:1234/v1", model: "local-model", supportsOauthToken: false, docsHint: "LM Studio running locally on host machine." },
];

function GlassCard({ children }: { children: React.ReactNode }) {
  return (
    <View style={{ padding: theme.spacing.lg, borderRadius: theme.radii.xl, backgroundColor: theme.colors.surface.raised, borderWidth: 1, borderColor: theme.colors.stroke.subtle, gap: theme.spacing.sm }}>
      {children}
    </View>
  );
}

function LabeledInput(props: {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
  secureTextEntry?: boolean;
  testID?: string;
  placeholder?: string;
}) {
  return (
    <View style={{ gap: 6 }}>
      <Text variant="label">{props.label}</Text>
      <TextInput
        testID={props.testID}
        value={props.value}
        onChangeText={props.onChangeText}
        secureTextEntry={props.secureTextEntry}
        placeholder={props.placeholder}
        placeholderTextColor={theme.colors.alpha.textPlaceholder}
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

export function SettingsScreen() {
  const { useSidebar, isTablet } = useLayoutContext();
  const navigation = useNavigation<any>();
  const [form, setForm] = useState<AgentRuntimeConfig>(DEFAULT_AGENT_CONFIG);
  const [saveStatus, setSaveStatus] = useState("Loading...");
  const [providerPickerOpen, setProviderPickerOpen] = useState(false);
  const [modelPickerOpen, setModelPickerOpen] = useState(false);
  const [modelSearch, setModelSearch] = useState("");
  const [openRouterModels, setOpenRouterModels] = useState<string[]>(MODELS_BY_PROVIDER.openrouter);
  const [openRouterLoading, setOpenRouterLoading] = useState(false);
  const [localModelStatus, setLocalModelStatus] = useState<"checking" | "not_downloaded" | "downloading" | "ready">("checking");
  const [localDownloadProgress, setLocalDownloadProgress] = useState(0);
  const [whisperModelStatus, setWhisperModelStatus] = useState<"checking" | "not_downloaded" | "downloading" | "ready">("checking");
  const [whisperDownloadProgress, setWhisperDownloadProgress] = useState(0);
  const [whisperPickerOpen, setWhisperPickerOpen] = useState(false);
  const hydratedRef = useRef(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const loaded = await loadAgentConfig();
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
    if (form.provider !== "local") return;
    let cancelled = false;
    (async () => {
      setLocalModelStatus("checking");
      try {
        const { exists, path } = await checkModelExists(form.model);
        if (cancelled) return;
        if (exists) {
          setLocalModelStatus("ready");
          if (path && form.localModelPath !== path) {
            setForm((prev) => ({ ...prev, localModelPath: path }));
          }
        } else {
          setLocalModelStatus("not_downloaded");
        }
      } catch {
        if (!cancelled) setLocalModelStatus("not_downloaded");
      }
    })();
    return () => { cancelled = true; };
  }, [form.provider, form.model]);

  // Check whisper model status
  useEffect(() => {
    if (form.voiceProvider !== "whisper") return;
    let cancelled = false;
    (async () => {
      setWhisperModelStatus("checking");
      try {
        const { exists, path } = await checkWhisperModelExists(form.whisperModel || "whisper-base");
        if (cancelled) return;
        if (exists) {
          setWhisperModelStatus("ready");
          if (path && form.whisperModelPath !== path) {
            setForm((prev) => ({ ...prev, whisperModelPath: path }));
          }
        } else {
          setWhisperModelStatus("not_downloaded");
        }
      } catch {
        if (!cancelled) setWhisperModelStatus("not_downloaded");
      }
    })();
    return () => { cancelled = true; };
  }, [form.voiceProvider, form.whisperModel]);

  const handleDownloadWhisperModel = async () => {
    const modelId = form.whisperModel || "whisper-base";
    setWhisperModelStatus("downloading");
    setWhisperDownloadProgress(0);
    try {
      const path = await downloadWhisperModel(modelId, (progress) => {
        setWhisperDownloadProgress(progress);
      });
      setWhisperModelStatus("ready");
      setForm((prev) => ({ ...prev, whisperModelPath: path }));
    } catch (error) {
      console.error("[SettingsScreen] Whisper model download failed:", error);
      setWhisperModelStatus("not_downloaded");
      void addActivity({ kind: "log", source: "settings", title: "Whisper model download failed", detail: error instanceof Error ? error.message : String(error) });
    }
  };

  const handleDownloadModel = async () => {
    console.log("[SettingsScreen] Starting model download for:", form.model);
    setLocalModelStatus("downloading");
    setLocalDownloadProgress(0);
    try {
      const path = await downloadModel(form.model, (progress) => {
        setLocalDownloadProgress(progress);
      });
      setLocalModelStatus("ready");
      setForm((prev) => ({ ...prev, localModelPath: path }));
    } catch (error) {
      console.error("[SettingsScreen] Model download failed:", error);
      setLocalModelStatus("not_downloaded");
      void addActivity({ kind: "log", source: "settings", title: "Model download failed", detail: error instanceof Error ? error.message : String(error) });
    }
  };

  const selected = useMemo(() => PROVIDERS.find((p) => p.id === form.provider) || PROVIDERS[0], [form.provider]);
  const providerModels = useMemo(() => {
    if (form.provider === "openrouter") return openRouterModels;
    return MODELS_BY_PROVIDER[form.provider] || [];
  }, [form.provider, openRouterModels]);
  const filteredModels = useMemo(() => {
    const query = modelSearch.trim().toLowerCase();
    if (!query) return providerModels;
    return providerModels.filter((m) => m.toLowerCase().includes(query));
  }, [providerModels, modelSearch]);

  const onProvider = (provider: ProviderId) => {
    const preset = PROVIDERS.find((p) => p.id === provider) || PROVIDERS[0];
    const defaultModel = MODELS_BY_PROVIDER[provider]?.[0] || preset.model;
    setForm((prev) => ({ ...prev, provider, apiUrl: preset.endpoint, model: defaultModel, authMode: "api_key" }));
    void addActivity({ kind: "action", source: "settings", title: "Provider changed", detail: provider });
    setProviderPickerOpen(false);
  };

  useEffect(() => {
    if (!hydratedRef.current) return;
    if (form.provider !== "openrouter") return;
    let cancelled = false;
    setOpenRouterLoading(true);
    (async () => {
      try {
        const token = form.apiKey || form.oauthAccessToken;
        const models = await fetchOpenRouterModels(token);
        if (!cancelled && models.length > 0) setOpenRouterModels(models);
      } catch (error) {
        if (!cancelled) {
          void addActivity({ kind: "log", source: "settings", title: "OpenRouter model list sync failed", detail: error instanceof Error ? error.message : "Unknown error" });
        }
      } finally {
        if (!cancelled) setOpenRouterLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [form.provider, form.apiKey, form.oauthAccessToken]);

  useEffect(() => {
    if (!hydratedRef.current) return;
    const timer = setTimeout(() => {
      const normalized = { ...form, temperature: Math.max(0, Math.min(2, Number(form.temperature) || 0.1)) };
      void (async () => {
        try {
          await saveAgentConfig(normalized);
          await applyRuntimeSupervisorConfig("settings_saved");
          setSaveStatus("Saved and applied");
        } catch (error) {
          setSaveStatus("Saved locally (apply failed)");
          void addActivity({
            kind: "log",
            source: "settings",
            title: "Runtime apply failed",
            detail: error instanceof Error ? error.message : "Unknown error",
          });
        }
      })();
    }, 300);
    return () => clearTimeout(timer);
  }, [form]);

  const settingsContent = (
    <>
      <GlassCard>
        <Text variant="title">Provider</Text>
        <Text variant="muted">Dropdown list - tap to choose provider.</Text>
        <Pressable
          testID="provider-dropdown"
          onPress={() => setProviderPickerOpen(true)}
          style={{ paddingVertical: 12, paddingHorizontal: 14, borderRadius: theme.radii.lg, borderWidth: 1, borderColor: theme.colors.stroke.subtle, backgroundColor: theme.colors.surface.panel }}
        >
          <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
            <Text variant="bodyMedium">{selected.title}</Text>
            <Ionicons name="chevron-down" size={18} color={theme.colors.base.textMuted} />
          </View>
        </Pressable>

        <Pressable
          testID="model-dropdown"
          onPress={() => setModelPickerOpen(true)}
          style={{ paddingVertical: 12, paddingHorizontal: 14, borderRadius: theme.radii.lg, borderWidth: 1, borderColor: theme.colors.stroke.subtle, backgroundColor: theme.colors.surface.panel }}
        >
          <Text variant="label">Model</Text>
          <Text variant="muted" style={{ marginTop: 4 }}>Searchable dropdown list</Text>
          <Text variant="bodyMedium" style={{ marginTop: 6 }}>{form.model}</Text>
          <View style={{ position: "absolute", right: 12, top: 12 }}>
            <Ionicons name="chevron-down" size={18} color={theme.colors.base.textMuted} />
          </View>
        </Pressable>
        {form.provider !== "local" ? (
          <>
            <LabeledInput testID="settings-endpoint" label="Endpoint" value={form.apiUrl} onChangeText={(value) => setForm((prev) => ({ ...prev, apiUrl: value }))} />
            <LabeledInput
              testID="settings-api-key"
              label={form.provider === "openrouter" ? "OpenRouter API key" : "API key"}
              placeholder="Enter API key"
              value={form.apiKey}
              onChangeText={(value) => setForm((prev) => ({ ...prev, apiKey: value, authMode: "api_key" }))}
              secureTextEntry
            />
          </>
        ) : null}
        {form.provider === "local" ? (
          <View style={{ gap: theme.spacing.sm, padding: theme.spacing.md, borderRadius: theme.radii.lg, backgroundColor: theme.colors.surface.panel, borderWidth: 1, borderColor: theme.colors.stroke.subtle }}>
            <Text variant="label">On-Device Model</Text>
            <Text variant="bodyMedium">Model: {getModelFileName(form.model)}</Text>
            <Text variant="muted">
              {localModelStatus === "checking" ? "Checking model status..." :
               localModelStatus === "not_downloaded" ? "Not downloaded" :
               localModelStatus === "downloading" ? `Downloading ${Math.round(localDownloadProgress)}%` :
               "Ready"}
            </Text>
            {localModelStatus === "not_downloaded" ? (
              <Pressable
                onPress={handleDownloadModel}
                style={{ paddingVertical: 12, borderRadius: theme.radii.lg, alignItems: "center", backgroundColor: theme.colors.base.primary }}
              >
                <Text variant="bodyMedium" style={{ color: "#fff" }}>Download Model</Text>
              </Pressable>
            ) : null}
            {localModelStatus === "downloading" ? (
              <View style={{ height: 4, borderRadius: 2, backgroundColor: theme.colors.stroke.subtle }}>
                <View style={{ height: 4, borderRadius: 2, backgroundColor: theme.colors.base.primary, width: `${localDownloadProgress}%` as any }} />
              </View>
            ) : null}
            <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: theme.spacing.sm }}>
              <View style={{ flex: 1 }}>
                <Text variant="label">Thinking Mode</Text>
                <Text variant="muted">Model reasons step-by-step before answering. Better quality, slower.</Text>
              </View>
              <Switch
                testID="thinking-mode-toggle"
                value={form.thinkingMode}
                onValueChange={(value) => setForm((prev) => ({ ...prev, thinkingMode: value }))}
                trackColor={{ false: theme.colors.stroke.subtle, true: theme.colors.base.primary }}
              />
            </View>
          </View>
        ) : null}
        {selected.supportsOauthToken ? (
          <>
            <LabeledInput testID="settings-oauth-access-token" label="OAuth access token (optional)" value={form.oauthAccessToken} onChangeText={(value) => setForm((prev) => ({ ...prev, oauthAccessToken: value, authMode: value.trim() ? "oauth_token" : "api_key" }))} secureTextEntry />
            <LabeledInput label="OAuth refresh token (optional)" value={form.oauthRefreshToken} onChangeText={(value) => setForm((prev) => ({ ...prev, oauthRefreshToken: value }))} secureTextEntry />
            <LabeledInput label="OAuth expires at (epoch ms)" value={String(form.oauthExpiresAtMs || "")} onChangeText={(value) => setForm((prev) => ({ ...prev, oauthExpiresAtMs: Number(value) || 0 }))} />
            {form.provider === "openai" ? <LabeledInput label="Account id (optional)" value={form.accountId} onChangeText={(value) => setForm((prev) => ({ ...prev, accountId: value }))} /> : null}
            {form.provider === "copilot" ? <LabeledInput label="Enterprise URL (optional)" value={form.enterpriseUrl} onChangeText={(value) => setForm((prev) => ({ ...prev, enterpriseUrl: value }))} /> : null}
          </>
        ) : null}
        <LabeledInput label="Temperature (0-2)" value={String(form.temperature)} onChangeText={(value) => setForm((prev) => ({ ...prev, temperature: Number(value) || 0.1 }))} />
        {form.provider === "openrouter" ? (
          <Text variant="muted">
            {openRouterLoading ? "Refreshing OpenRouter models..." : `OpenRouter model catalog loaded: ${openRouterModels.length} models`}
          </Text>
        ) : null}
        <Text variant="muted">{selected.docsHint}</Text>
      </GlassCard>

      <GlassCard>
        <Text variant="title">Advanced</Text>
        <Pressable
          testID="open-memory-screen"
          onPress={() => {
            const root = navigation.getParent();
            if (root) { root.navigate("Memory"); return; }
            navigation.navigate("Memory");
          }}
          style={{ paddingVertical: 12, paddingHorizontal: 14, borderRadius: theme.radii.lg, borderWidth: 1, borderColor: theme.colors.stroke.subtle, backgroundColor: theme.colors.surface.panel, marginBottom: theme.spacing.sm }}
        >
          <Text variant="bodyMedium">Open memory manager</Text>
        </Pressable>
        <Pressable
          testID="open-security-screen"
          onPress={() => {
            const root = navigation.getParent();
            if (root) { root.navigate("Security"); return; }
            navigation.navigate("Security");
          }}
          style={{ paddingVertical: 12, paddingHorizontal: 14, borderRadius: theme.radii.lg, borderWidth: 1, borderColor: theme.colors.stroke.subtle, backgroundColor: theme.colors.surface.panel }}
        >
          <Text variant="bodyMedium">Open security controls</Text>
        </Pressable>
      </GlassCard>

      <GlassCard>
        <Text variant="title">Voice Mode</Text>
        <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
          <View style={{ flex: 1 }}>
            <Text variant="label">Voice Provider</Text>
            <Text variant="muted">{form.voiceProvider === "whisper" ? "On-device (Whisper)" : "Cloud (Deepgram)"}</Text>
          </View>
          <Switch
            value={form.voiceProvider === "whisper"}
            onValueChange={(val) => setForm((prev) => ({ ...prev, voiceProvider: val ? "whisper" : "deepgram" }))}
            trackColor={{ false: theme.colors.stroke.subtle, true: theme.colors.base.primary }}
          />
        </View>
        {form.voiceProvider === "deepgram" ? (
          <>
            <Text variant="muted">Deepgram API key is required for cloud voice transcription.</Text>
            <LabeledInput label="Deepgram API key" testID="deepgram-key-input" value={form.deepgramApiKey} onChangeText={(value) => setForm((prev) => ({ ...prev, deepgramApiKey: value }))} secureTextEntry />
          </>
        ) : (
          <View style={{ gap: theme.spacing.sm }}>
            <Text variant="muted">On-device speech-to-text using Whisper. No internet required.</Text>
            <Pressable
              onPress={() => setWhisperPickerOpen(true)}
              style={{ paddingVertical: 12, paddingHorizontal: 16, borderRadius: theme.radii.lg, borderWidth: 1, borderColor: theme.colors.stroke.subtle, flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}
            >
              <View>
                <Text variant="label">Whisper Model</Text>
                <Text variant="bodyMedium">{form.whisperModel || "whisper-base"}</Text>
              </View>
              <Ionicons name="chevron-down" size={20} color={theme.colors.text.muted} />
            </Pressable>
            <Text variant="muted">
              {whisperModelStatus === "checking" ? "Checking..." :
               whisperModelStatus === "not_downloaded" ? "Not downloaded" :
               whisperModelStatus === "downloading" ? `Downloading ${Math.round(whisperDownloadProgress)}%` :
               "Ready"}
            </Text>
            {whisperModelStatus === "not_downloaded" ? (
              <Pressable
                onPress={handleDownloadWhisperModel}
                style={{ paddingVertical: 12, borderRadius: theme.radii.lg, alignItems: "center", backgroundColor: theme.colors.base.primary }}
              >
                <Text variant="bodyMedium" style={{ color: "#fff" }}>Download Whisper Model</Text>
              </Pressable>
            ) : null}
            {whisperModelStatus === "downloading" ? (
              <View style={{ height: 4, borderRadius: 2, backgroundColor: theme.colors.stroke.subtle }}>
                <View style={{ height: 4, borderRadius: 2, backgroundColor: theme.colors.base.primary, width: `${whisperDownloadProgress}%` as any }} />
              </View>
            ) : null}
          </View>
        )}
      </GlassCard>

      <GlassCard>
        <Text variant="title">Web Search</Text>
        <Text variant="muted">Brave Search API key enables rich web search results. Falls back to DuckDuckGo if not set.</Text>
        <LabeledInput
          label="Brave Search API key"
          testID="brave-api-key-input"
          value={form.braveApiKey}
          onChangeText={(value) => setForm((prev) => ({ ...prev, braveApiKey: value }))}
          secureTextEntry
        />
      </GlassCard>

      <Modal animationType="slide" transparent visible={providerPickerOpen} onRequestClose={() => setProviderPickerOpen(false)}>
        <View style={{ flex: 1, justifyContent: "flex-end", backgroundColor: theme.colors.alpha.scrim }}>
          <View style={{ maxHeight: "70%", padding: theme.spacing.lg, borderTopLeftRadius: theme.radii.xl, borderTopRightRadius: theme.radii.xl, backgroundColor: theme.colors.base.background, gap: theme.spacing.sm }}>
            <Text variant="title">Select provider</Text>
            <ScrollView contentContainerStyle={{ gap: theme.spacing.sm }}>
              {PROVIDERS.map((provider) => (
                <Pressable
                  key={provider.id}
                  testID={`provider-option-${provider.id}`}
                  onPress={() => onProvider(provider.id)}
                  style={{ paddingVertical: 12, paddingHorizontal: 14, borderRadius: theme.radii.lg, borderWidth: 1, borderColor: form.provider === provider.id ? theme.colors.base.primary : theme.colors.stroke.subtle, backgroundColor: form.provider === provider.id ? theme.colors.alpha.userBubbleBg : theme.colors.surface.panel }}
                >
                  <Text variant="bodyMedium">{provider.title}</Text>
                </Pressable>
              ))}
            </ScrollView>
            <Pressable onPress={() => setProviderPickerOpen(false)} style={{ paddingVertical: 12, borderRadius: theme.radii.lg, alignItems: "center", backgroundColor: theme.colors.surface.panel }}>
              <Text variant="bodyMedium">Close</Text>
            </Pressable>
          </View>
        </View>
      </Modal>

      <Modal animationType="slide" transparent visible={modelPickerOpen} onRequestClose={() => setModelPickerOpen(false)}>
        <View style={{ flex: 1, justifyContent: "flex-end", backgroundColor: theme.colors.alpha.scrim }}>
          <View style={{ maxHeight: "80%", padding: theme.spacing.lg, borderTopLeftRadius: theme.radii.xl, borderTopRightRadius: theme.radii.xl, backgroundColor: theme.colors.base.background, gap: theme.spacing.sm }}>
            <Text variant="title">Select model</Text>
            <TextInput
              testID="model-search-input"
              value={modelSearch}
              onChangeText={setModelSearch}
              placeholder="Search models"
              placeholderTextColor={theme.colors.alpha.textPlaceholder}
              style={{ borderRadius: theme.radii.lg, padding: theme.spacing.md, backgroundColor: theme.colors.surface.panel, borderWidth: 1, borderColor: theme.colors.stroke.subtle, color: theme.colors.base.text, fontFamily: theme.typography.body }}
            />
            <ScrollView contentContainerStyle={{ gap: theme.spacing.sm }}>
              {filteredModels.map((modelName) => (
                <Pressable
                  key={modelName}
                  testID={`model-option-${modelName.replace(/[^a-zA-Z0-9]/g, "-")}`}
                  onPress={() => { setForm((prev) => ({ ...prev, model: modelName })); setModelPickerOpen(false); }}
                  style={{ paddingVertical: 12, paddingHorizontal: 14, borderRadius: theme.radii.lg, borderWidth: 1, borderColor: form.model === modelName ? theme.colors.base.secondary : theme.colors.stroke.subtle, backgroundColor: form.model === modelName ? theme.colors.surface.glass : theme.colors.surface.panel }}
                >
                  <Text variant="bodyMedium">{modelName}</Text>
                </Pressable>
              ))}
              {filteredModels.length === 0 ? <Text variant="muted">No matches</Text> : null}
            </ScrollView>
            <Pressable onPress={() => setModelPickerOpen(false)} style={{ paddingVertical: 12, borderRadius: theme.radii.lg, alignItems: "center", backgroundColor: theme.colors.surface.panel }}>
              <Text variant="bodyMedium">Close</Text>
            </Pressable>
          </View>
        </View>
      </Modal>

      <Modal animationType="slide" transparent visible={whisperPickerOpen} onRequestClose={() => setWhisperPickerOpen(false)}>
        <View style={{ flex: 1, justifyContent: "flex-end", backgroundColor: theme.colors.alpha.scrim }}>
          <View style={{ maxHeight: "50%", padding: theme.spacing.lg, borderTopLeftRadius: theme.radii.xl, borderTopRightRadius: theme.radii.xl, backgroundColor: theme.colors.base.background, gap: theme.spacing.sm }}>
            <Text variant="title">Select Whisper model</Text>
            <ScrollView contentContainerStyle={{ gap: theme.spacing.sm }}>
              {(["whisper-tiny", "whisper-base", "whisper-small"] as const).map((id) => (
                <Pressable
                  key={id}
                  onPress={() => { setForm((prev) => ({ ...prev, whisperModel: id, whisperModelPath: "" })); setWhisperPickerOpen(false); }}
                  style={{ paddingVertical: 14, paddingHorizontal: 16, borderRadius: theme.radii.lg, borderWidth: 1, borderColor: form.whisperModel === id ? theme.colors.base.primary : theme.colors.stroke.subtle }}
                >
                  <Text variant="bodyMedium">{id}</Text>
                </Pressable>
              ))}
            </ScrollView>
            <Pressable onPress={() => setWhisperPickerOpen(false)} style={{ paddingVertical: 12, borderRadius: theme.radii.lg, alignItems: "center", backgroundColor: theme.colors.surface.panel }}>
              <Text variant="bodyMedium">Close</Text>
            </Pressable>
          </View>
        </View>
      </Modal>
    </>
  );

  if (useSidebar) {
    return (
      <Screen>
        <ScrollView contentContainerStyle={{ paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.xl, paddingBottom: 40, gap: theme.spacing.lg }}>
          <View style={{ flexDirection: "row", alignItems: "center", gap: theme.spacing.sm }}>
            <Text testID="screen-settings" variant="display">Settings</Text>
            <View style={{ flexDirection: "row", alignItems: "center", gap: 4 }}>
              <View style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: theme.colors.base.secondary }} />
              <Text variant="muted" style={{ fontSize: 12 }}>Autosaved</Text>
            </View>
          </View>
          {settingsContent}
        </ScrollView>
      </Screen>
    );
  }

  return (
    <Screen>
      <ScrollView contentContainerStyle={{ paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.xl, paddingBottom: 140, gap: theme.spacing.lg }}>
        <View>
          <Text testID="screen-settings" variant="display">Settings</Text>
          <Text variant="muted" style={{ marginTop: theme.spacing.xs }}>
            Provider, credentials, model, temperature, and voice key.
          </Text>
          <Text variant="mono" style={{ marginTop: theme.spacing.sm, color: theme.colors.base.textMuted }}>
            {saveStatus}
          </Text>
        </View>
        {settingsContent}
      </ScrollView>
    </Screen>
  );
}
