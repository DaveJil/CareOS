import { Injectable } from '@nestjs/common';
import { NotificationStatus, Prisma } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AuditService } from '../audit/audit.service';
import { DispatchNotificationDto } from './dto/notifications.dto';
import { NotificationDispatcher } from './notification-dispatcher';

@Injectable()
export class NotificationsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly dispatcher: NotificationDispatcher,
    private readonly audit: AuditService,
  ) {}

  async dispatch(dto: DispatchNotificationDto) {
    const notification = await this.prisma.notification.create({
      data: {
        userId: dto.userId,
        channel: dto.channel,
        target: dto.target,
        template: dto.template,
        payload: dto.payload as Prisma.InputJsonValue | undefined,
        status: NotificationStatus.pending,
      },
    });

    return this.attemptDelivery(notification.id);
  }

  async attemptDelivery(id: string) {
    const notification = await this.prisma.notification.findUnique({ where: { id } });
    if (!notification) {
      throw new Error('Notification not found.');
    }

    const result = await this.dispatcher.send({
      channel: notification.channel,
      target: notification.target,
      template: notification.template,
      payload: notification.payload as Record<string, unknown> | undefined,
    });

    const updated = await this.prisma.notification.update({
      where: { id },
      data: result.delivered
        ? {
            status: NotificationStatus.sent,
            attempts: { increment: 1 },
            sentAt: new Date(),
            lastError: null,
          }
        : {
            status: NotificationStatus.failed,
            attempts: { increment: 1 },
            lastError: result.error ?? 'Unknown notification failure.',
            nextAttemptAt: new Date(Date.now() + 5 * 60 * 1000),
          },
    });

    await this.audit.record({
      actor: notification.userId ? { id: notification.userId } : undefined,
      action: result.delivered ? 'notification.sent' : 'notification.failed',
      target: `notification:${id}`,
      metadata: {
        channel: notification.channel,
        template: notification.template,
      } as Prisma.InputJsonValue,
    });

    return updated;
  }

  async retryFailed(limit = 25) {
    const failed = await this.prisma.notification.findMany({
      where: {
        status: NotificationStatus.failed,
        nextAttemptAt: { lte: new Date() },
      },
      take: limit,
      orderBy: { createdAt: 'asc' },
    });

    return Promise.all(failed.map((notification) => this.attemptDelivery(notification.id)));
  }
}
