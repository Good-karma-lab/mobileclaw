/**
 * React Native bridge to Guappa World Wide Swarm (Kotlin backend).
 *
 * Manages WWSP connection, identity, tasks, holon deliberation,
 * and reputation tracking.
 */
import {
  NativeModules,
  NativeEventEmitter,
  Platform,
  type EmitterSubscription,
} from "react-native";

type NativeGuappaSwarm = {
  // Identity
  generateIdentity(): Promise<string>;
  getIdentity(): Promise<string>;
  getFingerprint(): Promise<string>;
  setDisplayName(name: string): Promise<boolean>;

  // Connection
  connect(connectorUrl: string): Promise<boolean>;
  disconnect(): Promise<boolean>;
  isConnected(): Promise<boolean>;
  getConnectionStatus(): Promise<string>;

  // Peers
  getPeers(): Promise<string>;
  getPeerCount(): Promise<number>;

  // Messaging
  sendSwarmMessage(recipientId: string, content: string): Promise<boolean>;
  broadcastSwarmMessage(content: string): Promise<boolean>;
  getRecentMessages(limit: number): Promise<string>;

  // Tasks
  getAvailableTasks(): Promise<string>;
  acceptTask(taskId: string): Promise<boolean>;
  rejectTask(taskId: string): Promise<boolean>;
  reportTaskResult(taskId: string, result: string, success: boolean): Promise<boolean>;
  getActiveTasks(): Promise<string>;
  getCompletedTaskCount(): Promise<number>;

  // Reputation
  getReputation(): Promise<string>;
  getReputationTier(): Promise<string>;

  // Holon
  joinHolon(holonId: string): Promise<boolean>;
  leaveHolon(holonId: string): Promise<boolean>;
  submitProposal(holonId: string, proposal: string): Promise<boolean>;
  castVote(holonId: string, proposalId: string, ranking: string): Promise<boolean>;
  getActiveHolons(): Promise<string>;

  // Stats
  getSwarmStats(): Promise<string>;

  // Config
  setPollingInterval(ms: number): Promise<boolean>;
  setAutoConnect(enabled: boolean): Promise<boolean>;
  registerCapabilities(caps: string): Promise<boolean>;
};

const moduleRef = (NativeModules.GuappaSwarm || null) as NativeGuappaSwarm | null;
const swarmEventEmitter = moduleRef ? new NativeEventEmitter(NativeModules.GuappaSwarm) : null;

// --- Types ---

export interface SwarmIdentity {
  publicKey: string;
  fingerprint: string;
  displayName: string;
  createdAt: number;
}

export type ConnectionStatus = "connected" | "connecting" | "disconnected" | "error";

export interface SwarmPeer {
  id: string;
  displayName: string;
  fingerprint: string;
  capabilities: string[];
  reputationTier: string;
  lastSeen: number;
  online: boolean;
}

export interface SwarmMessage {
  id: string;
  senderId: string;
  senderName: string;
  content: string;
  type: "chat" | "task_offer" | "holon_invite" | "reputation_update" | "system";
  timestamp: number;
}

export interface SwarmTask {
  id: string;
  description: string;
  offeredBy: string;
  reward: number;
  deadline: number;
  status: "available" | "accepted" | "in_progress" | "completed" | "failed";
  progress: number;
}

export interface SwarmReputation {
  score: number;
  tier: "new" | "trusted" | "veteran" | "elite";
  tasksCompleted: number;
  tasksFailed: number;
  totalEarned: number;
  joinedAt: number;
}

export interface SwarmHolon {
  id: string;
  name: string;
  memberCount: number;
  activeProposals: number;
  myRole: "member" | "observer";
}

export interface SwarmStats {
  peersOnline: number;
  totalPeers: number;
  tasksSent: number;
  tasksReceived: number;
  messagesTotal: number;
  holonParticipations: number;
  uptimeSeconds: number;
}

export type SwarmEvent = {
  type:
    | "peer_joined"
    | "peer_left"
    | "message_received"
    | "task_offered"
    | "task_completed"
    | "reputation_changed"
    | "holon_update"
    | "connection_changed";
  data: string;
};

// --- Identity ---

export async function generateIdentity(): Promise<SwarmIdentity | null> {
  if (Platform.OS !== "android" || !moduleRef) return null;
  try {
    const json = await moduleRef.generateIdentity();
    return JSON.parse(json) as SwarmIdentity;
  } catch {
    return null;
  }
}

export async function getIdentity(): Promise<SwarmIdentity | null> {
  if (Platform.OS !== "android" || !moduleRef) return null;
  try {
    const json = await moduleRef.getIdentity();
    return JSON.parse(json) as SwarmIdentity;
  } catch {
    return null;
  }
}

