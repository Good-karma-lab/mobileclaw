import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  Pressable,
  RefreshControl,
  NativeModules,
  Platform,
  Alert,
} from "react-native";
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
import { GlassCard } from "../../components/glass/GlassCard";
import { GlassButton } from "../../components/glass/GlassButton";
import { GlassInput } from "../../components/glass/GlassInput";
import { GlassStatusBadge } from "../../components/glass/GlassStatusBadge";
import { GlassChip } from "../../components/glass/GlassChip";
import { GlassProgressBar } from "../../components/glass/GlassProgressBar";
import { CollapsibleSection } from "../../components/glass/CollapsibleSection";

// ---------------------------------------------------------------------------
// Native bridge
// ---------------------------------------------------------------------------

// Raw native module returns JSON strings for complex types
type NativeSwarmManagerRaw = {
  connect(url: string): Promise<boolean>;
  disconnect(): Promise<boolean>;
  getStatus(): Promise<string>;
  getIdentity(): Promise<string>;
  generateIdentity(): Promise<string>;
  updateAgentName(name: string): Promise<boolean>;
  getMessages(since: number): Promise<string>;
  getPeers(): Promise<string>;
  getStats(): Promise<string>;
  getActiveTasks(): Promise<string>;
  acceptTask(taskId: string): Promise<boolean>;
  rejectTask(taskId: string): Promise<boolean>;
};

const RawSwarmManager: NativeSwarmManagerRaw | null =
  Platform.OS === "android"
    ? (NativeModules.GuappaSwarm as NativeSwarmManagerRaw) ?? null
    : null;

// Wrapper that parses JSON strings from the native module
const SwarmManager = RawSwarmManager
  ? {
      connect: (url: string) => RawSwarmManager!.connect(url),
      disconnect: () => RawSwarmManager!.disconnect(),
      getStatus: async (): Promise<SwarmStatusPayload> => {
        const json = await RawSwarmManager!.getStatus();
        return typeof json === "string" ? JSON.parse(json) : json;
      },
      getIdentity: async (): Promise<SwarmIdentityPayload> => {
        const json = await RawSwarmManager!.getIdentity();
        return typeof json === "string" ? JSON.parse(json) : json;
      },
      generateIdentity: async (): Promise<SwarmIdentityPayload> => {
        const json = await RawSwarmManager!.generateIdentity();
        return typeof json === "string" ? JSON.parse(json) : json;
      },
      updateAgentName: (name: string) => RawSwarmManager!.updateAgentName(name),
      getMessages: async (since: number): Promise<SwarmMessagePayload[]> => {
        const json = await RawSwarmManager!.getMessages(since);
        const parsed = typeof json === "string" ? JSON.parse(json) : json;
        return Array.isArray(parsed) ? parsed : [];
      },
      getPeers: async (): Promise<SwarmPeerPayload[]> => {
        const json = await RawSwarmManager!.getPeers();
        const parsed = typeof json === "string" ? JSON.parse(json) : json;
        return Array.isArray(parsed) ? parsed : [];
      },
      getStats: async (): Promise<SwarmStatsPayload> => {
        const json = await RawSwarmManager!.getStats();
        return typeof json === "string" ? JSON.parse(json) : json;
      },
      getActiveTasks: async (): Promise<SwarmTaskPayload[]> => {
        const json = await RawSwarmManager!.getActiveTasks();
        const parsed = typeof json === "string" ? JSON.parse(json) : json;
        return Array.isArray(parsed) ? parsed : [];
      },
      acceptTask: (taskId: string) => RawSwarmManager!.acceptTask(taskId),
      rejectTask: (taskId: string) => RawSwarmManager!.rejectTask(taskId),
    }
  : null;

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type ConnectionStatus = "connected" | "connecting" | "disconnected" | "error";

type ReputationTier = "new" | "trusted" | "veteran" | "elite";

type SwarmMessageType =
  | "chat"
  | "task_offer"
  | "holon_invite"
  | "reputation_update";

interface SwarmStatusPayload {
  connectionStatus: ConnectionStatus;
  peerCount: number;
  uptimeSeconds: number;
  connectorUrl: string;
}

interface SwarmIdentityPayload {
  publicKey?: string;
  displayName: string;
  publicKeyFingerprint: string;
  reputationTier: ReputationTier;
  reputationScore: number;
  hasIdentity: boolean;
}

interface SwarmMessagePayload {
  id: string;
  senderName: string;
  content: string;
  timestamp: number;
  type: SwarmMessageType;
}

interface SwarmPeerPayload {
  id: string;
  displayName: string;
  name?: string; // alias
  capabilities: string[];
  reputationTier: string;
  reputationScore?: number;
  lastSeen: number;
  lastSeenTimestamp?: number;
  online: boolean;
}

interface SwarmStatsPayload {
  tasksCompleted: number;
  tasksFailed: number;
  messagesSent: number;
  messagesReceived: number;
  holonParticipations: number;
  totalUptimeSeconds: number;
}

