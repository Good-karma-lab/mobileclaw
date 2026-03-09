import React, { useEffect, useState, useCallback } from "react";
import {
  View,
  Text,
  ScrollView,
  Pressable,
  RefreshControl,
  StyleSheet,
  Alert,
} from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { BlurView } from "expo-blur";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withRepeat,
  withSpring,
  withTiming,
  Easing,
} from "react-native-reanimated";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";
import {
  CollapsibleSection,
  GlassProgressBar,
  GlassToggle,
  GlassButton,
} from "../../components/glass";
import {
  getActiveTasks,
  getMemoryStats,
  getSessionHistory,
  type MemoryTask,
  type MemoryStats as NativeMemoryStats,
  type MemorySession,
} from "../../native/guappaMemory";
import { getTriggers, type ProactiveTrigger } from "../../native/guappaProactive";

const AnimatedView = Animated.createAnimatedComponent(View);

// ─── Types ──────────────────────────────────────────────────────────────────

type TaskStatus = "running" | "pending" | "done" | "failed";

interface ActiveTask {
  id: string;
  title: string;
  status: TaskStatus;
  progress: number; // 0-1
  elapsedMs: number;
  detail?: string;
}

interface ScheduledJob {
  id: string;
  name: string;
  cronExpression: string;
  cronReadable: string;
  nextRunIso: string;
  enabled: boolean;
}

interface Trigger {
  id: string;
  name: string;
  type: "event" | "condition" | "schedule" | "webhook";
  enabled: boolean;
  lastFiredIso?: string;
}

interface MemoryStats {
  workingTokens: number;
  maxTokens: number;
  shortTermFacts: number;
  longTermFacts: number;
  episodicMemories: number;
}

interface SessionSummary {
  id: string;
  title: string;
  dateIso: string;
  messageCount: number;
  summaryPreview: string;
}

// Mock data removed — all sections load from native bridges

// ─── Helpers ────────────────────────────────────────────────────────────────

function formatElapsed(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remainingSec = seconds % 60;
  return `${minutes}m ${remainingSec}s`;
}

function formatRelativeTime(isoString: string): string {
  const diff = Date.now() - new Date(isoString).getTime();
  const absDiff = Math.abs(diff);
  const future = diff < 0;

  if (absDiff < 60_000) return future ? "in <1m" : "<1m ago";
  if (absDiff < 3600_000) {
    const mins = Math.floor(absDiff / 60_000);
    return future ? `in ${mins}m` : `${mins}m ago`;
  }
  if (absDiff < 86400_000) {
    const hrs = Math.floor(absDiff / 3600_000);
    return future ? `in ${hrs}h` : `${hrs}h ago`;
  }
  const days = Math.floor(absDiff / 86400_000);
  return future ? `in ${days}d` : `${days}d ago`;
}

function formatDate(isoString: string): string {
  const date = new Date(isoString);
  const now = new Date();
  const diffDays = Math.floor(
    (now.getTime() - date.getTime()) / 86400_000
  );
  if (diffDays === 0) return "Today";
  if (diffDays === 1) return "Yesterday";
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
  });
}

// ─── Status Badge Colors ────────────────────────────────────────────────────

const STATUS_CONFIG: Record<
  TaskStatus,
  { color: string; label: string; icon: keyof typeof Ionicons.glyphMap }
> = {
  running: {
    color: colors.accent.cyan,
    label: "Running",
    icon: "play-circle",
  },
  pending: {
    color: colors.semantic.warning,
    label: "Pending",
    icon: "time-outline",
  },
  done: {
    color: colors.semantic.success,
    label: "Done",
    icon: "checkmark-circle",
  },
  failed: {
    color: colors.semantic.error,
    label: "Failed",
    icon: "close-circle",
  },
};

const TRIGGER_TYPE_CONFIG: Record<
  Trigger["type"],
  { color: string; icon: keyof typeof Ionicons.glyphMap }
> = {
  event: { color: colors.accent.cyan, icon: "flash-outline" },
  condition: { color: colors.semantic.warning, icon: "git-branch-outline" },
  schedule: { color: colors.accent.violet, icon: "calendar-outline" },
  webhook: { color: colors.accent.rose, icon: "globe-outline" },
};

// ─── Animated Components ────────────────────────────────────────────────────

