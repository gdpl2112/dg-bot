import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { TokenStore } from '../security/token-store';
import { getDb } from '../database';
import { logger } from '../utils/logger';
import type { AuthM } from '../model/types';

const TOKEN_COOKIE = 'dg-token';
const COOKIE_MAX_AGE = 2 * 60 * 60; // 2h in seconds

export function registerAuthHooks(app: FastifyInstance, tokenStore: TokenStore, superQid: string, manageKey: string, manageBid: string): void {
  // public routes
  const publicPaths = [
    '/api/bot/list',
    '/api/bot/alist',
    '/api/bot/avatar',
    '/api/pre/statistics',
    '/api/rec',
    '/bot/login',
    '/api/rec/test',
  ];

  app.addHook('onRequest', async (req: FastifyRequest, reply: FastifyReply) => {
    const path = req.url.split('?')[0];

    if (publicPaths.some(p => path === p)) return;

    // manage key check
    const manageKeyHeader = req.headers['x-manage-key'] as string;
    if (manageKeyHeader && manageKeyHeader === manageKey) {
      (req as any).user = { qid: manageBid, role: 'manage' };
      return;
    }

    // token from cookie or header
    let token = '';
    const cookies = (req as any).cookies ?? {};
    token = cookies[TOKEN_COOKIE] ?? '';
    if (!token) {
      const authHeader = req.headers.authorization;
      if (authHeader?.startsWith('Bearer ')) {
        token = authHeader.substring(7);
      }
    }

    const userId = tokenStore.validateToken(token);
    if (!userId) {
      reply.code(401).send({ error: 'Unauthorized' });
      return;
    }

    const role = userId === superQid ? 'admin' : 'user';
    (req as any).user = { qid: userId, role };
  });

  // login route
  app.route({
    method: ['GET', 'POST'],
    url: '/bot/login',
    handler: async (req, reply) => {
      let qid = (req.query as any)?.qid ?? '';
      let p = (req.query as any)?.p ?? '';
      if (!qid && req.body) {
        const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body;
        qid = body.qid ?? '';
        p = body.p ?? '';
      }

      if (!qid || !p) {
        reply.code(403).send('认证失败\n参数错误!');
        return;
      }

      const db = getDb();
      const authM = db.prepare('SELECT * FROM auth_m WHERE qid = ?').get(qid) as AuthM | undefined;
      if (!authM || authM.auth !== p) {
        reply.code(403).send('认证失败\n请使用正确的登录链接或授权码/密码!');
        return;
      }

      const token = tokenStore.createToken(qid);
      logger.info(`User ${qid} logged in`);

      reply.setCookie(TOKEN_COOKIE, token, {
        path: '/',
        httpOnly: true,
        maxAge: COOKIE_MAX_AGE,
      });

      if (req.method === 'GET') {
        reply.redirect('/bot');
      } else {
        reply.send('授权码/密码 正确');
      }
    },
  });

  // logout route
  app.post('/bot/logout', async (req, reply) => {
    const cookies = (req as any).cookies ?? {};
    const token = cookies[TOKEN_COOKIE];
    if (token) {
      const userId = tokenStore.validateToken(token);
      if (userId) tokenStore.removeToken(userId);
    }
    reply.clearCookie(TOKEN_COOKIE, { path: '/' });
    reply.send('已登出');
  });
}

export function requireRole(role: string) {
  return async (req: FastifyRequest, reply: FastifyReply) => {
    const user = (req as any).user;
    if (!user) {
      reply.code(401).send({ error: 'Unauthorized' });
      return;
    }
    if (role === 'admin' && user.role !== 'admin') {
      reply.code(403).send({ error: 'Forbidden: admin required' });
      return;
    }
  };
}
