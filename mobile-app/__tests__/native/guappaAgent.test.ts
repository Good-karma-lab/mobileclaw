/**
 * Unit tests for the GuappaAgent TypeScript bridge layer.
 */
import { NativeModules, Platform } from "react-native";

describe("guappaAgent — module unavailable", () => {
  beforeEach(() => { jest.resetModules(); NativeModules.GuappaAgent = null; });
  const load = () => require("../../src/native/guappaAgent");

  it("startAgent throws", async () => {
    await expect(load().startAgent()).rejects.toThrow();
  });
  it("sendMessage throws", async () => {
    await expect(load().sendMessage("hi")).rejects.toThrow();
  });
  it("stopAgent throws", async () => {
    await expect(load().stopAgent()).rejects.toThrow();
  });
  it("isAgentRunning returns false", async () => {
    expect(await load().isAgentRunning()).toBe(false);
  });
  it("subscribeToAgentEvents returns no-op", () => {
    const unsub = load().subscribeToAgentEvents(() => {});
    expect(typeof unsub).toBe("function");
    unsub();
  });
});

describe("guappaAgent — module available", () => {
  const setup = () => {
    jest.resetModules();
    NativeModules.GuappaAgent = {
      startAgent: jest.fn().mockResolvedValue(true),
      sendMessage: jest.fn().mockResolvedValue("Response"),
      sendMessageStream: jest.fn().mockResolvedValue("sess-1"),
      stopAgent: jest.fn().mockResolvedValue(true),
      isAgentRunning: jest.fn().mockResolvedValue(true),
      collectDebugInfo: jest.fn().mockResolvedValue("/debug.zip"),
    };
    return require("../../src/native/guappaAgent");
  };

  it("startAgent returns true", async () => {
    expect(await setup().startAgent({ provider: "openai" })).toBe(true);
  });
  it("sendMessage returns response", async () => {
    expect(await setup().sendMessage("Hello")).toBe("Response");
  });
  it("sendMessageStream returns session", async () => {
    expect(await setup().sendMessageStream("Hello")).toBe("sess-1");
  });
  it("stopAgent returns true", async () => {
    expect(await setup().stopAgent()).toBe(true);
  });
  it("isAgentRunning returns true", async () => {
    expect(await setup().isAgentRunning()).toBe(true);
  });
  it("collectDebugInfo returns path", async () => {
    expect(await setup().collectDebugInfo()).toBe("/debug.zip");
  });
  it("quickLlmCall sends prompt and system prompt", async () => {
    jest.resetModules();
    NativeModules.GuappaAgent = {
      startAgent: jest.fn().mockResolvedValue(true),
      sendMessage: jest.fn().mockResolvedValue("Response"),
      sendMessageStream: jest.fn().mockResolvedValue("sess-1"),
      stopAgent: jest.fn().mockResolvedValue(true),
      isAgentRunning: jest.fn().mockResolvedValue(true),
      collectDebugInfo: jest.fn().mockResolvedValue("/debug.zip"),
      quickLlmCall: jest.fn().mockResolvedValue('{"emotion":"happy","formation":"heart","display_text":null}'),
    };
    const mod = require("../../src/native/guappaAgent");
    const result = await mod.quickLlmCall("I love this!", "You are the Swarm Director");
    expect(result).toContain("happy");
    expect(NativeModules.GuappaAgent.quickLlmCall).toHaveBeenCalledWith("I love this!", "You are the Swarm Director");
  });
  it("quickLlmCall throws when module unavailable", async () => {
    jest.resetModules();
    NativeModules.GuappaAgent = null;
    const mod = require("../../src/native/guappaAgent");
    await expect(mod.quickLlmCall("test", "sys")).rejects.toThrow();
  });
});
