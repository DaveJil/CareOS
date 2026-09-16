import { Injectable } from '@nestjs/common';
import { Prisma, Role } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';

export type AuditActor = {
  id?: string;
  role?: Role;
};

@Injectable()
export class AuditService {
  constructor(private readonly prisma: PrismaService) {}

  async record(input: {
    actor?: AuditActor;
    action: string;
    target: string;
    metadata?: Prisma.InputJsonValue;
  }): Promise<void> {
    await this.prisma.auditEntry.create({
      data: {
        actorId: input.actor?.id,
        actorRole: input.actor?.role,
        action: input.action,
        target: input.target,
        metadata: input.metadata,
      },
    });
  }
}
