import mongoose from 'mongoose';
import { env } from './env.js';

export const connectDatabase = async (): Promise<void> => {
  try {
    // Set connection options
    mongoose.connection.on('connected', () => {
      console.log('MongoDB connected');
    });

    mongoose.connection.on('error', (err) => {
      console.error('MongoDB connection error:', err.message);
    });

    mongoose.connection.on('disconnected', () => {
      console.log('MongoDB disconnected');
    });

    // Connect to MongoDB Atlas with a 10-second timeout (we do NOT log the MONGODB_URI itself)
    await mongoose.connect(env.MONGODB_URI, {
      serverSelectionTimeoutMS: 10000,
    });
  } catch (error: any) {
    console.error('❌ Failed to connect to MongoDB:');
    if (error && typeof error === 'object') {
      console.error(`   Error Name: ${error.name || 'Unknown'}`);
      console.error(`   Error Code: ${error.code || 'None'}`);
      console.error(`   Error Message: ${error.message || error}`);
    } else {
      console.error(`   ${error}`);
    }
    process.exit(1);
  }
};
