import { app } from './app.js';
import { env } from './config/env.js';
import { connectDatabase } from './config/database.js';

const startServer = async () => {
  // 1. Establish Database Connection
  await connectDatabase();

  // 2. Start Listening
  const port = process.env.PORT ? Number(process.env.PORT) : env.PORT;

  app.listen(port, '0.0.0.0', () => {
    console.log(`Server is running in ${env.NODE_ENV} mode on port ${port}`);
  });
};

startServer();
