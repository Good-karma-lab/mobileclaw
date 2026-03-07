import { useState, useCallback } from "react";
import { NativeModules, Platform, Share } from "react-native";

const { GuappaAgent } = NativeModules;

interface DebugInfoState {
  loading: boolean;
  error: string | null;
}

export function useDebugInfo() {
  const [state, setState] = useState<DebugInfoState>({
    loading: false,
    error: null,
  });

  const downloadDebugInfo = useCallback(async () => {
    if (Platform.OS !== "android") {
      setState({ loading: false, error: "Debug info only available on Android" });
      return;
    }

    setState({ loading: true, error: null });

    try {
      let filePath: string;

      if (GuappaAgent?.collectDebugInfo) {
        filePath = await GuappaAgent.collectDebugInfo();
      } else {
        // Fallback: collect basic info from JS side
        const info = {
          appVersion: "0.1.0",
          platform: Platform.OS,
          osVersion: Platform.Version,
          timestamp: new Date().toISOString(),
          note: "Native debug collector not available. Basic info only.",
        };
        // Share as text fallback
        await Share.share({
          message: JSON.stringify(info, null, 2),
          title: "Guappa Debug Info",
        });
        setState({ loading: false, error: null });
        return;
      }

      // Trigger Android share sheet with the ZIP file
      await Share.share({
        url: `file://${filePath}`,
        title: "Guappa Debug Info",
      });

      setState({ loading: false, error: null });
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      console.error("[useDebugInfo] Failed to collect debug info:", message);
      setState({ loading: false, error: message });
    }
  }, []);

  return {
    ...state,
    downloadDebugInfo,
  };
}
