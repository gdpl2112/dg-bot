import vm from 'vm';
import { botManager } from '../bot/manager';
import { getDb } from '../database';
import { logger } from '../utils/logger';
import type { Conf, OneBotEvent } from '../model/types';

interface ScriptContext {
  context: vm.Context;
  code: string;
}

interface ScriptException {
  msg: string;
  time: number;
  qid: string;
}

class ScriptEngine {
  private contexts = new Map<string, ScriptContext>();
  private exceptions = new Map<string, ScriptException>();
  private printMap = new Map<string, string[]>();

  getOrCreate(bid: string): ScriptContext | null {
    if (this.contexts.has(bid)) return this.contexts.get(bid)!;

    const db = getDb();
    const conf = db.prepare('SELECT * FROM conf WHERE qid = ?').get(bid) as Conf | undefined;
    if (!conf?.code) return null;

    try {
      const ctx = this.createContext(bid);
      const script = new vm.Script(conf.code, { filename: `bot-${bid}.js` });
      script.runInContext(ctx.context);
      this.contexts.set(bid, { context: ctx.context, code: conf.code });
      return this.contexts.get(bid)!;
    } catch (e) {
      this.onException(bid, e as Error);
      return null;
    }
  }

  clearCache(bid: string): void {
    this.contexts.delete(bid);
  }

  handleEvent(botId: string, event: OneBotEvent): void {
    const sc = this.getOrCreate(botId);
    if (!sc) return;

    try {
      if (event.post_type === 'message') {
        this.callFunction(sc, 'onMsgEvent', [event.raw_message ?? '', event]);
      } else {
        this.callFunction(sc, 'onBotEvent', [event]);
      }
    } catch (e) {
      this.onException(botId, e as Error);
    }
  }

  executeFunction(bid: string, funcName: string, args: any[] = []): any {
    const sc = this.getOrCreate(bid);
    if (!sc) return undefined;
    return this.callFunction(sc, funcName, args);
  }

  getException(bid: string): ScriptException | undefined {
    return this.exceptions.get(bid);
  }

  getPrintLogs(bid: string): string[] {
    return this.printMap.get(bid) ?? [];
  }

  private createContext(bid: string): ScriptContext {
    const bot = botManager.getBot(bid);
    const prints: string[] = [];
    this.printMap.set(bid, prints);

    const utils = {
      sendGroupMsg: (groupId: number, msg: string) => bot?.sendGroupMsg(groupId, msg),
      sendPrivateMsg: (userId: number, msg: string) => bot?.sendPrivateMsg(userId, msg),
      sendMsg: (targetType: 'private' | 'group', targetId: number, msg: string) =>
        bot?.sendMessage(targetType, targetId, msg),
      callApi: (action: string, params: Record<string, any>) => bot?.callApi(action, params),
      httpGet: async (url: string) => {
        const resp = await fetch(url);
        return resp.text();
      },
      httpGetJson: async (url: string) => {
        const resp = await fetch(url);
        return resp.json();
      },
      httpPost: async (url: string, body: string, contentType?: string) => {
        const resp = await fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': contentType ?? 'application/json' },
          body,
        });
        return resp.text();
      },
      log: (...args: any[]) => {
        const line = args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' ');
        prints.push(line);
        if (prints.length > 30) prints.shift();
      },
    };

    const sandbox = {
      utils,
      console: {
        log: utils.log,
        error: utils.log,
        warn: utils.log,
        info: utils.log,
      },
      setTimeout,
      setInterval,
      clearTimeout,
      clearInterval,
      fetch,
      JSON,
      Math,
      Date,
      RegExp,
      Array,
      Object,
      String,
      Number,
      Boolean,
      parseInt,
      parseFloat,
      isNaN,
      isFinite,
      encodeURIComponent,
      decodeURIComponent,
      encodeURI,
      decodeURI,
    };

    const context = vm.createContext(sandbox, {
      name: `bot-${bid}`,
    });

    return { context, code: '' };
  }

  private callFunction(sc: ScriptContext, funcName: string, args: any[]): any {
    try {
      const check = new vm.Script(`typeof ${funcName} === 'function'`);
      const exists = check.runInContext(sc.context);
      if (!exists) return undefined;

      const argsStr = args.map((_, i) => `__arg${i}`).join(',');
      for (let i = 0; i < args.length; i++) {
        sc.context[`__arg${i}`] = args[i];
      }
      const call = new vm.Script(`${funcName}(${argsStr})`);
      return call.runInContext(sc.context);
    } catch (e) {
      logger.error(`Script function ${funcName} error: ${e}`);
      return undefined;
    }
  }

  private onException(bid: string, e: Error): void {
    logger.error(`Script exception bid:${bid}: ${e.message}`);
    this.exceptions.set(bid, {
      msg: e.message + '\n' + (e.stack ?? ''),
      time: Date.now(),
      qid: bid,
    });
  }
}

export const scriptEngine = new ScriptEngine();
