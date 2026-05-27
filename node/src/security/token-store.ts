import { v4 as uuidv4 } from 'uuid';

interface TokenEntry {
  token: string;
  createdAt: number;
}

const TOKEN_VALIDITY_MS = 2 * 60 * 60 * 1000; // 2h

export class TokenStore {
  private userStore = new Map<string, TokenEntry>();
  private tokenToUser = new Map<string, string>();

  createToken(userId: string): string {
    const old = this.userStore.get(userId);
    if (old) {
      this.tokenToUser.delete(old.token);
    }
    const token = uuidv4().replace(/-/g, '');
    const entry: TokenEntry = { token, createdAt: Date.now() };
    this.userStore.set(userId, entry);
    this.tokenToUser.set(token, userId);
    return token;
  }

  validateToken(token: string): string | null {
    if (!token) return null;
    const userId = this.tokenToUser.get(token);
    if (!userId) return null;
    const entry = this.userStore.get(userId);
    if (!entry || entry.token !== token) return null;
    if (Date.now() - entry.createdAt > TOKEN_VALIDITY_MS) {
      this.removeToken(userId);
      return null;
    }
    return userId;
  }

  removeToken(userId: string): void {
    const entry = this.userStore.get(userId);
    if (entry) {
      this.tokenToUser.delete(entry.token);
      this.userStore.delete(userId);
    }
  }

  cleanExpired(): void {
    const now = Date.now();
    for (const [userId, entry] of this.userStore.entries()) {
      if (now - entry.createdAt > TOKEN_VALIDITY_MS) {
        this.tokenToUser.delete(entry.token);
        this.userStore.delete(userId);
      }
    }
  }
}
