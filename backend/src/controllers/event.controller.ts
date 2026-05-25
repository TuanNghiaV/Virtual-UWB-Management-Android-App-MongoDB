import { Request, Response } from 'express';
import { GeofenceEvent } from '../models/geofenceEvent.model.js';

export const getEvents = async (req: Request, res: Response): Promise<void> => {
  try {
    const { limit, tagId, geofenceId, eventType } = req.query;
    const filter: any = {};

    // Validate limit
    let queryLimit = 50;
    if (limit) {
      const parsedLimit = parseInt(limit as string, 10);
      if (isNaN(parsedLimit) || parsedLimit <= 0) {
        res.status(400).json({ error: 'limit parameter must be a positive integer.' });
        return;
      }
      queryLimit = Math.min(parsedLimit, 200); // Caps at 200
    }

    if (tagId) {
      filter.tagId = tagId;
    }

    if (geofenceId) {
      filter.geofenceId = geofenceId;
    }

    if (eventType) {
      if (eventType !== 'ENTER' && eventType !== 'EXIT') {
        res.status(400).json({ error: 'eventType parameter must be ENTER or EXIT.' });
        return;
      }
      filter.eventType = eventType;
    }

    const events = await GeofenceEvent.find(filter)
      .sort({ createdAt: -1 })
      .limit(queryLimit);

    const items = events.map(e => ({
      id: e._id.toString(),
      tagId: e.tagId,
      tagName: e.tagName || null,
      tagCode: e.tagCode || null,
      geofenceId: e.geofenceId,
      geofenceName: e.geofenceName || null,
      geofenceType: e.geofenceType || null,
      eventType: e.eventType,
      latitude: e.latitude,
      longitude: e.longitude,
      location: e.location,
      createdAt: e.createdAt.toISOString()
    }));

    res.status(200).json({ items });
  } catch (error: any) {
    console.error('getEvents error:', error.message);
    res.status(500).json({ error: 'Internal Server Error.' });
  }
};

export const getRecentEvents = async (_req: Request, res: Response): Promise<void> => {
  try {
    const events = await GeofenceEvent.find()
      .sort({ createdAt: -1 })
      .limit(20);

    const items = events.map(e => ({
      id: e._id.toString(),
      tagId: e.tagId,
      tagName: e.tagName || null,
      tagCode: e.tagCode || null,
      geofenceId: e.geofenceId,
      geofenceName: e.geofenceName || null,
      geofenceType: e.geofenceType || null,
      eventType: e.eventType,
      latitude: e.latitude,
      longitude: e.longitude,
      location: e.location,
      createdAt: e.createdAt.toISOString()
    }));

    res.status(200).json({ items });
  } catch (error: any) {
    console.error('getRecentEvents error:', error.message);
    res.status(500).json({ error: 'Internal Server Error.' });
  }
};
