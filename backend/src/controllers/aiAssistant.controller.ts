import { Request, Response } from 'express';
import { buildAiContext } from '../services/aiContext.service.js';
import { queryGemini, IGeminiError } from '../services/gemini.service.js';

/**
 * POST /api/ai/assistant
 *
 * Location-aware AI Assistant.
 * Builds positioning context from MongoDB, queries Gemini, and returns answers.
 */
export const askAssistant = async (req: Request, res: Response): Promise<void> => {
  const { message, phone, selectedTagCode, route } = req.body;

  // 1. Validate message
  if (message === undefined || message === null) {
    res.status(400).json({
      error: 'INVALID_REQUEST',
      message: 'message field is required.',
    });
    return;
  }

  if (typeof message !== 'string' || message.trim() === '') {
    res.status(400).json({
      error: 'INVALID_REQUEST',
      message: 'message must be a non-empty string.',
    });
    return;
  }

  // 2. Validate phone location (optional)
  if (phone !== undefined) {
    if (typeof phone !== 'object' || phone === null) {
      res.status(400).json({
        error: 'INVALID_REQUEST',
        message: 'phone must be an object with latitude and longitude.',
      });
      return;
    }
    const lat = Number(phone.latitude);
    const lng = Number(phone.longitude);
    if (isNaN(lat) || lat < -90 || lat > 90) {
      res.status(400).json({
        error: 'INVALID_REQUEST',
        message: 'phone.latitude must be a number between -90 and 90.',
      });
      return;
    }
    if (isNaN(lng) || lng < -180 || lng > 180) {
      res.status(400).json({
        error: 'INVALID_REQUEST',
        message: 'phone.longitude must be a number between -180 and 180.',
      });
      return;
    }
  }

  // 3. Validate route (optional)
  if (route !== undefined) {
    if (typeof route !== 'object' || route === null) {
      res.status(400).json({
        error: 'INVALID_REQUEST',
        message: 'route must be an object.',
      });
      return;
    }
    // basic check of route structure
    if (route.steps !== undefined && !Array.isArray(route.steps)) {
      res.status(400).json({
        error: 'INVALID_REQUEST',
        message: 'route.steps must be an array.',
      });
      return;
    }
  }

  try {
    // 4. Build context from MongoDB
    const { promptText, summary } = await buildAiContext(
      message,
      selectedTagCode,
      phone,
      route
    );

    // Create the final prompt by combining context and user query
    const finalPrompt = `${promptText}

User Query: "${message}"

Please answer the user query based on the guidelines above.`;

    // 5. Query Gemini
    const answer = await queryGemini(finalPrompt);

    // 6. Return response
    res.status(200).json({
      answer,
      contextSummary: summary,
      source: 'GEMINI',
    });
  } catch (err: any) {
    // Cast error to our standard shape
    const geminiError = err as IGeminiError;

    // Handle missing Gemini API Key specifically
    if (geminiError.error === 'SERVICE_CONFIGURATION_ERROR') {
      res.status(500).json({
        error: 'SERVICE_CONFIGURATION_ERROR',
        message: 'Gemini API is not configured on the backend. Contact the administrator.',
      });
      return;
    }

    // Handle timeout error
    if (geminiError.error === 'UPSTREAM_TIMEOUT') {
      res.status(504).json({
        error: 'UPSTREAM_TIMEOUT',
        message: 'The AI assistant upstream request timed out. Please try again.',
      });
      return;
    }

    // Handle malformed response error
    if (geminiError.error === 'MALFORMED_GEMINI_RESPONSE') {
      res.status(502).json({
        error: 'BAD_UPSTREAM_RESPONSE',
        message: 'Received a malformed response from the AI service provider.',
      });
      return;
    }

    // Handle rate limiting or bad requests
    const statusCode =
      geminiError.error === 'GEMINI_RATE_LIMITED' ? 429 :
      geminiError.error === 'GEMINI_BAD_REQUEST' ? 400 :
      502; // Bad Gateway for general upstream failures

    res.status(statusCode).json({
      error: 'GEMINI_ERROR',
      message: geminiError.message || 'An error occurred while communicating with the Gemini AI service.',
    });
  }
};
