import { getDb } from '../database';
import { logger } from '../utils/logger';
import type { OneBotEvent, GroupConf } from '../model/types';

export function handleSaveMessage(botId: string, event: OneBotEvent): void {
  if (event.post_type !== 'message') return;

  const db = getDb();
  const tid = event.message_type === 'group'
    ? `g${event.group_id}`
    : `f${event.user_id}`;

  const type = event.message_type === 'group' ? 'group' : 'friend';
  const fromId = event.message_type === 'group' ? event.group_id : event.user_id;

  try {
    db.prepare(`
      INSERT INTO all_message (time, id, internal_id, sender_id, bot_id, type, from_id, content, recalled)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
    `).run(
      Date.now(),
      event.message_id ?? 0,
      0,
      event.user_id ?? 0,
      parseInt(botId),
      type,
      fromId ?? 0,
      event.raw_message ?? '',
    );
  } catch (e) {
    logger.error(`Save message error: ${e}`);
  }

  // update statistics
  try {
    const statType = event.message_type === 'group' ? 'GROUP' : 'PRIVATE';
    const existing = db.prepare(
      'SELECT * FROM statistics WHERE account = ? AND type = ?'
    ).get(botId, statType) as { count: number } | undefined;
    if (existing) {
      db.prepare('UPDATE statistics SET count = count + 1 WHERE account = ? AND type = ?')
        .run(botId, statType);
    } else {
      db.prepare('INSERT INTO statistics (count, account, type) VALUES (1, ?, ?)')
        .run(botId, statType);
    }
  } catch (e) {
    logger.error(`Statistics update error: ${e}`);
  }
}

export function handleRecall(botId: string, event: OneBotEvent): void {
  if (event.post_type !== 'notice' || event.notice_type !== 'group_recall') return;

  const tid = `g${event.group_id}`;
  if (isNotOpenK1(botId, tid)) return;

  const db = getDb();
  try {
    const messageId = (event as any).message_id;
    if (messageId) {
      db.prepare('UPDATE all_message SET recalled = 1 WHERE id = ? AND bot_id = ?')
        .run(messageId, parseInt(botId));
    }
  } catch (e) {
    logger.error(`Recall handling error: ${e}`);
  }
}

function isNotOpenK1(bid: string, tid: string): boolean {
  const db = getDb();
  const gc = db.prepare(
    'SELECT * FROM group_conf WHERE qid = ? AND tid = ?'
  ).get(bid, tid) as GroupConf | undefined;
  if (gc && gc.k1 === 0) return true;
  return false;
}
