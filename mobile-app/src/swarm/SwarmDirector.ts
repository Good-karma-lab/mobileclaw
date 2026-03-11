/**
 * SwarmDirector — separate fast LLM pipeline for emotion/intent classification.
 *
 * Analyzes streaming transcription fragments and agent responses to produce
 * visualization commands (emotion, formation, display_text).
 *
 * Critical design: This is completely separate from the main GUAPPA agent.
 * The main agent handles reasoning and tool use. The SwarmDirector only
 * handles visualization decisions with sub-200ms latency.
 */
import { swarmStore } from './SwarmController';
import type { EmotionKey } from './emotion/EmotionPalette';
import { EMOTION_KEYS } from './emotion/EmotionPalette';
import { quickLlmCall } from '../native/guappaAgent';

const NEURON_COUNT = 420;

interface SwarmDirective {
  emotion: string;
  formation: string | null;
  display_text: string | null;
}

const SWARM_DIRECTOR_PROMPT = `You are the Swarm Director for GUAPPA, an AI visualization system.
You analyze conversation transcript fragments and output ONLY a JSON object
that controls the neural swarm visualization.

Your job: detect emotion, and when appropriate, suggest a visual formation
(shape or text) the swarm should form.

RULES:
- Respond ONLY with valid JSON. No markdown, no explanation.
- emotion: one of: neutral, curious, happy, love, focused, alert, calm, sad,
  excited, mysterious, grateful, proud, playful, anxious, inspired, nostalgic,
  angry, surprised, confused, sleepy
- formation: one of: heart, star, brain, eye, infinity, dna, atom, spiral,
  lightning, diamond, crown, moon, fire, music, tree, wave, check,
  face_happy, face_sad, face_love, face_angry, face_surprise, face_think,
  face_wink, face_cool, face_cry, face_laugh, face_sleep, face_scared,
  scatter, OR null (no change)
- display_text: short text (1-6 chars) to form with nodes, or null
- Only suggest formation/display_text when strongly relevant to what was said.
  Most of the time, just set emotion and leave formation/display_text null.

Examples:
User: "I love this!"        → {"emotion":"love","formation":"heart","display_text":null}
User: "What time is it?"    → {"emotion":"curious","formation":null,"display_text":null}
User: "Say hi to everyone"  → {"emotion":"happy","formation":null,"display_text":"HI"}
User: "I'm so angry"       → {"emotion":"angry","formation":"face_angry","display_text":null}
User: "Let me think..."    → {"emotion":"focused","formation":"brain","display_text":null}
User: "Good night"         → {"emotion":"calm","formation":"moon","display_text":null}
User: "YES!"               → {"emotion":"excited","formation":null,"display_text":"YES"}`;

export class SwarmDirector {
  private debounceTimer: ReturnType<typeof setTimeout> | null = null;
  private lastTranscript = '';
  private pendingCall = false;
  private formationResetTimer: ReturnType<typeof setTimeout> | null = null;

  /**
   * Called with every interim STT transcript update.
   * Debounced to max ~3 calls/second.
   */
  async analyzeTranscript(transcript: string): Promise<SwarmDirective | null> {
    if (transcript === this.lastTranscript) return null;
    if (transcript.length < 3) return null;
    this.lastTranscript = transcript;

    if (this.debounceTimer) clearTimeout(this.debounceTimer);

    return new Promise((resolve) => {
      this.debounceTimer = setTimeout(async () => {
        const directive = await this.callDirector(transcript);
        if (directive) {
          this.applyDirective(directive);
        }
        resolve(directive);
      }, 300);
    });
  }

  /**
   * Called when the main agent starts speaking.
   * Analyzes the agent's response for emotion/formation.
   */
  async analyzeAgentResponse(text: string): Promise<SwarmDirective | null> {
    const prompt = `[GUAPPA is saying this to the user]: ${text.slice(0, 200)}`;
    const directive = await this.callDirector(prompt);
    if (directive) {
      this.applyDirective(directive);
    }
    return directive;
  }

