import { Request, Response } from 'express';
import { buildAiContext } from '../services/aiContext.service.js';
import { queryGemini, IGeminiError, detectUserLanguage } from '../services/gemini.service.js';

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

  // ── Route & Intent Resolution ──────────────────────────────────────────────
  const { Device } = await import('../models/device.model.js');
  const { computeGoogleRoute } = await import('../services/googleRoutes.service.js');

  let resolvedTagCode = selectedTagCode;
  let resolvedRoute = route;
  let fallbackNote = '';

  const NAV_INTENT_PREFIXES = [
    'guide me to',
    'directions to',
    'route to',
    'how do i get to',
    'navigate to',
    'take me to',
    'find route to',
    'chỉ đường đến',
    'chỉ đường tới',
    'đường đến',
    'đường tới',
    'đi đến',
    'đi tới',
    'dẫn tôi đến',
    'dẫn tôi tới',
    'tìm đường đến',
    'tìm đường tới'
  ];

  const msgLower = message.toLowerCase();
  const isNavQuery = NAV_INTENT_PREFIXES.some(prefix => msgLower.includes(prefix)) ||
    /\b(chỉ đường|bản đồ|hướng dẫn|đi tới|tìm đường|directions?|route|navigate)\b/i.test(msgLower);

  if (isNavQuery) {
    try {
      const activeTags = await Device.find({ role: 'TAG', isActive: true });

      const escapeRegExp = (str: string) => str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      const matchedTags = activeTags.filter(tag => {
        const nameLower = tag.name.toLowerCase();
        const codeLower = tag.deviceCode.toLowerCase();
        const regexName = new RegExp(`(^|\\s|[.,!?:])${escapeRegExp(nameLower)}($|\\s|[.,!?:])`, 'i');
        const regexCode = new RegExp(`(^|\\s|[.,!?:])${escapeRegExp(codeLower)}($|\\s|[.,!?:])`, 'i');
        return regexName.test(msgLower) || regexCode.test(msgLower);
      });

      if (matchedTags.length === 0) {
        // Try to extract name
        let extractedName = '';
        for (const prefix of NAV_INTENT_PREFIXES) {
          const idx = msgLower.indexOf(prefix);
          if (idx !== -1) {
            extractedName = message.substring(idx + prefix.length).trim().replace(/[?.!,]/g, '');
            break;
          }
        }
        if (!extractedName) {
          extractedName = message.split(' ').pop()?.replace(/[?.!,]/g, '') || '';
        }

        const lang = detectUserLanguage(message);
        const ans = lang === 'Vietnamese'
          ? `Tôi không tìm thấy tag tên "${extractedName}". Bạn hãy kiểm tra lại tên tag.`
          : `I couldn't find a tag named "${extractedName}". Please check the tag name.`;

        res.status(200).json({
          answer: ans,
          contextSummary: { tags: activeTags.length, anchors: 0, geofences: 0, recentEvents: 0 },
          source: 'GEMINI'
        });
        return;
      }

      if (matchedTags.length > 1) {
        const lang = detectUserLanguage(message);
        const tagNamesList = matchedTags.map(t => t.name).join(', ');
        const ans = lang === 'Vietnamese'
          ? `Tôi tìm thấy nhiều tag khớp: ${tagNamesList}. Bạn muốn chỉ đường đến tag nào?`
          : `I found multiple matching tags: ${tagNamesList}. Which one would you like directions to?`;

        res.status(200).json({
          answer: ans,
          contextSummary: { tags: activeTags.length, anchors: 0, geofences: 0, recentEvents: 0 },
          source: 'GEMINI'
        });
        return;
      }

      // Single matched tag resolved
      const targetTag = matchedTags[0];
      resolvedTagCode = targetTag.deviceCode;

      if (!phone || !phone.latitude || !phone.longitude) {
        const lang = detectUserLanguage(message);
        const ans = lang === 'Vietnamese'
          ? `Tôi cần vị trí hiện tại của điện thoại để tính đường đi.`
          : `I need your current phone location to calculate directions.`;

        res.status(200).json({
          answer: ans,
          contextSummary: { tags: activeTags.length, anchors: 0, geofences: 0, recentEvents: 0 },
          source: 'GEMINI'
        });
        return;
      }

      // Compute route internally using coordinates
      try {
        const routeResult = await computeGoogleRoute({
          origin: { latitude: phone.latitude, longitude: phone.longitude },
          destination: { latitude: targetTag.latitude, longitude: targetTag.longitude },
          travelMode: 'WALK'
        });
        resolvedRoute = routeResult;
      } catch (routeErr: any) {
        console.error('Failed to compute Google Route for AI context:', routeErr);
        fallbackNote = `\nNOTE: Google Routes API is currently unavailable. Please explain that street-level routing is unavailable and guide the user using direct distance/bearing fallback coordinates.`;
      }
    } catch (dbErr) {
      console.error('Error in navigation intent check:', dbErr);
    }
  }

  try {
    // 4. Build context from MongoDB
    const { promptText, summary } = await buildAiContext(
      message,
      resolvedTagCode,
      phone,
      resolvedRoute
    );

    // Create the final prompt by combining context and user query
    const finalPrompt = `${promptText}${fallbackNote}

User Query: "${message}"

Please answer the user query based on the guidelines above.`;

    // 5. Query Gemini
    const userLanguage = detectUserLanguage(message);
    const answer = await queryGemini(finalPrompt, userLanguage);

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
