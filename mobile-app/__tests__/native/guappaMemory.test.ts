/**
 * Unit tests for the GuappaMemory TypeScript bridge layer.
 */
import { NativeModules, Platform } from "react-native";

describe("guappaMemory — module unavailable", () => {
  beforeEach(() => { jest.resetModules(); NativeModules.GuappaMemory = null; });
  const load = () => require("../../src/native/guappaMemory");

  it("getMemories returns []", async () => { expect(await load().getMemories()).toEqual([]); });
  it("searchMemories returns []", async () => { expect(await load().searchMemories("q")).toEqual([]); });
  it("getSessionHistory returns []", async () => { expect(await load().getSessionHistory()).toEqual([]); });
  it("getTasks returns []", async () => { expect(await load().getTasks()).toEqual([]); });
  it("getActiveTasks returns []", async () => { expect(await load().getActiveTasks()).toEqual([]); });
  it("getMemoryStats returns null", async () => { expect(await load().getMemoryStats()).toBeNull(); });
  it("getEpisodes returns []", async () => { expect(await load().getEpisodes()).toEqual([]); });
  it("addMemory throws", async () => {
    await expect(load().addMemory("k", "v")).rejects.toThrow("GuappaMemory is only available on Android");
  });
});

describe("guappaMemory — module available", () => {
  const mockFact = { id: "f1", key: "k", value: "v", category: "fact", tier: "short_term", importance: 0.5, createdAt: 0, accessedAt: 0, accessCount: 1 };

  const setup = () => {
    jest.resetModules();
    NativeModules.GuappaMemory = {
      getMemories: jest.fn().mockResolvedValue([mockFact]),
      addMemory: jest.fn().mockResolvedValue(mockFact),
      searchMemories: jest.fn().mockResolvedValue([mockFact]),
      semanticSearch: jest.fn().mockResolvedValue([]),
      deleteMemory: jest.fn().mockResolvedValue(true),
      getSessionHistory: jest.fn().mockResolvedValue([]),
      createSession: jest.fn().mockResolvedValue({ id: "s1", title: "T", startedAt: 0, endedAt: null, summary: null, tokenCount: 0 }),
      endSession: jest.fn().mockResolvedValue(true),
      getSessionMessages: jest.fn().mockResolvedValue([]),
      getTasks: jest.fn().mockResolvedValue([]),
      getActiveTasks: jest.fn().mockResolvedValue([]),
      addTask: jest.fn().mockResolvedValue({ id: "t1", title: "T", status: "pending", priority: 1, dueDate: null, description: "", createdAt: 0 }),
      updateTaskStatus: jest.fn().mockResolvedValue(true),
      deleteTask: jest.fn().mockResolvedValue(true),
      getEpisodes: jest.fn().mockResolvedValue([]),
      getMemoryStats: jest.fn().mockResolvedValue({ totalFacts: 1, shortTermFacts: 1, longTermFacts: 0, totalEpisodes: 0, activeSessions: 0 }),
      runCleanup: jest.fn().mockResolvedValue({ deletedFacts: 0 }),
      runPromotion: jest.fn().mockResolvedValue({ promotedFacts: 0 }),
    };
    return require("../../src/native/guappaMemory");
  };

  it("getMemories returns facts", async () => {
    const r = await setup().getMemories();
    expect(r).toHaveLength(1);
    expect(r[0].id).toBe("f1");
  });
  it("addMemory creates fact", async () => {
    expect((await setup().addMemory("k", "v")).id).toBe("f1");
  });
  it("getMemoryStats returns stats", async () => {
    const s = await setup().getMemoryStats();
    expect(s?.totalFacts).toBe(1);
  });
  it("deleteMemory returns true", async () => {
    expect(await setup().deleteMemory("f1")).toBe(true);
  });
});
