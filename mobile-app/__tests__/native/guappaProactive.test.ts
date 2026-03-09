/**
 * Unit tests for the GuappaProactive TypeScript bridge layer.
 */
import { NativeModules, Platform } from "react-native";

describe("guappaProactive — module unavailable", () => {
  beforeEach(() => { jest.resetModules(); NativeModules.GuappaProactive = null; });
  const load = () => require("../../src/native/guappaProactive");

  it("getTriggers returns []", async () => { expect(await load().getTriggers()).toEqual([]); });
  it("addTrigger returns false", async () => {
    expect(await load().addTrigger({ type: "event_based", name: "T", description: "", enabled: true, config: {} })).toBe(false);
  });
  it("getQuietHours returns null", async () => { expect(await load().getQuietHours()).toBeNull(); });
  it("isInQuietHours returns false", async () => { expect(await load().isInQuietHours()).toBe(false); });
  it("getMorningBriefingConfig returns null", async () => { expect(await load().getMorningBriefingConfig()).toBeNull(); });
  it("getNotificationHistory returns []", async () => { expect(await load().getNotificationHistory()).toEqual([]); });
});

describe("guappaProactive — module available", () => {
  const setup = () => {
    jest.resetModules();
    NativeModules.GuappaProactive = {
      getTriggers: jest.fn().mockResolvedValue(JSON.stringify([
        { id: "t1", type: "EVENT_BASED", name: "Missed Call", description: "", config: {}, action: "notify", enabled: true, createdAt: 0 },
      ])),
      addTrigger: jest.fn().mockResolvedValue(true),
      removeTrigger: jest.fn().mockResolvedValue(true),
      toggleTrigger: jest.fn().mockResolvedValue(true),
      evaluateTriggers: jest.fn().mockResolvedValue("[]"),
      setQuietHours: jest.fn().mockResolvedValue(true),
      getQuietHours: jest.fn().mockResolvedValue(JSON.stringify({ startHour: 22, endHour: 7, enabled: true })),
      isInQuietHours: jest.fn().mockResolvedValue(false),
      setCooldown: jest.fn().mockResolvedValue(true),
      setMorningBriefing: jest.fn().mockResolvedValue(true),
      getMorningBriefingConfig: jest.fn().mockResolvedValue(JSON.stringify({ enabled: true, hour: 7, minute: 30 })),
      setEveningSummary: jest.fn().mockResolvedValue(true),
      getEveningSummaryConfig: jest.fn().mockResolvedValue(JSON.stringify({ enabled: false, hour: 21, minute: 0 })),
      generateBriefingNow: jest.fn().mockResolvedValue("Morning briefing"),
      getNotificationHistory: jest.fn().mockResolvedValue("[]"),
      clearNotificationHistory: jest.fn().mockResolvedValue(true),
      setNotificationEnabled: jest.fn().mockResolvedValue(true),
    };
    return require("../../src/native/guappaProactive");
  };

  it("getTriggers returns parsed list", async () => {
    const triggers = await setup().getTriggers();
    expect(triggers).toHaveLength(1);
    expect(triggers[0].name).toBe("Missed Call");
  });
  it("getQuietHours returns config", async () => {
    const c = await setup().getQuietHours();
    expect(c?.startHour).toBe(22);
    expect(c?.enabled).toBe(true);
  });
  it("getMorningBriefingConfig returns config", async () => {
    const c = await setup().getMorningBriefingConfig();
    expect(c?.enabled).toBe(true);
    expect(c?.hour).toBe(7);
  });
  it("setQuietHours calls native", async () => {
    const mod = setup();
    expect(await mod.setQuietHours(23, 6)).toBe(true);
  });
});
