import { app } from './app.js';
import { env } from './config/env.js';
import { connectDatabase } from './config/database.js';

const startServer = async () => {
  // 1. Establish Database Connection
  await connectDatabase();

  // 2. Start Listening
  app.listen(env.PORT, () => {
    console.log(`Server is running in ${env.NODE_ENV} mode on port ${env.PORT}`);
  });
};

startServer();
