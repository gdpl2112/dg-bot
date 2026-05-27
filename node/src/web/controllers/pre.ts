import type { FastifyInstance } from 'fastify';
import { getDb } from '../../database';
import { botManager } from '../../bot/manager';
import type { BotInfo } from '../../model/types';

export function registerPreRoutes(app: FastifyInstance): void {
  // public: statistics
  app.get('/api/pre/statistics', async () => {
    const db = getDb();
    const groupCount = (db.prepare(
      "SELECT COALESCE(SUM(count),0) as total FROM statistics WHERE type='GROUP'"
    ).get() as any)?.total ?? 0;
    const privateCount = (db.prepare(
      "SELECT COALESCE(SUM(count),0) as total FROM statistics WHERE type='PRIVATE'"
    ).get() as any)?.total ?? 0;
    return { group: groupCount, private: privateCount, all: groupCount + privateCount };
  });

  // public: bot list
  app.get('/api/bot/list', async () => {
    const bots = botManager.getAllBots();
    return bots.map(b => {
      const info = b.info;
      return {
        id: info?.id ?? parseInt(b.botId),
        nick: info?.nick ?? '',
        online: b.isConnected,
        friendCount: info?.friendCount ?? 0,
        groupCount: info?.groupCount ?? 0,
      };
    });
  });

  // public: all bot list (including offline from db)
  app.get('/api/bot/alist', async () => {
    const db = getDb();
    const authList = db.prepare('SELECT qid FROM auth_m').all() as { qid: string }[];
    return authList.map(a => {
      const bot = botManager.getBot(a.qid);
      const info = bot?.info;
      return {
        id: parseInt(a.qid),
        nick: info?.nick ?? '',
        online: bot?.isConnected ?? false,
        friendCount: info?.friendCount ?? 0,
        groupCount: info?.groupCount ?? 0,
      };
    });
  });

  // public: avatar proxy
  app.get('/api/bot/avatar', async (req, reply) => {
    const t = (req.query as any)?.t;
    if (!t) {
      reply.code(400).send('Missing parameter t');
      return;
    }
    const url = `https://q1.qlogo.cn/g?b=qq&nk=${t}&s=640`;
    try {
      const resp = await fetch(url);
      const buffer = Buffer.from(await resp.arrayBuffer());
      reply.type('image/png').send(buffer);
    } catch (e) {
      reply.code(500).send('Avatar fetch failed');
    }
  });
}
