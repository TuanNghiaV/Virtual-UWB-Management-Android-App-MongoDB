/**
 * Google Routes API proxy service.
 *
 * SECURITY NOTE: GOOGLE_ROUTES_API_KEY is NEVER logged, returned to the client,
 * or included in any error message. All API calls use the key only as an HTTP header.
 *
 * Android must call /api/routes/google (this backend).
 * Android must NEVER call Google Routes API directly.
 */

import { env } from '../config/env.js';

const GOOGLE_ROUTES_API_URL =
  'https://routes.googleapis.com/directions/v2:computeRoutes';

const FIELD_MASK = [
  'routes.distanceMeters',
  'routes.duration',
  'routes.polyline.encodedPolyline',
  'routes.legs.steps.distanceMeters',
  'routes.legs.steps.staticDuration',
  'routes.legs.steps.polyline.encodedPolyline',
  'routes.legs.steps.navigationInstruction',
].join(',');

// Timeout for the upstream Google API call (milliseconds)
const UPSTREAM_TIMEOUT_MS = 10_000;

export type TravelMode = 'WALK' | 'DRIVE' | 'TWO_WHEELER' | 'BICYCLE';

export interface IRouteRequest {
  origin: { latitude: number; longitude: number };
  destination: { latitude: number; longitude: number };
  travelMode: TravelMode;
}

export interface IRouteStep {
  instruction: string;
  distanceMeters: number;
  duration: string;
  encodedPolyline?: string;
}

export interface IRouteResult {
  distanceMeters: number;
  duration: string;
  encodedPolyline: string;
  steps: IRouteStep[];
  source: 'GOOGLE_ROUTES';
}

export interface IRouteError {
  error: string;
  message: string;
}

/**
 * Call Google Routes API and return the first computed route.
 * Throws structured IRouteError objects on any failure.
 */
export const computeGoogleRoute = async (
  request: IRouteRequest
): Promise<IRouteResult> => {
  const apiKey = env.GOOGLE_ROUTES_API_KEY;

  if (!apiKey) {
    throw {
      error: 'GOOGLE_ROUTES_NOT_CONFIGURED',
      message:
        'Google Routes API key is not configured on the backend. Set GOOGLE_ROUTES_API_KEY in backend/.env.',
    } as IRouteError;
  }

  const body = {
    origin: {
      location: {
        latLng: {
          latitude: request.origin.latitude,
          longitude: request.origin.longitude,
        },
      },
    },
    destination: {
      location: {
        latLng: {
          latitude: request.destination.latitude,
          longitude: request.destination.longitude,
        },
      },
    },
    travelMode: request.travelMode,
    computeAlternativeRoutes: false,
    languageCode: 'vi-VN',
    units: 'METRIC',
  };

  // AbortController-based timeout for fetch
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS);

  let rawResponse: Response;
  try {
    rawResponse = await fetch(GOOGLE_ROUTES_API_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // Key is sent only as a header — never in the URL, body, or logs
        'X-Goog-Api-Key': apiKey,
        'X-Goog-FieldMask': FIELD_MASK,
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
  } catch (err: any) {
    clearTimeout(timeoutId);
    if (err.name === 'AbortError') {
      throw {
        error: 'GOOGLE_ROUTES_TIMEOUT',
        message: `Google Routes API did not respond within ${UPSTREAM_TIMEOUT_MS / 1000}s.`,
      } as IRouteError;
    }
    throw {
      error: 'GOOGLE_ROUTES_NETWORK_ERROR',
      message: 'Network error while contacting Google Routes API.',
    } as IRouteError;
  } finally {
    clearTimeout(timeoutId);
  }

  // Handle non-2xx responses from Google
  if (!rawResponse.ok) {
    let googleErrorJson: any = null;
    try {
      googleErrorJson = await rawResponse.clone().json();
    } catch {
      // ignore parse error for error body
    }

    console.error(`Google Routes API returned HTTP status: ${rawResponse.status}`);
    if (googleErrorJson && googleErrorJson.error) {
      const errObj = googleErrorJson.error;
      console.error(`Google Upstream Error Status: ${errObj.status || 'UNKNOWN'}`);
      console.error(`Google Upstream Error Message: ${errObj.message || 'No message'}`);
      if (errObj.details) {
        console.error(`Google Upstream Error Details: ${JSON.stringify(errObj.details)}`);
      }
    } else if (googleErrorJson) {
      console.error(`Google Upstream Error Raw Body: ${JSON.stringify(googleErrorJson)}`);
    }

    let statusLabel: string;
    switch (rawResponse.status) {
      case 400:
        statusLabel = 'BAD_REQUEST';
        break;
      case 403:
        statusLabel = 'FORBIDDEN';
        break;
      case 429:
        statusLabel = 'RATE_LIMITED';
        break;
      default:
        statusLabel = 'UPSTREAM_ERROR';
    }
    throw {
      error: `GOOGLE_ROUTES_${statusLabel}`,
      message: `Google Routes API returned HTTP ${rawResponse.status}.`,
    } as IRouteError;
  }

  let json: any;
  try {
    json = await rawResponse.json();
  } catch {
    throw {
      error: 'GOOGLE_ROUTES_PARSE_ERROR',
      message: 'Failed to parse response from Google Routes API.',
    } as IRouteError;
  }

  // Check that Google actually returned at least one route
  if (!json.routes || json.routes.length === 0) {
    throw {
      error: 'NO_ROUTE_FOUND',
      message: 'No route found between the given origin and destination.',
    } as IRouteError;
  }

  const route = json.routes[0];

  // Parse steps from the first leg
  const steps: IRouteStep[] = [];
  const legs = route.legs ?? [];
  if (legs.length > 0) {
    const firstLeg = legs[0];
    for (const step of firstLeg.steps ?? []) {
      steps.push({
        instruction:
          step.navigationInstruction?.instructions ?? '',
        distanceMeters: step.distanceMeters ?? 0,
        duration: step.staticDuration ?? '0s',
        encodedPolyline: step.polyline?.encodedPolyline,
      });
    }
  }

  return {
    distanceMeters: route.distanceMeters ?? 0,
    duration: route.duration ?? '0s',
    encodedPolyline: route.polyline?.encodedPolyline ?? '',
    steps,
    source: 'GOOGLE_ROUTES',
  };
};
