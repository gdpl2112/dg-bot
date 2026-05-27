import WebSocket from 'ws';
import { EventEmitter } from 'events';
import { logger } from '../utils/logger';
import type { ConnConfig, OneBotEvent, BotInfo } from '../model/types';

export class OneBotClient extends EventEmitter {
  private ws: WebSocket | null = null;
  private config: ConnConfig;
  private retryCount = 0;
  private maxRetry = 3;
  private retryDelay = 7000;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private closed = false;
  private _info: BotInfo | null = null;
  private pendingCalls = new Map<string, { resolve: (v: any) => void; reject: (e: Error) => void; timer: ReturnType<typeof setTimeout> }>();
  private callSeq = 0;

  constructor(config: ConnConfig) {
    super();
    this.config = config;
  }

  get info(): BotInfo | null { return this._info; }
  get botId(): string { return this.config.qid; }
  get isConnected(): boolean { return this.ws?.readyState === WebSocket.OPEN; }

  connect(): void {
    this.closed = false;
    const url = this.config.type === 'ws'
      ? this.config.ip
      : `ws://127.0.0.1:${this.config.port}`;

    logger.info(`Bot ${this.config.qid} connecting to ${url}`);

    this.ws = new WebSocket(url, {
      headers: this.config.token ? { Authorization: `Bearer ${this.config.token}` } : {},
    });

    this.ws.on('open', () => {
      logger.info(`Bot ${this.config.qid} connected`);
      this.retryCount = 0;
      this.startHeartbeat();
      this.fetchBotInfo();
      this.emit('connected');
    });

    this.ws.on('message', (data) => {
      try {
        const event: any = JSON.parse(data.toString());
        if (event.echo) {
          const pending = this.pendingCalls.get(event.echo);
          if (pending) {
            clearTimeout(pending.timer);
            this.pendingCalls.delete(event.echo);
            pending.resolve(event);
          }
          return;
        }
        if (event.post_type) {
          this.emit('event', event as OneBotEvent);
        }
      } catch (e) {
        logger.error(`Bot ${this.config.qid} parse error: ${e}`);
      }
    });

    this.ws.on('close', () => {
      logger.warn(`Bot ${this.config.qid} disconnected`);
      this.stopHeartbeat();
      this.emit('disconnected');
      if (!this.closed) this.scheduleRetry();
    });

    this.ws.on('error', (err) => {
      logger.error(`Bot ${this.config.qid} ws error: ${err.message}`);
    });
  }

  close(): void {
    this.closed = true;
    this.stopHeartbeat();
    for (const [, p] of this.pendingCalls) {
      clearTimeout(p.timer);
      p.reject(new Error('Connection closed'));
    }
    this.pendingCalls.clear();
    this.ws?.close();
    this.ws = null;
    this._info = null;
  }

  async callApi(action: string, params: Record<string, any> = {}): Promise<any> {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      throw new Error(`Bot ${this.config.qid} not connected`);
    }
    const echo = `call_${++this.callSeq}_${Date.now()}`;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingCalls.delete(echo);
        reject(new Error(`API call ${action} timed out`));
      }, 30000);
      this.pendingCalls.set(echo, { resolve, reject, timer });
      this.ws!.send(JSON.stringify({ action, params, echo }));
    });
  }

  async sendMessage(targetType: 'private' | 'group', targetId: number, message: string | any[]): Promise<any> {
    const msgArr = typeof message === 'string'
      ? [{ type: 'text', data: { text: message } }]
      : message;
    return this.callApi('send_msg', {
      message_type: targetType,
      [targetType === 'group' ? 'group_id' : 'user_id']: targetId,
      message: msgArr,
    });
  }

  async sendGroupMsg(groupId: number, message: string | any[]): Promise<any> {
    return this.sendMessage('group', groupId, message);
  }

  async sendPrivateMsg(userId: number, message: string | any[]): Promise<any> {
    return this.sendMessage('private', userId, message);
  }

  private async fetchBotInfo(): Promise<void> {
    try {
      const loginInfo = await this.callApi('get_login_info');
      const friendList = await this.callApi('get_friend_list');
      const groupList = await this.callApi('get_group_list');
      this._info = {
        id: loginInfo?.data?.user_id ?? parseInt(this.config.qid),
        nick: loginInfo?.data?.nickname ?? '',
        online: true,
        friendCount: Array.isArray(friendList?.data) ? friendList.data.length : 0,
        groupCount: Array.isArray(groupList?.data) ? groupList.data.length : 0,
      };
      this.emit('info', this._info);
    } catch (e) {
      logger.error(`Bot ${this.config.qid} fetch info error: ${e}`);
    }
  }

  private startHeartbeat(): void {
    const interval = (this.config.heart || 30) * 1000;
    this.heartbeatTimer = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.ping();
      }
    }, interval);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private scheduleRetry(): void {
    if (this.retryCount >= this.maxRetry) {
      logger.error(`Bot ${this.config.qid} max retries reached`);
      return;
    }
    this.retryCount++;
    logger.info(`Bot ${this.config.qid} retry ${this.retryCount}/${this.maxRetry} in ${this.retryDelay}ms`);
    setTimeout(() => {
      if (!this.closed) this.connect();
    }, this.retryDelay);
  }
}
