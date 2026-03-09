/**
 * React Native bridge to Guappa memory system (Kotlin backend).
 *
 * Exposes the 5-tier memory system to JavaScript:
 *   - Working memory (in-memory, managed by Kotlin side)
 *   - Short-term facts (Room, 24h TTL, auto-promoted on access)
 *   - Long-term facts (Room, permanent)
 *   - Episodic memory (session summaries and key events)
 *   - Semantic search (keyword fallback now, vector embeddings in future)
 *
 * All methods return typed objects directly (no JSON.parse needed) because
 * the Kotlin MemoryBridge uses WritableMap/WritableArray.
 */
import { NativeModules, Platform } from "react-native";

// ---------- Types ----------

export type MemoryTier = "short_term" | "long_term";
export type MemoryCategory =
  | "preference"
  | "fact"
  | "relationship"
  | "routine"
  | "date";
export type TaskStatus = "pending" | "in_progress" | "completed" | "cancelled";

export interface MemoryFact {
  id: string;
  key: string;
  value: string;
  category: string;
  tier: string;
  importance: number;
  createdAt: number;
  accessedAt: number;
  accessCount: number;
}

export interface MemorySession {
  id: string;
  title: string;
  startedAt: number;
  endedAt: number | null;
  summary: string | null;
  tokenCount: number;
}

export interface MemoryMessage {
  id: string;
  sessionId: string;
  role: string;
  content: string;
  timestamp: number;
  tokenCount: number;
}

export interface MemoryTask {
  id: string;
  title: string;
  status: string;
  priority: number;
  dueDate: number | null;
  description: string;
  createdAt: number;
}

export interface MemoryEpisode {
  id: string;
  sessionId: string;
  summary: string;
  emotion: string;
  outcome: string;
  timestamp: number;
}

export interface MemorySearchResult {
  id: string;
  content: string;
  source: string;
  category: string;
  score: number;
}

export interface MemoryStats {
  totalFacts: number;
  shortTermFacts: number;
  longTermFacts: number;
  totalEpisodes: number;
  activeSessions: number;
}

export interface CleanupResult {
  deletedFacts: number;
}

export interface PromotionResult {
  promotedFacts: number;
}

// ---------- Native module binding ----------

type NativeGuappaMemory = {
  getMemories(
    category: string | null,
    tier: string | null
  ): Promise<MemoryFact[]>;
  addMemory(
    key: string,
    value: string,
    category: string,
    tier: string | null,
    importance: number
  ): Promise<MemoryFact>;
  searchMemories(query: string): Promise<MemoryFact[]>;
  semanticSearch(query: string, limit: number): Promise<MemorySearchResult[]>;
  deleteMemory(id: string): Promise<boolean>;
  getSessionHistory(limit: number): Promise<MemorySession[]>;
  createSession(title: string | null): Promise<MemorySession>;
  endSession(sessionId: string, summary: string | null): Promise<boolean>;
  getSessionMessages(sessionId: string): Promise<MemoryMessage[]>;
  getTasks(): Promise<MemoryTask[]>;
  getActiveTasks(): Promise<MemoryTask[]>;
  addTask(
    title: string,
    description: string | null,
    priority: number,
    dueDate: number
  ): Promise<MemoryTask>;
  updateTaskStatus(taskId: string, status: string): Promise<boolean>;
  deleteTask(taskId: string): Promise<boolean>;
  getEpisodes(limit: number): Promise<MemoryEpisode[]>;
  getMemoryStats(): Promise<MemoryStats>;
  runCleanup(): Promise<CleanupResult>;
  runPromotion(): Promise<PromotionResult>;
};

const moduleRef = (NativeModules.GuappaMemory ||
  null) as NativeGuappaMemory | null;

function requireModule(): NativeGuappaMemory {
  if (Platform.OS !== "android" || !moduleRef) {
    throw new Error("GuappaMemory is only available on Android");
  }
  return moduleRef;
}

// =====================================================================
//  Memory Facts
// =====================================================================

export async function getMemories(
  category?: MemoryCategory | string,
  tier?: MemoryTier | string
): Promise<MemoryFact[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  return moduleRef.getMemories(category ?? null, tier ?? null);
}

export async function addMemory(
  key: string,
  value: string,
  category: MemoryCategory | string = "fact",
  tier?: MemoryTier | string,
  importance: number = 0.5
): Promise<MemoryFact> {
  return requireModule().addMemory(
    key,
    value,
    category,
    tier ?? null,
    importance
  );
}

export async function searchMemories(query: string): Promise<MemoryFact[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  return moduleRef.searchMemories(query);
}

export async function semanticSearch(
  query: string,
  limit: number = 10
): Promise<MemorySearchResult[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  return moduleRef.semanticSearch(query, limit);
}

export async function deleteMemory(id: string): Promise<boolean> {
  return requireModule().deleteMemory(id);
}

// =====================================================================
//  Sessions
// =====================================================================

export async function getSessionHistory(
  limit: number = 20
): Promise<MemorySession[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  return moduleRef.getSessionHistory(limit);
}

export async function createSession(
  title?: string
): Promise<MemorySession> {
  return requireModule().createSession(title ?? null);
}

export async function endSession(
  sessionId: string,
  summary?: string
): Promise<boolean> {
  return requireModule().endSession(sessionId, summary ?? null);
}

export async function getSessionMessages(
  sessionId: string
): Promise<MemoryMessage[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  return moduleRef.getSessionMessages(sessionId);
}

// =====================================================================
//  Tasks
// =====================================================================

export async function getTasks(): Promise<MemoryTask[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  return moduleRef.getTasks();
}

export async function getActiveTasks(): Promise<MemoryTask[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  return moduleRef.getActiveTasks();
}

export async function addTask(
  title: string,
  description?: string,
  priority: number = 1,
  dueDate?: number
): Promise<MemoryTask> {
  return requireModule().addTask(
    title,
    description ?? null,
    priority,
    dueDate ?? 0
  );
}

export async function updateTaskStatus(
  taskId: string,
  status: TaskStatus | string
): Promise<boolean> {
  return requireModule().updateTaskStatus(taskId, status);
}

export async function deleteTask(taskId: string): Promise<boolean> {
  return requireModule().deleteTask(taskId);
}

// =====================================================================
//  Episodes
// =====================================================================

export async function getEpisodes(
  limit: number = 20
): Promise<MemoryEpisode[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  return moduleRef.getEpisodes(limit);
}

// =====================================================================
//  Stats & Maintenance
// =====================================================================

export async function getMemoryStats(): Promise<MemoryStats | null> {
  if (Platform.OS !== "android" || !moduleRef) return null;
  return moduleRef.getMemoryStats();
}

export async function runCleanup(): Promise<CleanupResult | null> {
  if (Platform.OS !== "android" || !moduleRef) return null;
  return moduleRef.runCleanup();
}

export async function runPromotion(): Promise<PromotionResult | null> {
  if (Platform.OS !== "android" || !moduleRef) return null;
  return moduleRef.runPromotion();
}
