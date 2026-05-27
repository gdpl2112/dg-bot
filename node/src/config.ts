import fs from 'fs';
import path from 'path';
import YAML from 'yaml';

export interface AppConfig {
  server: { port: number };
  super: { qid: string };
  database: { path: string };
  manage: { bid: string; key: string };
  report: { pointGid: string; bid: string; gid: string };
  logging: { level: string; file: string };
}

const defaults: AppConfig = {
  server: { port: 34740 },
  super: { qid: '3474006766' },
  database: { path: './data.db' },
  manage: { bid: '3474006766', key: 'change-me-manage-secret-key' },
  report: { pointGid: '943648151', bid: '291841860', gid: '1041541077' },
  logging: { level: 'info', file: './logs/app.log' },
};

export function loadConfig(profile?: string): AppConfig {
  const config = { ...defaults };
  const baseFile = path.resolve('application.yml');
  if (fs.existsSync(baseFile)) {
    merge(config, parseYaml(baseFile));
  }
  if (profile) {
    const profileFile = path.resolve(`application-${profile}.yml`);
    if (fs.existsSync(profileFile)) {
      merge(config, parseYaml(profileFile));
    }
  }
  return config;
}

function parseYaml(filePath: string): Record<string, any> {
  const content = fs.readFileSync(filePath, 'utf-8');
  return YAML.parse(content) || {};
}

function merge(target: any, source: any): void {
  for (const key of Object.keys(source)) {
    if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
      if (!target[key]) target[key] = {};
      merge(target[key], source[key]);
    } else {
      target[key] = source[key];
    }
  }
}
