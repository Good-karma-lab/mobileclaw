/**
 * Unit tests for the GuappaChannels TypeScript bridge layer.
 */
import { NativeModules, Platform } from "react-native";

describe("guappaChannels — module unavailable", () => {
  beforeEach(() => { jest.resetModules(); NativeModules.GuappaChannels = null; });
  const load = () => require("../../src/native/guappaChannels");

  it("listChannels returns []", async () => { expect(await load().listChannels()).toEqual([]); });
  it("configureChannel returns false", async () => { expect(await load().configureChannel("telegram", {})).toBe(false); });
  it("testChannel returns unhealthy", async () => {
    const r = await load().testChannel("telegram");
    expect(r.healthy).toBe(false);
  });
  it("sendChannelMessage returns false", async () => { expect(await load().sendChannelMessage("telegram", "hi")).toBe(false); });
  it("broadcastMessage returns empty", async () => {
    const r = await load().broadcastMessage("hi");
    expect(r.sent).toEqual([]);
  });
  it("getChannelStatus returns null", async () => { expect(await load().getChannelStatus("telegram")).toBeNull(); });
  it("getAllowlist returns []", async () => { expect(await load().getAllowlist("telegram")).toEqual([]); });
});

describe("guappaChannels — module available", () => {
  const setup = () => {
    jest.resetModules();
    NativeModules.GuappaChannels = {
      listChannels: jest.fn().mockResolvedValue(JSON.stringify([{ id: "telegram", name: "Telegram", isConfigured: true }])),
      configureChannel: jest.fn().mockResolvedValue(true),
      removeChannel: jest.fn().mockResolvedValue(true),
      testChannel: jest.fn().mockResolvedValue(JSON.stringify({ channelId: "telegram", healthy: true, latencyMs: 50 })),
      sendMessage: jest.fn().mockResolvedValue(true),
      broadcastMessage: jest.fn().mockResolvedValue(JSON.stringify({ sent: ["telegram"], failed: [] })),
      getChannelStatus: jest.fn().mockResolvedValue(JSON.stringify({ id: "telegram", isConfigured: true })),
      setAllowlist: jest.fn().mockResolvedValue(true),
      getAllowlist: jest.fn().mockResolvedValue("[]"),
    };
    return require("../../src/native/guappaChannels");
  };

  it("listChannels returns parsed list", async () => {
    const ch = await setup().listChannels();
    expect(ch).toHaveLength(1);
    expect(ch[0].id).toBe("telegram");
  });
  it("testChannel returns healthy", async () => {
    const r = await setup().testChannel("telegram");
    expect(r.healthy).toBe(true);
    expect(r.latencyMs).toBe(50);
  });
  it("broadcastMessage returns sent", async () => {
    const r = await setup().broadcastMessage("Hello");
    expect(r.sent).toContain("telegram");
  });
});
