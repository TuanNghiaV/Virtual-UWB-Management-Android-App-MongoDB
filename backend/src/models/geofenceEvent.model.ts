import { Schema, model, Document } from 'mongoose';

export interface IGeofenceEvent extends Document {
  tagId: string;
  tagName?: string;
  tagCode?: string;
  geofenceId: string;
  geofenceName?: string;
  geofenceType?: string;
  eventType: 'ENTER' | 'EXIT';
  latitude: number;
  longitude: number;
  location: {
    type: 'Point';
    coordinates: [number, number]; // [longitude, latitude]
  };
  createdAt: Date;
}

const geofenceEventSchema = new Schema<IGeofenceEvent>(
  {
    tagId: {
      type: String,
      required: [true, 'tagId is required.'],
      trim: true,
    },
    tagName: {
      type: String,
      trim: true,
    },
    tagCode: {
      type: String,
      trim: true,
    },
    geofenceId: {
      type: String,
      required: [true, 'geofenceId is required.'],
      trim: true,
    },
    geofenceName: {
      type: String,
      trim: true,
    },
    geofenceType: {
      type: String,
      trim: true,
    },
    eventType: {
      type: String,
      required: [true, 'eventType is required.'],
      enum: {
        values: ['ENTER', 'EXIT'],
        message: 'eventType must be either ENTER or EXIT.',
      },
    },
    latitude: {
      type: Number,
      required: [true, 'latitude is required.'],
    },
    longitude: {
      type: Number,
      required: [true, 'longitude is required.'],
    },
    location: {
      type: {
        type: String,
        enum: ['Point'],
        required: true,
        default: 'Point',
      },
      coordinates: {
        type: [Number],
        required: true,
      },
    },
    createdAt: {
      type: Date,
      required: true,
      default: Date.now,
    },
  },
  {
    // No automatic timestamps (updatedAt is not needed for immutable events)
    timestamps: false,
  }
);

// Indexes
geofenceEventSchema.index({ createdAt: -1 });
geofenceEventSchema.index({ tagId: 1, createdAt: -1 });
geofenceEventSchema.index({ geofenceId: 1, createdAt: -1 });
geofenceEventSchema.index({ eventType: 1 });

export const GeofenceEvent = model<IGeofenceEvent>('GeofenceEvent', geofenceEventSchema);
