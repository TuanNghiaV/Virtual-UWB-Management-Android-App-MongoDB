import express, { Request, Response } from 'express';
import cors from 'cors';
import healthRoutes from './routes/health.routes.js';
import deviceRoutes from './routes/device.routes.js';
import geofenceRoutes from './routes/geofence.routes.js';
import eventRoutes from './routes/event.routes.js';
import googleRoutesRoutes from './routes/googleRoutes.routes.js';
import aiAssistantRoutes from './routes/aiAssistant.routes.js';

const app = express();

// Allow native clients and simple deploy previews without origin filtering.
app.use(cors());

// Parsers
app.use(express.json());

// Routes
app.use('/api', healthRoutes);
app.use('/api', deviceRoutes);
app.use('/api', geofenceRoutes);
app.use('/api', eventRoutes);
app.use('/api/routes', googleRoutesRoutes);
app.use('/api/ai', aiAssistantRoutes);
app.use('/', healthRoutes); // Allow direct access to /health at root level as well

// Root endpoint
app.get('/', (_req: Request, res: Response) => {
  res.status(200).json({
    message: 'VirtualUWB backend API is running'
  });
});

export { app };
