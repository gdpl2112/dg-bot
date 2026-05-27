import OpenAI from 'openai';
import { getDb } from '../database';
import { botManager } from '../bot/manager';
import { logger } from '../utils/logger';
import type { OneBotEvent, AiConf } from '../model/types';

// bid -> conversation history
const conversations = new Map<string, Array<{ role: string; content: string }>>();

export function handleAi(botId: string, event: OneBotEvent): void {
  if (event.post_type !== 'message') return;
  const rawMsg = event.raw_message ?? '';
  if (!rawMsg) return;

  const db = getDb();
  const aiConf = db.prepare('SELECT * FROM ai_conf WHERE qid = ?').get(botId) as AiConf | undefined;
  if (!aiConf || !aiConf.open) return;

  const prefix = aiConf.prefix || 'AI';
  if (!rawMsg.startsWith(prefix)) return;

  const userMsg = rawMsg.substring(prefix.length).trim();
  if (!userMsg) return;

  processAiMessage(botId, event, aiConf, userMsg).catch(e => {
    logger.error(`AI error for bot ${botId}: ${e}`);
    reply(botId, event, 'AI请求失败，请稍后重试');
  });
}

async function processAiMessage(botId: string, event: OneBotEvent, aiConf: AiConf, userMsg: string): Promise<void> {
  const client = new OpenAI({
    apiKey: aiConf.api_key || 'placeholder',
    baseURL: aiConf.base_url || 'https://ai.kloping.top',
  });

  const convKey = `${botId}:${event.user_id}`;
  let history = conversations.get(convKey) ?? [];

  history.push({ role: 'user', content: userMsg });

  // trim to max messages
  const maxMsg = aiConf.max_message || 10;
  if (history.length > maxMsg * 2) {
    history = history.slice(-maxMsg * 2);
    conversations.set(convKey, history);
  }

  const systemPrompt = `你是${aiConf.name || '小生AI'}，性格${aiConf.trait || '乖巧,可爱'}。`;

  try {
    const resp = await client.chat.completions.create({
      model: aiConf.model_id || 'gpt-5.4-mini',
      temperature: aiConf.temperature ?? 0.7,
      messages: [
        { role: 'system', content: systemPrompt },
        ...history.map(m => ({ role: m.role as 'user' | 'assistant', content: m.content })),
      ],
    });

    const answer = resp.choices?.[0]?.message?.content ?? '无回复';
    history.push({ role: 'assistant', content: answer });
    conversations.set(convKey, history);

    reply(botId, event, answer);
  } catch (e) {
    throw e;
  }
}

function reply(botId: string, event: OneBotEvent, text: string): void {
  const bot = botManager.getBot(botId);
  if (!bot) return;
  const targetId = event.message_type === 'group' ? event.group_id! : event.user_id!;
  const targetType = event.message_type === 'group' ? 'group' : 'private' as const;
  bot.sendMessage(targetType, targetId, text).catch(e => logger.error(`AI reply error: ${e}`));
}