function StatusPill({ status }: { status: "idle" | "active" | "error" }) {
  const dotOpacity = useSharedValue(0.3);

  const dotColor =
    status === "active"
      ? colors.accent.cyan
      : status === "error"
        ? colors.semantic.error
        : colors.text.tertiary;

  const label =
    status === "active"
      ? "Active"
      : status === "error"
        ? "Error"
        : "Idle";

  useEffect(() => {
    if (status === "active") {
      dotOpacity.value = withRepeat(
        withSpring(1, { damping: 4, stiffness: 40 }),
        -1,
        true
      );
    } else {
      dotOpacity.value = withTiming(1, { duration: 300 });
    }
  }, [dotOpacity, status]);

  const dotStyle = useAnimatedStyle(() => ({
    opacity: dotOpacity.value,
  }));

  return (
    <BlurView intensity={25} tint="dark" style={styles.statusPill}>
      <AnimatedView
        style={[styles.statusDot, { backgroundColor: dotColor }, dotStyle]}
      />
      <Text style={[styles.statusLabel, { color: dotColor }]}>{label}</Text>
    </BlurView>
  );
}

function AnimatedEmptyIcon({
  icon,
}: {
  icon: keyof typeof Ionicons.glyphMap;
}) {
  const scale = useSharedValue(1);

  useEffect(() => {
    scale.value = withRepeat(
      withSpring(1.06, { damping: 3, stiffness: 30 }),
      -1,
      true
    );
  }, [scale]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  return (
    <AnimatedView style={[styles.emptyIcon, animatedStyle]}>
      <Ionicons name={icon} size={36} color={colors.text.tertiary} />
    </AnimatedView>
  );
}

function PulsingDot({ color }: { color: string }) {
  const opacity = useSharedValue(0.4);

  useEffect(() => {
    opacity.value = withRepeat(
      withTiming(1, { duration: 800, easing: Easing.inOut(Easing.ease) }),
      -1,
      true
    );
  }, [opacity]);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
  }));

  return (
    <AnimatedView
      style={[
        styles.pulsingDot,
        { backgroundColor: color },
        animatedStyle,
      ]}
    />
  );
}

// ─── Section: Empty State ───────────────────────────────────────────────────

function EmptyState({
  icon,
  title,
  subtitle,
  testID,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  title: string;
  subtitle: string;
  testID?: string;
}) {
  return (
    <View style={styles.sectionContent} testID={testID}>
      <AnimatedEmptyIcon icon={icon} />
      <Text style={styles.emptyTitle}>{title}</Text>
      <Text style={styles.emptySubtitle}>{subtitle}</Text>
    </View>
  );
}

// ─── Section: Active Tasks ──────────────────────────────────────────────────

function TaskStatusBadge({ status }: { status: TaskStatus }) {
  const config = STATUS_CONFIG[status];
  return (
    <View style={[styles.badge, { borderColor: `${config.color}40` }]}>
      {status === "running" ? (
        <PulsingDot color={config.color} />
      ) : (
        <Ionicons name={config.icon} size={12} color={config.color} />
      )}
      <Text style={[styles.badgeLabel, { color: config.color }]}>
        {config.label}
      </Text>
    </View>
  );
}

function TaskCard({ task }: { task: ActiveTask }) {
  const config = STATUS_CONFIG[task.status];
  return (
    <View style={styles.taskCard}>
      <View style={styles.taskHeader}>
        <Text style={styles.taskTitle} numberOfLines={1}>
          {task.title}
        </Text>
        <TaskStatusBadge status={task.status} />
      </View>
      {task.detail && (
        <Text style={styles.taskDetail} numberOfLines={1}>
          {task.detail}
        </Text>
      )}
      <View style={styles.taskFooter}>
        <GlassProgressBar
          progress={task.progress}
          color={config.color}
          height={4}
        />
        <View style={styles.taskMeta}>
          <View style={styles.taskMetaItem}>
            <Ionicons
              name="time-outline"
              size={12}
              color={colors.text.tertiary}
            />
            <Text style={styles.taskMetaText}>
              {formatElapsed(task.elapsedMs)}
            </Text>
          </View>
          <Text style={styles.taskProgress}>
            {Math.round(task.progress * 100)}%
          </Text>
        </View>
      </View>
    </View>
  );
}

function ActiveTasksContent({ tasks }: { tasks: ActiveTask[] }) {
  if (tasks.length === 0) {
    return (
      <EmptyState
        icon="list-outline"
        title="No active tasks"
        subtitle="Ask GUAPPA to do something and tasks will appear here"
        testID="tasks-empty-state"
      />
    );
  }

  return (
    <View style={styles.taskList}>
      {tasks.map((task) => (
        <TaskCard key={task.id} task={task} />
      ))}
    </View>
  );
}

