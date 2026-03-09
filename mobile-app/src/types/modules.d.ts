declare module "@siteed/expo-audio-studio" {
  export type AudioStreamEvent = { data?: string };
  export type AudioAnalysis = { dataPoints?: Array<{ amplitude?: number }> };

  export type StartRecordingOptions = {
    sampleRate: number;
    channels: number;
    encoding: string;
    interval: number;
    enableProcessing: boolean;
    onAudioStream?: (event: AudioStreamEvent) => Promise<void> | void;
    onAudioAnalysis?: (analysis: AudioAnalysis) => Promise<void> | void;
  };

  export type AudioRecorder = {
    startRecording: (opts: StartRecordingOptions) => Promise<void>;
    stopRecording: () => Promise<void>;
  };

  export function useAudioRecorder(): AudioRecorder;
}

declare module "whisper.rn" {
  export type WhisperContext = {
    transcribe: (
      audioUri: string,
      options?: { language?: string }
    ) => { promise: Promise<{ result?: string }> };
  };

  export function initWhisper(options: { filePath: string }): Promise<WhisperContext>;
}

// Native module declarations for Guappa Kotlin backend bridges

declare module "react-native" {
  interface NativeModulesStatic {
    GuappaAgent: {
      startAgent(config: Record<string, unknown>): Promise<boolean>;
      sendMessage(text: string, sessionId: string | null): Promise<string>;
      sendMessageStream(text: string, sessionId: string | null): Promise<string>;
      stopAgent(): Promise<boolean>;
      isAgentRunning(): Promise<boolean>;
      collectDebugInfo(): Promise<string>;
    };
    GuappaConfig: {
      getProviderModels(config: Record<string, unknown>): Promise<Array<Record<string, unknown>>>;
      getProviderHealth(config: Record<string, unknown>): Promise<boolean>;
      getSecureString(key: string): Promise<string | null>;
      setSecureString(key: string, value: string): Promise<boolean>;
      removeSecureString(key: string): Promise<boolean>;
    };
    GuappaMemory: {
      getMemories(category: string | null, tier: string | null): Promise<Array<Record<string, unknown>>>;
      addMemory(key: string, value: string, category: string, tier: string | null, importance: number): Promise<Record<string, unknown>>;
      searchMemories(query: string): Promise<Array<Record<string, unknown>>>;
      semanticSearch(query: string, limit: number): Promise<Array<Record<string, unknown>>>;
      deleteMemory(id: string): Promise<boolean>;
      getSessionHistory(limit: number): Promise<Array<Record<string, unknown>>>;
      createSession(title: string | null): Promise<Record<string, unknown>>;
      endSession(sessionId: string, summary: string | null): Promise<boolean>;
      getSessionMessages(sessionId: string): Promise<Array<Record<string, unknown>>>;
      getTasks(): Promise<Array<Record<string, unknown>>>;
      getActiveTasks(): Promise<Array<Record<string, unknown>>>;
      addTask(title: string, description: string | null, priority: number, dueDate: number): Promise<Record<string, unknown>>;
      updateTaskStatus(taskId: string, status: string): Promise<boolean>;
      deleteTask(taskId: string): Promise<boolean>;
      getEpisodes(limit: number): Promise<Array<Record<string, unknown>>>;
      getMemoryStats(): Promise<Record<string, unknown>>;
      runCleanup(): Promise<Record<string, unknown>>;
      runPromotion(): Promise<Record<string, unknown>>;
    };
    GuappaChannels: {
      listChannels(): Promise<string>;
      configureChannel(channelId: string, config: string): Promise<boolean>;
      removeChannel(channelId: string): Promise<boolean>;
      testChannel(channelId: string): Promise<string>;
      sendMessage(channelId: string, message: string): Promise<boolean>;
      broadcastMessage(message: string): Promise<string>;
      getChannelStatus(channelId: string): Promise<string>;
      setAllowlist(channelId: string, allowedIds: string): Promise<boolean>;
      getAllowlist(channelId: string): Promise<string>;
    };
    GuappaSwarm: {
      generateIdentity(): Promise<string>;
      getIdentity(): Promise<string>;
      getFingerprint(): Promise<string>;
      setDisplayName(name: string): Promise<boolean>;
      connect(connectorUrl: string): Promise<boolean>;
      disconnect(): Promise<boolean>;
      isConnected(): Promise<boolean>;
      getConnectionStatus(): Promise<string>;
      getPeers(): Promise<string>;
      getPeerCount(): Promise<number>;
      sendSwarmMessage(recipientId: string, content: string): Promise<boolean>;
      broadcastSwarmMessage(content: string): Promise<boolean>;
      getRecentMessages(limit: number): Promise<string>;
      getAvailableTasks(): Promise<string>;
      acceptTask(taskId: string): Promise<boolean>;
      rejectTask(taskId: string): Promise<boolean>;
      reportTaskResult(taskId: string, result: string, success: boolean): Promise<boolean>;
      getActiveTasks(): Promise<string>;
      getCompletedTaskCount(): Promise<number>;
      getReputation(): Promise<string>;
      getReputationTier(): Promise<string>;
      joinHolon(holonId: string): Promise<boolean>;
      leaveHolon(holonId: string): Promise<boolean>;
      submitProposal(holonId: string, proposal: string): Promise<boolean>;
      castVote(holonId: string, proposalId: string, ranking: string): Promise<boolean>;
      getActiveHolons(): Promise<string>;
      getSwarmStats(): Promise<string>;
      setPollingInterval(ms: number): Promise<boolean>;
      setAutoConnect(enabled: boolean): Promise<boolean>;
      registerCapabilities(caps: string): Promise<boolean>;
    };
    GuappaProactive: {
      getTriggers(): Promise<string>;
      addTrigger(triggerJson: string): Promise<boolean>;
      removeTrigger(triggerId: string): Promise<boolean>;
      toggleTrigger(triggerId: string, enabled: boolean): Promise<boolean>;
      evaluateTriggers(): Promise<string>;
      setQuietHours(startHour: number, endHour: number): Promise<boolean>;
      getQuietHours(): Promise<string>;
      isInQuietHours(): Promise<boolean>;
      setCooldown(channelId: string, cooldownMs: number): Promise<boolean>;
      setMorningBriefing(enabled: boolean, hour: number, minute: number): Promise<boolean>;
      getMorningBriefingConfig(): Promise<string>;
      setEveningSummary(enabled: boolean, hour: number, minute: number): Promise<boolean>;
      getEveningSummaryConfig(): Promise<string>;
      generateBriefingNow(): Promise<string>;
      getNotificationHistory(limit: number): Promise<string>;
      clearNotificationHistory(): Promise<boolean>;
      setNotificationEnabled(channelId: string, enabled: boolean): Promise<boolean>;
    };
  }
}

// Keep this file for any ad-hoc module shims.
