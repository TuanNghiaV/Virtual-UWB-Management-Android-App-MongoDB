import { Request, Response } from 'express';
import mongoose from 'mongoose';

export const getHealth = (_req: Request, res: Response): void => {
  const dbState = mongoose.connection.readyState;
  
  // Mongoose readyState: 0 = disconnected, 1 = connected, 2 = connecting, 3 = disconnecting
  const isConnected = dbState === 1;

  res.status(200).json({
    status: 'ok',
    service: 'virtualuwb-backend',
    database: isConnected ? 'connected' : 'disconnected',
    timestamp: new Date().toISOString()
  });
};
