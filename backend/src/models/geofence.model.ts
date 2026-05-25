import { Schema, model, Document } from 'mongoose';

export interface IGeofence extends Document {
  geofenceCode: string;
  name: string;
  type: 'ROOM' | 'SAFE_ZONE' | 'RESTRICTED_ZONE';
  floorId?: string;
  area: {
    type: 'Polygon';
    coordinates: [number, number][][]; // [[[longitude, latitude], ...]]
  };
  isActive: boolean;
  createdAt: Date;
  updatedAt: Date;
}

const geofenceSchema = new Schema<IGeofence>(
  {
    geofenceCode: {
      type: String,
      required: [true, 'geofenceCode is required.'],
      unique: true,
      trim: true,
    },
    name: {
      type: String,
      required: [true, 'name is required.'],
      trim: true,
    },
    type: {
      type: String,
      required: [true, 'type is required.'],
      enum: {
        values: ['ROOM', 'SAFE_ZONE', 'RESTRICTED_ZONE'],
        message: 'type must be ROOM, SAFE_ZONE, or RESTRICTED_ZONE.',
      },
    },
    floorId: {
      type: String,
      trim: true,
    },
    area: {
      type: {
        type: String,
        enum: ['Polygon'],
        required: true,
        default: 'Polygon',
      },
      coordinates: {
        type: [[[Number]]],
        required: true,
        validate: {
          validator: (coords: number[][][]) => {
            if (coords.length === 0 || coords[0].length < 4) return false;
            // Check if polygon is closed (first point equals last point)
            const first = coords[0][0];
            const last = coords[0][coords[0].length - 1];
            return first[0] === last[0] && first[1] === last[1];
          },
          message: 'area coordinates must define a valid closed Polygon (first point must match last point).',
        },
      },
    },
    isActive: {
      type: Boolean,
      required: true,
      default: true,
    },
  },
  {
    timestamps: true,
  }
);

// Indexes
geofenceSchema.index({ type: 1 });
geofenceSchema.index({ isActive: 1 });
geofenceSchema.index({ area: '2dsphere' });

export const Geofence = model<IGeofence>('Geofence', geofenceSchema);
