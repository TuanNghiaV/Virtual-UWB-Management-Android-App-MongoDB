import { IDevice } from '../models/device.model.js';
import { Geofence, IGeofence } from '../models/geofence.model.js';
import { GeofenceEvent, IGeofenceEvent } from '../models/geofenceEvent.model.js';
import { broadcastGeofenceEvent } from './sseEvent.service.js';

interface IEvaluatedZone {
  id: string | null;
  name: string | null;
  type: 'ROOM' | 'SAFE_ZONE' | 'RESTRICTED_ZONE' | 'UNKNOWN';
  safetyStatus: 'SAFE' | 'DANGER' | 'UNKNOWN';
}

interface IEvaluationResult {
  device: IDevice;
  zone: IEvaluatedZone;
  events: IGeofenceEvent[];
}

const zonePriority = {
  RESTRICTED_ZONE: 3,
  SAFE_ZONE: 2,
  ROOM: 1,
  UNKNOWN: 0
};

const safetyStatusMap = {
  RESTRICTED_ZONE: 'DANGER' as const,
  SAFE_ZONE: 'SAFE' as const,
  ROOM: 'UNKNOWN' as const,
  UNKNOWN: 'UNKNOWN' as const
};

export const evaluateGeofenceForDevice = async (
  device: IDevice,
  latitude: number,
  longitude: number
): Promise<IEvaluationResult> => {
  const createdEvents: IGeofenceEvent[] = [];

  // 1. Query active geofences containing/intersecting the point
  // MongoDB uses [longitude, latitude] for GeoJSON coordinates
  const matchedGeofences = await Geofence.find({
    isActive: true,
    area: {
      $geoIntersects: {
        $geometry: {
          type: 'Point',
          coordinates: [longitude, latitude],
        },
      },
    },
  });

  // 2. Select the geofence with the highest priority
  let selectedGeofence: IGeofence | null = null;
  let maxPriority = 0;

  matchedGeofences.forEach((geofence) => {
    const priority = zonePriority[geofence.type] || 0;
    if (priority > maxPriority) {
      maxPriority = priority;
      selectedGeofence = geofence;
    }
  });

  const newZoneId = selectedGeofence ? (selectedGeofence as IGeofence)._id.toString() : null;
  const newZoneName = selectedGeofence ? (selectedGeofence as IGeofence).name : null;
  const newZoneType = selectedGeofence ? (selectedGeofence as IGeofence).type : 'UNKNOWN';
  const newSafetyStatus = safetyStatusMap[newZoneType];

  const oldZoneId = device.currentZoneId || null;

  // 3. Check for zone change to create EXIT/ENTER events
  if (oldZoneId !== newZoneId) {
    // A zone change has occurred

    // 3.1. Create EXIT event for previous zone
    if (oldZoneId) {
      let previousGeofence = await Geofence.findById(oldZoneId);
      
      // If geofence wasn't found (deleted or modified), try resolving it using current device fields
      const pName = previousGeofence?.name || device.currentZoneName;
      const pType = previousGeofence?.type || device.currentZoneType;

      const exitEvent = new GeofenceEvent({
        tagId: device._id.toString(),
        tagName: device.name,
        tagCode: device.deviceCode,
        geofenceId: oldZoneId,
        geofenceName: pName || 'Unknown Geofence',
        geofenceType: pType || 'UNKNOWN',
        eventType: 'EXIT' as const,
        latitude,
        longitude,
        location: {
          type: 'Point' as const,
          coordinates: [longitude, latitude],
        },
        createdAt: new Date(),
      });

      const savedExitEvent = await exitEvent.save();
      createdEvents.push(savedExitEvent);
      // Broadcast EXIT event to all connected SSE clients AFTER saving to MongoDB
      broadcastGeofenceEvent(savedExitEvent);
    }

    // 3.2. Create ENTER event for new zone
    if (newZoneId && selectedGeofence) {
      const enterEvent = new GeofenceEvent({
        tagId: device._id.toString(),
        tagName: device.name,
        tagCode: device.deviceCode,
        geofenceId: newZoneId,
        geofenceName: (selectedGeofence as IGeofence).name,
        geofenceType: (selectedGeofence as IGeofence).type,
        eventType: 'ENTER' as const,
        latitude,
        longitude,
        location: {
          type: 'Point' as const,
          coordinates: [longitude, latitude],
        },
        createdAt: new Date(),
      });

      const savedEnterEvent = await enterEvent.save();
      createdEvents.push(savedEnterEvent);
      // Broadcast ENTER event to all connected SSE clients AFTER saving to MongoDB
      broadcastGeofenceEvent(savedEnterEvent);
    }
  }

  // 4. Update the Device model properties
  device.latitude = latitude;
  device.longitude = longitude;
  device.location = {
    type: 'Point' as const,
    coordinates: [longitude, latitude],
  };
  device.currentZoneId = newZoneId || undefined;
  device.currentZoneName = newZoneName || undefined;
  device.currentZoneType = newZoneType !== 'UNKNOWN' ? newZoneType : undefined;
  device.safetyStatus = newSafetyStatus;

  const updatedDevice = await device.save();

  const currentZoneInfo: IEvaluatedZone = {
    id: newZoneId,
    name: newZoneName,
    type: newZoneType as 'ROOM' | 'SAFE_ZONE' | 'RESTRICTED_ZONE' | 'UNKNOWN',
    safetyStatus: newSafetyStatus
  };

  return {
    device: updatedDevice,
    zone: currentZoneInfo,
    events: createdEvents,
  };
};
