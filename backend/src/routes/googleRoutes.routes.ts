import { Router } from 'express';
import { computeRoute } from '../controllers/googleRoutes.controller.js';

const router = Router();

// POST /api/routes/google
// Android must call this endpoint — NEVER call Google Routes API directly from the app.
router.post('/google', computeRoute);

export default router;