export async function getFingerprint(): Promise<string> {
  if (Platform.OS !== "android" || !moduleRef) return "";
  return moduleRef.getFingerprint();
}

export async function setDisplayName(name: string): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.setDisplayName(name);
}

// --- Connection ---

export async function connectToSwarm(connectorUrl: string): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.connect(connectorUrl);
}

export async function disconnectFromSwarm(): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.disconnect();
}

export async function isSwarmConnected(): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.isConnected();
}

export async function getConnectionStatus(): Promise<ConnectionStatus> {
  if (Platform.OS !== "android" || !moduleRef) return "disconnected";
  try {
    const status = await moduleRef.getConnectionStatus();
    return status as ConnectionStatus;
  } catch {
    return "disconnected";
  }
}

// --- Peers ---

export async function getSwarmPeers(): Promise<SwarmPeer[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  try {
    const json = await moduleRef.getPeers();
    return JSON.parse(json) as SwarmPeer[];
  } catch {
    return [];
  }
}

export async function getPeerCount(): Promise<number> {
  if (Platform.OS !== "android" || !moduleRef) return 0;
  return moduleRef.getPeerCount();
}

// --- Messaging ---

export async function sendSwarmMessage(
  recipientId: string,
  content: string
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.sendSwarmMessage(recipientId, content);
}

export async function broadcastSwarmMessage(content: string): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.broadcastSwarmMessage(content);
}

export async function getRecentMessages(
  limit: number = 50
): Promise<SwarmMessage[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  try {
    const json = await moduleRef.getRecentMessages(limit);
    return JSON.parse(json) as SwarmMessage[];
  } catch {
    return [];
  }
}

// --- Tasks ---

export async function getAvailableTasks(): Promise<SwarmTask[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  try {
    const json = await moduleRef.getAvailableTasks();
    return JSON.parse(json) as SwarmTask[];
  } catch {
    return [];
  }
}

export async function acceptTask(taskId: string): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.acceptTask(taskId);
}

export async function rejectTask(taskId: string): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.rejectTask(taskId);
}

export async function reportTaskResult(
  taskId: string,
  result: string,
  success: boolean
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.reportTaskResult(taskId, result, success);
}

export async function getActiveTasks(): Promise<SwarmTask[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  try {
    const json = await moduleRef.getActiveTasks();
    return JSON.parse(json) as SwarmTask[];
  } catch {
    return [];
  }
}

export async function getCompletedTaskCount(): Promise<number> {
  if (Platform.OS !== "android" || !moduleRef) return 0;
  return moduleRef.getCompletedTaskCount();
}

// --- Reputation ---

export async function getReputation(): Promise<SwarmReputation | null> {
  if (Platform.OS !== "android" || !moduleRef) return null;
  try {
    const json = await moduleRef.getReputation();
    return JSON.parse(json) as SwarmReputation;
  } catch {
    return null;
  }
}

export async function getReputationTier(): Promise<string> {
  if (Platform.OS !== "android" || !moduleRef) return "new";
  return moduleRef.getReputationTier();
}

// --- Holon ---

export async function joinHolon(holonId: string): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.joinHolon(holonId);
}

export async function leaveHolon(holonId: string): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.leaveHolon(holonId);
}

export async function submitProposal(
  holonId: string,
  proposal: string
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.submitProposal(holonId, proposal);
}

export async function castVote(
  holonId: string,
  proposalId: string,
  ranking: string[]
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.castVote(holonId, proposalId, JSON.stringify(ranking));
}

export async function getActiveHolons(): Promise<SwarmHolon[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  try {
    const json = await moduleRef.getActiveHolons();
    return JSON.parse(json) as SwarmHolon[];
  } catch {
    return [];
  }
}

// --- Stats ---

export async function getSwarmStats(): Promise<SwarmStats | null> {
  if (Platform.OS !== "android" || !moduleRef) return null;
  try {
    const json = await moduleRef.getSwarmStats();
    return JSON.parse(json) as SwarmStats;
  } catch {
    return null;
  }
}

// --- Config ---

export async function setPollingInterval(ms: number): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.setPollingInterval(ms);
}

export async function setAutoConnect(enabled: boolean): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.setAutoConnect(enabled);
}

export async function registerCapabilities(
  capabilities: string[]
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.registerCapabilities(JSON.stringify(capabilities));
}

// --- Events ---

export function subscribeToSwarmEvents(
  listener: (event: SwarmEvent) => void
): () => void {
  if (!swarmEventEmitter) return () => {};
  const subscription: EmitterSubscription = swarmEventEmitter.addListener(
    "guappa_swarm_event",
    (event: SwarmEvent) => listener(event)
  );
  return () => subscription.remove();
}
