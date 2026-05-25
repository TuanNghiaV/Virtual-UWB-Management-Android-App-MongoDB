import dotenv from 'dotenv';
import { z } from 'zod';

// Load .env file
dotenv.config();

const envSchema = z.object({
  PORT: z.coerce.number().default(3001),
  NODE_ENV: z.enum(['development', 'production', 'test']).default('development'),
  MONGODB_URI: z.string({
    required_error: 'MONGODB_URI environmental variable is required but missing.',
  }),
  // Optional — backend starts without this key, but /api/routes/google returns 500
  // until a valid key is provided. Do NOT make this required to avoid breaking dev startup.
  GOOGLE_ROUTES_API_KEY: z.string().optional(),
  GEMINI_API_KEY: z.string().optional(),
});

const parseEnv = () => {
  const result = envSchema.safeParse(process.env);

  if (!result.success) {
    // Print clear errors, but do not dump process.env containing secrets
    console.error('❌ Environment configuration validation failed:');
    result.error.issues.forEach((issue) => {
      console.error(`   - ${issue.path.join('.')}: ${issue.message}`);
    });
    process.exit(1);
  }

  // Safe diagnostics for GOOGLE_ROUTES_API_KEY
  const key = result.data.GOOGLE_ROUTES_API_KEY;
  if (key) {
    console.log('GOOGLE_ROUTES_API_KEY configured: true');
  } else {
    console.log('GOOGLE_ROUTES_API_KEY configured: false');
  }

  return result.data;
};

export const env = parseEnv();
