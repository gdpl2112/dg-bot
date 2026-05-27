import { CronJob } from 'cron';
import { getDb } from '../database';
import { botManager } from '../bot/manager';
import { logger } from '../utils/logger';
import { scriptEngine } from './script';
import type { CronMessage } from '../model/types';

const cronJobs = new Map<number, CronJob>();

export function loadCronTasks(): void {
  const db = getDb();
  const msgs = db.prepare('SELECT * FROM cron_message').all() as CronMessage[];
  for (const msg of msgs) {
    appendTask(msg);
  }
  logger.info(`Loaded ${msgs.length} cron task(s)`);
}

export function appendTask(msg: CronMessage): void {
  try {
    const job = new CronJob(msg.cron, () => {
      executeCronTask(msg);
    }, null, true);
    cronJobs.set(msg.id, job);
    logger.info(`Cron task ${msg.id}: ${msg.qid} => ${msg.target_id} (${msg.cron})`);
  } catch (e) {
    logger.error(`Failed to create cron task ${msg.id}: ${e}`);
  }
}

export function removeCronTask(id: number): void {
  const job = cronJobs.get(id);
  if (job) {
    job.stop();
    cronJobs.delete(id);
    logger.info(`Cron task ${id} removed`);
  }
}

export function getCronTasksForBot(bid: string): CronMessage[] {
  const db = getDb();
  return db.prepare('SELECT * FROM cron_message WHERE qid = ?').all(bid) as CronMessage[];
}

function executeCronTask(msg: CronMessage): void {
  logger.info(`Executing cron: ${msg.qid} => ${msg.target_id}`);

  if (msg.target_id.endsWith('FUNCTION') || msg.target_id.endsWith('function')) {
    const bid = msg.qid;
    const bot = botManager.getBot(bid);
    if (!bot || !bot.isConnected) {
      logger.warn(`Bot ${bid} not available for cron task`);
      return;
    }
    try {
      scriptEngine.executeFunction(bid, msg.msg);
    } catch (e) {
      logger.error(`Cron script error for bot ${bid}: ${e}`);
    }
  } else {
    sendCronMessage(msg.qid, msg.target_id, msg.msg);
  }
}

function sendCronMessage(qid: string, targetId: string, msg: string): void {
  const bot = botManager.getBot(qid);
  if (!bot || !bot.isConnected) {
    logger.warn(`Bot ${qid} not available for cron message`);
    return;
  }

  try {
    if (targetId.startsWith('g')) {
      const gid = parseInt(targetId.substring(1));
      bot.sendGroupMsg(gid, msg);
    } else {
      let uid: number;
      if (targetId.startsWith('f')) {
        uid = parseInt(targetId.substring(1));
      } else {
        uid = parseInt(targetId);
      }
      bot.sendPrivateMsg(uid, msg);
    }
  } catch (e) {
    logger.error(`Cron send error qid:${qid}: ${e}`);
  }
}

export function stopAllCronTasks(): void {
  for (const [id, job] of cronJobs) {
    job.stop();
  }
  cronJobs.clear();
}
