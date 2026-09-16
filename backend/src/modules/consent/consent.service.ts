import { Injectable, NotFoundException } from '@nestjs/common';
import { ConsentType, Prisma } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AuditService } from '../audit/audit.service';
import { RequestUser } from '../auth/current-user.decorator';
import { GrantConsentDto } from './dto/consent.dto';

@Injectable()
export class ConsentService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  async grant(user: RequestUser, dto: GrantConsentDto) {
    const consent = await this.prisma.consentRecord.create({
      data: {
        userId: user.id,
        type: dto.type,
        version: dto.version,
        metadata: dto.metadata as Prisma.InputJsonValue | undefined,
      },
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'consent.grant',
      target: `consent:${consent.id}`,
      metadata: { type: dto.type, version: dto.version },
    });

    return consent;
  }

  async list(user: RequestUser) {
    return this.prisma.consentRecord.findMany({
      where: { userId: user.id },
      orderBy: { grantedAt: 'desc' },
    });
  }

  async revoke(user: RequestUser, type: ConsentType) {
    const consent = await this.prisma.consentRecord.findFirst({
      where: {
        userId: user.id,
        type,
        revokedAt: null,
      },
      orderBy: { grantedAt: 'desc' },
    });

    if (!consent) {
      throw new NotFoundException('No active consent was found for this type.');
    }

    const revoked = await this.prisma.consentRecord.update({
      where: { id: consent.id },
      data: { revokedAt: new Date() },
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'consent.revoke',
      target: `consent:${consent.id}`,
      metadata: { type },
    });

    return revoked;
  }
}
