import { Router } from 'express';
import { askAssistant } from '../controllers/aiAssistant.controller.js';

const router = Router();

// POST /api/ai/assistant
router.post('/assistant', askAssistant);

export default router;
