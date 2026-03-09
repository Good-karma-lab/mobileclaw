/**
 * Unit tests for the GuappaSwarm TypeScript bridge layer.
 *
 * The bridge caches moduleRef at require() time, so we must set
 * NativeModules.GuappaSwarm BEFORE requiring the module.
 */
import { NativeModules, Platform } from "react-native";

describe("guappaSwarm bridge — module unavailable", () => {
  beforeEach(() => {
    jest.resetModules();
    NativeModules.GuappaSwarm = null;
  });

  const load = () => require("../../src/native/guappaSwarm");

  it("generateIdentity returns null", async () => {
    expect(await load().generateIdentity()).toBeNull();
  });

  it("connectToSwarm returns false", async () => {
    expect(await load().connectToSwarm("http://localhost:9371")).toBe(false);
  });

  it("disconnectFromSwarm returns false", async () => {
    expect(await load().disconnectFromSwarm()).toBe(false);
  });

  it("isSwarmConnected returns false", async () => {
    expect(await load().isSwarmConnected()).toBe(false);
  });

  it("getConnectionStatus returns disconnected", async () => {
    expect(await load().getConnectionStatus()).toBe("disconnected");
  });

  it("getSwarmPeers returns []", async () => {
    expect(await load().getSwarmPeers()).toEqual([]);
  });

  it("getPeerCount returns 0", async () => {
    expect(await load().getPeerCount()).toBe(0);
  });

  it("getAvailableTasks returns []", async () => {
    expect(await load().getAvailableTasks()).toEqual([]);
  });

  it("getReputation returns null", async () => {
    expect(await load().getReputation()).toBeNull();
  });

  it("getSwarmStats returns null", async () => {
    expect(await load().getSwarmStats()).toBeNull();
  });

  it("getActiveHolons returns []", async () => {
    expect(await load().getActiveHolons()).toEqual([]);
  });

  it("sendSwarmMessage returns false", async () => {
    expect(await load().sendSwarmMessage("p1", "hi")).toBe(false);
  });

  it("broadcastSwarmMessage returns false", async () => {
    expect(await load().broadcastSwarmMessage("hi")).toBe(false);
  });

  it("subscribeToSwarmEvents returns no-op", () => {
    const unsub = load().subscribeToSwarmEvents(() => {});
    expect(typeof unsub).toBe("function");
    unsub();
  });
});

describe("guappaSwarm bridge — module available", () => {
  const mockIdentity = JSON.stringify({
    publicKey: "pk123", fingerprint: "did:swarm:abc",
    displayName: "Test Agent", createdAt: 1000,
  });

  const setup = () => {
    jest.resetModules();
    NativeModules.GuappaSwarm = {
      generateIdentity: jest.fn().mockResolvedValue(mockIdentity),
      getIdentity: jest.fn().mockResolvedValue(mockIdentity),
      getFingerprint: jest.fn().mockResolvedValue("did:swarm:abc"),
      setDisplayName: jest.fn().mockResolvedValue(true),
      connect: jest.fn().mockResolvedValue(true),
      disconnect: jest.fn().mockResolvedValue(true),
      isConnected: jest.fn().mockResolvedValue(true),
      getConnectionStatus: jest.fn().mockResolvedValue("connected"),
      getPeers: jest.fn().mockResolvedValue(JSON.stringify([{ id: "p1", displayName: "Peer1" }])),
      getPeerCount: jest.fn().mockResolvedValue(1),
      sendSwarmMessage: jest.fn().mockResolvedValue(true),
      broadcastSwarmMessage: jest.fn().mockResolvedValue(true),
      getRecentMessages: jest.fn().mockResolvedValue("[]"),
      getAvailableTasks: jest.fn().mockResolvedValue("[]"),
      acceptTask: jest.fn().mockResolvedValue(true),
      rejectTask: jest.fn().mockResolvedValue(true),
      reportTaskResult: jest.fn().mockResolvedValue(true),
      getActiveTasks: jest.fn().mockResolvedValue("[]"),
      getCompletedTaskCount: jest.fn().mockResolvedValue(5),
      getReputation: jest.fn().mockResolvedValue(JSON.stringify({ score: 100, tier: "trusted" })),
      getReputationTier: jest.fn().mockResolvedValue("trusted"),
      joinHolon: jest.fn().mockResolvedValue(true),
      leaveHolon: jest.fn().mockResolvedValue(true),
      submitProposal: jest.fn().mockResolvedValue(true),
      castVote: jest.fn().mockResolvedValue(true),
      getActiveHolons: jest.fn().mockResolvedValue("[]"),
      getSwarmStats: jest.fn().mockResolvedValue(JSON.stringify({ peersOnline: 3, totalPeers: 5 })),
      setPollingInterval: jest.fn().mockResolvedValue(true),
      setAutoConnect: jest.fn().mockResolvedValue(true),
      registerCapabilities: jest.fn().mockResolvedValue(true),
    };
    return require("../../src/native/guappaSwarm");
  };

  it("generateIdentity parses JSON response", async () => {
    const s = setup();
    const r = await s.generateIdentity();
    expect(r).not.toBeNull();
    expect(r!.publicKey).toBe("pk123");
    expect(r!.displayName).toBe("Test Agent");
  });

  it("connectToSwarm calls native", async () => {
    const s = setup();
    expect(await s.connectToSwarm("http://localhost:9371")).toBe(true);
  });

  it("getConnectionStatus returns connected", async () => {
    const s = setup();
    expect(await s.getConnectionStatus()).toBe("connected");
  });

  it("getSwarmPeers parses JSON", async () => {
    const s = setup();
    const peers = await s.getSwarmPeers();
    expect(peers).toHaveLength(1);
    expect(peers[0].id).toBe("p1");
  });

  it("getReputation parses JSON", async () => {
    const s = setup();
    const r = await s.getReputation();
    expect(r).not.toBeNull();
    expect(r!.score).toBe(100);
  });

  it("getSwarmStats parses JSON", async () => {
    const s = setup();
    const r = await s.getSwarmStats();
    expect(r).not.toBeNull();
    expect(r!.peersOnline).toBe(3);
  });

  it("getCompletedTaskCount returns count", async () => {
    const s = setup();
    expect(await s.getCompletedTaskCount()).toBe(5);
  });

  it("getReputationTier returns tier", async () => {
    const s = setup();
    expect(await s.getReputationTier()).toBe("trusted");
  });
});

describe("guappaSwarm bridge — non-android", () => {
  it("returns safe defaults on iOS", async () => {
    jest.resetModules();
    Platform.OS = "ios" as any;
    NativeModules.GuappaSwarm = { generateIdentity: jest.fn() };
    const s = require("../../src/native/guappaSwarm");
    expect(await s.generateIdentity()).toBeNull();
    expect(await s.getSwarmPeers()).toEqual([]);
    Platform.OS = "android" as any;
  });
});
