import { Request, Response } from 'express';
import mongoose from 'mongoose';
import { Device } from '../models/device.model.js';
import { evaluateGeofenceForDevice } from '../services/geofenceEvaluation.service.js';

export const getDevices = async (req: Request, res: Response): Promise<void> => {
  try {
    const { role, active } = req.query;
    const filter: any = {};

    if (role) {
      if (role !== 'ANCHOR' && role !== 'TAG') {
        res.status(400).json({ error: 'role parameter must be ANCHOR or TAG.' });
        return;
      }
      filter.role = role;
    }

    if (active) {
      if (active !== 'true' && active !== 'false') {
        res.status(400).json({ error: 'active parameter must be true or false.' });
        return;
      }
      filter.isActive = active === 'true';
    }

    const devices = await Device.find(filter);
    
    // Convert to target model format if required
    const items = devices.map(d => ({
      id: d._id.toString(),
      deviceCode: d.deviceCode,
      name: d.name,
      role: d.role,
      latitude: d.latitude,
      longitude: d.longitude,
      location: d.location,
      isActive: d.isActive,
      floorId: d.floorId || null,
      currentZoneId: d.currentZoneId || null,
      currentZoneName: d.currentZoneName || null,
      currentZoneType: d.currentZoneType || null,
      safetyStatus: d.safetyStatus || 'UNKNOWN',
      createdAt: d.createdAt.toISOString(),
      updatedAt: d.updatedAt.toISOString(),
    }));

    res.status(200).json({ items });
  } catch (error: any) {
    console.error('getDevices error:', error.message);
    res.status(500).json({ error: 'Internal Server Error.' });
  }
};

export const getDeviceById = async (req: Request, res: Response): Promise<void> => {
  try {
    const { id } = req.params;
    let device;

    // Support both MongoDB ObjectId and custom unique deviceCode
    if (mongoose.Types.ObjectId.isValid(id)) {
      device = await Device.findById(id);
    } else {
      device = await Device.findOne({ deviceCode: id });
    }

    if (!device) {
      res.status(404).json({ error: `Device with ID or code '${id}' not found.` });
      return;
    }

    res.status(200).json({
      id: device._id.toString(),
      deviceCode: device.deviceCode,
      name: device.name,
      role: device.role,
      latitude: device.latitude,
      longitude: device.longitude,
      location: device.location,
      isActive: device.isActive,
      floorId: device.floorId || null,
      currentZoneId: device.currentZoneId || null,
      currentZoneName: device.currentZoneName || null,
      currentZoneType: device.currentZoneType || null,
      safetyStatus: device.safetyStatus || 'UNKNOWN',
      createdAt: device.createdAt.toISOString(),
      updatedAt: device.updatedAt.toISOString(),
    });
  } catch (error: any) {
    console.error('getDeviceById error:', error.message);
    res.status(500).json({ error: 'Internal Server Error.' });
  }
};

export const updateDevicePosition = async (req: Request, res: Response): Promise<void> => {
  try {
    const { id } = req.params;
    const { latitude, longitude } = req.body;

    // Validate coordinates
    if (latitude === undefined || longitude === undefined) {
      res.status(400).json({ error: 'Both latitude and longitude parameters are required.' });
      return;
    }

    const lat = Number(latitude);
    const lng = Number(longitude);

    if (isNaN(lat) || lat < -90 || lat > 90) {
      res.status(400).json({ error: 'latitude must be a valid number between -90 and 90.' });
      return;
    }

    if (isNaN(lng) || lng < -180 || lng > 180) {
      res.status(400).json({ error: 'longitude must be a valid number between -180 and 180.' });
      return;
    }

    let device;
    if (mongoose.Types.ObjectId.isValid(id)) {
      device = await Device.findById(id);
    } else {
      device = await Device.findOne({ deviceCode: id });
    }

    if (!device) {
      res.status(404).json({ error: `Device with ID or code '${id}' not found.` });
      return;
    }

    // Anchor updates coordinate directly but doesn't evaluate geofence or create events
    if (device.role === 'ANCHOR') {
      device.latitude = lat;
      device.longitude = lng;
      device.location = {
        type: 'Point',
        coordinates: [lng, lat]
      };
      const updatedDevice = await device.save();
      res.status(200).json({
        device: {
          id: updatedDevice._id.toString(),
          deviceCode: updatedDevice.deviceCode,
          name: updatedDevice.name,
          role: updatedDevice.role,
          latitude: updatedDevice.latitude,
          longitude: updatedDevice.longitude,
          location: updatedDevice.location,
          isActive: updatedDevice.isActive,
          floorId: updatedDevice.floorId || null,
          createdAt: updatedDevice.createdAt.toISOString(),
          updatedAt: updatedDevice.updatedAt.toISOString(),
        },
        zone: {
          id: null,
          name: null,
          type: 'UNKNOWN',
          safetyStatus: 'UNKNOWN'
        },
        events: []
      });
      return;
    }

    // Evaluate geofence for TAG
    const result = await evaluateGeofenceForDevice(device, lat, lng);

    const formattedDevice = {
      id: result.device._id.toString(),
      deviceCode: result.device.deviceCode,
      name: result.device.name,
      role: result.device.role,
      latitude: result.device.latitude,
      longitude: result.device.longitude,
      location: result.device.location,
      isActive: result.device.isActive,
      floorId: result.device.floorId || null,
      currentZoneId: result.device.currentZoneId || null,
      currentZoneName: result.device.currentZoneName || null,
      currentZoneType: result.device.currentZoneType || null,
      safetyStatus: result.device.safetyStatus || 'UNKNOWN',
      createdAt: result.device.createdAt.toISOString(),
      updatedAt: result.device.updatedAt.toISOString(),
    };

    res.status(200).json({
      device: formattedDevice,
      zone: result.zone,
      events: result.events.map(e => ({
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
      }))
    });

  } catch (error: any) {
    console.error('updateDevicePosition error:', error.message);
    res.status(500).json({ error: 'Internal Server Error.' });
  }
};