// ─── Section: Scheduled Jobs ────────────────────────────────────────────────

function JobCard({
  job,
  onToggle,
}: {
  job: ScheduledJob;
  onToggle: (id: string) => void;
}) {
  return (
    <View style={styles.jobCard}>
      <View style={styles.jobContent}>
        <View style={styles.jobHeader}>
          <Ionicons
            name="calendar-outline"
            size={16}
            color={job.enabled ? colors.accent.cyan : colors.text.tertiary}
          />
          <Text
            style={[
              styles.jobName,
              !job.enabled && styles.jobNameDisabled,
            ]}
            numberOfLines={1}
          >
            {job.name}
          </Text>
        </View>
        <Text style={styles.jobSchedule}>{job.cronReadable}</Text>
        <View style={styles.jobNextRun}>
          <Ionicons
            name="arrow-forward-outline"
            size={11}
            color={colors.text.tertiary}
          />
          <Text style={styles.jobNextRunText}>
            Next: {formatRelativeTime(job.nextRunIso)}
          </Text>
        </View>
      </View>
      <GlassToggle
        value={job.enabled}
        onValueChange={() => onToggle(job.id)}
      />
    </View>
  );
}

function ScheduledJobsContent({
  jobs,
  onToggleJob,
}: {
  jobs: ScheduledJob[];
  onToggleJob: (id: string) => void;
}) {
  if (jobs.length === 0) {
    return (
      <EmptyState
        icon="calendar-outline"
        title="No scheduled jobs"
        subtitle='Create recurring actions like "Every morning, check my calendar"'
        testID="schedules-empty-state"
      />
    );
  }

  return (
    <View style={styles.jobList}>
      {jobs.map((job) => (
        <JobCard key={job.id} job={job} onToggle={onToggleJob} />
      ))}
    </View>
  );
}

// ─── Section: Triggers ──────────────────────────────────────────────────────

function TriggerTypeBadge({ type }: { type: Trigger["type"] }) {
  const config = TRIGGER_TYPE_CONFIG[type];
  return (
    <View style={[styles.badge, { borderColor: `${config.color}40` }]}>
      <Ionicons name={config.icon} size={12} color={config.color} />
      <Text style={[styles.badgeLabel, { color: config.color }]}>
        {type.charAt(0).toUpperCase() + type.slice(1)}
      </Text>
    </View>
  );
}

function TriggerCard({
  trigger,
  onToggle,
}: {
  trigger: Trigger;
  onToggle: (id: string) => void;
}) {
  return (
    <View style={styles.triggerCard}>
      <View style={styles.triggerContent}>
        <View style={styles.triggerHeader}>
          <Text
            style={[
              styles.triggerName,
              !trigger.enabled && styles.triggerNameDisabled,
            ]}
            numberOfLines={1}
          >
            {trigger.name}
          </Text>
          <TriggerTypeBadge type={trigger.type} />
        </View>
        {trigger.lastFiredIso && (
          <View style={styles.triggerLastFired}>
            <Ionicons
              name="time-outline"
              size={11}
              color={colors.text.tertiary}
            />
            <Text style={styles.triggerLastFiredText}>
              Last fired: {formatRelativeTime(trigger.lastFiredIso)}
            </Text>
          </View>
        )}
        {!trigger.lastFiredIso && (
          <Text style={styles.triggerNeverFired}>Never fired</Text>
        )}
      </View>
      <GlassToggle
        value={trigger.enabled}
        onValueChange={() => onToggle(trigger.id)}
      />
    </View>
  );
}

function TriggersContent({
  triggers,
  onToggleTrigger,
}: {
  triggers: Trigger[];
  onToggleTrigger: (id: string) => void;
}) {
  if (triggers.length === 0) {
    return (
      <EmptyState
        icon="flash-outline"
        title="No triggers configured"
        subtitle='Set up event-based actions like "When I get a call, log it"'
        testID="triggers-empty-state"
      />
    );
  }

  return (
    <View style={styles.triggerList}>
      {triggers.map((trigger) => (
        <TriggerCard
          key={trigger.id}
          trigger={trigger}
          onToggle={onToggleTrigger}
        />
      ))}
    </View>
  );
}

// ─── Section: Memory Stats ──────────────────────────────────────────────────

