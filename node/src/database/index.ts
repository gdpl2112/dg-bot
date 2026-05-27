import initSqlJs from 'sql.js';
import type { Database as SqlJsDatabase } from 'sql.js';
import fs from 'fs';
import path from 'path';
import { logger } from '../utils/logger';

interface RunResult {
  changes: number;
  lastInsertRowid: number;
}

interface PreparedStatement {
  all(...params: any[]): any[];
  get(...params: any[]): any | undefined;
  run(...params: any[]): RunResult;
}

export interface DatabaseWrapper {
  prepare(sql: string): PreparedStatement;
  exec(sql: string): void;
  pragma(pragma: string): any[];
}

let wrapper: DatabaseWrapper;
let rawDb: SqlJsDatabase;
let dbFilePath: string;
let saveTimer: ReturnType<typeof setTimeout> | null = null;
const SAVE_DEBOUNCE_MS = 500;

export function getDb(): DatabaseWrapper {
  if (!wrapper) throw new Error('Database not initialized');
  return wrapper;
}

export async function initDatabase(dbPath: string): Promise<DatabaseWrapper> {
  const resolved = path.resolve(dbPath);
  dbFilePath = resolved;

  const SQL = await initSqlJs();

  if (fs.existsSync(resolved)) {
    const fileBuffer = fs.readFileSync(resolved);
    rawDb = new SQL.Database(fileBuffer);
    logger.info(`Database loaded from: ${resolved}`);
  } else {
    rawDb = new SQL.Database();
    logger.info(`New database created: ${resolved}`);
  }

  wrapper = createWrapper(rawDb);

  wrapper.pragma('journal_mode = WAL');
  wrapper.pragma('foreign_keys = ON');
  createTables();
  migrateColumns();
  saveToDisk();

  // auto-save every 30 seconds
  setInterval(() => saveToDisk(), 30000);

  logger.info(`Database initialized: ${resolved}`);
  return wrapper;
}

function scheduleSave(): void {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    saveToDisk();
  }, SAVE_DEBOUNCE_MS);
}

export function saveToDisk(): void {
  if (!rawDb || !dbFilePath) return;
  try {
    const data = rawDb.export();
    const buffer = Buffer.from(data);
    const dir = path.dirname(dbFilePath);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(dbFilePath, buffer);
  } catch (e) {
    logger.error(`Database save error: ${e}`);
  }
}

function createWrapper(db: SqlJsDatabase): DatabaseWrapper {
  return {
    prepare(sql: string): PreparedStatement {
      return {
        all(...params: any[]): any[] {
          try {
            const stmt = db.prepare(sql);
            if (params.length > 0) stmt.bind(params);
            const results: any[] = [];
            while (stmt.step()) {
              results.push(stmt.getAsObject());
            }
            stmt.free();
            return results;
          } catch (e) {
            logger.error(`SQL all error [${sql}]: ${e}`);
            return [];
          }
        },
        get(...params: any[]): any | undefined {
          try {
            const stmt = db.prepare(sql);
            if (params.length > 0) stmt.bind(params);
            let result: any = undefined;
            if (stmt.step()) {
              result = stmt.getAsObject();
            }
            stmt.free();
            return result;
          } catch (e) {
            logger.error(`SQL get error [${sql}]: ${e}`);
            return undefined;
          }
        },
        run(...params: any[]): RunResult {
          try {
            if (params.length > 0) {
              db.run(sql, params);
            } else {
              db.run(sql);
            }
            scheduleSave();
            const changesResult = db.exec('SELECT changes() as c, last_insert_rowid() as r');
            const row = changesResult[0]?.values[0];
            return {
              changes: (row?.[0] as number) ?? 0,
              lastInsertRowid: (row?.[1] as number) ?? 0,
            };
          } catch (e) {
            logger.error(`SQL run error [${sql}]: ${e}`);
            return { changes: 0, lastInsertRowid: 0 };
          }
        },
      };
    },
    exec(sql: string): void {
      try {
        db.run(sql);
        scheduleSave();
      } catch (e) {
        logger.error(`SQL exec error: ${e}`);
      }
    },
    pragma(pragma: string): any[] {
      try {
        const result = db.exec(`PRAGMA ${pragma}`);
        if (result.length > 0) {
          const cols = result[0].columns;
          return result[0].values.map((row: any[]) => {
            const obj: Record<string, any> = {};
            cols.forEach((col: string, i: number) => { obj[col] = row[i]; });
            return obj;
          });
        }
        return [];
      } catch (e) {
        logger.error(`PRAGMA error [${pragma}]: ${e}`);
        return [];
      }
    },
  };
}

