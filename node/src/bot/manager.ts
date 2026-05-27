import { OneBotClient } from './onebot';
import { getDb } from '../database';
import { logger } from '../utils/logger';
import type { ConnConfig, BotInfo, OneBotEvent } from '../model/types';

export class BotManager {
  private bots = new Map<string, OneBotClient>();
  private eventHandlers: Array<(botId: string, event: OneBotEvent) => void> = [];

  getBot(qid: string): OneBotClient | undefined {
    return this.bots.get(qid);
  }

  getAllBots(): OneBotClient[] {
    return Array.from(this.bots.values());
  }

  getBotInfoList(): BotInfo[] {
    return this.getAllBots().map(b => b.info ?? {
      id: parseInt(b.botId),
      nick: '',
      online: b.isConnected,
    });
  }

  onEvent(handler: (botId: string, event: OneBotEvent) => void): void {
    this.eventHandlers.push(handler);
  }

  addBot(config: ConnConfig): OneBotClient {
    if (this.bots.has(config.qid)) {
      this.removeBot(config.qid);
    }
    const client = new OneBotClient(config);
    client.on('event', (event: OneBotEvent) => {
      for (const handler of this.eventHandlers) {
        try {
          handler(config.qid, event);
        } catch (e) {
          logger.error(`Event handler error for bot ${config.qid}: ${e}`);
        }
      }
    });
    this.bots.set(config.qid, client);
    client.connect();
    return client;
  }

  removeBot(qid: string): void {
    const bot = this.bots.get(qid);
    if (bot) {
      bot.close();
      this.bots.delete(qid);
      logger.info(`Bot ${qid} removed`);
    }
  }

  loadFromDb(): void {
    const db = getDb();
    const configs = db.prepare('SELECT * FROM conn_config').all() as ConnConfig[];
    for (const config of configs) {
      try {
        this.addBot(config);
      } catch (e) {
        logger.error(`Failed to load bot ${config.qid}: ${e}`);
      }
    }
    logger.info(`Loaded ${configs.length} bot(s) from database`);
  }

  closeAll(): void {
    for (const [qid, bot] of this.bots) {
      bot.close();
    }
    this.bots.clear();
  }
}

export const botManager = new BotManager();
