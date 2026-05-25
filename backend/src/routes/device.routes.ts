import { Router } from 'express';
import { getDevices, getDeviceById, updateDevicePosition } from '../controllers/device.controller.js';

const router = Router();

router.get('/devices', getDevices);
router.get('/devices/:id', getDeviceById);
router.patch('/devices/:id/position', updateDevicePosition);

export default router;
