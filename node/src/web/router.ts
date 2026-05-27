import Fastify from 'fastify';
import fastifyCookie from '@fastify/cookie';
import fastifyCors from '@fastify/cors';
import fastifyFormbody from '@fastify/formbody';
import { TokenStore } from '../security/token-store';
import { registerAuthHooks } from './auth';
import { registerPreRoutes } from './controllers/pre';
import { registerUserRoutes } from './controllers/user';
import { registerAdminRoutes } from './controllers/admin';
import { logger } from '../utils/logger';

export async function createServer(port: number, superQid: string, manageKey: string, manageBid: string) {
  const app = Fastify({ logger: false });
  const tokenStore = new TokenStore();

  await app.register(fastifyCookie);
  await app.register(fastifyCors, { origin: true, credentials: true });
  await app.register(fastifyFormbody);

  registerAuthHooks(app, tokenStore, superQid, manageKey, manageBid);
  registerPreRoutes(app);
  registerUserRoutes(app);
  registerAdminRoutes(app);

  // health check
  app.get('/api/health', async () => ({ status: 'ok', uptime: process.uptime(), memory: process.memoryUsage() }));

  // clean expired tokens periodically
  setInterval(() => tokenStore.cleanExpired(), 10 * 60 * 1000);

  await app.listen({ port, host: '0.0.0.0' });
  logger.info(`Server listening on port ${port}`);
  return app;
}
