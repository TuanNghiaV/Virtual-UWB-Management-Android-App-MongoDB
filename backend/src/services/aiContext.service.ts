import { Device, IDevice } from '../models/device.model.js';
import { Geofence, IGeofence } from '../models/geofence.model.js';
import { GeofenceEvent, IGeofenceEvent } from '../models/geofenceEvent.model.js';

export interface IPhoneLocation {
  latitude: number;
  longitude: number;
}

export interface IRouteStep {
  instruction: string;
  distanceMeters: number;
  duration: string;
}

export interface IRouteContext {
  distanceMeters: number;
  duration: string;
  steps: IRouteStep[];
}

export interface IContextSummary {
  tags: number;
  anchors: number;
  geofences: number;
  recentEvents: number;
  selectedTagCode?: string;
}

export interface IBuiltContext {
  promptText: string;
  summary: IContextSummary;
}

/**
 * Calculates the Haversine distance between two coordinates in meters.
 */
export function calculateHaversineDistance(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  const R = 6371e3; // Earth radius in meters
  const phi1 = (lat1 * Math.PI) / 180;
  const phi2 = (lat2 * Math.PI) / 180;
  const deltaPhi = ((lat2 - lat1) * Math.PI) / 180;
  const deltaLambda = ((lon2 - lon1) * Math.PI) / 180;

  const a =
    Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
    Math.cos(phi1) * Math.cos(phi2) *
      Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return R * c;
}

/**
 * Evaluates which geofence a given point lies inside.
 * Returns the highest priority zone (RESTRICTED_ZONE > SAFE_ZONE > ROOM) or null.
 */
async function evaluatePointZone(
  latitude: number,
  longitude: number
): Promise<IGeofence | null> {
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

  const zonePriority: Record<string, number> = {
    RESTRICTED_ZONE: 3,
    SAFE_ZONE: 2,
    ROOM: 1,
  };

  let selectedGeofence: IGeofence | null = null;
  let maxPriority = 0;

  matchedGeofences.forEach((geofence) => {
    const priority = zonePriority[geofence.type] || 0;
    if (priority > maxPriority) {
      maxPriority = priority;
      selectedGeofence = geofence;
    }
  });

  return selectedGeofence;
}

/**
 * Retrieves the required MongoDB collections and creates the structured prompt.
 */
