import { Router } from 'express';
import { getEvents, getRecentEvents } from '../controllers/event.controller.js';
import { streamEvents } from '../controllers/eventStream.controller.js';

const router = Router();

router.get('/events', getEvents);
// NOTE: /events/stream must be registered BEFORE /events/recent (and any /:id route)
// to prevent Express from treating "stream" as a dynamic segment value.
router.get('/events/stream', streamEvents);
router.get('/events/recent', getRecentEvents);

export default router;
