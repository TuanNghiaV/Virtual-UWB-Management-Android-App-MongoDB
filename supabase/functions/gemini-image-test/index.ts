import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

/**
 * VirtualUWB Gemini Image Generation Edge Function
 * Tests Gemini image generation with cost-conscious model fallback strategy.
 */

const IMAGE_MODELS = [
  "gemini-2.5-flash-image",
  "gemini-3.1-flash-image-preview",
  "gemini-3-pro-image-preview"
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

async function callGeminiImageWithFallback(
  prompt: string,
  responseModalities: string[] = ["IMAGE"]
): Promise<{ imageBase64: string; mimeType: string; model: string }> {
  let lastError = "No attempts made";
  let needsTextModality = false;

  for (const model of IMAGE_MODELS) {
    const apiEndpoint = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${GEMINI_API_KEY}`;

    for (let attempt = 0; attempt < 3; attempt++) { // Initial attempt + 2 retries
      try {
        const modalities = needsTextModality ? ["TEXT", "IMAGE"] : responseModalities;

        const response = await fetch(apiEndpoint, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            contents: [
              {
                role: "user",
                parts: [
                  {
                    text: prompt,
                  },
                ],
              },
            ],
            generationConfig: {
              responseModalities: modalities,
            },
          }),
        });

        if (response.ok) {
          const data = await response.json();
          const parts = data.candidates?.[0]?.content?.parts;

          if (!parts) {
            lastError = "Gemini returned success but no content parts found.";
            break;
          }

          // Look for inline image data
          for (const part of parts) {
            if (part.inlineData) {
              return {
                imageBase64: part.inlineData.data,
                mimeType: part.inlineData.mimeType || "image/png",
                model,
              };
            }
            if (part.inline_data) {
              return {
                imageBase64: part.inline_data.data,
                mimeType: part.inline_data.mimeType || "image/png",
                model,
              };
            }
          }

          // If text response says responseModalities needs adjustment, retry
          if (
            parts.some(
              (p: any) =>
                p.text &&
                p.text.toLowerCase().includes("responsemodalities must include text")
            )
          ) {
            needsTextModality = true;
            continue;
          }

          lastError = `No image data found in response. Parts: ${JSON.stringify(parts)}`;
          break;
        }

        lastError = await response.text();
        const retryable = [429, 500, 502, 503, 504].includes(response.status);

        if (!retryable || attempt === 2) {
          console.warn(`Model ${model} failed with status ${response.status}: ${lastError}`);
          break;
        }

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
    const { prompt } = await req.json();

    // 3. Validate Input
    if (!prompt || typeof prompt !== "string" || prompt.trim() === "") {
      return json({ error: "The 'prompt' field is missing or empty." }, 400);
    }

    try {
      // 4. Call Gemini Image Generation with Fallback and Retry
      const result = await callGeminiImageWithFallback(prompt);
      return json({
        imageBase64: result.imageBase64,
        mimeType: result.mimeType,
        model: result.model,
      });
    } catch (fallbackError: any) {
      console.error("All Gemini image generation attempts failed:", fallbackError.message);
      return json(
        {
          error: "Image generation failed",
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
