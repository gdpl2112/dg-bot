import type { FastifyInstance, FastifyRequest } from 'fastify';
import { getDb } from '../../database';
import { scriptEngine } from '../../handler/script';
import { botManager } from '../../bot/manager';
import type { Conf, Passive, CronMessage, CallTemplate, GroupConf, V11Conf, Administrator } from '../../model/types';
import { appendTask, removeCronTask } from '../../handler/cron';

function getQid(req: FastifyRequest): string {
  return (req as any).user?.qid ?? '';
}

export function registerUserRoutes(app: FastifyInstance): void {
  const db = getDb();

  // ─── /api/user (Java: UserController.user) ───
  app.all('/api/user', async (req) => {
    const qid = getQid(req);
    const authM = db.prepare('SELECT * FROM auth_m WHERE qid = ?').get(qid) as any;
    if (!authM) return null;
    const bot = botManager.getBot(qid);
    const isOnline = bot?.isConnected ?? false;
    const nickname = isOnline ? (bot?.info?.nick || qid) : '未在线';
    const t0 = isOnline ? (authM.t0 ?? -1) : -1;
    const role = (req as any).user?.role === 'admin' ? 'admin' : 'user';
    return {
      qid: authM.qid,
      nickname,
      t0,
      icon: `https://q1.qlogo.cn/g?b=qq&nk=${qid}&s=640`,
      expire: authM.exp,
      role,
    };
  });

  // ─── /api/statistics (Java: UserController.statistics) ───
  app.all('/api/statistics', async (req) => {
    const qid = getQid(req);
    const cc = (db.prepare('SELECT COUNT(*) as c FROM cron_message WHERE qid = ?').get(qid) as any)?.c ?? 0;
    const mc = (db.prepare('SELECT COUNT(*) as c FROM administrator WHERE qid = ?').get(qid) as any)?.c ?? 0;
    const pc = (db.prepare('SELECT COUNT(*) as c FROM passive WHERE qid = ?').get(qid) as any)?.c ?? 0;
    const cac = (db.prepare('SELECT COUNT(*) as c FROM call_template WHERE qid = ?').get(qid) as any)?.c ?? 0;
    return { cc, mc, pc, cac };
  });

  // ─── Password (Java: AuthController) ───
  app.all('/api/mpwd', async (req) => {
    const qid = getQid(req);
    const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body as any;
    const pwd = body?.pwd;
    if (pwd) {
      db.prepare('UPDATE auth_m SET auth = ? WHERE qid = ?').run(pwd, qid);
    }
    return 'ok';
  });

  app.all('/api/cpwd', async (req) => {
    const qid = getQid(req);
    const auth = db.prepare('SELECT auth FROM auth_m WHERE qid = ?').get(qid) as { auth: string } | undefined;
    return auth?.auth ?? '';
  });

  // ─── Config (Java: UserConfigController) ───
  app.all('/api/config', async (req) => {
    const qid = getQid(req);
    let conf = db.prepare('SELECT * FROM conf WHERE qid = ?').get(qid) as Conf | undefined;
    if (!conf) {
      db.prepare('INSERT INTO conf (qid) VALUES (?)').run(qid);
      conf = db.prepare('SELECT * FROM conf WHERE qid = ?').get(qid) as Conf;
    }
    return conf;
  });

  app.all('/api/conf-modify', async (req) => {
    const qid = getQid(req);
    const key = (req.query as any)?.key ?? (req.body as any)?.key;
    const value = (req.query as any)?.value ?? (req.body as any)?.value;
    if (!key) return '失败';
    let conf = db.prepare('SELECT * FROM conf WHERE qid = ?').get(qid) as Conf | undefined;
    if (!conf) {
      db.prepare('INSERT INTO conf (qid) VALUES (?)').run(qid);
    }
    const allowedFields = ['cd0', 'retell', 'open0', 'close0', 'open1', 'close1', 'add0', 'cancel0', 'select0', 'del0', 'rsid', 'nu', 'code', 'status0'];
    if (allowedFields.includes(key)) {
      db.prepare(`UPDATE conf SET ${key} = ? WHERE qid = ?`).run(value, qid);
      return '成功';
    }
    return '失败';
  });

  app.post('/api/code-modify', async (req) => {
    const qid = getQid(req);
    const code = (req.body as any)?.code ?? '';
    let conf = db.prepare('SELECT * FROM conf WHERE qid = ?').get(qid) as Conf | undefined;
    if (!conf) {
      db.prepare('INSERT INTO conf (qid) VALUES (?)').run(qid);
    }
    db.prepare('UPDATE conf SET code = ? WHERE qid = ?').run(code, qid);
    scriptEngine.clearCache(qid);
    return '成功';
  });

  app.all('/api/get-exception', async (req) => {
    const qid = getQid(req);
    return scriptEngine.getException(qid) ?? { msg: '未发现报错', time: Date.now(), bid: parseInt(qid) || 0 };
  });

  app.all('/api/get-log', async (req) => {
    const qid = getQid(req);
    return scriptEngine.getPrintLogs(qid);
  });

  // ─── Passive (Java: UserPassiveController) ───
  app.all('/api/p-list', async (req) => {
    const qid = getQid(req);
    const passives = db.prepare('SELECT * FROM passive WHERE qid = ?').all(qid) as Passive[];
    const grouped: Record<string, { qid: string; touch: string; outs: string[] }> = {};
    for (const p of passives) {
      if (!grouped[p.touch]) {
        grouped[p.touch] = { qid: p.qid, touch: p.touch, outs: [] };
      }
      grouped[p.touch].outs.push(p.out);
    }
    return Object.values(grouped);
  });

  app.all('/api/p-add', async (req) => {
    const qid = getQid(req);
    const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body as any;
    const touch = body?.t0;
    const out = body?.t1;
    if (touch && out) {
      db.prepare('INSERT INTO passive (qid, touch, out) VALUES (?, ?, ?)').run(qid, touch, out);
    }
    // return updated list (same format as p-list)
    const passives = db.prepare('SELECT * FROM passive WHERE qid = ?').all(qid) as Passive[];
    const grouped: Record<string, { qid: string; touch: string; outs: string[] }> = {};
    for (const p of passives) {
      if (!grouped[p.touch]) {
        grouped[p.touch] = { qid: p.qid, touch: p.touch, outs: [] };
      }
      grouped[p.touch].outs.push(p.out);
    }
    return Object.values(grouped);
  });

  app.all('/api/p-del', async (req) => {
    const qid = getQid(req);
    const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body as any;
    const touch = body?.touch;
    const out = body?.out;
    if (touch || out) {
      let sql = 'DELETE FROM passive WHERE qid = ?';
      const params: any[] = [qid];
      if (touch) { sql += ' AND touch = ?'; params.push(touch); }
      if (out) { sql += ' AND out = ?'; params.push(out); }
      db.prepare(sql).run(...params);
      return true;
    }
    return false;
  });

  // ─── Cron (Java: UserCronController) ───
  app.all('/api/cronAdd', async (req) => {
    const qid = getQid(req);
    let body = typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
    try {
      body = decodeURIComponent(body);
      while (body.endsWith('=')) body = body.slice(0, -1);
    } catch { /* ignore */ }

    const jo = JSON.parse(body);
    const content = jo.content;
    if (!content) return '内容为空';
    const targetId = jo.targetId;
    if (!targetId) return '目标id为空';
    const targetType = jo['tid-type'] ?? '';
    const hour = jo.hour ?? '0';
    const mil = jo.mil ?? '0';

    let cronStr = '8 ';
    let desc = '';
    cronStr += `${mil} ${hour} `;
    desc = `${hour}点${mil}分`;

    const l1type = jo['l1-type'];
    if (l1type === 'week') {
      const wks = jo.week;
      if (!wks) return '未选择星期';
      const weeks = wks.split('');
      cronStr += '? * ';
      let weekDesc = '';
      for (const w of weeks) {
        cronStr += `${w},`;
        const n = ((parseInt(w) + 5) % 7) + 1;
        weekDesc = `${n},${weekDesc}`;
      }
      desc = `每星期${weekDesc}${desc}`;
      cronStr = cronStr.slice(0, -1) + ' ';
    } else {
      const dayType = jo['for-day'];
      if (dayType === 'eve') {
        cronStr += '* ';
        desc = `每日${desc}`;
      } else {
        const day = jo.day ?? '*';
        cronStr += `${day} `;
        desc = `${day}日${desc}`;
      }
      const monthType = jo['for-month'];
      if (monthType === 'eve') {
        cronStr += '* ';
        desc = `每月${desc}`;
      } else {
        const month = jo.month ?? '*';
        cronStr += `${month} `;
        desc = `${month}月${desc}`;
      }
      cronStr += '?';
    }

    const tid = targetType + targetId;
    const result = db.prepare(
      'INSERT INTO cron_message (qid, desc, cron, target_id, msg) VALUES (?, ?, ?, ?, ?)'
    ).run(qid, desc, cronStr, tid, content);
    const id = result.lastInsertRowid as number;

    if (id > 0) {
      const msg = db.prepare('SELECT * FROM cron_message WHERE id = ?').get(id) as CronMessage;
      if (msg) appendTask(msg);
      return 'ok';
    }
    return 'error';
  });

  app.all('/api/cron-list', async (req) => {
    const qid = getQid(req);
    return db.prepare('SELECT * FROM cron_message WHERE qid = ?').all(qid);
  });

  app.all('/api/cron-del', async (req) => {
    const qid = getQid(req);
    const id = parseInt((req.query as any)?.id ?? (req.body as any)?.id ?? '0');
    if (id > 0) {
      db.prepare('DELETE FROM cron_message WHERE id = ? AND qid = ?').run(id, qid);
      removeCronTask(id);
    }
    return db.prepare('SELECT * FROM cron_message WHERE qid = ?').all(qid);
  });

  // ─── Call API (Java: UserCallApiController, prefix /api/ca) ───
  app.all('/api/ca/get_data', async (req) => {
    const qid = getQid(req);
    return db.prepare('SELECT * FROM call_template WHERE qid = ?').all(qid);
  });

  app.all('/api/ca/delete', async (req) => {
    const qid = getQid(req);
    const touch = (req.query as any)?.touch ?? (req.body as any)?.touch;
    if (touch) {
      db.prepare('DELETE FROM call_template WHERE qid = ? AND touch = ?').run(qid, touch);
    }
    return db.prepare('SELECT * FROM call_template WHERE qid = ?').all(qid);
  });

  app.all('/api/ca/append', async (req) => {
    const qid = getQid(req);
    const q = req.query as any;
    const b = req.body as any;
    const touch = q?.touch ?? b?.touch;
    const url = q?.url ?? b?.url;
    const out = q?.out ?? b?.out;
    const outArgs = q?.outArgs ?? b?.outArgs ?? '';
    const jude = q?.jude ?? b?.jude ?? '';
    if (!touch || !url || !out) {
      return db.prepare('SELECT * FROM call_template WHERE qid = ?').all(qid);
    }
    const existing = db.prepare('SELECT * FROM call_template WHERE qid = ? AND touch = ?').get(qid, touch);
    if (existing) {
      db.prepare('UPDATE call_template SET url=?, out=?, out_args=?, jude=? WHERE qid=? AND touch=?')
        .run(url, out, outArgs, jude, qid, touch);
    } else {
      db.prepare('INSERT INTO call_template (qid, touch, url, out, out_args, jude) VALUES (?, ?, ?, ?, ?, ?)')
        .run(qid, touch, url, out, outArgs, jude);
    }
    return db.prepare('SELECT * FROM call_template WHERE qid = ?').all(qid);
  });

  // ─── Administrator list (Java: UserController.mlist/mdel/m_add) ───
  app.all('/api/mlist', async (req) => {
    const qid = getQid(req);
    return db.prepare('SELECT * FROM administrator WHERE qid = ?').all(qid);
  });

  app.all('/api/mdel', async (req) => {
    const qid = getQid(req);
    const id = (req.query as any)?.id ?? (req.body as any)?.id;
    if (id) {
      const result = db.prepare('DELETE FROM administrator WHERE qid = ? AND target_id = ?').run(qid, id);
      return result.changes > 0;
    }
    return false;
  });

  app.all('/api/m_add', async (req) => {
    const qid = getQid(req);
    const id = (req.query as any)?.id ?? (req.body as any)?.id;
    if (id) {
      const existing = db.prepare('SELECT * FROM administrator WHERE qid = ? AND target_id = ?').get(qid, id);
      if (!existing) {
        db.prepare('INSERT INTO administrator (qid, target_id) VALUES (?, ?)').run(qid, id);
        return true;
      }
    }
    return false;
  });

  // ─── Group / Friend list (Java: UserController.glist/flist) ───
  app.all('/api/glist', async (req) => {
    const qid = getQid(req);
    const confs = db.prepare('SELECT * FROM group_conf WHERE qid = ?').all(qid) as GroupConf[];
    const tid2conf: Record<string, GroupConf> = {};
    for (const c of confs) tid2conf[c.tid] = c;

    const bot = botManager.getBot(qid);
    const groups = bot?.info?.groups ?? [];
    return groups.map((g: any) => {
      const tid = 'g' + g.id;
      const conf = tid2conf[tid];
      const name = (g.name?.length > 6) ? g.name.substring(0, 5) + '..' : (g.name ?? '');
      return {
        tid,
        name,
        icon: g.icon ?? `https://p.qlogo.cn/gh/${g.id}/${g.id}/640`,
        k0: conf?.k0 ?? 1,
        k1: conf?.k1 ?? 1,
        k2: conf?.k2 ?? 1,
        k3: conf?.k3 ?? 1,
        k4: conf?.k4 ?? 0,
      };
    });
  });

  app.all('/api/flist', async (req) => {
    const qid = getQid(req);
    const confs = db.prepare('SELECT * FROM group_conf WHERE qid = ?').all(qid) as GroupConf[];
    const tid2conf: Record<string, GroupConf> = {};
    for (const c of confs) tid2conf[c.tid] = c;

    const bot = botManager.getBot(qid);
    const friends = bot?.info?.friends ?? [];
    return friends.map((f: any) => {
      const tid = 'f' + f.id;
      const conf = tid2conf[tid];
      const nick = f.remark || f.nick || '';
      const name = (nick.length > 6) ? nick.substring(0, 5) + '..' : nick;
      return {
        tid,
        name,
        icon: `https://q1.qlogo.cn/g?b=qq&nk=${f.id}&s=640`,
        k0: conf?.k0 ?? 1,
        k1: conf?.k1 ?? 1,
        k2: conf?.k2 ?? 1,
        k3: conf?.k3 ?? 1,
        k4: conf?.k4 ?? 0,
      };
    });
  });

  // ─── Group config toggle (Java: UserController.gc0-gc4) ───
  const gcToggle = (field: string, defaultOff: boolean) => async (req: FastifyRequest) => {
    const qid = getQid(req);
    const tid = (req.query as any)?.tid ?? (req.body as any)?.tid;
    if (!tid) return false;
    try {
      const existing = db.prepare('SELECT * FROM group_conf WHERE qid = ? AND tid = ?').get(qid, tid) as GroupConf | undefined;
      if (existing) {
        const current = (existing as any)[field] ?? (defaultOff ? 0 : 1);
        const newVal = current ? 0 : 1;
        db.prepare(`UPDATE group_conf SET ${field} = ? WHERE qid = ? AND tid = ?`).run(newVal, qid, tid);
      } else {
        const gc: any = { qid, tid, k0: 1, k1: 1, k2: 1, k3: 1, k4: 0 };
        gc[field] = defaultOff ? 1 : 0;
        db.prepare('INSERT INTO group_conf (qid, tid, k0, k1, k2, k3, k4) VALUES (?, ?, ?, ?, ?, ?, ?)')
          .run(gc.qid, gc.tid, gc.k0, gc.k1, gc.k2, gc.k3, gc.k4);
      }
      return true;
    } catch {
      return false;
    }
  };

  app.all('/api/gc0', gcToggle('k0', false));
  app.all('/api/gc1', gcToggle('k1', false));
  app.all('/api/gc2', gcToggle('k2', false));
  app.all('/api/gc3', gcToggle('k3', false));
  app.all('/api/gc4', gcToggle('k4', true));

  // ─── V11 config (Java: UserV11Controller) ───
  app.all('/api/v11/get-conf', async (req) => {
    const qid = getQid(req);
    let conf = db.prepare('SELECT * FROM v11_conf WHERE qid = ?').get(qid) as V11Conf | undefined;
    if (!conf) {
      db.prepare('INSERT INTO v11_conf (qid) VALUES (?)').run(qid);
      conf = db.prepare('SELECT * FROM v11_conf WHERE qid = ?').get(qid) as V11Conf;
    }
    return conf;
  });

  app.all('/api/v11/modify-conf', async (req) => {
    const qid = getQid(req);
    const key = (req.query as any)?.key ?? (req.body as any)?.key;
    const value = (req.query as any)?.value ?? (req.body as any)?.value;
    if (!key) return null;

    let conf = db.prepare('SELECT * FROM v11_conf WHERE qid = ?').get(qid) as V11Conf | undefined;
    if (!conf) {
      db.prepare('INSERT INTO v11_conf (qid) VALUES (?)').run(qid);
    }

    const fieldMap: Record<string, string> = {
      autoLike: 'auto_like',
      needMaxLike: 'need_max_like',
      autoLikeYesterday: 'auto_like_yesterday',
      autoZoneLike: 'auto_zone_like',
      signGroups: 'sign_groups',
      zoneComment: 'zone_comment',
      zoneWalks: 'zone_walks',
      likeBlack: 'like_black',
      likeWhite: 'like_white',
      zoneEvl: 'zone_evl',
    };

    const dbField = fieldMap[key];
    if (!dbField) return null;

    const boolFields = ['auto_like', 'need_max_like', 'auto_like_yesterday', 'auto_zone_like'];
    const intFields = ['zone_evl'];
    let dbValue: any = value;
    if (boolFields.includes(dbField)) {
      dbValue = value === 'true' || value === true ? 1 : 0;
    } else if (intFields.includes(dbField)) {
      dbValue = parseInt(value) || 0;
    }

    db.prepare(`UPDATE v11_conf SET ${dbField} = ? WHERE qid = ?`).run(dbValue, qid);
    return db.prepare('SELECT * FROM v11_conf WHERE qid = ?').get(qid);
  });

  app.all('/api/v11/getGroups', async (req) => {
    const qid = getQid(req);
    const bot = botManager.getBot(qid);
    if (bot?.isConnected) {
      const groups = bot?.info?.groups ?? [];
      return groups.map((g: any) => ({
        id: g.id,
        name: g.name,
        icon: g.icon ?? `https://p.qlogo.cn/gh/${g.id}/${g.id}/640`,
      }));
    }
    return '未在线';
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