  /**
   * Fallback: detect emotion from keywords without LLM call.
   * Used when no provider is configured.
   */
  detectEmotionLocally(text: string): EmotionKey {
    const lower = text.toLowerCase();
    const keywords: Record<string, EmotionKey> = {
      'love': 'love', 'heart': 'love', 'adore': 'love',
      'happy': 'happy', 'great': 'happy', 'awesome': 'happy', 'wonderful': 'happy',
      'sad': 'sad', 'sorry': 'sad', 'unfortunately': 'sad',
      'angry': 'angry', 'furious': 'angry', 'mad': 'angry',
      'curious': 'curious', 'wonder': 'curious', 'what': 'curious', 'how': 'curious',
      'think': 'focused', 'focus': 'focused', 'analyze': 'focused',
      'calm': 'calm', 'peace': 'calm', 'relax': 'calm',
      'excited': 'excited', 'amazing': 'excited', 'wow': 'excited',
      'surprise': 'surprised', 'unexpected': 'surprised',
      'confused': 'confused', 'unclear': 'confused',
      'sleep': 'sleepy', 'tired': 'sleepy', 'night': 'sleepy',
      'proud': 'proud', 'accomplish': 'proud',
      'play': 'playful', 'fun': 'playful', 'game': 'playful',
      'inspire': 'inspired', 'creative': 'inspired',
      'thank': 'grateful', 'grateful': 'grateful',
      'anxious': 'anxious', 'worry': 'anxious', 'nervous': 'anxious',
      'mystery': 'mysterious', 'secret': 'mysterious',
      'nostalgi': 'nostalgic', 'remember': 'nostalgic',
      'alert': 'alert', 'warning': 'alert', 'danger': 'alert',
    };

    for (const [keyword, emotion] of Object.entries(keywords)) {
      if (lower.includes(keyword)) return emotion;
    }
    return 'neutral';
  }

  private async callDirector(prompt: string): Promise<SwarmDirective | null> {
    // Prevent overlapping calls
    if (this.pendingCall) {
      const emotion = this.detectEmotionLocally(prompt);
      return { emotion, formation: null, display_text: null };
    }

    // Skip LLM call for local models — it blocks the inference queue
    // and adds 3-5 seconds latency to every user message
    try {
      const { useGuappaStore } = require('../state/guappa');
      const state = useGuappaStore?.getState?.();
      const provider = state?.agent?.provider;
      const apiUrl = state?.agent?.apiUrl || '';
      // Local model: provider is "local" or apiUrl points to localhost
      if (provider === 'local' || apiUrl.includes('127.0.0.1:8888') || apiUrl.includes('localhost:8888')) {
        const emotion = this.detectEmotionLocally(prompt);
        return { emotion, formation: null, display_text: null };
      }
    } catch {
      // If store not available, use local detection as safe fallback
      const emotion = this.detectEmotionLocally(prompt);
      return { emotion, formation: null, display_text: null };
    }

    this.pendingCall = true;
    try {
      const response = await quickLlmCall(prompt, SWARM_DIRECTOR_PROMPT);
      this.pendingCall = false;
      // Strip markdown fences if present
      const clean = response.replace(/^```json?\n?|\n?```$/g, '').trim();
      return JSON.parse(clean) as SwarmDirective;
    } catch {
      this.pendingCall = false;
      // Fallback to local keyword detection on any error
      const emotion = this.detectEmotionLocally(prompt);
      return { emotion, formation: null, display_text: null };
    }
  }

  private applyDirective(directive: SwarmDirective) {
    // Validate and apply emotion
    const emotion = EMOTION_KEYS.includes(directive.emotion as EmotionKey)
      ? (directive.emotion as EmotionKey)
      : 'neutral';
    swarmStore.setEmotion(emotion);

    // Apply formation or text
    if (directive.display_text) {
      swarmStore.setDisplayText(directive.display_text);
      swarmStore.setFormation(null);
      this.scheduleFormationReset();
    } else if (directive.formation && directive.formation !== 'scatter') {
      swarmStore.setFormation(directive.formation);
      swarmStore.setDisplayText(null);
      this.scheduleFormationReset();
    }
  }

  /** Reset formation to scatter after 4 seconds (formations are transient visual moments). */
  private scheduleFormationReset() {
    if (this.formationResetTimer) clearTimeout(this.formationResetTimer);
    this.formationResetTimer = setTimeout(() => {
      swarmStore.setFormation(null);
      swarmStore.setDisplayText(null);
      this.formationResetTimer = null;
    }, 4000);
  }

  /** Force reset to idle scatter state. */
  resetToIdle() {
    if (this.formationResetTimer) clearTimeout(this.formationResetTimer);
    swarmStore.setFormation(null);
    swarmStore.setDisplayText(null);
    swarmStore.setEmotion('neutral');
  }
}

// Singleton
export const swarmDirector = new SwarmDirector();
