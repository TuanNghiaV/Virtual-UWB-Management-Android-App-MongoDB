import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

/**
 * VirtualUWB AI Assistant Edge Function
 * Integrates with Google Gemini API with fallback and retry logic.
 */

const TEXT_MODELS = [
  "gemini-2.5-flash-lite",
  "gemini-2.0-flash",
  "gemini-2.5-flash"
];
const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY");

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(payload: any, status: number = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

async function callGeminiWithFallback(fullPrompt: string): Promise<string> {
  let lastError = "No attempts made";

  for (const model of TEXT_MODELS) {
    const apiEndpoint = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${GEMINI_API_KEY}`;

    for (let attempt = 0; attempt < 3; attempt++) { // Initial attempt + 2 retries
      try {
        const response = await fetch(apiEndpoint, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            contents: [
              {
                role: "user",
                parts: [{ text: fullPrompt }],
              },
            ],
            generationConfig: {
              temperature: 0.4,
              maxOutputTokens: 512,
            },
          }),
        });

        if (response.ok) {
          const data = await response.json();
          const answer = data.candidates?.[0]?.content?.parts?.[0]?.text;
          if (answer) return answer;
          lastError = "Gemini returned success but no content parts found.";
          // If success but no content, don't retry this model, try next model
          break;
        }

        lastError = await response.text();
        const retryable = [429, 500, 502, 503, 504].includes(response.status);

        if (!retryable || attempt === 2) {
          // Non-retryable error or exhausted retries for this model
          console.warn(`Model ${model} failed with status ${response.status}: ${lastError}`);
          break;
        }

        // Wait before next retry
        const delay = attempt === 0 ? 600 : 1200;
        await sleep(delay);

      } catch (err: any) {
        lastError = err.message || "Network or unexpected error during fetch.";
        if (attempt === 2) break;
        await sleep(attempt === 0 ? 600 : 1200);
      }
    }
  }

  throw new Error(lastError);
}

serve(async (req) => {
  // 1. Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  // 2. Validate Method
  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  if (!GEMINI_API_KEY) {
    console.error("GEMINI_API_KEY is not set in environment variables.");
    return json({ error: "Internal Server Configuration Error" }, 500);
  }

  try {
    const { question, context } = await req.json();

    // 3. Validate Input
    if (!question || typeof question !== "string" || question.trim() === "") {
      return json({ error: "The 'question' field is missing or empty." }, 400);
    }

    // 4. Prepare System Prompt
    const systemPrompt = `
      You are a helpful AI assistant inside the VirtualUWB Android app.

      You have two roles:

      1. VirtualUWB assistant:
      When the user asks about UWB tags, anchors, indoor positioning, geofences, safe zones, restricted zones, danger status, or navigation inside the app, answer using the provided UWB context.

      Rules for UWB questions:
      - Use the provided UWB context.
      - Do NOT invent tags, zones, coordinates, distances, directions, or events.
      - If the requested tag is missing from the context, say it is not available.
      - If a tag is in a RESTRICTED zone, clearly say it is dangerous.
      - If a tag is in a SAFE zone, clearly say it is safe.
      - AI must not mention raw numeric bearing degrees.
      - Use navigationHintText for direct line-of-sight guidance.
      - If \`context.selectedTagRoute\` is provided, you MAY use its steps for turn-by-turn guidance. Mention that it is an outdoor Google route.
      - If \`context.selectedTagRoute\` is missing or asked for turn-by-turn indoor route, explain that the app currently uses direct UWB/GPS guidance and that indoor floor-plan routing is needed.
      - AI must not invent street-by-street or corridor-by-corridor directions itself. Only use what is in the context.
      - If phone position is unavailable (distance is null), say direction cannot be calculated.
      - If asked about indoor/outdoor status, only say "inside known UWB/geofence area" if inferred from geofence. Do not claim true indoor/outdoor unless building/indoor polygon exists.
      - Keep UWB answers short, clear, and demo-friendly.
      - Match tags by name, id, or deviceCode from the context.

      2. General AI assistant:
      When the user asks a normal everyday question that is not related to VirtualUWB, answer as a helpful general-purpose AI assistant.
      You can help with explanations, writing, learning, coding, planning, brainstorming, and general knowledge.

      Realtime limitation:
      You do not have guaranteed access to live realtime data unless it is explicitly provided in the user question or context.
      For current weather, latest news, live prices, sports scores, or traffic, say that realtime data is not available and ask the user to provide details or connect a realtime data source.

      General rules:
      - Reply in the same language as the user.
      - Be concise by default.
      - Be friendly and practical.
      - The context may include context.phone with the phone's current zone and safety status; use it when answering about the user's location.

      UWB CONTEXT:
      ${JSON.stringify(context || {}, null, 2)}
    `;

    const fullPrompt = `${systemPrompt}\n\nUser Question: ${question}`;

    try {
      // 5. Call Gemini with Fallback and Retry
      const answer = await callGeminiWithFallback(fullPrompt);
      return json({ answer });
    } catch (fallbackError: any) {
      console.error("All Gemini attempts failed:", fallbackError.message);
      return json(
        {
          error: "External AI service communication error.",
          detail: fallbackError.message,
        },
        502
      );
    }

  } catch (err) {
    console.error("Exception in Edge Function:", err);
    return json({ error: "An unexpected error occurred processing your request." }, 500);
  }
});
