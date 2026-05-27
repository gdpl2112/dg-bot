import { botManager } from '../bot/manager';
import { logger } from '../utils/logger';
import type { OneBotEvent } from '../model/types';

interface SongData {
  page: number;
  keyword: string;
  type: string;
  data: any;
  qid: number;
  time: number;
}

const songSessions = new Map<number, SongData>();
const MAX_CD = 30 * 60 * 1000;

export function handleSong(botId: string, event: OneBotEvent): void {
  const rawMsg = event.raw_message ?? '';
  const userId = event.user_id ?? 0;

  // clean expired sessions
  const now = Date.now();
  for (const [uid, data] of songSessions) {
    if (now - data.time > MAX_CD) songSessions.delete(uid);
  }

  if (rawMsg === '取消点歌' || rawMsg === '取消选择') {
    if (songSessions.has(userId)) {
      songSessions.delete(userId);
      reply(botId, event, '已取消选择');
    }
    return;
  }

  // check if user is selecting a song
  const session = songSessions.get(userId);
  if (session) {
    const num = parseInt(rawMsg);
    if (!isNaN(num)) {
      if (num === 0) {
        searchSongs(botId, event, session.keyword, session.page + 1, session.type);
      } else {
        selectSong(botId, event, session, num);
      }
      return;
    }
  }

  // song search commands
  const commands: Record<string, string> = {
    '点歌': 'qq',
    'QQ点歌': 'qq',
    'qq点歌': 'qq',
    '网易点歌': 'wy',
    '酷狗点歌': 'kg',
  };

  for (const [cmd, type] of Object.entries(commands)) {
    if (rawMsg.startsWith(cmd)) {
      const keyword = rawMsg.substring(cmd.length).trim();
      if (keyword) {
        searchSongs(botId, event, keyword, 1, type);
      } else {
        reply(botId, event, `请输入歌名，例如: ${cmd} 青花瓷`);
      }
      return;
    }
  }
}

async function searchSongs(botId: string, event: OneBotEvent, keyword: string, page: number, type: string): Promise<void> {
  const userId = event.user_id ?? 0;
  try {
    let results: string;
    if (type === 'qq') {
      const url = `https://zj.v.api.aa1.cn/api/qqmusic/demo.php?type=1&q=${encodeURIComponent(keyword)}&p=${page}&n=10`;
      const resp = await fetch(url);
      const data = await resp.json() as Record<string, any>;
      const lines = [`歌名:${keyword},页数:${page},总数:${data.count ?? '?'}`];
      const list = (data.list ?? []) as any[];
      list.forEach((item: any, i: number) => {
        lines.push(`${i + 1}.${item.name}--${item.singer}`);
      });
      lines.push('选择歌曲前数字.选择0时进入下一页');
      lines.push("使用'取消点歌'/'取消选择'来取消选择");
      results = lines.join('\n');
      songSessions.set(userId, { page, keyword, type, data, qid: userId, time: Date.now() });
    } else if (type === 'kg') {
      const url = `http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=${encodeURIComponent(keyword)}&page=${page}&pagesize=10`;
      const resp = await fetch(url);
      const data = await resp.json() as Record<string, any>;
      const info = (data?.data?.info ?? []) as any[];
      const lines = [`歌名:${keyword},页数:${page}`];
      info.forEach((item: any, i: number) => {
        lines.push(`${i + 1}.${item.songname}--${item.singername}`);
      });
      lines.push('选择歌曲前数字.选择0时进入下一页');
      lines.push("使用'取消点歌'/'取消选择'来取消选择");
      results = lines.join('\n');
      songSessions.set(userId, { page, keyword, type, data, qid: userId, time: Date.now() });
    } else {
      results = '暂不支持该平台';
    }
    reply(botId, event, results);
  } catch (e) {
    logger.error(`Song search error: ${e}`);
    reply(botId, event, '搜索失败，请稍后重试');
  }
}

async function selectSong(botId: string, event: OneBotEvent, session: SongData, num: number): Promise<void> {
  const userId = event.user_id ?? 0;
  try {
    if (session.type === 'qq') {
      const list = session.data?.list ?? [];
      if (num < 1 || num > list.length) {
        reply(botId, event, '序号超出范围');
        return;
      }
      const item = list[num - 1];
      const bot = botManager.getBot(botId);
      if (!bot) return;

      const musicMsg = [{
        type: 'music',
        data: { type: 'qq', id: item.songid ?? item.id ?? '' },
      }];
      const targetId = event.message_type === 'group' ? event.group_id! : event.user_id!;
      const targetType = event.message_type === 'group' ? 'group' : 'private' as const;
      await bot.sendMessage(targetType, targetId, musicMsg);
    } else {
      reply(botId, event, '已选择');
    }
    songSessions.delete(userId);
  } catch (e) {
    logger.error(`Song select error: ${e}`);
    reply(botId, event, '点歌失败');
  }
}

function reply(botId: string, event: OneBotEvent, text: string): void {
  const bot = botManager.getBot(botId);
  if (!bot) return;
  const targetId = event.message_type === 'group' ? event.group_id! : event.user_id!;
  const targetType = event.message_type === 'group' ? 'group' : 'private' as const;
  bot.sendMessage(targetType, targetId, text).catch(e => logger.error(`Reply error: ${e}`));
}
