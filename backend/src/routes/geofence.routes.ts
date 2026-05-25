import { Router } from 'express';
import { getGeofences, getGeofenceById } from '../controllers/geofence.controller.js';

const router = Router();

router.get('/geofences', getGeofences);
router.get('/geofences/:id', getGeofenceById);

export default router;
