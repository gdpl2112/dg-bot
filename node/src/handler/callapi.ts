import { getDb } from '../database';
import { botManager } from '../bot/manager';
import { logger } from '../utils/logger';
import type { CallTemplate, GroupConf, OneBotEvent } from '../model/types';

export function handleCallApi(botId: string, event: OneBotEvent): void {
  if (event.post_type !== 'message') return;
  const rawMsg = event.raw_message ?? '';
  if (!rawMsg) return;

  const bid = botId;
  const tid = event.message_type === 'group'
    ? `g${event.group_id}`
    : `f${event.user_id}`;

  if (isNotOpenK0(bid, tid)) return;

  const parts = rawMsg.split(/[\s,，]+/);
  if (parts.length === 0) return;
  const touch = parts[0];
  if (!touch) return;
  const args = parts.slice(1);

  const db = getDb();
  let template = db.prepare(
    'SELECT * FROM call_template WHERE qid = ? AND touch = ?'
  ).get(bid, touch) as CallTemplate | undefined;

  if (!template) return;

  executeCallTemplate(template, botId, event, args).catch(e => {
    logger.error(`CallApi error: ${e}`);
  });
}

async function executeCallTemplate(
  template: CallTemplate,
  botId: string,
  event: OneBotEvent,
  args: string[]
): Promise<void> {
  let url = template.url;

  // replace $1, $2, etc.
  for (let i = 0; i < args.length; i++) {
    url = url.replace(`$${i + 1}`, encodeURIComponent(args[i]));
  }
  // replace $number (sender id)
  url = url.replace(/\$number/g, String(event.user_id ?? 0));
  url = url.replace(/\$numberOrSelf/g, String(event.user_id ?? event.self_id ?? 0));

  try {
    const resp = await fetch(url);
    const body = await resp.text();

    let outMsg = template.out || body;
    // substitute placeholders in output template
    if (template.out_args) {
      try {
        const jsonBody = JSON.parse(body);
        const outParts = template.out_args.split(',');
        for (const part of outParts) {
          const key = part.trim();
          if (key && jsonBody[key] !== undefined) {
            outMsg = outMsg.replace(`{${key}}`, String(jsonBody[key]));
          }
        }
      } catch {
        // not JSON, use raw body
      }
    }

    // check condition (jude)
    if (template.jude) {
      try {
        const jsonBody = JSON.parse(body);
        const fn = new Function('data', `return ${template.jude}`);
        if (!fn(jsonBody)) {
          outMsg = template.err;
        }
      } catch {
        // condition check failed
      }
    }

    const bot = botManager.getBot(botId);
    if (!bot) return;
    const targetId = event.message_type === 'group' ? event.group_id! : event.user_id!;
    const targetType = event.message_type === 'group' ? 'group' : 'private' as const;
    await bot.sendMessage(targetType, targetId, outMsg);
  } catch (e) {
    const bot = botManager.getBot(botId);
    if (bot) {
      const targetId = event.message_type === 'group' ? event.group_id! : event.user_id!;
      const targetType = event.message_type === 'group' ? 'group' : 'private' as const;
      await bot.sendMessage(targetType, targetId, template.err);
    }
  }
}

function isNotOpenK0(bid: string, tid: string): boolean {
  const db = getDb();
  const gc = db.prepare(
    'SELECT * FROM group_conf WHERE qid = ? AND tid = ?'
  ).get(bid, tid) as GroupConf | undefined;
  if (gc && gc.k0 === 0) return true;
  return false;
}