function createTables(): void {
  // sql.js db.run() can only handle one statement at a time, so split them
  const statements = [
    `CREATE TABLE IF NOT EXISTS administrator (
      qid TEXT NOT NULL,
      target_id TEXT NOT NULL
    )`,
    `CREATE TABLE IF NOT EXISTS all_message (
      time INTEGER,
      id INTEGER,
      internal_id INTEGER,
      sender_id INTEGER,
      bot_id INTEGER,
      type TEXT,
      from_id INTEGER,
      content TEXT,
      recalled INTEGER DEFAULT 0
    )`,
    `CREATE TABLE IF NOT EXISTS auth_m (
      qid TEXT PRIMARY KEY,
      auth TEXT,
      exp INTEGER,
      t0 INTEGER
    )`,
    `CREATE TABLE IF NOT EXISTS call_template (
      qid TEXT,
      touch TEXT,
      url TEXT,
      out TEXT,
      out_args TEXT,
      jude TEXT,
      err TEXT DEFAULT '调用失败'
    )`,
    `CREATE TABLE IF NOT EXISTS conf (
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
    )`,
    `CREATE TABLE IF NOT EXISTS ai_conf (
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
    )`,
    `CREATE TABLE IF NOT EXISTS conn_config (
      qid TEXT PRIMARY KEY,
      ip TEXT,
      port INTEGER,
      type TEXT,
      token TEXT,
      heart INTEGER
    )`,
    `CREATE TABLE IF NOT EXISTS cron_message (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      qid TEXT,
      desc TEXT,
      cron TEXT,
      target_id TEXT,
      msg TEXT
    )`,
    `CREATE TABLE IF NOT EXISTS group_conf (
      qid TEXT,
      tid TEXT,
      k0 INTEGER DEFAULT 1,
      k1 INTEGER DEFAULT 1,
      k2 INTEGER DEFAULT 1,
      k3 INTEGER DEFAULT 1,
      k4 INTEGER DEFAULT 0
    )`,
    `CREATE TABLE IF NOT EXISTS like_reco (
      bid TEXT,
      date TEXT,
      tid TEXT
    )`,
    `CREATE TABLE IF NOT EXISTS optional (
      qid TEXT,
      opt TEXT,
      open INTEGER
    )`,
    `CREATE TABLE IF NOT EXISTS passive (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      qid TEXT,
      touch TEXT,
      out TEXT
    )`,
    `CREATE TABLE IF NOT EXISTS statistics (
      count INTEGER DEFAULT 0,
      account TEXT,
      type TEXT DEFAULT 'PRIVATE'
    )`,
    `CREATE TABLE IF NOT EXISTS v11_conf (
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
    )`,
  ];

  for (const sql of statements) {
    wrapper.exec(sql);
  }
  logger.info('Tables created/verified');
}

function migrateColumns(): void {
  const safeAddColumn = (table: string, column: string, type: string, defaultVal: string) => {
    try {
      const cols = wrapper.prepare(`PRAGMA table_info('${table}')`).all() as { name: string }[];
      if (!cols.some(c => c.name === column)) {
        wrapper.exec(`ALTER TABLE ${table} ADD ${column} ${type} DEFAULT ${defaultVal}`);
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
