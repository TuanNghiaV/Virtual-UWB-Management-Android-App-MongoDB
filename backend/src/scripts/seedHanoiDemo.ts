import mongoose from 'mongoose';
import dotenv from 'dotenv';
import { Device } from '../models/device.model.js';
import { Geofence } from '../models/geofence.model.js';
import { GeofenceEvent } from '../models/geofenceEvent.model.js';

// Load env variables
dotenv.config();

const MONGODB_URI = process.env.MONGODB_URI;

if (!MONGODB_URI) {
  console.error('❌ MONGODB_URI environmental variable is missing.');
  process.exit(1);
}

const seedData = async () => {
  try {
    console.log('Connecting to database for seeding...');
    await mongoose.connect(MONGODB_URI);
    console.log('MongoDB connected.');

    // 1. Clear existing devices & geofences
    console.log('Clearing existing devices, geofences, and events...');
    await Device.deleteMany({});
    await Geofence.deleteMany({});
    await GeofenceEvent.deleteMany({});
    console.log('Existing collections cleared.');

    // 2. Define Geofences
    const safeZone = {
      geofenceCode: 'safe-zone-1',
      name: 'Safe Zone',
      type: 'SAFE_ZONE' as const,
      area: {
        type: 'Polygon' as const,
        coordinates: [
          [
            [105.834300, 21.037100],
            [105.834500, 21.037100],
            [105.834500, 21.036900],
            [105.834300, 21.036900],
            [105.834300, 21.037100] // Closed polygon loop
          ]
        ]
      },
      isActive: true,
    };

    const restrictedZone = {
      geofenceCode: 'restricted-zone-1',
      name: 'Restricted Zone',
      type: 'RESTRICTED_ZONE' as const,
      area: {
        type: 'Polygon' as const,
        coordinates: [
          [
            [105.834800, 21.036700],
            [105.835000, 21.036700],
            [105.835000, 21.036500],
            [105.834800, 21.036500],
            [105.834800, 21.036700] // Closed polygon loop
          ]
        ]
      },
      isActive: true,
    };

    console.log('Inserting default geofences...');
    const insertedGeofences = await Geofence.insertMany([safeZone, restrictedZone]);
    console.log(`Successfully inserted ${insertedGeofences.length} geofences.`);

    // 3. Define Devices (Anchors & Tags)
    const devices = [
      // Anchors
      {
        deviceCode: 'anchor-a1',
        name: 'Anchor A1',
        role: 'ANCHOR' as const,
        latitude: 21.037250,
        longitude: 105.834150,
        location: {
          type: 'Point' as const,
          coordinates: [105.834150, 21.037250]
        },
        isActive: true,
      },
      {
        deviceCode: 'anchor-a2',
        name: 'Anchor A2',
        role: 'ANCHOR' as const,
        latitude: 21.037250,
        longitude: 105.835250,
        location: {
          type: 'Point' as const,
          coordinates: [105.835250, 21.037250]
        },
        isActive: true,
      },
      {
        deviceCode: 'anchor-a3',
        name: 'Anchor A3',
        role: 'ANCHOR' as const,
        latitude: 21.036300,
        longitude: 105.834150,
        location: {
          type: 'Point' as const,
          coordinates: [105.834150, 21.036300]
        },
        isActive: true,
      },
      {
        deviceCode: 'anchor-a4',
        name: 'Anchor A4',
        role: 'ANCHOR' as const,
        latitude: 21.036300,
        longitude: 105.835250,
        location: {
          type: 'Point' as const,
          coordinates: [105.835250, 21.036300]
        },
        isActive: true,
      },

      // Tags
      {
        deviceCode: 'tag-t1',
        name: 'Kiên',
        role: 'TAG' as const,
        latitude: 21.036784,
        longitude: 105.834711,
        location: {
          type: 'Point' as const,
          coordinates: [105.834711, 21.036784]
        },
        isActive: true,
        safetyStatus: 'UNKNOWN' as const,
      },
      {
        deviceCode: 'tag-t2',
        name: 'Anh',
        role: 'TAG' as const,
        latitude: 21.036700,
        longitude: 105.834900, // Inside Restricted Zone
        location: {
          type: 'Point' as const,
          coordinates: [105.834900, 21.036700]
        },
        isActive: true,
        currentZoneId: insertedGeofences[1]._id.toString(),
        currentZoneName: 'Restricted Zone',
        currentZoneType: 'RESTRICTED_ZONE',
        safetyStatus: 'DANGER' as const,
      },
      {
        deviceCode: 'tag-t3',
        name: 'Minh',
        role: 'TAG' as const,
        latitude: 21.036800,
        longitude: 105.834300,
        location: {
          type: 'Point' as const,
          coordinates: [105.834300, 21.036800]
        },
        isActive: true,
        safetyStatus: 'UNKNOWN' as const,
      },
      {
        deviceCode: 'tag-t4',
        name: 'Huy',
        role: 'TAG' as const,
        latitude: 21.036500,
        longitude: 105.834500,
        location: {
          type: 'Point' as const,
          coordinates: [105.834500, 21.036500]
        },
        isActive: true,
        safetyStatus: 'UNKNOWN' as const,
      },
      {
        deviceCode: 'tag-t5',
        name: 'Linh',
        role: 'TAG' as const,
        latitude: 21.037000,
        longitude: 105.835000,
        location: {
          type: 'Point' as const,
          coordinates: [105.835000, 21.037000]
        },
        isActive: true,
        safetyStatus: 'UNKNOWN' as const,
      }
    ];

    console.log('Inserting default devices...');
    const insertedDevices = await Device.insertMany(devices);
    console.log(`Successfully inserted ${insertedDevices.length} devices.`);

    // 4. Create one initial event for demo
    const initialEvent = {
      tagId: insertedDevices.find(d => d.deviceCode === 'tag-t2')?._id.toString() || 'tag-t2',
      tagName: 'Anh',
      tagCode: 'tag-t2',
      geofenceId: insertedGeofences[1]._id.toString(),
      geofenceName: 'Restricted Zone',
      geofenceType: 'RESTRICTED_ZONE',
      eventType: 'ENTER' as const,
      latitude: 21.036700,
      longitude: 105.834900,
      location: {
        type: 'Point' as const,
        coordinates: [105.834900, 21.036700]
      },
      createdAt: new Date()
    };

    console.log('Inserting initial geofence event...');
    await GeofenceEvent.create(initialEvent);
    console.log('Successfully inserted initial geofence event.');

    console.log('✅ Hanoi demo data seeding completed successfully!');
    process.exit(0);
  } catch (error) {
    console.error('❌ Seeding failed:', error);
    process.exit(1);
  }
};

seedData();
