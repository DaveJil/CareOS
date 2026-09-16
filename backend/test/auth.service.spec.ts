import { ConflictException, UnauthorizedException } from '@nestjs/common';
import { ConsentType, OtpPurpose, Role } from '@prisma/client';
import * as bcrypt from 'bcryptjs';
import { AuthService } from '../src/modules/auth/auth.service';
import { ConsentService } from '../src/modules/consent/consent.service';
import { TokenService } from '../src/modules/auth/token.service';

type StoredUser = {
  id: string;
  email: string;
  phone: string | null;
  role: Role;
  passwordHash: string;
  emailVerified: boolean;
  phoneVerified: boolean;
  disabledAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
};

describe('AuthService Phase 1 account access', () => {
  it.each([
    Role.patient,
    Role.clinician,
    Role.hospital_admin,
    Role.hmo_staff,
    Role.fund_admin,
    Role.super_admin,
  ])('registers and logs in %s accounts', async (role) => {
    const { auth, prisma } = createHarness();
    const email = `${role}@careos.test`;

    const registered = await auth.register({
      email,
      password: 'strong-password-123',
      role,
      deviceId: `${role}-device`,
      deviceName: `${role} phone`,
    });

    expect(registered.user.email).toBe(email);
    expect(registered.user.role).toBe(role);
    expect(registered.accessToken).toEqual(expect.any(String));
    expect(registered.refreshToken).toEqual(expect.any(String));

    const login = await auth.login({
      email,
      password: 'strong-password-123',
      deviceId: `${role}-device`,
    });

    expect(login.user.role).toBe(role);
    expect(prisma.auditEntries.map((entry) => entry.action)).toEqual([
      'auth.register',
      'auth.login',
    ]);
  });

  it('blocks duplicate account registration', async () => {
    const { auth } = createHarness();
    await auth.register({
      email: 'patient@careos.test',
      password: 'strong-password-123',
      role: Role.patient,
    });

    await expect(
      auth.register({
        email: 'patient@careos.test',
        password: 'strong-password-123',
        role: Role.patient,
      }),
    ).rejects.toThrow(ConflictException);
  });

  it('rejects invalid login credentials', async () => {
    const { auth } = createHarness();
    await auth.register({
      email: 'patient@careos.test',
      password: 'strong-password-123',
      role: Role.patient,
    });

    await expect(
      auth.login({
        email: 'patient@careos.test',
        password: 'wrong-password',
      }),
    ).rejects.toThrow(UnauthorizedException);
  });
});

describe('ConsentService Phase 1 consent history', () => {
  it('grants, lists, and revokes consent with audit entries', async () => {
    const { consent, prisma } = createHarness();
    const user = { id: 'user-1', role: Role.patient };

    const granted = await consent.grant(user, {
      type: ConsentType.ai_triage,
      version: '2026-09-12',
      metadata: { source: 'test' },
    });
    const listed = await consent.list(user);
    const revoked = await consent.revoke(user, ConsentType.ai_triage);

    expect(granted.type).toBe(ConsentType.ai_triage);
    expect(listed).toHaveLength(1);
    expect(revoked.revokedAt).toBeInstanceOf(Date);
    expect(prisma.auditEntries.map((entry) => entry.action)).toEqual([
      'consent.grant',
      'consent.revoke',
    ]);
  });
});

function createHarness() {
  const prisma = createPrismaMock();
  const audit = {
    record: jest.fn(async (entry) => {
      prisma.auditEntries.push(entry);
    }),
  };
  const jwt = new TokenService({
    get: () => 'unit-test-secret',
  } as never);

  return {
    prisma,
    audit,
    auth: new AuthService(prisma as never, jwt, audit as never),
    consent: new ConsentService(prisma as never, audit as never),
  };
}

