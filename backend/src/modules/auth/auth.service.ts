import {
  BadRequestException,
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { OtpPurpose, Role } from '@prisma/client';
import * as bcrypt from 'bcryptjs';
import { randomBytes, createHash } from 'node:crypto';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AuditService } from '../audit/audit.service';
import {
  BiometricExchangeDto,
  LoginDto,
  PasswordResetConfirmDto,
  PasswordResetRequestDto,
  RefreshDto,
  RegisterDto,
  VerifyOtpDto,
} from './dto/auth.dto';
import { AuthResponse, AuthUser, PublicUser } from './auth.types';
import { TokenService } from './token.service';

const ACCESS_TOKEN_TTL_SECONDS = 15 * 60;
const REFRESH_TOKEN_TTL_DAYS = 30;
const OTP_TTL_MINUTES = 10;

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly jwt: TokenService,
    private readonly audit: AuditService,
  ) {}

  async register(dto: RegisterDto): Promise<AuthResponse> {
    if (dto.role !== Role.patient && dto.role !== Role.clinician) {
      throw new BadRequestException('Only patient and clinician accounts can self-register.');
    }

    const existing = await this.prisma.user.findFirst({
      where: {
        OR: [{ email: dto.email.toLowerCase() }, ...(dto.phone ? [{ phone: dto.phone }] : [])],
      },
    });

    if (existing) {
      throw new ConflictException('An account already exists for these credentials.');
    }

    const passwordHash = await bcrypt.hash(dto.password, 12);
    const user = await this.prisma.user.create({
      data: {
        email: dto.email.toLowerCase(),
        phone: dto.phone,
        role: dto.role,
        passwordHash,
        trustedDevices: dto.deviceId
          ? {
              create: {
                deviceId: dto.deviceId,
                name: dto.deviceName,
              },
            }
          : undefined,
      },
    });

    if (dto.phone) {
      await this.createOtp({
        userId: user.id,
        phone: dto.phone,
        purpose: OtpPurpose.phone_verification,
      });
    }

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'auth.register',
      target: `user:${user.id}`,
      metadata: { role: user.role },
    });

    return this.createAuthResponse(user, dto.deviceId);
  }

  async login(dto: LoginDto): Promise<AuthResponse> {
    const user = await this.prisma.user.findUnique({
      where: { email: dto.email.toLowerCase() },
    });

    if (!user || user.disabledAt) {
      throw new UnauthorizedException('Invalid email or password.');
    }

    const passwordValid = await bcrypt.compare(dto.password, user.passwordHash);
    if (!passwordValid) {
      throw new UnauthorizedException('Invalid email or password.');
    }

    if (dto.deviceId) {
      await this.prisma.trustedDevice.upsert({
        where: { userId_deviceId: { userId: user.id, deviceId: dto.deviceId } },
        create: {
          userId: user.id,
          deviceId: dto.deviceId,
          name: dto.deviceName,
        },
        update: {
          name: dto.deviceName,
          lastSeenAt: new Date(),
        },
      });
    }

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'auth.login',
      target: `user:${user.id}`,
    });

    return this.createAuthResponse(user, dto.deviceId);
  }

  async verifyPhoneOtp(dto: VerifyOtpDto): Promise<{ verified: true }> {
    const otp = await this.prisma.otpCode.findFirst({
      where: {
        phone: dto.phone,
        purpose: OtpPurpose.phone_verification,
        consumedAt: null,
        expiresAt: { gt: new Date() },
      },
      orderBy: { createdAt: 'desc' },
    });

    if (!otp || !(await bcrypt.compare(dto.code, otp.codeHash))) {
      throw new BadRequestException('Invalid or expired OTP.');
    }

    await this.prisma.$transaction([
      this.prisma.otpCode.update({
        where: { id: otp.id },
        data: { consumedAt: new Date() },
      }),
      this.prisma.user.updateMany({
        where: { phone: dto.phone },
        data: { phoneVerified: true },
      }),
    ]);

    if (otp.userId) {
      await this.audit.record({
        actor: { id: otp.userId },
        action: 'auth.phone.verify',
        target: `user:${otp.userId}`,
      });
    }

    return { verified: true };
  }

  async refresh(dto: RefreshDto): Promise<AuthResponse> {
    const tokenHash = this.hashToken(dto.refreshToken);
    const session = await this.prisma.refreshToken.findUnique({
      where: { tokenHash },
      include: { user: true },
    });

    if (!session || session.revokedAt || session.expiresAt <= new Date()) {
      throw new UnauthorizedException('Refresh token is invalid or expired.');
    }

    await this.prisma.refreshToken.update({
      where: { id: session.id },
      data: { revokedAt: new Date() },
    });

    await this.audit.record({
      actor: { id: session.user.id, role: session.user.role },
      action: 'auth.refresh',
      target: `user:${session.user.id}`,
    });

    return this.createAuthResponse(session.user, session.deviceId ?? undefined);
  }

  async exchangeBiometric(dto: BiometricExchangeDto): Promise<AuthResponse> {
    const session = await this.prisma.refreshToken.findUnique({
      where: { tokenHash: this.hashToken(dto.refreshToken) },
      include: { user: true },
    });

    const deviceTrusted = session
      ? await this.prisma.trustedDevice.findUnique({
          where: { userId_deviceId: { userId: session.userId, deviceId: dto.deviceId } },
        })
      : null;

    if (!session || !deviceTrusted || session.revokedAt || session.expiresAt <= new Date()) {
      throw new UnauthorizedException('Biometric token exchange is not allowed for this device.');
    }

    await this.audit.record({
      actor: { id: session.user.id, role: session.user.role },
      action: 'auth.biometric.exchange',
      target: `trusted_device:${deviceTrusted.id}`,
    });

    return this.createAuthResponse(session.user, dto.deviceId);
  }

  async logout(dto: RefreshDto): Promise<{ revoked: true }> {
    await this.prisma.refreshToken.updateMany({
      where: { tokenHash: this.hashToken(dto.refreshToken), revokedAt: null },
      data: { revokedAt: new Date() },
    });

    return { revoked: true };
  }

  async logoutAll(userId: string, role: Role): Promise<{ revoked: true }> {
    await this.prisma.refreshToken.updateMany({
      where: { userId, revokedAt: null },
      data: { revokedAt: new Date() },
    });

    await this.audit.record({
      actor: { id: userId, role },
      action: 'auth.logout_all',
      target: `user:${userId}`,
    });

    return { revoked: true };
  }

  async requestPasswordReset(dto: PasswordResetRequestDto): Promise<{ sent: true }> {
    const user = await this.prisma.user.findUnique({ where: { email: dto.email.toLowerCase() } });
    if (user) {
      await this.createOtp({
        userId: user.id,
        email: user.email,
        purpose: OtpPurpose.password_reset,
      });
      await this.audit.record({
        actor: { id: user.id, role: user.role },
        action: 'auth.password_reset.request',
        target: `user:${user.id}`,
      });
    }

    return { sent: true };
  }

  async confirmPasswordReset(dto: PasswordResetConfirmDto): Promise<{ reset: true }> {
    const user = await this.prisma.user.findUnique({ where: { email: dto.email.toLowerCase() } });
    if (!user) {
      throw new BadRequestException('Invalid or expired password reset OTP.');
    }

    const otp = await this.prisma.otpCode.findFirst({
      where: {
        userId: user.id,
        purpose: OtpPurpose.password_reset,
        consumedAt: null,
        expiresAt: { gt: new Date() },
      },
      orderBy: { createdAt: 'desc' },
    });

    if (!otp || !(await bcrypt.compare(dto.code, otp.codeHash))) {
      throw new BadRequestException('Invalid or expired password reset OTP.');
    }

    await this.prisma.$transaction([
      this.prisma.user.update({
        where: { id: user.id },
        data: { passwordHash: await bcrypt.hash(dto.newPassword, 12) },
      }),
      this.prisma.otpCode.update({
        where: { id: otp.id },
        data: { consumedAt: new Date() },
      }),
      this.prisma.refreshToken.updateMany({
        where: { userId: user.id, revokedAt: null },
        data: { revokedAt: new Date() },
      }),
    ]);

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'auth.password_reset.confirm',
      target: `user:${user.id}`,
    });

    return { reset: true };
  }

  private async createAuthResponse(user: AuthUser, deviceId?: string): Promise<AuthResponse> {
    const refreshToken = randomBytes(48).toString('base64url');
    await this.prisma.refreshToken.create({
      data: {
        userId: user.id,
        tokenHash: this.hashToken(refreshToken),
        deviceId,
        expiresAt: this.daysFromNow(REFRESH_TOKEN_TTL_DAYS),
      },
    });

    return {
      user: this.toPublicUser(user),
      accessToken: await this.jwt.signAsync(
        { sub: user.id, role: user.role },
        { expiresIn: ACCESS_TOKEN_TTL_SECONDS },
      ),
      refreshToken,
      expiresInSeconds: ACCESS_TOKEN_TTL_SECONDS,
    };
  }

  private async createOtp(input: {
    userId?: string;
    phone?: string;
    email?: string;
    purpose: OtpPurpose;
  }): Promise<void> {
    const code = '123456';
    await this.prisma.otpCode.create({
      data: {
        userId: input.userId,
        phone: input.phone,
        email: input.email,
        purpose: input.purpose,
        codeHash: await bcrypt.hash(code, 10),
        expiresAt: this.minutesFromNow(OTP_TTL_MINUTES),
      },
    });
  }

  private hashToken(token: string): string {
    return createHash('sha256').update(token).digest('hex');
  }

  private daysFromNow(days: number): Date {
    return new Date(Date.now() + days * 24 * 60 * 60 * 1000);
  }

  private minutesFromNow(minutes: number): Date {
    return new Date(Date.now() + minutes * 60 * 1000);
  }

  private toPublicUser(user: AuthUser): PublicUser {
    return {
      id: user.id,
      email: user.email,
      phone: user.phone,
      role: user.role,
    };
  }
}
