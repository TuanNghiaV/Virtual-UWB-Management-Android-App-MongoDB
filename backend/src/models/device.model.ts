import { Schema, model, Document } from 'mongoose';

export interface IDevice extends Document {
  deviceCode: string;
  name: string;
  role: 'ANCHOR' | 'TAG';
  floorId?: string;
  latitude: number;
  longitude: number;
  location: {
    type: 'Point';
    coordinates: [number, number]; // [longitude, latitude]
  };
  isActive: boolean;
  currentZoneId?: string;
  currentZoneName?: string;
  currentZoneType?: string;
  safetyStatus?: 'SAFE' | 'DANGER' | 'UNKNOWN';
  createdAt: Date;
  updatedAt: Date;
}

const deviceSchema = new Schema<IDevice>(
  {
    deviceCode: {
      type: String,
      required: [true, 'deviceCode is required.'],
      unique: true,
      trim: true,
    },
    name: {
      type: String,
      required: [true, 'name is required.'],
      trim: true,
    },
    role: {
      type: String,
      required: [true, 'role is required.'],
      enum: {
        values: ['ANCHOR', 'TAG'],
        message: 'role must be either ANCHOR or TAG.',
      },
    },
    floorId: {
      type: String,
      trim: true,
    },
    latitude: {
      type: Number,
      required: [true, 'latitude is required.'],
      min: [-90, 'latitude must be between -90 and 90.'],
      max: [90, 'latitude must be between -90 and 90.'],
    },
    longitude: {
      type: Number,
      required: [true, 'longitude is required.'],
      min: [-180, 'longitude must be between -180 and 180.'],
      max: [180, 'longitude must be between -180 and 180.'],
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
        validate: {
          validator: (coords: number[]) => coords.length === 2,
          message: 'location coordinates must be [longitude, latitude].',
        },
      },
    },
    isActive: {
      type: Boolean,
      required: true,
      default: true,
    },
    currentZoneId: {
      type: String,
      trim: true,
    },
    currentZoneName: {
      type: String,
      trim: true,
    },
    currentZoneType: {
      type: String,
      trim: true,
    },
    safetyStatus: {
      type: String,
      enum: ['SAFE', 'DANGER', 'UNKNOWN'],
      default: 'UNKNOWN',
    },
  },
  {
    timestamps: true,
  }
);

// Indexes
deviceSchema.index({ role: 1 });
deviceSchema.index({ isActive: 1 });
deviceSchema.index({ location: '2dsphere' });

export const Device = model<IDevice>('Device', deviceSchema);