interface SwarmTaskPayload {
  id: string;
  description: string;
  progress: number; // 0-1
  timeRemainingSeconds: number;
  status: "pending" | "in_progress" | "completed" | "failed";
}

// ---------------------------------------------------------------------------
// Defaults (when native module is unavailable or returns null)
// ---------------------------------------------------------------------------

const DEFAULT_STATUS: SwarmStatusPayload = {
  connectionStatus: "disconnected",
  peerCount: 0,
  uptimeSeconds: 0,
  connectorUrl: "http://10.0.2.2:9371",
};

const DEFAULT_IDENTITY: SwarmIdentityPayload = {
  displayName: "Guappa Agent",
  publicKeyFingerprint: "",
  reputationTier: "new",
  reputationScore: 0,
  hasIdentity: false,
};

const DEFAULT_STATS: SwarmStatsPayload = {
  tasksCompleted: 0,
  tasksFailed: 0,
  messagesSent: 0,
  messagesReceived: 0,
  holonParticipations: 0,
  totalUptimeSeconds: 0,
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function formatUptime(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}m ${s}s`;
  }
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  return `${h}h ${m}m`;
}

function formatTimestamp(ts: number): string {
  const now = Date.now();
  const diff = Math.floor((now - ts) / 1000);
  if (diff < 60) return "just now";
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return `${Math.floor(diff / 86400)}d ago`;
}

function truncateFingerprint(fp: string): string {
  if (fp.length <= 16) return fp;
  return `${fp.slice(0, 8)}...${fp.slice(-8)}`;
}

const MESSAGE_TYPE_CONFIG: Record<
  SwarmMessageType,
  { label: string; color: string; icon: keyof typeof Ionicons.glyphMap }
> = {
  chat: { label: "Chat", color: colors.accent.cyan, icon: "chatbubble-outline" },
  task_offer: {
    label: "Task",
    color: colors.accent.violet,
    icon: "briefcase-outline",
  },
  holon_invite: {
    label: "Holon",
    color: colors.accent.amber,
    icon: "people-outline",
  },
  reputation_update: {
    label: "Rep",
    color: colors.accent.lime,
    icon: "star-outline",
  },
};

const TIER_CONFIG: Record<
  ReputationTier,
  { label: string; color: string; icon: keyof typeof Ionicons.glyphMap }
> = {
  new: { label: "NEW", color: colors.text.tertiary, icon: "leaf-outline" },
  trusted: {
    label: "TRUSTED",
    color: colors.semantic.info,
    icon: "shield-checkmark-outline",
  },
  veteran: {
    label: "VETERAN",
    color: colors.accent.violet,
    icon: "ribbon-outline",
  },
  elite: { label: "ELITE", color: colors.accent.gold, icon: "diamond-outline" },
};

// ---------------------------------------------------------------------------
// Animated components
// ---------------------------------------------------------------------------

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

// ---------------------------------------------------------------------------
// RotatingGlobe (disconnected empty state)
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
// PulsingDot (connection indicator)
// ---------------------------------------------------------------------------

function PulsingDot({ color }: { color: string }) {
  const opacity = useSharedValue(1);

  useEffect(() => {
    opacity.value = withRepeat(
      withTiming(0.3, { duration: 1000, easing: Easing.inOut(Easing.ease) }),
      -1,
      true,
    );
  }, [opacity]);

  const dotStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
  }));

  return (
    <Animated.View
      style={[
        { width: 10, height: 10, borderRadius: 5, backgroundColor: color },
        dotStyle,
      ]}
    />
  );
}

// ---------------------------------------------------------------------------
// StatBox
// ---------------------------------------------------------------------------

function StatBox({
  label,
  value,
  icon,
}: {
  label: string;
  value: string | number;
  icon: keyof typeof Ionicons.glyphMap;
}) {
  return (
    <View style={statBoxStyles.container}>
      <Ionicons name={icon} size={18} color={colors.accent.cyan} />
      <Text style={statBoxStyles.value}>{value}</Text>
      <Text style={statBoxStyles.label}>{label}</Text>
    </View>
  );
}

const statBoxStyles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: "center",
    gap: 4,
    paddingVertical: 8,
  },
  value: {
    color: colors.text.primary,
    fontSize: 18,
    fontFamily: typography.mono.fontFamily,
    fontWeight: "600",
  },
  label: {
    color: colors.text.tertiary,
    fontSize: 11,
    fontFamily: typography.body.fontFamily,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
});

// ---------------------------------------------------------------------------
// FeedMessageRow
// ---------------------------------------------------------------------------

function FeedMessageRow({ message }: { message: SwarmMessagePayload }) {
  const cfg = MESSAGE_TYPE_CONFIG[message.type];

  return (
    <View style={feedStyles.messageRow}>
      <View style={feedStyles.messageHeader}>
        <View style={feedStyles.messageLeft}>
          <Ionicons name={cfg.icon} size={14} color={cfg.color} />
          <Text style={feedStyles.senderName}>{message.senderName}</Text>
          <View style={[feedStyles.typeBadge, { borderColor: `${cfg.color}40` }]}>
            <Text style={[feedStyles.typeBadgeText, { color: cfg.color }]}>
              {cfg.label}
            </Text>
          </View>
        </View>
        <Text style={feedStyles.timestamp}>
          {formatTimestamp(message.timestamp)}
        </Text>
      </View>
      <Text style={feedStyles.messageContent} numberOfLines={3}>
        {message.content}
      </Text>
    </View>
  );
}

const feedStyles = StyleSheet.create({
  messageRow: {
    backgroundColor: "rgba(255, 255, 255, 0.07)",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.06)",
    borderRadius: 14,
    padding: 12,
    marginBottom: spacing.sm,
  },
  messageHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 6,
  },
  messageLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    flex: 1,
  },
  senderName: {
    color: colors.text.primary,
    fontSize: 13,
    fontFamily: typography.bodySemiBold.fontFamily,
  },
  typeBadge: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 6,
    paddingVertical: 2,
    backgroundColor: "rgba(255, 255, 255, 0.07)",
  },
  typeBadgeText: {
    fontSize: 10,
    fontFamily: typography.mono.fontFamily,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  timestamp: {
    color: colors.text.tertiary,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
  },
  messageContent: {
    color: colors.text.secondary,
    fontSize: 13,
    fontFamily: typography.body.fontFamily,
    lineHeight: 18,
  },
});

// ---------------------------------------------------------------------------
// PeerRow
// ---------------------------------------------------------------------------

function PeerRow({ peer }: { peer: SwarmPeerPayload }) {
  return (
    <View style={peerStyles.row}>
      <View style={peerStyles.header}>
        <View
          style={[
            peerStyles.onlineDot,
            {
              backgroundColor: peer.online
                ? "#22C55E"
                : colors.text.tertiary,
            },
          ]}
        />
        <Text style={peerStyles.name} numberOfLines={1}>
          {peer.name}
        </Text>
        <View style={peerStyles.repContainer}>
          <Ionicons name="star" size={12} color={colors.accent.amber} />
          <Text style={peerStyles.repScore}>{peer.reputationScore}</Text>
        </View>
      </View>
      <View style={peerStyles.capabilities}>
        {(peer.capabilities ?? []).slice(0, 3).map((cap) => (
          <View key={cap} style={peerStyles.capBadge}>
            <Text style={peerStyles.capText}>{cap}</Text>
          </View>
        ))}
        {(peer.capabilities ?? []).length > 3 && (
          <Text style={peerStyles.capMore}>
            +{(peer.capabilities ?? []).length - 3}
          </Text>
        )}
      </View>
      <Text style={peerStyles.lastSeen}>
        {peer.online ? "Online now" : `Last seen ${formatTimestamp(peer.lastSeen)}`}
      </Text>
    </View>
  );
}

const peerStyles = StyleSheet.create({
  row: {
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: "rgba(255, 255, 255, 0.05)",
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginBottom: 6,
  },
  onlineDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  name: {
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.bodySemiBold.fontFamily,
    flex: 1,
  },
  repContainer: {
    flexDirection: "row",
    alignItems: "center",
    gap: 3,
  },
  repScore: {
    color: colors.accent.amber,
    fontSize: 12,
    fontFamily: typography.mono.fontFamily,
  },
  capabilities: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 4,
    marginBottom: 4,
  },
  capBadge: {
    backgroundColor: "rgba(255, 255, 255, 0.08)",
    borderRadius: 6,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.08)",
  },
  capText: {
    color: colors.text.tertiary,
    fontSize: 10,
    fontFamily: typography.mono.fontFamily,
  },
  capMore: {
    color: colors.text.tertiary,
    fontSize: 10,
    fontFamily: typography.mono.fontFamily,
    alignSelf: "center",
  },
  lastSeen: {
    color: colors.text.tertiary,
    fontSize: 11,
    fontFamily: typography.body.fontFamily,
  },
});

// ---------------------------------------------------------------------------
// TaskRow
// ---------------------------------------------------------------------------

function TaskRow({
  task,
  onAccept,
  onReject,
}: {
  task: SwarmTaskPayload;
  onAccept: (id: string) => void;
  onReject: (id: string) => void;
}) {
  const isPending = task.status === "pending";
  const isInProgress = task.status === "in_progress";

  const statusColor =
    task.status === "completed"
      ? colors.semantic.success
      : task.status === "failed"
        ? colors.semantic.error
        : task.status === "in_progress"
          ? colors.accent.cyan
          : colors.accent.amber;

  return (
    <View style={taskStyles.row}>
      <View style={taskStyles.header}>
        <View
          style={[taskStyles.statusDot, { backgroundColor: statusColor }]}
        />
        <Text style={taskStyles.description} numberOfLines={2}>
          {task.description}
        </Text>
      </View>

      {isInProgress && (
        <View style={taskStyles.progressContainer}>
          <GlassProgressBar
            progress={task.progress}
            color={colors.accent.cyan}
            height={4}
          />
          <View style={taskStyles.progressInfo}>
            <Text style={taskStyles.progressText}>
              {Math.round(task.progress * 100)}%
            </Text>
            {task.timeRemainingSeconds > 0 && (
              <Text style={taskStyles.timeRemaining}>
                {formatUptime(task.timeRemainingSeconds)} left
              </Text>
            )}
          </View>
        </View>
      )}

      {isPending && (
        <View style={taskStyles.actions}>
          <Pressable
            style={taskStyles.acceptButton}
            onPress={() => onAccept(task.id)}
          >
            <Ionicons
              name="checkmark-outline"
              size={16}
              color={colors.semantic.success}
            />
            <Text
              style={[taskStyles.actionText, { color: colors.semantic.success }]}
            >
              Accept
            </Text>
          </Pressable>
          <Pressable
            style={taskStyles.rejectButton}
            onPress={() => onReject(task.id)}
          >
            <Ionicons
              name="close-outline"
              size={16}
              color={colors.semantic.error}
            />
            <Text
              style={[taskStyles.actionText, { color: colors.semantic.error }]}
            >
              Reject
            </Text>
          </Pressable>
        </View>
      )}
    </View>
  );
}

const taskStyles = StyleSheet.create({
  row: {
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: "rgba(255, 255, 255, 0.05)",
  },
  header: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
    marginBottom: 6,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginTop: 4,
  },
  description: {
    color: colors.text.primary,
    fontSize: 13,
    fontFamily: typography.body.fontFamily,
    flex: 1,
    lineHeight: 18,
  },
  progressContainer: {
    marginTop: 4,
    gap: 4,
  },
  progressInfo: {
    flexDirection: "row",
    justifyContent: "space-between",
  },
  progressText: {
    color: colors.accent.cyan,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
  },
  timeRemaining: {
    color: colors.text.tertiary,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
  },
  actions: {
    flexDirection: "row",
    gap: 8,
    marginTop: 4,
  },
  acceptButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: "rgba(20, 184, 166, 0.12)",
    borderWidth: 1,
    borderColor: "rgba(20, 184, 166, 0.25)",
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  rejectButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: "rgba(239, 68, 68, 0.12)",
    borderWidth: 1,
    borderColor: "rgba(239, 68, 68, 0.25)",
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  actionText: {
    fontSize: 12,
    fontFamily: typography.bodySemiBold.fontFamily,
  },
});

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

const POLL_INTERVAL_MS = 5_000;
const FEED_POLL_INTERVAL_MS = 3_000;

export function SwarmScreen({ isActive }: { isActive?: boolean }) {
  const insets = useSafeAreaInsets();
  const feedScrollRef = useRef<ScrollView>(null);

  // ---------- State ----------
  const [status, setStatus] = useState<SwarmStatusPayload>(DEFAULT_STATUS);
  const [identity, setIdentity] = useState<SwarmIdentityPayload>(DEFAULT_IDENTITY);
  const [messages, setMessages] = useState<SwarmMessagePayload[]>([]);
  const [peers, setPeers] = useState<SwarmPeerPayload[]>([]);
  const [stats, setStats] = useState<SwarmStatsPayload>(DEFAULT_STATS);
  const [tasks, setTasks] = useState<SwarmTaskPayload[]>([]);

  const [connectorUrl, setConnectorUrl] = useState("http://10.0.2.2:9371");
  const [editingName, setEditingName] = useState(false);
  const [nameInput, setNameInput] = useState("");
  const [connectLoading, setConnectLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [lastFeedTimestamp, setLastFeedTimestamp] = useState(0);

  const isConnected = status.connectionStatus === "connected";
  const isConnecting = status.connectionStatus === "connecting";

  // ---------- Data fetching ----------

  const fetchStatus = useCallback(async () => {
    if (!SwarmManager) return;
    try {
      const s = await SwarmManager.getStatus();
      setStatus(s);
    } catch {
      // Swarm module not available; leave defaults
    }
  }, []);

  const fetchIdentity = useCallback(async () => {
    if (!SwarmManager) return;
    try {
      const id = await SwarmManager.getIdentity();
      setIdentity(id);
      setNameInput(id.displayName);
    } catch {
      // leave defaults
    }
  }, []);

  const fetchMessages = useCallback(async () => {
    if (!SwarmManager) return;
    try {
      const msgs = await SwarmManager.getMessages(lastFeedTimestamp);
      if (msgs.length > 0) {
        setMessages((prev) => {
          const existing = new Set(prev.map((m) => m.id));
          const newMsgs = msgs.filter((m) => !existing.has(m.id));
          if (newMsgs.length === 0) return prev;
          const combined = [...prev, ...newMsgs].slice(-200); // cap at 200
          return combined;
        });
        const maxTs = Math.max(...msgs.map((m) => m.timestamp));
        setLastFeedTimestamp(maxTs);
        // Auto-scroll to bottom
        setTimeout(() => {
          feedScrollRef.current?.scrollToEnd({ animated: true });
        }, 100);
      }
    } catch {
      // leave current
    }
  }, [lastFeedTimestamp]);

  const fetchPeers = useCallback(async () => {
    if (!SwarmManager) return;
    try {
      const p = await SwarmManager.getPeers();
      setPeers(p);
    } catch {
      // leave current
    }
  }, []);

  const fetchStats = useCallback(async () => {
    if (!SwarmManager) return;
    try {
      const s = await SwarmManager.getStats();
      setStats(s);
    } catch {
      // leave current
    }
  }, []);

  const fetchTasks = useCallback(async () => {
    if (!SwarmManager) return;
    try {
      const t = await SwarmManager.getActiveTasks();
      setTasks(t);
    } catch {
      // leave current
    }
  }, []);

  const fetchAll = useCallback(async () => {
    await Promise.all([
      fetchStatus(),
      fetchIdentity(),
      fetchMessages(),
      fetchPeers(),
      fetchStats(),
      fetchTasks(),
    ]);
  }, [fetchStatus, fetchIdentity, fetchMessages, fetchPeers, fetchStats, fetchTasks]);

  // ---------- Initial load + auto-connect ----------

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  // Auto-generate identity and auto-connect on mount
  useEffect(() => {
    if (!SwarmManager) return;
    let cancelled = false;
    (async () => {
      try {
        // Auto-generate identity if missing
        let id = await SwarmManager.getIdentity();
        if (!id.publicKey && !cancelled) {
          const raw = await SwarmManager.generateIdentity();
          if (raw && typeof raw === "string") {
            id = JSON.parse(raw);
          }
          await fetchIdentity();
        }
        // Auto-connect if not already connected
        if (!cancelled && status.connectionStatus === "disconnected") {
          await SwarmManager.connect(connectorUrl);
          await fetchStatus();
        }
      } catch {
        // Connector may not be running — that's fine
      }
    })();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    const statusInterval = setInterval(() => {
      fetchStatus();
      fetchPeers();
      fetchStats();
      fetchTasks();
    }, POLL_INTERVAL_MS);

    return () => clearInterval(statusInterval);
  }, [fetchStatus, fetchPeers, fetchStats, fetchTasks]);

  useEffect(() => {
    if (!isConnected) return;
    const feedInterval = setInterval(fetchMessages, FEED_POLL_INTERVAL_MS);
    return () => clearInterval(feedInterval);
  }, [isConnected, fetchMessages]);

  // ---------- Actions ----------

  const handleConnect = useCallback(async () => {
    if (!SwarmManager) {
      Alert.alert("Unavailable", "Swarm module is not available on this platform.");
      return;
    }
    setConnectLoading(true);
    try {
      if (isConnected) {
        await SwarmManager.disconnect();
      } else {
        await SwarmManager.connect(connectorUrl);
      }
      await fetchStatus();
      await fetchIdentity();
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Unknown error";
      Alert.alert("Connection Error", msg);
    } finally {
      setConnectLoading(false);
    }
  }, [isConnected, connectorUrl, fetchStatus, fetchIdentity]);

  const handleGenerateIdentity = useCallback(async () => {
    if (!SwarmManager) return;
    try {
      const newId = await SwarmManager.generateIdentity();
      setIdentity(newId);
      setNameInput(newId.displayName);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Unknown error";
      Alert.alert("Error", msg);
    }
  }, []);

  const handleSaveName = useCallback(async () => {
    if (!SwarmManager) return;
    try {
      await SwarmManager.updateAgentName(nameInput);
      setIdentity((prev) => ({ ...prev, displayName: nameInput }));
      setEditingName(false);
    } catch {
      Alert.alert("Error", "Failed to update name.");
    }
  }, [nameInput]);

  const handleCopyFingerprint = useCallback(() => {
    if (identity.publicKeyFingerprint) {
      Alert.alert("Public Key Fingerprint", identity.publicKeyFingerprint);
    }
  }, [identity.publicKeyFingerprint]);

  const handleAcceptTask = useCallback(
    async (taskId: string) => {
      if (!SwarmManager) return;
      try {
        await SwarmManager.acceptTask(taskId);
        await fetchTasks();
      } catch {
        Alert.alert("Error", "Failed to accept task.");
      }
    },
    [fetchTasks],
  );

  const handleRejectTask = useCallback(
    async (taskId: string) => {
      if (!SwarmManager) return;
      try {
        await SwarmManager.rejectTask(taskId);
        await fetchTasks();
      } catch {
        Alert.alert("Error", "Failed to reject task.");
      }
    },
    [fetchTasks],
  );

  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    await fetchAll();
    setRefreshing(false);
  }, [fetchAll]);

  // ---------- Derived ----------

  const connectionStatusBadge = useMemo((): {
    status: "online" | "offline" | "warning" | "error";
    label: string;
  } => {
    switch (status.connectionStatus) {
      case "connected":
        return { status: "online", label: "Connected" };
      case "connecting":
        return { status: "warning", label: "Connecting..." };
      case "error":
        return { status: "error", label: "Error" };
      default:
        return { status: "offline", label: "Disconnected" };
    }
  }, [status.connectionStatus]);

  const tierCfg = TIER_CONFIG[identity.reputationTier] ?? TIER_CONFIG.new;
  const onlinePeers = peers.filter((p) => p.online);
  const activeTasks = tasks.filter(
    (t) => t.status === "in_progress" || t.status === "pending",
  );

  // ---------- Render ----------

  return (
    <View
      style={[styles.container, { backgroundColor: "rgba(2, 2, 6, 0.9)" }]}
      testID="swarm-screen"
    >
      {/* ---- Header ---- */}
      <BlurView
        intensity={24}
        tint="dark"
        style={[styles.header, { paddingTop: insets.top + spacing.sm }]}
      >
        <View style={styles.headerInner}>
          <View style={styles.headerTitleRow}>
            <Ionicons
              name="globe-outline"
              size={20}
              color={colors.accent.violet}
            />
            <Text style={styles.headerTitle}>World Wide Swarm</Text>
          </View>
          <View testID="swarm-status-pill">
            <GlassStatusBadge
              status={connectionStatusBadge.status}
              label={connectionStatusBadge.label}
              size="sm"
            />
          </View>
        </View>
        <LinearGradient
          colors={[colors.accent.violet, "transparent", colors.accent.violet]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 0 }}
          style={styles.headerLine}
        />
      </BlurView>

      {/* ---- Scrollable content ---- */}
      <ScrollView
        style={styles.feed}
        contentContainerStyle={[
          styles.feedContent,
          { paddingBottom: 120 + insets.bottom },
        ]}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={handleRefresh}
            tintColor={colors.accent.violet}
            colors={[colors.accent.violet]}
          />
        }
      >
        {/* ---- Identity Card ---- */}
        <GlassCard style={styles.sectionCard} testID="identity-card">
          <View style={styles.sectionHeader}>
            <Ionicons
              name="finger-print-outline"
              size={18}
              color={colors.accent.cyan}
            />
            <Text style={styles.sectionTitle}>Identity</Text>
          </View>

          {identity.hasIdentity ? (
            <>
              {/* Display name */}
              <View style={styles.identityRow}>
                <Text style={styles.identityLabel}>Name</Text>
                {editingName ? (
                  <View style={styles.nameEditRow}>
                    <GlassInput
                      value={nameInput}
                      onChangeText={setNameInput}
                      placeholder="Agent name"
                      style={styles.nameInput}
                    />
                    <Pressable onPress={handleSaveName} style={styles.nameAction}>
                      <Ionicons
                        name="checkmark"
                        size={18}
                        color={colors.semantic.success}
                      />
                    </Pressable>
                    <Pressable
                      onPress={() => {
                        setEditingName(false);
                        setNameInput(identity.displayName);
                      }}
                      style={styles.nameAction}
                    >
                      <Ionicons
                        name="close"
                        size={18}
                        color={colors.semantic.error}
                      />
                    </Pressable>
                  </View>
                ) : (
                  <Pressable
                    onPress={() => setEditingName(true)}
                    style={styles.nameDisplay}
                  >
                    <Text style={styles.identityValue}>
                      {identity.displayName}
                    </Text>
                    <Ionicons
                      name="pencil-outline"
                      size={14}
                      color={colors.text.tertiary}
                    />
                  </Pressable>
                )}
              </View>

              {/* Public key fingerprint */}
              <Pressable
                onPress={handleCopyFingerprint}
                style={styles.identityRow}
              >
                <Text style={styles.identityLabel}>Key</Text>
                <View style={styles.fingerprintRow}>
                  <Text style={styles.fingerprintText}>
                    {truncateFingerprint(identity.publicKeyFingerprint)}
                  </Text>
                  <Ionicons
                    name="copy-outline"
                    size={14}
                    color={colors.text.tertiary}
                  />
                </View>
              </Pressable>

              {/* Reputation */}
              <View style={styles.identityRow}>
                <Text style={styles.identityLabel}>Reputation</Text>
                <View style={styles.reputationRow}>
                  <View
                    style={[
                      styles.tierBadge,
                      { borderColor: `${tierCfg.color}40` },
                    ]}
                  >
                    <Ionicons
                      name={tierCfg.icon}
                      size={12}
                      color={tierCfg.color}
                    />
                    <Text style={[styles.tierText, { color: tierCfg.color }]}>
                      {tierCfg.label}
                    </Text>
                  </View>
                  <View style={styles.repScoreContainer}>
                    <Ionicons
                      name="star"
                      size={14}
                      color={colors.accent.amber}
                    />
                    <Text style={styles.repScoreText}>
                      {identity.reputationScore}
                    </Text>
                  </View>
                </View>
              </View>
            </>
          ) : (
            <View style={styles.noIdentity}>
              <Text style={styles.noIdentityText}>
                No swarm identity generated yet.
              </Text>
              <GlassButton
                title="Generate New Identity"
                icon="key-outline"
                variant="primary"
                onPress={handleGenerateIdentity}
                testID="generate-identity-btn"
              />
            </View>
          )}
        </GlassCard>

        {/* ---- Connection Panel ---- */}
        <GlassCard style={styles.sectionCard}>
          <View style={styles.sectionHeader}>
            <Ionicons
              name="link-outline"
              size={18}
              color={colors.accent.cyan}
            />
            <Text style={styles.sectionTitle}>Connection</Text>
          </View>

          <GlassInput
            label="Connector URL"
            value={connectorUrl}
            onChangeText={setConnectorUrl}
            placeholder="http://192.168.1.100:9371"
            keyboardType="url"
            style={styles.urlInput}
          />

          <GlassButton
            title={
              isConnected
                ? "Disconnect"
                : isConnecting
                  ? "Connecting..."
                  : "Connect"
            }
            icon={isConnected ? "unlink-outline" : "flash-outline"}
            variant={isConnected ? "secondary" : "primary"}
            onPress={handleConnect}
            loading={connectLoading || isConnecting}
            style={styles.connectButton}
            testID="swarm-connect-btn"
          />

          <View style={styles.connectionInfo}>
            <View style={styles.connectionInfoItem}>
              <View style={styles.connectionDotRow}>
                {isConnected ? (
                  <PulsingDot color="#22C55E" />
                ) : isConnecting ? (
                  <PulsingDot color={colors.semantic.warning} />
                ) : (
                  <View
                    style={[
                      styles.staticDot,
                      {
                        backgroundColor:
                          status.connectionStatus === "error"
                            ? colors.semantic.error
                            : colors.text.tertiary,
                      },
                    ]}
                  />
                )}
                <Text style={styles.connectionLabel}>
                  {connectionStatusBadge.label}
                </Text>
              </View>
            </View>

            <View style={styles.connectionInfoItem}>
              <Ionicons
                name="people-outline"
                size={14}
                color={colors.text.secondary}
              />
              <Text style={styles.connectionLabel}>
                {status.peerCount} {status.peerCount === 1 ? "Peer" : "Peers"}
              </Text>
            </View>

            {isConnected && (
              <View style={styles.connectionInfoItem}>
                <Ionicons
                  name="time-outline"
                  size={14}
                  color={colors.text.secondary}
                />
                <Text style={styles.connectionLabel}>
                  {formatUptime(status.uptimeSeconds)}
                </Text>
              </View>
            )}
          </View>
        </GlassCard>

        {/* ---- Live Feed ---- */}
        <View style={styles.sectionCard}>
          <View style={styles.sectionHeader}>
            <Ionicons
              name="radio-outline"
              size={18}
              color={colors.accent.cyan}
            />
            <Text style={styles.sectionTitle}>Live Feed</Text>
            {messages.length > 0 && (
              <View style={styles.feedCountBadge}>
                <Text style={styles.feedCountText}>{messages.length}</Text>
              </View>
            )}
          </View>

          {!isConnected ? (
            <View style={emptyStyles.container} testID="feed-empty-state">
              <RotatingGlobe />
              <Text style={emptyStyles.title}>Connect to the Swarm</Text>
              <Text style={emptyStyles.subtitle}>
                Join the World Wide Swarm to discover and collaborate with other
                agents across the network.
              </Text>
            </View>
          ) : messages.length === 0 ? (
            <View style={emptyStyles.containerSmall}>
              <Ionicons
                name="radio-outline"
                size={32}
                color={colors.accent.violet}
                style={{ opacity: 0.5 }}
              />
              <Text style={emptyStyles.subtitleSmall}>
                No swarm events yet. Activity will appear here as peers connect
                and tasks are distributed.
              </Text>
            </View>
          ) : (
            <ScrollView
              ref={feedScrollRef}
              style={styles.feedScroll}
              nestedScrollEnabled
              showsVerticalScrollIndicator={false}
            >
              {messages.map((msg) => (
                <FeedMessageRow key={msg.id} message={msg} />
              ))}
            </ScrollView>
          )}
        </View>

        {/* ---- Peers Panel ---- */}
        <CollapsibleSection
          title={`Peers (${onlinePeers.length}/${peers.length})`}
          icon="people-outline"
          defaultExpanded={false}
        >
          {peers.length === 0 ? (
            <Text style={styles.emptyText} testID="peers-empty-state">No peers discovered yet.</Text>
          ) : (
            peers.map((peer) => <PeerRow key={peer.id} peer={peer} />)
          )}
        </CollapsibleSection>

        {/* ---- Stats Panel ---- */}
        <CollapsibleSection
          title="Statistics"
          icon="stats-chart-outline"
          defaultExpanded={false}
        >
          <View style={styles.statsGrid} testID="swarm-stats-panel">
            <StatBox
              label="Completed"
              value={stats.tasksCompleted}
              icon="checkmark-circle-outline"
            />
            <StatBox
              label="Failed"
              value={stats.tasksFailed}
              icon="close-circle-outline"
            />
            <StatBox
              label="Sent"
              value={stats.messagesSent}
              icon="arrow-up-outline"
            />
          </View>
          <View style={styles.statsGrid}>
            <StatBox
              label="Received"
              value={stats.messagesReceived}
              icon="arrow-down-outline"
            />
            <StatBox
              label="Holons"
              value={stats.holonParticipations}
              icon="git-network-outline"
            />
            <StatBox
              label="Uptime"
              value={formatUptime(stats.totalUptimeSeconds)}
              icon="time-outline"
            />
          </View>
        </CollapsibleSection>

        {/* ---- Active Tasks ---- */}
        <CollapsibleSection
          title={`Active Tasks (${activeTasks.length})`}
          icon="briefcase-outline"
          defaultExpanded={activeTasks.length > 0}
        >
          {activeTasks.length === 0 ? (
            <Text style={styles.emptyText}>No active tasks.</Text>
          ) : (
            activeTasks.map((task) => (
              <TaskRow
                key={task.id}
                task={task}
                onAccept={handleAcceptTask}
                onReject={handleRejectTask}
              />
            ))
          )}
        </CollapsibleSection>
      </ScrollView>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },

  // Header
  header: {
    backgroundColor: "rgba(255, 255, 255, 0.10)",
    borderBottomWidth: 0,
  },
  headerInner: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.md,
    paddingBottom: spacing.sm,
  },
  headerTitleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
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

  // Feed
  feed: {
    flex: 1,
  },
  feedContent: {
    padding: spacing.md,
    gap: spacing.md,
  },

  // Section cards
  sectionCard: {
    marginBottom: 0,
  },
  sectionHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginBottom: 12,
  },
  sectionTitle: {
    color: colors.text.primary,
    fontSize: 16,
    fontFamily: typography.bodySemiBold.fontFamily,
    flex: 1,
  },

  // Identity
  identityRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: "rgba(255, 255, 255, 0.05)",
  },
  identityLabel: {
    color: colors.text.tertiary,
    fontSize: 12,
    fontFamily: typography.bodySemiBold.fontFamily,
    textTransform: "uppercase",
    letterSpacing: 0.5,
    width: 80,
  },
  identityValue: {
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.body.fontFamily,
  },
  nameEditRow: {
    flexDirection: "row",
    alignItems: "center",
    flex: 1,
    gap: 6,
  },
  nameInput: {
    flex: 1,
    marginBottom: 0,
  },
  nameAction: {
    padding: 6,
  },
  nameDisplay: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  fingerprintRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  fingerprintText: {
    color: colors.text.secondary,
    fontSize: 13,
    fontFamily: typography.mono.fontFamily,
  },
  reputationRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  tierBadge: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 8,
    paddingVertical: 3,
    backgroundColor: "rgba(255, 255, 255, 0.07)",
  },
  tierText: {
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
    letterSpacing: 0.5,
  },
  repScoreContainer: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  repScoreText: {
    color: colors.accent.amber,
    fontSize: 14,
    fontFamily: typography.mono.fontFamily,
    fontWeight: "600",
  },
  noIdentity: {
    alignItems: "center",
    gap: 12,
    paddingVertical: 16,
  },
  noIdentityText: {
    color: colors.text.tertiary,
    fontSize: 13,
    fontFamily: typography.body.fontFamily,
    textAlign: "center",
  },

  // Connection
  urlInput: {
    marginBottom: spacing.sm,
  },
  connectButton: {
    marginBottom: spacing.sm,
  },
  connectionInfo: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
    marginTop: 4,
  },
  connectionInfoItem: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  connectionDotRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  staticDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  connectionLabel: {
    color: colors.text.secondary,
    fontSize: 13,
    fontFamily: typography.mono.fontFamily,
  },

  // Feed
  feedCountBadge: {
    backgroundColor: "rgba(139, 92, 246, 0.20)",
    borderRadius: 10,
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderWidth: 1,
    borderColor: "rgba(139, 92, 246, 0.35)",
  },
  feedCountText: {
    color: colors.accent.violet,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
    fontWeight: "600",
  },
  feedScroll: {
    maxHeight: 360,
  },

  // Stats
  statsGrid: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 8,
  },

  // Empty
  emptyText: {
    color: colors.text.tertiary,
    fontSize: 13,
    fontFamily: typography.body.fontFamily,
    textAlign: "center",
    paddingVertical: 12,
  },
});

// Empty / disconnected state styles
const emptyStyles = StyleSheet.create({
  container: {
    alignItems: "center",
    paddingTop: 40,
    paddingBottom: 20,
    gap: 12,
  },
  containerSmall: {
    alignItems: "center",
    paddingVertical: 24,
    gap: 8,
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
    fontFamily: typography.bodySemiBold.fontFamily,
    fontSize: 18,
    opacity: 0.6,
  },
  subtitle: {
    color: colors.text.tertiary,
    fontFamily: typography.body.fontFamily,
    fontSize: 14,
    textAlign: "center",
    maxWidth: 300,
    lineHeight: 20,
  },
  subtitleSmall: {
    color: colors.text.tertiary,
    fontFamily: typography.body.fontFamily,
    fontSize: 13,
    textAlign: "center",
    maxWidth: 280,
    lineHeight: 18,
  },
});
