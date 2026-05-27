// ===== Database entity types (matching SQLite schema) =====

export interface Administrator {
  qid: string;
  target_id: string;
}

export interface AllMessage {
  time: number;
  id: number;
  internal_id: number;
  sender_id: number;
  bot_id: number;
  type: string;
  from_id: number;
  content: string;
  recalled: number;
}

export interface AuthM {
  qid: string;
  auth: string;
  exp: number;
  t0: number;
}

export interface CallTemplate {
  qid: string;
  touch: string;
  url: string;
  out: string;
  out_args: string;
  jude: string;
  err: string;
}

export interface Conf {
  qid: string;
  cd0: number;
  retell: string;
  open0: string;
  close0: string;
  open1: string;
  close1: string;
  add0: string;
  cancel0: string;
  select0: string;
  del0: string;
  rsid: string;
  nu: string;
  code: string;
  status0: string;
}

export interface AiConf {
  qid: string;
  open: number;
  prefix: string;
  api_key: string;
  base_url: string;
  model_id: string;
  temperature: number;
  network: number;
  name: string;
  trait: string;
  max_message: number;
}

export interface ConnConfig {
  qid: string;
  ip: string;
  port: number;
  type: string;
  token: string;
  heart: number;
}

export interface CronMessage {
  id: number;
  qid: string;
  desc: string;
  cron: string;
  target_id: string;
  msg: string;
}

export interface GroupConf {
  qid: string;
  tid: string;
  k0: number;
  k1: number;
  k2: number;
  k3: number;
  k4: number;
}

export interface LikeReco {
  bid: string;
  date: string;
  tid: string;
}

export interface Optional {
  qid: string;
  opt: string;
  open: number;
}

export interface Passive {
  id: number;
  qid: string;
  touch: string;
  out: string;
}

export interface Statistics {
  count: number;
  account: string;
  type: string;
}

export interface V11Conf {
  qid: string;
  auto_like: number;
  need_max_like: number;
  auto_like_yesterday: number;
  like_black: string;
  like_white: string;
  sign_groups: string;
  zone_evl: number;
  auto_zone_like: number;
  zone_comment: string;
  zone_walks: string;
}

// ===== OneBot protocol types =====

export interface OneBotMessage {
  type: string;
  data: Record<string, any>;
}

export interface OneBotEvent {
  time: number;
  self_id: number;
  post_type: string;
  message_type?: string;
  sub_type?: string;
  group_id?: number;
  user_id?: number;
  sender?: {
    user_id: number;
    nickname: string;
    card?: string;
    role?: string;
  };
  message?: OneBotMessage[] | string;
  raw_message?: string;
  message_id?: number;
  notice_type?: string;
  operator_id?: number;
}

// ===== Bot info =====

export interface BotGroupInfo {
  id: number;
  name: string;
  icon?: string;
}

export interface BotFriendInfo {
  id: number;
  nick: string;
  remark?: string;
}

export interface BotInfo {
  id: number;
  nick: string;
  online: boolean;
  avatarUrl?: string;
  friendCount?: number;
  groupCount?: number;
  groups?: BotGroupInfo[];
  friends?: BotFriendInfo[];
}

export interface OptionalDto {
  name: string;
  desc: string;
  open: boolean;
}
