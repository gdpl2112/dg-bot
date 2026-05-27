import { handlePassive } from './passive';
import { handleCallApi } from './callapi';
import { handleSaveMessage, handleRecall } from './save';
import { scriptEngine } from './script';
import { handleOptionals } from '../optional';
import { logger } from '../utils/logger';
import type { OneBotEvent } from '../model/types';

export function dispatchEvent(botId: string, event: OneBotEvent): void {
  try {
    // save all messages
    handleSaveMessage(botId, event);

    // recall monitoring
    handleRecall(botId, event);

    // keyword reply
    handlePassive(botId, event);

    // API call templates
    handleCallApi(botId, event);

    // optional features (song, AI, etc.)
    handleOptionals(botId, event);

    // user scripts
    scriptEngine.handleEvent(botId, event);
  } catch (e) {
    logger.error(`Event dispatch error for bot ${botId}: ${e}`);
  }
}