function createPrismaMock() {
  const users: StoredUser[] = [];
  const refreshTokens: Array<Record<string, unknown>> = [];
  const otpCodes: Array<Record<string, unknown>> = [];
  const trustedDevices: Array<Record<string, unknown>> = [];
  const consents: Array<Record<string, unknown>> = [];
  const auditEntries: Array<Record<string, unknown>> = [];

  return {
    auditEntries,
    user: {
      findFirst: jest.fn(
        async ({ where }) =>
          users.find(
            (user) =>
              where.OR?.some(
                (condition: { email?: string; phone?: string }) =>
                  condition.email === user.email || condition.phone === user.phone,
              ) ?? false,
          ) ?? null,
      ),
      findUnique: jest.fn(async ({ where }) => {
        if ('email' in where) {
          return users.find((user) => user.email === where.email) ?? null;
        }
        if ('id' in where) {
          return users.find((user) => user.id === where.id) ?? null;
        }
        return null;
      }),
      create: jest.fn(async ({ data }) => {
        const user: StoredUser = {
          id: `user-${users.length + 1}`,
          email: data.email,
          phone: data.phone ?? null,
          role: data.role,
          passwordHash: data.passwordHash,
          emailVerified: false,
          phoneVerified: false,
          disabledAt: null,
          createdAt: new Date(),
          updatedAt: new Date(),
        };
        users.push(user);
        if (data.trustedDevices?.create) {
          trustedDevices.push({
            id: `device-${trustedDevices.length + 1}`,
            userId: user.id,
            ...data.trustedDevices.create,
          });
        }
        return user;
      }),
      update: jest.fn(async ({ where, data }) => {
        const user = users.find((candidate) => candidate.id === where.id);
        if (!user) {
          throw new Error('User not found');
        }
        Object.assign(user, data);
        return user;
      }),
      updateMany: jest.fn(async ({ where, data }) => {
        const matches = users.filter((user) => !where.phone || user.phone === where.phone);
        matches.forEach((user) => Object.assign(user, data));
        return { count: matches.length };
      }),
    },
    trustedDevice: {
      upsert: jest.fn(async ({ where, create, update }) => {
        const existing = trustedDevices.find(
          (device) =>
            device.userId === where.userId_deviceId.userId &&
            device.deviceId === where.userId_deviceId.deviceId,
        );
        if (existing) {
          Object.assign(existing, update);
          return existing;
        }
        const device = { id: `device-${trustedDevices.length + 1}`, ...create };
        trustedDevices.push(device);
        return device;
      }),
      findUnique: jest.fn(
        async ({ where }) =>
          trustedDevices.find(
            (device) =>
              device.userId === where.userId_deviceId.userId &&
              device.deviceId === where.userId_deviceId.deviceId,
          ) ?? null,
      ),
    },
    refreshToken: {
      create: jest.fn(async ({ data }) => {
        const token = { id: `refresh-${refreshTokens.length + 1}`, revokedAt: null, ...data };
        refreshTokens.push(token);
        return token;
      }),
      findUnique: jest.fn(async ({ where }) => {
        const session = refreshTokens.find((token) => token.tokenHash === where.tokenHash);
        if (!session) {
          return null;
        }
        return {
          ...session,
          user: users.find((user) => user.id === session.userId),
        };
      }),
      update: jest.fn(async ({ where, data }) => {
        const session = refreshTokens.find((token) => token.id === where.id);
        Object.assign(session ?? {}, data);
        return session;
      }),
      updateMany: jest.fn(async ({ where, data }) => {
        const matches = refreshTokens.filter(
          (token) =>
            (!where.userId || token.userId === where.userId) &&
            (!where.tokenHash || token.tokenHash === where.tokenHash) &&
            (where.revokedAt === undefined || token.revokedAt === where.revokedAt),
        );
        matches.forEach((token) => Object.assign(token, data));
        return { count: matches.length };
      }),
    },
    otpCode: {
      create: jest.fn(async ({ data }) => {
        const otp = { id: `otp-${otpCodes.length + 1}`, consumedAt: null, ...data };
        otpCodes.push(otp);
        return otp;
      }),
      findFirst: jest.fn(async ({ where }) => {
        const matches = otpCodes.filter(
          (otp) =>
            (!where.phone || otp.phone === where.phone) &&
            (!where.userId || otp.userId === where.userId) &&
            otp.purpose === where.purpose &&
            otp.consumedAt === null &&
            otp.expiresAt instanceof Date &&
            otp.expiresAt > new Date(),
        );
        return matches.at(-1) ?? null;
      }),
      update: jest.fn(async ({ where, data }) => {
        const otp = otpCodes.find((candidate) => candidate.id === where.id);
        Object.assign(otp ?? {}, data);
        return otp;
      }),
    },
    consentRecord: {
      create: jest.fn(async ({ data }) => {
        const consent = {
          id: `consent-${consents.length + 1}`,
          grantedAt: new Date(),
          revokedAt: null,
          ...data,
        };
        consents.push(consent);
        return consent;
      }),
      findMany: jest.fn(async ({ where }) =>
        consents.filter((consent) => consent.userId === where.userId),
      ),
      findFirst: jest.fn(
        async ({ where }) =>
          [...consents]
            .reverse()
            .find(
              (consent) =>
                consent.userId === where.userId &&
                consent.type === where.type &&
                consent.revokedAt === where.revokedAt,
            ) ?? null,
      ),
      update: jest.fn(async ({ where, data }) => {
        const consent = consents.find((candidate) => candidate.id === where.id);
        Object.assign(consent ?? {}, data);
        return consent;
      }),
    },
    $transaction: jest.fn(async (operations: Array<Promise<unknown>>) => Promise.all(operations)),
  };
}
