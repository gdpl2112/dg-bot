import { loadConfig } from './config';
import { initDatabase } from './database';
import { botManager } from './bot/manager';
import { dispatchEvent } from './handler/event-dispatcher';
import { loadCronTasks, stopAllCronTasks } from './handler/cron';
import { createServer } from './web/router';
import { logger } from './utils/logger';

async function main(): Promise<void> {
  logger.info('DG-Bot Node.js starting...');

  // load config
  const profile = process.argv.find(a => a.startsWith('--profile='))?.split('=')[1]
    ?? process.env.PROFILE;
  const config = loadConfig(profile);
  logger.info(`Config loaded (port=${config.server.port}, super=${config.super.qid})`);

  // init database
  initDatabase(config.database.path);

  // register event dispatcher
  botManager.onEvent((botId, event) => {
    dispatchEvent(botId, event);
  });

  // load bots from database
  botManager.loadFromDb();

  // load cron tasks
  loadCronTasks();

  // start web server
  await createServer(
    config.server.port,
    config.super.qid,
    config.manage.key,
    config.manage.bid,
  );

  logger.info('DG-Bot Node.js started successfully');
  logMemoryUsage();

  // periodic memory logging
  setInterval(logMemoryUsage, 5 * 60 * 1000);

  // graceful shutdown
  const shutdown = () => {
    logger.info('Shutting down...');
    stopAllCronTasks();
    botManager.closeAll();
    process.exit(0);
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

function logMemoryUsage(): void {
  const mem = process.memoryUsage();
  logger.info(
    `Memory: RSS=${(mem.rss / 1024 / 1024).toFixed(1)}MB, Heap=${(mem.heapUsed / 1024 / 1024).toFixed(1)}/${(mem.heapTotal / 1024 / 1024).toFixed(1)}MB`
  );
}

main().catch(e => {
  logger.error(`Startup error: ${e}`);
  process.exit(1);
});