export const buildAiContext = async (
  message: string,
  selectedTagCode?: string,
  phone?: IPhoneLocation,
  route?: IRouteContext
): Promise<IBuiltContext> => {
  // 1. Fetch active devices (tags and anchors)
  const activeDevices = await Device.find({ isActive: true });
  const tags = activeDevices.filter((d) => d.role === 'TAG');
  const anchors = activeDevices.filter((d) => d.role === 'ANCHOR');

  // 2. Fetch active geofences
  const geofences = await Geofence.find({ isActive: true });

  // 3. Fetch latest 10-20 geofence events
  const recentEvents = await GeofenceEvent.find()
    .sort({ createdAt: -1 })
    .limit(15);

  // 4. Handle selected tag prominent details
  let selectedTag: IDevice | null = null;
  if (selectedTagCode) {
    selectedTag = await Device.findOne({
      deviceCode: selectedTagCode,
      role: 'TAG',
    });
  }

  // 5. Evaluate phone geofence zone if provided
  let phoneZone: IGeofence | null = null;
  if (phone) {
    phoneZone = await evaluatePointZone(phone.latitude, phone.longitude);
  }

  // Build the text context block
  let contextBlock = `--- START VIRTUAL UWB CONTEXT ---
System Current Time: ${new Date().toISOString()}

1. ACTIVE ANCHORS (Fixed reference devices):
`;
  if (anchors.length === 0) {
    contextBlock += "No active anchors.\n";
  } else {
    anchors.forEach((anchor) => {
      contextBlock += `- Code: ${anchor.deviceCode}, Name: ${anchor.name}, Position: (${anchor.latitude}, ${anchor.longitude})\n`;
    });
  }

  contextBlock += `\n2. ACTIVE TAGS (Mobile tracked items):\n`;
  if (tags.length === 0) {
    contextBlock += "No active tags.\n";
  } else {
    tags.forEach((tag) => {
      const zoneStr = tag.currentZoneName
        ? `${tag.currentZoneName} (${tag.currentZoneType})`
        : 'None/Unknown';
      contextBlock += `- Code: ${tag.deviceCode}, Name: ${tag.name}, Position: (${tag.latitude}, ${tag.longitude}), Safety: ${tag.safetyStatus || 'UNKNOWN'}, Zone: ${zoneStr}\n`;
    });
  }

  contextBlock += `\n3. GEOFENCES (Defined map areas/zones):\n`;
  if (geofences.length === 0) {
    contextBlock += "No active geofences configured.\n";
  } else {
    geofences.forEach((geo) => {
      const coordSample = geo.area?.coordinates?.[0]
        ? geo.area.coordinates[0].map(([lng, lat]) => `[${lat}, ${lng}]`).join(', ')
        : 'No coordinates';
      contextBlock += `- Code: ${geo.geofenceCode}, Name: ${geo.name}, Type: ${geo.type}, Coordinates: ${coordSample}\n`;
    });
  }

  contextBlock += `\n4. RECENT GEOFENCE EVENTS (Latest activity):\n`;
  if (recentEvents.length === 0) {
    contextBlock += "No recent geofence events logged.\n";
  } else {
    recentEvents.forEach((ev) => {
      contextBlock += `- [${ev.createdAt.toISOString()}] Tag ${ev.tagName || 'Unknown'} (${ev.tagCode || 'Unknown'}) did ${ev.eventType} ${ev.geofenceName || 'Unknown'} (${ev.geofenceType || 'UNKNOWN'}) at (${ev.latitude}, ${ev.longitude})\n`;
    });
  }

  if (selectedTagCode) {
    contextBlock += `\n5. SELECTED TAG IN FOCUS:\n`;
    if (!selectedTag) {
      contextBlock += `- Tag Code: ${selectedTagCode} is NOT currently available in the database.\n`;
    } else {
      const zoneStr = selectedTag.currentZoneName
        ? `${selectedTag.currentZoneName} (${selectedTag.currentZoneType})`
        : 'None/Unknown';
      contextBlock += `- Code: ${selectedTag.deviceCode}
- Name: ${selectedTag.name}
- Position: (${selectedTag.latitude}, ${selectedTag.longitude})
- Current Zone: ${zoneStr}
- Safety Status: ${selectedTag.safetyStatus || 'UNKNOWN'}
`;
      if (phone) {
        const dist = calculateHaversineDistance(
          phone.latitude,
          phone.longitude,
          selectedTag.latitude,
          selectedTag.longitude
        );
        contextBlock += `- Distance to phone: ${dist.toFixed(1)} meters\n`;
      }
    }
  }

  if (phone) {
    contextBlock += `\n6. PHONE OWNER'S LOCATION (Current user):
- Position: (${phone.latitude}, ${phone.longitude})
- Current Zone: ${phoneZone ? `${phoneZone.name} (${phoneZone.type})` : 'Outside of all zones'}
`;
    // Distance to all active tags
    if (tags.length > 0) {
      contextBlock += `- Distance to other tags:\n`;
      tags.forEach((tag) => {
        if (tag.deviceCode !== selectedTagCode) {
          const dist = calculateHaversineDistance(
            phone.latitude,
            phone.longitude,
            tag.latitude,
            tag.longitude
          );
          contextBlock += `  * Tag ${tag.name} (${tag.deviceCode}): ${dist.toFixed(1)} meters\n`;
        }
      });
    }
  }

  if (route) {
    contextBlock += `\n7. ROUTE INFORMATION (Google Routes Context):
- Total Distance: ${route.distanceMeters} meters
- Total Duration: ${route.duration}
- Steps:\n`;
    const limitedSteps = route.steps.slice(0, 5);
    limitedSteps.forEach((step, index) => {
      contextBlock += `  * Step ${index + 1}: ${step.instruction} (Distance: ${step.distanceMeters}m, Duration: ${step.duration})\n`;
    });
    if (route.steps.length > 5) {
      contextBlock += `  * (Total steps: ${route.steps.length})\n`;
    }
  }

  contextBlock += `\n--- END VIRTUAL UWB CONTEXT ---`;

  const summary: IContextSummary = {
    tags: tags.length,
    anchors: anchors.length,
    geofences: geofences.length,
    recentEvents: recentEvents.length,
    selectedTagCode,
  };

  return {
    promptText: contextBlock,
    summary,
  };
};