function MemoryStatRow({
  label,
  value,
  color,
  icon,
}: {
  label: string;
  value: string;
  color: string;
  icon: keyof typeof Ionicons.glyphMap;
}) {
  return (
    <View style={styles.memStatRow}>
      <View style={styles.memStatLeft}>
        <View style={[styles.memStatDot, { backgroundColor: color }]} />
        <Ionicons name={icon} size={14} color={color} style={styles.memStatIcon} />
        <Text style={styles.memStatLabel}>{label}</Text>
      </View>
      <Text style={[styles.memStatValue, { color }]}>{value}</Text>
    </View>
  );
}

function MemoryStatsContent({
  stats,
  onClearWorking,
}: {
  stats: MemoryStats;
  onClearWorking: () => void;
}) {
  const tokenProgress = stats.maxTokens > 0 ? stats.workingTokens / stats.maxTokens : 0;
  const tokenColor =
    tokenProgress > 0.8
      ? colors.semantic.error
      : tokenProgress > 0.5
        ? colors.semantic.warning
        : colors.accent.cyan;

  return (
    <View style={styles.memoryPanel} testID="memory-stats-panel">
      {/* Working Memory with progress bar */}
      <View style={styles.memoryWorkingCard}>
        <View style={styles.memoryWorkingHeader}>
          <View style={styles.memoryWorkingLeft}>
            <Ionicons
              name="hardware-chip-outline"
              size={18}
              color={tokenColor}
            />
            <Text style={styles.memoryWorkingTitle}>Working Memory</Text>
          </View>
          <Text style={[styles.memoryWorkingCount, { color: tokenColor }]}>
            {stats.workingTokens.toLocaleString()} /{" "}
            {stats.maxTokens.toLocaleString()}
          </Text>
        </View>
        <View style={styles.memoryWorkingBarWrap}>
          <GlassProgressBar
            progress={tokenProgress}
            color={tokenColor}
            height={6}
          />
        </View>
        <Text style={styles.memoryWorkingPct}>
          {Math.round(tokenProgress * 100)}% context used
        </Text>
      </View>

      {/* Fact counts */}
      <View style={styles.memStatsGrid}>
        <MemoryStatRow
          label="Short-term facts"
          value={String(stats.shortTermFacts)}
          color={colors.accent.lime}
          icon="document-text-outline"
        />
        <MemoryStatRow
          label="Long-term facts"
          value={String(stats.longTermFacts)}
          color={colors.accent.violet}
          icon="library-outline"
        />
        <MemoryStatRow
          label="Episodic memories"
          value={String(stats.episodicMemories)}
          color={colors.accent.amber}
          icon="film-outline"
        />
      </View>

      {/* Clear button */}
      <GlassButton
        title="Clear Working Memory"
        icon="trash-outline"
        variant="secondary"
        onPress={onClearWorking}
        style={styles.clearMemoryBtn}
      />
    </View>
  );
}

// ─── Section: Recent Sessions ───────────────────────────────────────────────

function SessionCard({ session }: { session: SessionSummary }) {
  const [expanded, setExpanded] = useState(false);

  const handlePress = useCallback(() => {
    setExpanded((prev) => !prev);
  }, []);

  return (
    <Pressable onPress={handlePress} style={styles.sessionCard}>
      <View style={styles.sessionHeader}>
        <View style={styles.sessionTitleRow}>
          <Ionicons
            name="chatbubbles-outline"
            size={16}
            color={colors.accent.cyan}
          />
          <Text style={styles.sessionTitle} numberOfLines={1}>
            {session.title}
          </Text>
        </View>
        <View style={styles.sessionMeta}>
          <Text style={styles.sessionDate}>
            {formatDate(session.dateIso)}
          </Text>
          <View style={styles.sessionMsgCount}>
            <Ionicons
              name="chatbubble-outline"
              size={11}
              color={colors.text.tertiary}
            />
            <Text style={styles.sessionMsgCountText}>
              {session.messageCount}
            </Text>
          </View>
          <Ionicons
            name={expanded ? "chevron-up" : "chevron-down"}
            size={16}
            color={colors.text.tertiary}
          />
        </View>
      </View>
      {!expanded && (
        <Text style={styles.sessionPreview} numberOfLines={2}>
          {session.summaryPreview}
        </Text>
      )}
      {expanded && (
        <View style={styles.sessionExpanded}>
          <Text style={styles.sessionFullSummary}>
            {session.summaryPreview}
          </Text>
          <View style={styles.sessionExpandedMeta}>
            <View style={styles.sessionExpandedMetaItem}>
              <Ionicons
                name="time-outline"
                size={12}
                color={colors.text.tertiary}
              />
              <Text style={styles.sessionExpandedMetaText}>
                {formatRelativeTime(session.dateIso)}
              </Text>
            </View>
            <View style={styles.sessionExpandedMetaItem}>
              <Ionicons
                name="chatbubbles-outline"
                size={12}
                color={colors.text.tertiary}
              />
              <Text style={styles.sessionExpandedMetaText}>
                {session.messageCount} messages
              </Text>
            </View>
          </View>
        </View>
      )}
    </Pressable>
  );
}

