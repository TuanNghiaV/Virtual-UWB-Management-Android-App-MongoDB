import { Response } from 'express';
import { IGeofenceEvent } from '../models/geofenceEvent.model.js';

// TODO: Add authentication/authorization to the SSE endpoint before deploying to production.
// Currently the /api/events/stream endpoint is open to any client.

interface ISseClient {
  id: string;
  res: Response;
}

// In-memory map of connected SSE clients
const clients = new Map<string, ISseClient>();

let clientIdCounter = 0;

/**
 * Generate a unique client ID.
 */
function generateClientId(): string {
  clientIdCounter += 1;
  return `sse-client-${Date.now()}-${clientIdCounter}`;
}

/**
 * Format a SSE message string.
 * @param event - SSE event name (e.g. 'geofence_event', 'connected', 'ping')
 * @param data - JSON-serializable data payload
 */
function formatSseMessage(event: string, data: object | string): string {
  const payload = typeof data === 'string' ? data : JSON.stringify(data);
  return `event: ${event}\ndata: ${payload}\n\n`;
}

/**
 * Register a new SSE client connection.
 * Sets up SSE headers and returns the assigned client ID.
 */
export function addClient(res: Response): string {
  const clientId = generateClientId();
  clients.set(clientId, { id: clientId, res });
  console.log(`[SSE] Client connected: ${clientId}. Total clients: ${clients.size}`);
  return clientId;
}

/**
 * Remove a disconnected SSE client.
 */
export function removeClient(clientId: string): void {
  if (clients.has(clientId)) {
    clients.delete(clientId);
    console.log(`[SSE] Client disconnected: ${clientId}. Total clients: ${clients.size}`);
  }
}

/**
 * Send the initial "connected" event to a single client.
 */
export function sendConnectedEvent(clientId: string): void {
  const client = clients.get(clientId);
  if (!client) return;

  try {
    client.res.write(
      formatSseMessage('connected', {
        status: 'connected',
        message: 'VirtualUWB SSE stream established',
        timestamp: new Date().toISOString(),
      })
    );
  } catch (err) {
    console.error(`[SSE] Failed to send connected event to ${clientId}:`, err);
    removeClient(clientId);
  }
}

/**
 * Broadcast a GeofenceEvent to ALL connected SSE clients.
 * Safely removes any clients that have already disconnected.
 */
export function broadcastGeofenceEvent(event: IGeofenceEvent): void {
  if (clients.size === 0) {
    // No connected clients – silently do nothing (no crash, no memory leak)
    return;
  }

  const payload = {
    id: event._id.toString(),
    tagId: event.tagId,
    tagName: event.tagName ?? null,
    tagCode: event.tagCode ?? null,
    geofenceId: event.geofenceId,
    geofenceName: event.geofenceName ?? null,
    geofenceType: event.geofenceType ?? null,
    eventType: event.eventType,
    latitude: event.latitude,
    longitude: event.longitude,
    location: event.location,
    createdAt: event.createdAt.toISOString(),
  };

  const message = formatSseMessage('geofence_event', payload);
  const deadClients: string[] = [];

  clients.forEach((client) => {
    try {
      client.res.write(message);
    } catch (err) {
      // Client pipe was broken – mark for removal
      console.warn(`[SSE] Dead client detected: ${client.id}. Removing.`);
      deadClients.push(client.id);
    }
  });

  // Clean up dead clients outside of the forEach to avoid mutation during iteration
  deadClients.forEach((id) => removeClient(id));
}

/**
 * Send a heartbeat ping comment to ALL connected SSE clients.
 * SSE comments (lines starting with ":") are ignored by the browser/client
 * but keep the TCP connection alive through proxies and load-balancers.
 */
export function broadcastHeartbeat(): void {
  if (clients.size === 0) return;

  const heartbeatMsg = `: ping\n\n`;
  const deadClients: string[] = [];

  clients.forEach((client) => {
    try {
      client.res.write(heartbeatMsg);
    } catch (err) {
      console.warn(`[SSE] Dead client on heartbeat: ${client.id}. Removing.`);
      deadClients.push(client.id);
    }
  });

  deadClients.forEach((id) => removeClient(id));
}

/**
 * Return the number of currently connected SSE clients.
 */
export function getClientCount(): number {
  return clients.size;
}

// Start the heartbeat interval – runs every 25 seconds
// The interval is kept alive for the lifetime of the process (no cleanup needed).
const HEARTBEAT_INTERVAL_MS = 25_000;
setInterval(broadcastHeartbeat, HEARTBEAT_INTERVAL_MS);
console.log(`[SSE] Heartbeat scheduler started (every ${HEARTBEAT_INTERVAL_MS / 1000}s).`);
