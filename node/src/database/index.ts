import Database from 'better-sqlite3';
import path from 'path';
import { logger } from '../utils/logger';

let db: Database.Database;

export function getDb(): Database.Database {
  if (!db) throw new Error('Database not initialized');
  return db;
}

export function initDatabase(dbPath: string): Database.Database {
  const resolved = path.resolve(dbPath);
  db = new Database(resolved);
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');
  createTables();
  migrateColumns();
  logger.info(`Database initialized: ${resolved}`);
  return db;
}

function createTables(): void {
  db.exec(`
    CREATE TABLE IF NOT EXISTS administrator (
      qid TEXT NOT NULL,
      target_id TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS all_message (
      time INTEGER,
      id INTEGER,
      internal_id INTEGER,
      sender_id INTEGER,
      bot_id INTEGER,
      type TEXT,
      from_id INTEGER,
      content TEXT,
      recalled INTEGER DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS auth_m (
      qid TEXT PRIMARY KEY,
      auth TEXT,
      exp INTEGER,
      t0 INTEGER
    );

    CREATE TABLE IF NOT EXISTS call_template (
      qid TEXT,
      touch TEXT,
      url TEXT,
      out TEXT,
      out_args TEXT,
      jude TEXT,
      err TEXT DEFAULT '调用失败'
    );

    CREATE TABLE IF NOT EXISTS conf (
      qid TEXT PRIMARY KEY,
      cd0 INTEGER DEFAULT 1,
      retell TEXT DEFAULT '复述',
      open0 TEXT DEFAULT '开启回复',
      close0 TEXT DEFAULT '关闭回复',
      open1 TEXT DEFAULT '开启调用',
      close1 TEXT DEFAULT '关闭调用',
      add0 TEXT DEFAULT '添加',
      cancel0 TEXT DEFAULT '取消',
      select0 TEXT DEFAULT '查询',
      del0 TEXT DEFAULT '删词',
      rsid TEXT DEFAULT '',
      nu TEXT DEFAULT '',
      code TEXT DEFAULT '',
      status0 TEXT DEFAULT '查看状态'
    );

    CREATE TABLE IF NOT EXISTS ai_conf (
      qid TEXT PRIMARY KEY,
      open INTEGER DEFAULT 0,
      prefix TEXT DEFAULT 'AI',
      api_key TEXT DEFAULT '',
      base_url TEXT DEFAULT 'https://ai.kloping.top',
      model_id TEXT DEFAULT 'gpt-5.4-mini',
      temperature REAL DEFAULT 0.7,
      network INTEGER DEFAULT 0,
      name TEXT DEFAULT '小生AI',
      trait TEXT DEFAULT '乖巧,可爱',
      max_message INTEGER DEFAULT 10
    );

    CREATE TABLE IF NOT EXISTS conn_config (
      qid TEXT PRIMARY KEY,
      ip TEXT,
      port INTEGER,
      type TEXT,
      token TEXT,
      heart INTEGER
    );

    CREATE TABLE IF NOT EXISTS cron_message (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      qid TEXT,
      desc TEXT,
      cron TEXT,
      target_id TEXT,
      msg TEXT
    );

    CREATE TABLE IF NOT EXISTS group_conf (
      qid TEXT,
      tid TEXT,
      k0 INTEGER DEFAULT 1,
      k1 INTEGER DEFAULT 1,
      k2 INTEGER DEFAULT 1,
      k3 INTEGER DEFAULT 1,
      k4 INTEGER DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS like_reco (
      bid TEXT,
      date TEXT,
      tid TEXT
    );

    CREATE TABLE IF NOT EXISTS optional (
      qid TEXT,
      opt TEXT,
      open INTEGER
    );

    CREATE TABLE IF NOT EXISTS passive (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      qid TEXT,
      touch TEXT,
      out TEXT
    );

    CREATE TABLE IF NOT EXISTS statistics (
      count INTEGER DEFAULT 0,
      account TEXT,
      type TEXT DEFAULT 'PRIVATE'
    );

    CREATE TABLE IF NOT EXISTS v11_conf (
      qid TEXT PRIMARY KEY,
      auto_like INTEGER DEFAULT 0,
      need_max_like INTEGER DEFAULT 0,
      auto_like_yesterday INTEGER DEFAULT 0,
      like_black TEXT DEFAULT '',
      like_white TEXT DEFAULT '',
      sign_groups TEXT DEFAULT '',
      zone_evl INTEGER DEFAULT 0,
      auto_zone_like INTEGER DEFAULT 0,
      zone_comment TEXT DEFAULT '',
      zone_walks TEXT DEFAULT ''
    );
  `);
  logger.info('Tables created/verified');
}

function migrateColumns(): void {
  const safeAddColumn = (table: string, column: string, type: string, defaultVal: string) => {
    try {
      const cols = db.prepare(`PRAGMA table_info('${table}')`).all() as { name: string }[];
      if (!cols.some(c => c.name === column)) {
        db.exec(`ALTER TABLE ${table} ADD ${column} ${type} DEFAULT ${defaultVal};`);
        logger.info(`Added column ${table}.${column}`);
      }
    } catch (e) {
      // column likely already exists
    }
  };

  safeAddColumn('v11_conf', 'like_black', 'TEXT', "''");
  safeAddColumn('v11_conf', 'like_white', 'TEXT', "''");
  safeAddColumn('v11_conf', 'zone_walks', 'TEXT', "''");
}
