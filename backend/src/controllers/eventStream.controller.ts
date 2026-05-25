import { Request, Response } from 'express';
import {
  addClient,
  removeClient,
  sendConnectedEvent,
} from '../services/sseEvent.service.js';

/**
 * GET /api/events/stream
 *
 * Establishes a long-lived Server-Sent Events (SSE) connection.
 * - Sends an initial "connected" event.
 * - Keeps the connection alive via the heartbeat scheduler in sseEvent.service.ts.
 * - Removes the client from the registry when the connection closes.
 *
 * TODO: Add authentication/authorization before deploying to production.
 * The endpoint is currently open to any client without a token.
 */
export const streamEvents = (req: Request, res: Response): void => {
  // Set SSE-specific headers
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  // Disable response buffering so events are flushed immediately
  res.setHeader('X-Accel-Buffering', 'no');
  // Status 200 must be sent before writing SSE data
  res.status(200);
  // Flush the headers immediately
  res.flushHeaders();

  // Register this client
  const clientId = addClient(res);

  // Send the initial "connected" event
  sendConnectedEvent(clientId);

  // Handle client disconnect (browser tab closed, network drop, etc.)
  req.on('close', () => {
    removeClient(clientId);
  });
};
