import { getDb } from '../database';
import { handleSong } from './song';
import { handleAi } from './ai';
import { logger } from '../utils/logger';
import type { OneBotEvent, Optional, GroupConf } from '../model/types';

interface OptionalFeature {
  name: string;
  desc: string;
  handler: (botId: string, event: OneBotEvent) => void;
}

const features: OptionalFeature[] = [
  { name: 'SongPoint', desc: '包含[点歌,QQ点歌,酷狗点歌,网易点歌,取消点歌,取消选择] 功能', handler: handleSong },
  { name: 'AIAssistantOptional', desc: 'AI助手 支持OpenAI兼容API', handler: handleAi },
];

export function handleOptionals(botId: string, event: OneBotEvent): void {
  if (event.post_type !== 'message') return;

  const tid = event.message_type === 'group'
    ? `g${event.group_id}`
    : `f${event.user_id}`;
  if (isNotOpenK3(botId, tid)) return;

  for (const feature of features) {
    if (isFeatureEnabled(botId, feature.name)) {
      try {
        feature.handler(botId, event);
      } catch (e) {
        logger.error(`Optional ${feature.name} error: ${e}`);
      }
    }
  }
}

export function getOptionalDtos(qid: string): Array<{ name: string; desc: string; open: boolean }> {
  const db = getDb();
  return features.map(f => {
    const opt = db.prepare(
      'SELECT * FROM optional WHERE qid = ? AND opt = ?'
    ).get(qid, f.name) as Optional | undefined;
    return { name: f.name, desc: f.desc, open: !!opt?.open };
  });
}

function isFeatureEnabled(bid: string, featureName: string): boolean {
  const db = getDb();
  const opt = db.prepare(
    'SELECT * FROM optional WHERE qid = ? AND opt = ?'
  ).get(bid, featureName) as Optional | undefined;
  return !!opt?.open;
}

function isNotOpenK3(bid: string, tid: string): boolean {
  const db = getDb();
  const gc = db.prepare(
    'SELECT * FROM group_conf WHERE qid = ? AND tid = ?'
  ).get(bid, tid) as GroupConf | undefined;
  if (gc && gc.k3 === 0) return true;
  return false;
}