function RecentSessionsContent({
  sessions,
}: {
  sessions: SessionSummary[];
}) {
  if (sessions.length === 0) {
    return (
      <EmptyState
        icon="chatbubbles-outline"
        title="No sessions yet"
        subtitle="Start a conversation and your session history will appear here"
      />
    );
  }

  return (
    <View style={styles.sessionList}>
      {sessions.map((session) => (
        <SessionCard key={session.id} session={session} />
      ))}
    </View>
  );
}

// ─── Main Screen ────────────────────────────────────────────────────────────

export function CommandScreen({ isActive }: { isActive?: boolean }) {
  const insets = useSafeAreaInsets();
  const [refreshing, setRefreshing] = useState(false);

  // State for all sections — initialize empty, load from native
  const [tasks, setTasks] = useState<ActiveTask[]>([]);
  const [jobs, setJobs] = useState<ScheduledJob[]>([]);
  const [triggers, setTriggers] = useState<Trigger[]>([]);
  const [memoryStats, setMemoryStats] = useState<MemoryStats>({
    workingTokens: 0,
    maxTokens: 8192,
    shortTermFacts: 0,
    longTermFacts: 0,
    episodicMemories: 0,
  });
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // Derive overall status from tasks
  const overallStatus: "idle" | "active" | "error" = tasks.some(
    (t) => t.status === "failed"
  )
    ? "error"
    : tasks.some((t) => t.status === "running")
      ? "active"
      : "idle";

  const runningCount = tasks.filter((t) => t.status === "running").length;
  const enabledTriggersCount = triggers.filter((t) => t.enabled).length;

  // Load live data from native bridges
  const loadLiveData = useCallback(async () => {
    try {
      const [nativeTasks, nativeStats, nativeSessions, nativeTriggers] =
        await Promise.allSettled([
          getActiveTasks(),
          getMemoryStats(),
          getSessionHistory(10),
          getTriggers(),
        ]);

      if (nativeTasks.status === "fulfilled") {
        setTasks(
          nativeTasks.value.map((t: MemoryTask) => ({
            id: t.id,
            title: t.title,
            status: (t.status === "in_progress" ? "running" : t.status === "completed" ? "done" : t.status === "cancelled" ? "failed" : t.status) as TaskStatus,
            progress: t.status === "completed" ? 1 : t.status === "in_progress" ? 0.5 : 0,
            elapsedMs: Math.max(0, Date.now() - t.createdAt),
            detail: t.description,
          }))
        );
      }

      if (nativeStats.status === "fulfilled" && nativeStats.value) {
        const s = nativeStats.value;
        setMemoryStats({
          workingTokens: 0,
          maxTokens: 8192,
          shortTermFacts: s.shortTermFacts,
          longTermFacts: s.longTermFacts,
          episodicMemories: s.totalEpisodes,
        });
      }

      if (nativeSessions.status === "fulfilled") {
        setSessions(
          nativeSessions.value.map((s: MemorySession) => ({
            id: s.id,
            title: s.title,
            dateIso: new Date(s.startedAt).toISOString(),
            messageCount: s.tokenCount,
            summaryPreview: s.summary ?? "No summary available",
          }))
        );
      }

      if (nativeTriggers.status === "fulfilled") {
        setTriggers(
          nativeTriggers.value.map((t: ProactiveTrigger) => ({
            id: t.id,
            name: t.name,
            type: (t.type === "time_based" ? "schedule" : t.type === "event_based" ? "event" : t.type === "condition_based" ? "condition" : "event") as Trigger["type"],
            enabled: t.enabled,
            lastFiredIso: t.lastFired
              ? new Date(t.lastFired).toISOString()
              : undefined,
          }))
        );
      }
    } catch {
      // Leave empty states on error — no mock fallback
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Load data on mount
  useEffect(() => {
    loadLiveData();
  }, [loadLiveData]);

  // Pull-to-refresh handler
  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    await loadLiveData();
    setRefreshing(false);
  }, [loadLiveData]);

  // Toggle handlers
  const handleToggleJob = useCallback((id: string) => {
    setJobs((prev) =>
      prev.map((j) => (j.id === id ? { ...j, enabled: !j.enabled } : j))
    );
  }, []);

  const handleToggleTrigger = useCallback((id: string) => {
    setTriggers((prev) =>
      prev.map((t) => (t.id === id ? { ...t, enabled: !t.enabled } : t))
    );
  }, []);

  const handleClearWorkingMemory = useCallback(() => {
    Alert.alert(
      "Clear Working Memory",
      "This will reset the current conversation context. The agent will lose track of the current discussion.",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Clear",
          style: "destructive",
          onPress: () => {
            setMemoryStats((prev) => ({ ...prev, workingTokens: 0 }));
            // In production: await NativeModules.GuappaAgent.clearWorkingMemory();
          },
        },
      ]
    );
  }, []);

  return (
    <View
      style={[styles.container, { backgroundColor: "rgba(2, 2, 6, 0.85)" }]}
      testID="command-screen"
    >
      {/* Glass Header */}
      <BlurView
        intensity={30}
        tint="dark"
        style={[styles.header, { paddingTop: insets.top + spacing.sm }]}
      >
        <View style={styles.headerInner}>
          <View style={styles.headerTitleRow}>
            <Text style={styles.title}>Command Center</Text>
            {runningCount > 0 && (
              <View style={styles.runningBadge}>
                <Text style={styles.runningBadgeText}>{runningCount}</Text>
              </View>
            )}
          </View>
          <StatusPill status={overallStatus} />
        </View>

        {/* Summary bar */}
        <View style={styles.headerSummary}>
          <View style={styles.headerSummaryItem}>
            <Text style={styles.headerSummaryValue}>{tasks.length}</Text>
            <Text style={styles.headerSummaryLabel}>Tasks</Text>
          </View>
          <View style={styles.headerSummaryDivider} />
          <View style={styles.headerSummaryItem}>
            <Text style={styles.headerSummaryValue}>{jobs.length}</Text>
            <Text style={styles.headerSummaryLabel}>Scheduled</Text>
          </View>
          <View style={styles.headerSummaryDivider} />
          <View style={styles.headerSummaryItem}>
            <Text style={styles.headerSummaryValue}>
              {enabledTriggersCount}
            </Text>
            <Text style={styles.headerSummaryLabel}>Triggers</Text>
          </View>
          <View style={styles.headerSummaryDivider} />
          <View style={styles.headerSummaryItem}>
            <Text style={styles.headerSummaryValue}>
              {sessions.length}
            </Text>
            <Text style={styles.headerSummaryLabel}>Sessions</Text>
          </View>
        </View>

        {/* Bottom gradient line */}
        <LinearGradient
          colors={[colors.accent.cyan, "transparent", colors.accent.cyan]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 0 }}
          style={styles.headerLine}
        />
      </BlurView>

      {/* Collapsible Sections */}
      <ScrollView
        style={styles.content}
        contentContainerStyle={styles.contentInner}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={handleRefresh}
            tintColor={colors.accent.cyan}
            colors={[colors.accent.cyan]}
            progressBackgroundColor={colors.base.spaceBlack}
          />
        }
      >
        <CollapsibleSection
          title="Active Tasks"
          icon="list-outline"
          defaultExpanded
        >
          <ActiveTasksContent tasks={tasks} />
        </CollapsibleSection>

        <CollapsibleSection title="Scheduled Jobs" icon="calendar-outline">
          <ScheduledJobsContent jobs={jobs} onToggleJob={handleToggleJob} />
        </CollapsibleSection>

        <CollapsibleSection title="Triggers" icon="flash-outline">
          <TriggersContent
            triggers={triggers}
            onToggleTrigger={handleToggleTrigger}
          />
        </CollapsibleSection>

        <CollapsibleSection
          title="Memory Stats"
          icon="server-outline"
          defaultExpanded
        >
          <MemoryStatsContent
            stats={memoryStats}
            onClearWorking={handleClearWorkingMemory}
          />
        </CollapsibleSection>

        <CollapsibleSection
          title="Recent Sessions"
          icon="chatbubbles-outline"
        >
          <RecentSessionsContent sessions={sessions} />
        </CollapsibleSection>
      </ScrollView>
    </View>
  );
}

