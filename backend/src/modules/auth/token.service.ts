import { Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Buffer } from 'node:buffer';
import { createHmac, timingSafeEqual } from 'node:crypto';

type JwtHeader = {
  alg: 'HS256';
  typ: 'JWT';
};

type JwtPayload = Record<string, unknown> & {
  exp?: number;
};

@Injectable()
export class TokenService {
  constructor(private readonly config: ConfigService) {}

  async signAsync(
    payload: Record<string, unknown>,
    options: { expiresIn: number },
  ): Promise<string> {
    const nowSeconds = Math.floor(Date.now() / 1000);
    const header: JwtHeader = { alg: 'HS256', typ: 'JWT' };
    const body: JwtPayload = {
      ...payload,
      iat: nowSeconds,
      exp: nowSeconds + options.expiresIn,
    };
    const encodedHeader = this.encodeJson(header);
    const encodedBody = this.encodeJson(body);
    const signature = this.sign(`${encodedHeader}.${encodedBody}`);

    return `${encodedHeader}.${encodedBody}.${signature}`;
  }

  async verifyAsync<T extends JwtPayload>(token: string): Promise<T> {
    const [encodedHeader, encodedBody, signature] = token.split('.');
    if (!encodedHeader || !encodedBody || !signature) {
      throw new UnauthorizedException('Invalid token.');
    }

    const expected = this.sign(`${encodedHeader}.${encodedBody}`);
    if (!this.safeEqual(signature, expected)) {
      throw new UnauthorizedException('Invalid token signature.');
    }

    const payload = JSON.parse(Buffer.from(encodedBody, 'base64url').toString('utf8')) as T;
    if (payload.exp && payload.exp <= Math.floor(Date.now() / 1000)) {
      throw new UnauthorizedException('Token expired.');
    }

    return payload;
  }

  private encodeJson(value: unknown): string {
    return Buffer.from(JSON.stringify(value)).toString('base64url');
  }

  private sign(value: string): string {
    return createHmac('sha256', this.secret()).update(value).digest('base64url');
  }

  private safeEqual(left: string, right: string): boolean {
    const leftBuffer = Buffer.from(left);
    const rightBuffer = Buffer.from(right);
    return leftBuffer.length === rightBuffer.length && timingSafeEqual(leftBuffer, rightBuffer);
  }

  private secret(): string {
    return this.config.get<string>('JWT_ACCESS_TOKEN_SECRET') ?? 'development-only-secret';
  }
}
