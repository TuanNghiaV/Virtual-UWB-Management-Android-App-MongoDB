import { Request, Response } from 'express';
import mongoose from 'mongoose';
import { Geofence } from '../models/geofence.model.js';

export const getGeofences = async (req: Request, res: Response): Promise<void> => {
  try {
    const { type, active } = req.query;
    const filter: any = {};

    if (type) {
      if (type !== 'ROOM' && type !== 'SAFE_ZONE' && type !== 'RESTRICTED_ZONE') {
        res.status(400).json({ error: 'type parameter must be ROOM, SAFE_ZONE, or RESTRICTED_ZONE.' });
        return;
      }
      filter.type = type;
    }

    if (active) {
      if (active !== 'true' && active !== 'false') {
        res.status(400).json({ error: 'active parameter must be true or false.' });
        return;
      }
      filter.isActive = active === 'true';
    }

    const geofences = await Geofence.find(filter);

    const items = geofences.map(g => ({
      id: g._id.toString(),
      geofenceCode: g.geofenceCode,
      name: g.name,
      type: g.type,
      floorId: g.floorId || null,
      area: g.area,
      isActive: g.isActive,
      createdAt: g.createdAt.toISOString(),
      updatedAt: g.updatedAt.toISOString(),
    }));

    res.status(200).json({ items });
  } catch (error: any) {
    console.error('getGeofences error:', error.message);
    res.status(500).json({ error: 'Internal Server Error.' });
  }
};

export const getGeofenceById = async (req: Request, res: Response): Promise<void> => {
  try {
    const { id } = req.params;
    let geofence;

    if (mongoose.Types.ObjectId.isValid(id)) {
      geofence = await Geofence.findById(id);
    } else {
      geofence = await Geofence.findOne({ geofenceCode: id });
    }

    if (!geofence) {
      res.status(404).json({ error: `Geofence with ID or code '${id}' not found.` });
      return;
    }

    res.status(200).json({
      id: geofence._id.toString(),
      geofenceCode: geofence.geofenceCode,
      name: geofence.name,
      type: geofence.type,
      floorId: geofence.floorId || null,
      area: geofence.area,
      isActive: geofence.isActive,
      createdAt: geofence.createdAt.toISOString(),
      updatedAt: geofence.updatedAt.toISOString(),
    });
  } catch (error: any) {
    console.error('getGeofenceById error:', error.message);
    res.status(500).json({ error: 'Internal Server Error.' });
  }
};
