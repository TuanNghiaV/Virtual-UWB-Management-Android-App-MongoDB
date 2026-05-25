import { env } from '../config/env.js';

const UPSTREAM_TIMEOUT_MS = 10_000;

export interface IGeminiError {
  error: string;
  message: string;
}

const SYSTEM_INSTRUCTION = `You are the AI Assistant inside the VirtualUWB application, a system for simulator-based Ultra-Wideband (UWB) indoor positioning, geofencing, and safety monitoring.

Follow these strict rules:
1. Reply in the same language as the user. If the user asks in Vietnamese, reply in Vietnamese.
2. If the user's question is about UWB, tags, anchors, geofences, safety, danger, route, map, or events, answer using the provided context.
3. Do not invent tags, zones, coordinates, distances, events, or routes. Only use the data present in the context.
4. If the requested tag is missing or not configured, clearly state that it is not available.
5. If a tag is in RESTRICTED_ZONE, clearly state that it is dangerous (nguy hiểm).
6. If a tag is in SAFE_ZONE, clearly state that it is safe (an toàn).
7. Google Maps routing/directions is temporarily disabled. If the user asks for directions, routing, or how to get somewhere, explicitly reply: "Tính năng route theo Google Maps đang được tạm tắt. App hiện vẫn hỗ trợ xem vị trí tag, khoảng cách trực tiếp và trạng thái vùng an toàn/nguy hiểm." Do not invent or simulate turn-by-turn route steps.
8. The app uses direct distance and direction (direct guidance) to guide users to tags.
9. Keep answers concise and demo-friendly by default.
10. If the question is general and unrelated to VirtualUWB, answer as a general assistant.
11. Do not claim access to live realtime data beyond the context provided.`;

/**
 * Calls a specific Gemini model endpoint.
 */
const callGeminiModel = async (
  model: string,
  prompt: string,
  apiKey: string
): Promise<string> => {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;

  const body = {
    systemInstruction: {
      parts: [
        {
          text: SYSTEM_INSTRUCTION,
        },
      ],
    },
    contents: [
      {
        parts: [
          {
            text: prompt,
          },
        ],
      },
    ],
    generationConfig: {
      maxOutputTokens: 768,
    },
  };

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS);

  let rawResponse: Response;
  try {
    rawResponse = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
  } catch (err: any) {
    if (err.name === 'AbortError') {
      throw {
        error: 'UPSTREAM_TIMEOUT',
        message: `Gemini API model ${model} did not respond within ${UPSTREAM_TIMEOUT_MS / 1000}s.`,
      } as IGeminiError;
    }
    throw {
      error: 'GEMINI_NETWORK_ERROR',
      message: `Network error contacting Gemini API model ${model}.`,
    } as IGeminiError;
  } finally {
    clearTimeout(timeoutId);
  }

  if (!rawResponse.ok) {
    let errorDetail: any;
    try {
      errorDetail = await rawResponse.json();
    } catch {
      // Ignored if unable to parse JSON
    }

    let statusLabel: string;
    switch (rawResponse.status) {
      case 400:
        statusLabel = 'BAD_REQUEST';
        break;
      case 401:
      case 403:
        statusLabel = 'UNAUTHORIZED';
        break;
      case 429:
        statusLabel = 'RATE_LIMITED';
        break;
      default:
        statusLabel = 'UPSTREAM_ERROR';
    }

    throw {
      error: `GEMINI_${statusLabel}`,
      message: errorDetail?.error?.message || `Gemini API model ${model} returned HTTP ${rawResponse.status}.`,
    } as IGeminiError;
  }

  let json: any;
  try {
    json = await rawResponse.json();
  } catch {
    throw {
      error: 'MALFORMED_GEMINI_RESPONSE',
      message: `Failed to parse JSON response from Gemini API model ${model}.`,
    } as IGeminiError;
  }

  const text = json?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (!text) {
    throw {
      error: 'MALFORMED_GEMINI_RESPONSE',
      message: `Response from Gemini model ${model} did not contain text content.`,
    } as IGeminiError;
  }

  return text;
};

/**
 * Orchestrates calls to Gemini with a fallback model sequence.
 */
export const queryGemini = async (prompt: string): Promise<string> => {
  const apiKey = env.GEMINI_API_KEY;

  if (!apiKey) {
    throw {
      error: 'SERVICE_CONFIGURATION_ERROR',
      message: 'Gemini API key is not configured on the backend. Set GEMINI_API_KEY in backend/.env.',
    } as IGeminiError;
  }

  const fallbackModels = [
    'gemini-2.5-flash-lite',
    'gemini-2.0-flash',
    'gemini-2.5-flash',
  ];

  let lastError: any = null;

  for (const model of fallbackModels) {
    try {
      console.log(`[Gemini] Attempting generation using model: ${model}`);
      const result = await callGeminiModel(model, prompt, apiKey);
      console.log(`[Gemini] Generation succeeded with model: ${model}`);
      return result;
    } catch (err: any) {
      lastError = err;
      console.warn(
        `[Gemini] Model ${model} failed: ${err.message || JSON.stringify(err)}. Trying fallback...`
      );
    }
  }

  // If we reach here, all fallback models failed
  throw lastError || {
    error: 'GEMINI_ERROR',
    message: 'All fallback Gemini models failed to respond.',
  };
};