// ─── Styles ─────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },

  // ── Header ──────────────────────────────────────────────
  header: {
    backgroundColor: "rgba(255, 255, 255, 0.04)",
    borderBottomWidth: 0,
    overflow: "hidden",
  },
  headerInner: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.sm,
  },
  headerTitleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
  },
  title: {
    color: colors.text.primary,
    fontSize: 18,
    fontFamily: typography.display.fontFamily,
    letterSpacing: 1,
  },
  runningBadge: {
    backgroundColor: "rgba(20, 70, 90, 0.2)",
    borderWidth: 1,
    borderColor: "rgba(30, 85, 105, 0.25)",
    borderRadius: 10,
    minWidth: 20,
    height: 20,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 6,
  },
  runningBadgeText: {
    color: colors.accent.cyan,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
    fontWeight: "700",
  },
  headerSummary: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.md,
    gap: spacing.md,
  },
  headerSummaryItem: {
    alignItems: "center",
    flex: 1,
  },
  headerSummaryValue: {
    color: colors.text.primary,
    fontSize: 18,
    fontFamily: typography.mono.fontFamily,
    fontWeight: "700",
  },
  headerSummaryLabel: {
    color: colors.text.tertiary,
    fontSize: 10,
    fontFamily: typography.body.fontFamily,
    textTransform: "uppercase",
    letterSpacing: 0.5,
    marginTop: 2,
  },
  headerSummaryDivider: {
    width: 1,
    height: 28,
    backgroundColor: "rgba(255, 255, 255, 0.08)",
  },
  headerLine: {
    height: 1,
    width: "100%",
  },

  // ── Status Pill ─────────────────────────────────────────
  statusPill: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
    overflow: "hidden",
    backgroundColor: "rgba(255, 255, 255, 0.06)",
    borderWidth: 1,
    borderColor: colors.glass.borderSubtle,
    gap: 6,
  },
  statusDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
  statusLabel: {
    fontSize: 12,
    fontFamily: typography.mono.fontFamily,
    letterSpacing: 0.5,
  },

  // ── Content ─────────────────────────────────────────────
  content: {
    flex: 1,
  },
  contentInner: {
    padding: spacing.md,
    paddingBottom: 120,
  },

  // ── Empty States ────────────────────────────────────────
  sectionContent: {
    alignItems: "center",
    paddingVertical: spacing.lg,
  },
  emptyIcon: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: "rgba(255, 255, 255, 0.04)",
    borderWidth: 1,
    borderColor: colors.glass.borderSubtle,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: spacing.md,
  },
  emptyTitle: {
    color: colors.text.primary,
    fontSize: 16,
    fontFamily: typography.bodySemiBold.fontFamily,
    marginBottom: spacing.xs,
  },
  emptySubtitle: {
    color: colors.text.tertiary,
    fontSize: 13,
    fontFamily: typography.body.fontFamily,
    textAlign: "center",
    maxWidth: 260,
    lineHeight: 19,
  },

  // ── Badge (shared) ──────────────────────────────────────
  badge: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
    backgroundColor: "rgba(255, 255, 255, 0.04)",
    borderWidth: 1,
  },
  badgeLabel: {
    fontSize: 10,
    fontFamily: typography.bodySemiBold.fontFamily,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },

  // ── Pulsing Dot ─────────────────────────────────────────
  pulsingDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },

  // ── Active Tasks ────────────────────────────────────────
  taskList: {
    gap: spacing.sm,
  },
  taskCard: {
    backgroundColor: "rgba(255, 255, 255, 0.04)",
    borderRadius: 14,
    padding: spacing.md,
    borderWidth: 1,
    borderColor: colors.glass.borderSubtle,
  },
  taskHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: spacing.xs,
    gap: spacing.sm,
  },
  taskTitle: {
    flex: 1,
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.bodySemiBold.fontFamily,
  },
  taskDetail: {
    color: colors.text.tertiary,
    fontSize: 12,
    fontFamily: typography.body.fontFamily,
    marginBottom: spacing.sm,
  },
  taskFooter: {
    gap: spacing.xs,
  },
  taskMeta: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginTop: spacing.xs,
  },
  taskMetaItem: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  taskMetaText: {
    color: colors.text.tertiary,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
  },
  taskProgress: {
    color: colors.text.secondary,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
  },

  // ── Scheduled Jobs ──────────────────────────────────────
  jobList: {
    gap: spacing.sm,
  },
  jobCard: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "rgba(255, 255, 255, 0.04)",
    borderRadius: 14,
    padding: spacing.md,
    borderWidth: 1,
    borderColor: colors.glass.borderSubtle,
  },
  jobContent: {
    flex: 1,
    marginRight: spacing.sm,
  },
  jobHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    marginBottom: 4,
  },
  jobName: {
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.bodySemiBold.fontFamily,
    flex: 1,
  },
  jobNameDisabled: {
    color: colors.text.tertiary,
  },
  jobSchedule: {
    color: colors.text.secondary,
    fontSize: 12,
    fontFamily: typography.body.fontFamily,
    marginBottom: 4,
    marginLeft: 24,
  },
  jobNextRun: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    marginLeft: 24,
  },
  jobNextRunText: {
    color: colors.text.tertiary,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
  },

  // ── Triggers ────────────────────────────────────────────
  triggerList: {
    gap: spacing.sm,
  },
  triggerCard: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "rgba(255, 255, 255, 0.04)",
    borderRadius: 14,
    padding: spacing.md,
    borderWidth: 1,
    borderColor: colors.glass.borderSubtle,
  },
  triggerContent: {
    flex: 1,
    marginRight: spacing.sm,
  },
  triggerHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    marginBottom: 4,
  },
  triggerName: {
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.bodySemiBold.fontFamily,
    flex: 1,
  },
  triggerNameDisabled: {
    color: colors.text.tertiary,
  },
  triggerLastFired: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  triggerLastFiredText: {
    color: colors.text.tertiary,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
  },
  triggerNeverFired: {
    color: colors.text.tertiary,
    fontSize: 11,
    fontFamily: typography.body.fontFamily,
    fontStyle: "italic",
  },

  // ── Memory Stats ────────────────────────────────────────
  memoryPanel: {
    gap: spacing.sm,
  },
  memoryWorkingCard: {
    backgroundColor: "rgba(255, 255, 255, 0.04)",
    borderRadius: 14,
    padding: spacing.md,
    borderWidth: 1,
    borderColor: colors.glass.borderSubtle,
  },
  memoryWorkingHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: spacing.sm,
  },
  memoryWorkingLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
  },
  memoryWorkingTitle: {
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.bodySemiBold.fontFamily,
  },
  memoryWorkingCount: {
    fontSize: 12,
    fontFamily: typography.mono.fontFamily,
  },
  memoryWorkingBarWrap: {
    marginBottom: spacing.xs,
  },
  memoryWorkingPct: {
    color: colors.text.tertiary,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
    textAlign: "right",
  },
  memStatsGrid: {
    backgroundColor: "rgba(255, 255, 255, 0.04)",
    borderRadius: 14,
    padding: spacing.md,
    borderWidth: 1,
    borderColor: colors.glass.borderSubtle,
    gap: spacing.sm,
  },
  memStatRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  memStatLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
  },
  memStatDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  memStatIcon: {
    marginLeft: -2,
  },
  memStatLabel: {
    color: colors.text.secondary,
    fontSize: 13,
    fontFamily: typography.body.fontFamily,
  },
  memStatValue: {
    fontSize: 15,
    fontFamily: typography.mono.fontFamily,
    fontWeight: "700",
  },
  clearMemoryBtn: {
    marginTop: spacing.xs,
  },

  // ── Recent Sessions ─────────────────────────────────────
  sessionList: {
    gap: spacing.sm,
  },
  sessionCard: {
    backgroundColor: "rgba(255, 255, 255, 0.04)",
    borderRadius: 14,
    padding: spacing.md,
    borderWidth: 1,
    borderColor: colors.glass.borderSubtle,
  },
  sessionHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: spacing.xs,
  },
  sessionTitleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    flex: 1,
    marginRight: spacing.sm,
  },
  sessionTitle: {
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.bodySemiBold.fontFamily,
    flex: 1,
  },
  sessionMeta: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
  },
  sessionDate: {
    color: colors.text.tertiary,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
  },
  sessionMsgCount: {
    flexDirection: "row",
    alignItems: "center",
    gap: 3,
  },
  sessionMsgCountText: {
    color: colors.text.tertiary,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
  },
  sessionPreview: {
    color: colors.text.tertiary,
    fontSize: 12,
    fontFamily: typography.body.fontFamily,
    lineHeight: 18,
  },
  sessionExpanded: {
    marginTop: spacing.xs,
  },
  sessionFullSummary: {
    color: colors.text.secondary,
    fontSize: 13,
    fontFamily: typography.body.fontFamily,
    lineHeight: 20,
    marginBottom: spacing.sm,
  },
  sessionExpandedMeta: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    paddingTop: spacing.sm,
    borderTopWidth: 1,
    borderTopColor: "rgba(255, 255, 255, 0.06)",
  },
  sessionExpandedMetaItem: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  sessionExpandedMetaText: {
    color: colors.text.tertiary,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
  },
});
