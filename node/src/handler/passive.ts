import { getDb } from '../database';
import { botManager } from '../bot/manager';
import { logger } from '../utils/logger';
import type { Passive, Conf, GroupConf, OneBotEvent } from '../model/types';

const cdMap = new Map<string, number>(); // `${bid}:${tid}` -> expiry timestamp

export function handlePassive(botId: string, event: OneBotEvent): void {
  if (event.post_type !== 'message') return;
  const rawMsg = event.raw_message ?? '';
  if (!rawMsg) return;

  const bid = botId;
  const tid = event.message_type === 'group'
    ? `g${event.group_id}`
    : `f${event.user_id}`;

  if (isNotOpenK2(bid, tid)) return;

  const cdKey = `${bid}:${tid}`;
  const now = Date.now();
  const cdExpiry = cdMap.get(cdKey) ?? 0;
  if (now < cdExpiry) return;

  const db = getDb();

  // exact match first
  let passives = db.prepare(
    'SELECT * FROM passive WHERE qid = ? AND touch = ?'
  ).all(bid, rawMsg.trim()) as Passive[];

  // regex match fallback
  if (!passives.length) {
    const allPassives = db.prepare(
      'SELECT * FROM passive WHERE qid = ?'
    ).all(bid) as Passive[];
    passives = allPassives.filter(p => {
      try {
        return new RegExp(p.touch).test(rawMsg);
      } catch { return false; }
    });
  }

  if (!passives.length) return;

  const chosen = passives[Math.floor(Math.random() * passives.length)];
  if (!chosen.out) return;

  const bot = botManager.getBot(bid);
  if (!bot) return;

  try {
    const targetId = event.message_type === 'group' ? event.group_id! : event.user_id!;
    const targetType = event.message_type === 'group' ? 'group' : 'private' as const;
    bot.sendMessage(targetType, targetId, chosen.out);
  } catch (e) {
    logger.error(`Passive send error bid:${bid}: ${e}`);
  }

  // update cooldown
  const conf = db.prepare('SELECT * FROM conf WHERE qid = ?').get(bid) as Conf | undefined;
  const cd = (conf?.cd0 ?? 1) * 1000;
  cdMap.set(cdKey, now + cd);
}

function isNotOpenK2(bid: string, tid: string): boolean {
  const db = getDb();
  const gc = db.prepare(
    'SELECT * FROM group_conf WHERE qid = ? AND tid = ?'
  ).get(bid, tid) as GroupConf | undefined;
  if (gc && gc.k2 === 0) return true;
  return false;
}
