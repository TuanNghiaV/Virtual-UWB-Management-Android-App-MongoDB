# Backend Deploy Guide

This backend is ready to deploy as a Node.js/Express service backed by MongoDB Atlas.

## Render

- Root Directory: `backend`
- Build Command: `npm install --include=dev && npm run build`
- Start Command: `npm start`
- Health Check Path: `/health`
- Environment Variables:
  - `MONGODB_URI=...`
  - `GEMINI_API_KEY=...`
  - `NODE_ENV=production`

Render needs devDependencies during the build step so TypeScript, `@types/node`, and `@types/express` are available when `tsc` runs.

## Railway

- Root Directory: `backend`
- Build Command: `npm run build`
- Start Command: `npm start`
- Environment Variables:
  - `MONGODB_URI=...`
  - `GEMINI_API_KEY=...`
  - `NODE_ENV=production`

## Runtime Notes

- The server listens on `process.env.PORT` when provided by the host.
- The Express app binds to `0.0.0.0` so cloud platforms can route traffic to it.
- CORS is permissive for native Android clients.
- Google Routes stays optional and only works if `GOOGLE_ROUTES_API_KEY` is set.

## Android Base URL

After deployment, set `MONGODB_API_BASE_URL` in `local.properties` to your cloud URL, for example:

```properties
MONGODB_API_BASE_URL=https://virtualuwb-backend.onrender.com
```

Do not commit `local.properties` or backend `.env`.