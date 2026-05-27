import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { getDb } from '../../database';
import { botManager } from '../../bot/manager';
import { requireRole } from '../auth';
import type { AuthM, ConnConfig } from '../../model/types';

export function registerAdminRoutes(app: FastifyInstance): void {
  const db = getDb();

  // admin: list all users
  app.get('/api/m/list', { preHandler: [requireRole('admin')] }, async () => {
    const authList = db.prepare('SELECT * FROM auth_m').all() as AuthM[];
    return authList.map(a => {
      const bot = botManager.getBot(a.qid);
      return {
        qid: a.qid,
        auth: a.auth,
        exp: a.exp,
        t0: bot?.isConnected ? a.t0 : -1,
      };
    });
  });

  // admin: modify user
  app.get('/api/m/modify', { preHandler: [requireRole('admin')] }, async (req) => {
    const { qid, exp, auth } = req.query as { qid: string; exp: string; auth: string };
    db.prepare('UPDATE auth_m SET exp = ?, auth = ? WHERE qid = ?')
      .run(parseInt(exp), auth, qid);
    const authList = db.prepare('SELECT * FROM auth_m').all() as AuthM[];
    return authList.map(a => {
      const bot = botManager.getBot(a.qid);
      return { qid: a.qid, auth: a.auth, exp: a.exp, t0: bot?.isConnected ? a.t0 : -1 };
    });
  });

  // admin: get-exp helper
  app.get('/api/m/get-exp', { preHandler: [requireRole('admin')] }, async (req) => {
    const { y, m, d } = req.query as { y: string; m: string; d: string };
    try {
      const date = new Date(`${y}-${m.padStart(2, '0')}-${d.padStart(2, '0')}T12:01:00`);
      return date.getTime();
    } catch {
      return 0;
    }
  });

  // ─── Connection config (admin) ───
  app.get('/api/conn-config/page', async (req) => {
    const page = parseInt((req.query as any)?.page ?? '1');
    const size = parseInt((req.query as any)?.size ?? '10');
    const offset = (page - 1) * size;
    const total = (db.prepare('SELECT COUNT(*) as c FROM conn_config').get() as any)?.c ?? 0;
    const records = db.prepare('SELECT * FROM conn_config LIMIT ? OFFSET ?').all(size, offset);
    return { records, total, current: page, pages: Math.ceil(total / size) };
  });

  app.get('/api/conn-config/detail', async (req) => {
    const id = (req.query as any)?.id;
    const config = db.prepare('SELECT * FROM conn_config WHERE qid = ?').get(id);
    if (!config) return { error: '配置不存在' };
    return config;
  });

  app.post('/api/conn-config/add', async (req) => {
    const body = req.body as ConnConfig;
    db.prepare(
      'INSERT OR REPLACE INTO conn_config (qid, ip, port, type, token, heart) VALUES (?, ?, ?, ?, ?, ?)'
    ).run(body.qid, body.ip, body.port, body.type, body.token, body.heart);
    botManager.addBot(body);
    return { success: true, msg: '添加成功' };
  });

  app.post('/api/conn-config/update', async (req) => {
    const body = req.body as ConnConfig;
    db.prepare(
      'UPDATE conn_config SET ip=?, port=?, type=?, token=?, heart=? WHERE qid=?'
    ).run(body.ip, body.port, body.type, body.token, body.heart, body.qid);
    botManager.removeBot(body.qid);
    botManager.addBot(body);
    return { success: true, msg: '更新成功' };
  });

  app.post('/api/conn-config/delete', async (req) => {
    const body = req.body as { qid: string };
    db.prepare('DELETE FROM conn_config WHERE qid = ?').run(body.qid);
    botManager.removeBot(body.qid);
    return { success: true, msg: '删除成功' };
  });

  // ─── AI config ───
  app.get('/api/ai-conf/config', async (req) => {
    const qid = (req as any).user?.qid;
    let conf = db.prepare('SELECT * FROM ai_conf WHERE qid = ?').get(qid);
    if (!conf) {
      db.prepare('INSERT INTO ai_conf (qid) VALUES (?)').run(qid);
      conf = db.prepare('SELECT * FROM ai_conf WHERE qid = ?').get(qid);
    }
    return conf;
  });

  app.post('/api/ai-conf/update', async (req) => {
    const qid = (req as any).user?.qid;
    const body = req.body as Record<string, any>;
    const fields = ['open', 'prefix', 'api_key', 'base_url', 'model_id', 'temperature', 'network', 'name', 'trait', 'max_message'];
    const sets: string[] = [];
    const vals: any[] = [];
    for (const f of fields) {
      if (body[f] !== undefined) {
        sets.push(`${f} = ?`);
        vals.push(body[f]);
      }
    }
    if (sets.length) {
      let existing = db.prepare('SELECT * FROM ai_conf WHERE qid = ?').get(qid);
      if (!existing) {
        db.prepare('INSERT INTO ai_conf (qid) VALUES (?)').run(qid);
      }
      vals.push(qid);
      db.prepare(`UPDATE ai_conf SET ${sets.join(', ')} WHERE qid = ?`).run(...vals);
    }
    return db.prepare('SELECT * FROM ai_conf WHERE qid = ?').get(qid);
  });

  // ─── Optional features ───
  app.get('/api/opts', async (req) => {
    const { getOptionalDtos } = await import('../../optional');
    const qid = (req as any).user?.qid;
    return getOptionalDtos(qid);
  });

  app.get('/api/opts/toggle', async (req) => {
    const qid = (req as any).user?.qid;
    const opt = (req.query as any)?.opt;
    const existing = db.prepare('SELECT * FROM optional WHERE qid = ? AND opt = ?').get(qid, opt) as any;
    if (existing) {
      db.prepare('UPDATE optional SET open = ? WHERE qid = ? AND opt = ?')
        .run(existing.open ? 0 : 1, qid, opt);
    } else {
      db.prepare('INSERT INTO optional (qid, opt, open) VALUES (?, ?, 1)')
        .run(qid, opt);
    }
    const { getOptionalDtos } = await import('../../optional');
    return getOptionalDtos(qid);
  });
}
