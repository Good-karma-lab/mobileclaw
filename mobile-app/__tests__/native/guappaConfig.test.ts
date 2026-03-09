/**
 * Unit tests for the GuappaConfig TypeScript bridge layer.
 */
import { NativeModules, Platform } from "react-native";

describe("guappaConfig — module unavailable", () => {
  beforeEach(() => { jest.resetModules(); NativeModules.GuappaConfig = null; });
  const load = () => require("../../src/native/guappaConfig");

  it("getProviderModels returns []", async () => {
    expect(await load().getProviderModels({ provider: "openai" })).toEqual([]);
  });
  it("getProviderHealth returns false", async () => {
    expect(await load().getProviderHealth({ provider: "openai" })).toBe(false);
  });
  it("getSecureString returns null", async () => {
    expect(await load().getSecureString("k")).toBeNull();
  });
  it("setSecureString is no-op", async () => {
    await load().setSecureString("k", "v");
  });
});

describe("guappaConfig — module available", () => {
  const setup = () => {
    jest.resetModules();
    NativeModules.GuappaConfig = {
      getProviderModels: jest.fn().mockResolvedValue([
        { id: "gpt-4", name: "GPT-4" },
        { id: "gpt-3.5-turbo", name: "GPT-3.5" },
      ]),
      getProviderHealth: jest.fn().mockResolvedValue(true),
      getSecureString: jest.fn().mockResolvedValue("stored"),
      setSecureString: jest.fn().mockResolvedValue(true),
      removeSecureString: jest.fn().mockResolvedValue(true),
    };
    return require("../../src/native/guappaConfig");
  };

  it("getProviderModels returns models", async () => {
    const models = await setup().getProviderModels({ provider: "openai" });
    expect(models).toHaveLength(2);
    expect(models[0].id).toBe("gpt-4");
  });
  it("getProviderModels filters empty IDs", async () => {
    jest.resetModules();
    NativeModules.GuappaConfig = {
      getProviderModels: jest.fn().mockResolvedValue([{ id: "ok", name: "OK" }, { id: "", name: "" }]),
      getProviderHealth: jest.fn(),
      getSecureString: jest.fn(),
      setSecureString: jest.fn(),
      removeSecureString: jest.fn(),
    };
    const m = require("../../src/native/guappaConfig");
    const models = await m.getProviderModels({ provider: "x" });
    expect(models).toHaveLength(1);
  });
  it("getProviderHealth returns true", async () => {
    expect(await setup().getProviderHealth({ provider: "openai" })).toBe(true);
  });
  it("getSecureString returns stored value", async () => {
    expect(await setup().getSecureString("key")).toBe("stored");
  });
});
