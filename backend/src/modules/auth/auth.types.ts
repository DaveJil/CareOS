import type { Role } from '@prisma/client';

export type AuthUser = {
  id: string;
  email: string;
  phone: string | null;
  role: Role;
  passwordHash: string;
  disabledAt: Date | null;
};

export type PublicUser = {
  id: string;
  email: string;
  phone: string | null;
  role: Role;
};

export type TokenPair = {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
};

export type AuthResponse = TokenPair & {
  user: PublicUser;
};
