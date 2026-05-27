import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { getDb } from '../../database';
import { scriptEngine } from '../../handler/script';
import type { Conf, Passive, CronMessage, CallTemplate, GroupConf, V11Conf } from '../../model/types';
import { appendTask, removeCronTask } from '../../handler/cron';

function getQid(req: FastifyRequest): string {
  return (req as any).user?.qid ?? '';
}

export function registerUserRoutes(app: FastifyInstance): void {
  const db = getDb();

  // ─── Password ───
  app.post('/api/mpwd', async (req) => {
    const qid = getQid(req);
    const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body as any;
    const pwd = body.pwd;
    db.prepare('UPDATE auth_m SET auth = ? WHERE qid = ?').run(pwd, qid);
    return 'ok';
  });

  app.get('/api/cpwd', async (req) => {
    const qid = getQid(req);
    const auth = db.prepare('SELECT auth FROM auth_m WHERE qid = ?').get(qid) as { auth: string } | undefined;
    return auth?.auth ?? '';
  });

  // ─── Config (Conf) ───
  app.get('/api/conf', async (req) => {
    const qid = getQid(req);
    let conf = db.prepare('SELECT * FROM conf WHERE qid = ?').get(qid) as Conf | undefined;
    if (!conf) {
      db.prepare('INSERT INTO conf (qid) VALUES (?)').run(qid);
      conf = db.prepare('SELECT * FROM conf WHERE qid = ?').get(qid) as Conf;
    }
    return conf;
  });

  app.post('/api/conf/update', async (req) => {
    const qid = getQid(req);
    const body = req.body as Partial<Conf>;
    const fields = ['cd0', 'retell', 'open0', 'close0', 'open1', 'close1', 'add0', 'cancel0', 'select0', 'del0', 'rsid', 'nu', 'code', 'status0'];
    const sets: string[] = [];
    const vals: any[] = [];
    for (const f of fields) {
      if ((body as any)[f] !== undefined) {
        sets.push(`${f} = ?`);
        vals.push((body as any)[f]);
      }
    }
    if (sets.length) {
      vals.push(qid);
      db.prepare(`UPDATE conf SET ${sets.join(', ')} WHERE qid = ?`).run(...vals);
      scriptEngine.clearCache(qid);
    }
    return 'ok';
  });

  // ─── Passive (keyword reply) ───
  app.get('/api/passives', async (req) => {
    const qid = getQid(req);
    return db.prepare('SELECT * FROM passive WHERE qid = ?').all(qid);
  });

  app.post('/api/passive/add', async (req) => {
    const qid = getQid(req);
    const body = req.body as { touch: string; out: string };
    db.prepare('INSERT INTO passive (qid, touch, out) VALUES (?, ?, ?)').run(qid, body.touch, body.out);
    return 'ok';
  });

  app.post('/api/passive/del', async (req) => {
    const body = req.body as { id: number };
    db.prepare('DELETE FROM passive WHERE id = ?').run(body.id);
    return 'ok';
  });

  // ─── Cron messages ───
  app.get('/api/crons', async (req) => {
    const qid = getQid(req);
    return db.prepare('SELECT * FROM cron_message WHERE qid = ?').all(qid);
  });

  app.post('/api/cron/add', async (req) => {
    const qid = getQid(req);
    const body = req.body as Omit<CronMessage, 'id' | 'qid'>;
    const result = db.prepare(
      'INSERT INTO cron_message (qid, desc, cron, target_id, msg) VALUES (?, ?, ?, ?, ?)'
    ).run(qid, body.desc, body.cron, body.target_id, body.msg);
    const id = result.lastInsertRowid as number;
    const msg = db.prepare('SELECT * FROM cron_message WHERE id = ?').get(id) as CronMessage;
    appendTask(msg);
    return 'ok';
  });

  app.post('/api/cron/del', async (req) => {
    const body = req.body as { id: number };
    db.prepare('DELETE FROM cron_message WHERE id = ?').run(body.id);
    removeCronTask(body.id);
    return 'ok';
  });

  // ─── Call API templates ───
  app.get('/api/calls', async (req) => {
    const qid = getQid(req);
    return db.prepare('SELECT * FROM call_template WHERE qid = ?').all(qid);
  });

  app.post('/api/call/add', async (req) => {
    const qid = getQid(req);
    const body = req.body as Omit<CallTemplate, 'qid'>;
    db.prepare(
      'INSERT INTO call_template (qid, touch, url, out, out_args, jude, err) VALUES (?, ?, ?, ?, ?, ?, ?)'
    ).run(qid, body.touch, body.url, body.out, body.out_args, body.jude, body.err);
    return 'ok';
  });

  app.post('/api/call/del', async (req) => {
    const qid = getQid(req);
    const body = req.body as { touch: string };
    db.prepare('DELETE FROM call_template WHERE qid = ? AND touch = ?').run(qid, body.touch);
    return 'ok';
  });

  // ─── Group config ───
  app.get('/api/group-conf', async (req) => {
    const qid = getQid(req);
    return db.prepare('SELECT * FROM group_conf WHERE qid = ?').all(qid);
  });

  app.post('/api/group-conf/update', async (req) => {
    const qid = getQid(req);
    const body = req.body as GroupConf;
    const existing = db.prepare('SELECT * FROM group_conf WHERE qid = ? AND tid = ?').get(qid, body.tid);
    if (existing) {
      db.prepare('UPDATE group_conf SET k0=?, k1=?, k2=?, k3=?, k4=? WHERE qid=? AND tid=?')
        .run(body.k0 ?? 1, body.k1 ?? 1, body.k2 ?? 1, body.k3 ?? 1, body.k4 ?? 0, qid, body.tid);
    } else {
      db.prepare('INSERT INTO group_conf (qid, tid, k0, k1, k2, k3, k4) VALUES (?, ?, ?, ?, ?, ?, ?)')
        .run(qid, body.tid, body.k0 ?? 1, body.k1 ?? 1, body.k2 ?? 1, body.k3 ?? 1, body.k4 ?? 0);
    }
    return 'ok';
  });

  // ─── V11 config ───
  app.get('/api/v11-conf', async (req) => {
    const qid = getQid(req);
    let conf = db.prepare('SELECT * FROM v11_conf WHERE qid = ?').get(qid) as V11Conf | undefined;
    if (!conf) {
      db.prepare('INSERT INTO v11_conf (qid) VALUES (?)').run(qid);
      conf = db.prepare('SELECT * FROM v11_conf WHERE qid = ?').get(qid) as V11Conf;
    }
    return conf;
  });

  app.post('/api/v11-conf/update', async (req) => {
    const qid = getQid(req);
    const body = req.body as Partial<V11Conf>;
    const fields = ['auto_like', 'need_max_like', 'auto_like_yesterday', 'like_black', 'like_white', 'sign_groups', 'zone_evl', 'auto_zone_like', 'zone_comment', 'zone_walks'];
    const sets: string[] = [];
    const vals: any[] = [];
    for (const f of fields) {
      if ((body as any)[f] !== undefined) {
        sets.push(`${f} = ?`);
        vals.push((body as any)[f]);
      }
    }
    if (sets.length) {
      vals.push(qid);
      db.prepare(`UPDATE v11_conf SET ${sets.join(', ')} WHERE qid = ?`).run(...vals);
    }
    return 'ok';
  });

  // ─── Script logs & exceptions ───
  app.get('/api/script/exception', async (req) => {
    const qid = getQid(req);
    return scriptEngine.getException(qid) ?? null;
  });

  app.get('/api/script/prints', async (req) => {
    const qid = getQid(req);
    return scriptEngine.getPrintLogs(qid);
  });

  // ─── Messages (recalled) ───
  app.get('/api/messages/recalled', async (req) => {
    const qid = getQid(req);
    const page = parseInt((req.query as any)?.page ?? '1');
    const size = Math.min(parseInt((req.query as any)?.size ?? '20'), 100);
    const offset = (page - 1) * size;
    const total = (db.prepare('SELECT COUNT(*) as c FROM all_message WHERE bot_id = ? AND recalled = 1').get(parseInt(qid)) as any)?.c ?? 0;
    const list = db.prepare('SELECT * FROM all_message WHERE bot_id = ? AND recalled = 1 ORDER BY time DESC LIMIT ? OFFSET ?')
      .all(parseInt(qid), size, offset);
    return { total, page, size, list };
  });
}
