import { Request, Response } from 'express';
import {
  computeGoogleRoute,
  TravelMode,
  IRouteError,
} from '../services/googleRoutes.service.js';

const VALID_TRAVEL_MODES: TravelMode[] = ['WALK', 'DRIVE', 'TWO_WHEELER', 'BICYCLE'];

/**
 * POST /api/routes/google
 *
 * Proxy endpoint for Google Routes API.
 * Android calls this backend — it must NEVER call Google Routes directly.
 * GOOGLE_ROUTES_API_KEY stays only in backend/.env.
 *
 * TODO: Add authentication/authorization before deploying to production.
 */
export const computeRoute = async (req: Request, res: Response): Promise<void> => {
  const { origin, destination, travelMode } = req.body;

  // ── Validate origin ──────────────────────────────────────────────────────────
  if (!origin || typeof origin !== 'object') {
    res.status(400).json({ error: 'INVALID_REQUEST', message: 'origin is required and must be an object.' });
    return;
  }
  const originLat = Number(origin.latitude);
  const originLng = Number(origin.longitude);
  if (isNaN(originLat) || originLat < -90 || originLat > 90) {
    res.status(400).json({ error: 'INVALID_REQUEST', message: 'origin.latitude must be a number between -90 and 90.' });
    return;
  }
  if (isNaN(originLng) || originLng < -180 || originLng > 180) {
    res.status(400).json({ error: 'INVALID_REQUEST', message: 'origin.longitude must be a number between -180 and 180.' });
    return;
  }

  // ── Validate destination ─────────────────────────────────────────────────────
  if (!destination || typeof destination !== 'object') {
    res.status(400).json({ error: 'INVALID_REQUEST', message: 'destination is required and must be an object.' });
    return;
  }
  const destLat = Number(destination.latitude);
  const destLng = Number(destination.longitude);
  if (isNaN(destLat) || destLat < -90 || destLat > 90) {
    res.status(400).json({ error: 'INVALID_REQUEST', message: 'destination.latitude must be a number between -90 and 90.' });
    return;
  }
  if (isNaN(destLng) || destLng < -180 || destLng > 180) {
    res.status(400).json({ error: 'INVALID_REQUEST', message: 'destination.longitude must be a number between -180 and 180.' });
    return;
  }

  // ── Validate travelMode (optional, default WALK) ─────────────────────────────
  const resolvedMode: TravelMode = travelMode ?? 'WALK';
  if (!VALID_TRAVEL_MODES.includes(resolvedMode)) {
    res.status(400).json({
      error: 'INVALID_REQUEST',
      message: `travelMode must be one of: ${VALID_TRAVEL_MODES.join(', ')}.`,
    });
    return;
  }

  // ── Call Google Routes proxy service ─────────────────────────────────────────
  try {
    const result = await computeGoogleRoute({
      origin: { latitude: originLat, longitude: originLng },
      destination: { latitude: destLat, longitude: destLng },
      travelMode: resolvedMode,
    });
    res.status(200).json(result);
  } catch (err: any) {
    // err is a structured IRouteError thrown by the service
    const routeError = err as IRouteError;

    if (routeError.error === 'GOOGLE_ROUTES_NOT_CONFIGURED') {
      res.status(500).json({
        error: 'SERVICE_CONFIGURATION_ERROR',
        message: 'Google Routes API is not configured on the backend. Contact the administrator.',
      });
      return;
    }

    if (routeError.error === 'GOOGLE_ROUTES_FORBIDDEN') {
      res.status(403).json({
        error: 'GOOGLE_ROUTES_FORBIDDEN',
        message: 'Google Routes API returned HTTP 403.',
      });
      return;
    }

    if (routeError.error === 'GOOGLE_ROUTES_RATE_LIMITED') {
      res.status(429).json({
        error: 'GOOGLE_ROUTES_RATE_LIMITED',
        message: 'Google Routes API returned HTTP 429.',
      });
      return;
    }

    if (routeError.error === 'NO_ROUTE_FOUND') {
      res.status(404).json(routeError);
      return;
    }

    if (routeError.error === 'GOOGLE_ROUTES_BAD_REQUEST') {
      res.status(400).json({
        error: 'INVALID_REQUEST',
        message: 'Google Routes API returned HTTP 400.',
      });
      return;
    }

    // All other upstream errors (timeout, network error, parse error, etc.) -> 502
    res.status(502).json({
      error: 'GOOGLE_ROUTES_ERROR',
      message: routeError.message ?? 'An error occurred while computing the route.',
    });
  }
};
