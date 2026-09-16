import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaModule } from '../../common/prisma/prisma.module';
import { AuditModule } from '../audit/audit.module';
import { AuthModule } from '../auth/auth.module';
import {
  ExternalNotificationDispatcher,
  MockNotificationDispatcher,
  NotificationDispatcher,
} from './notification-dispatcher';
import { NotificationsController } from './notifications.controller';
import { NotificationsService } from './notifications.service';

@Module({
  imports: [PrismaModule, AuditModule, AuthModule],
  controllers: [NotificationsController],
  providers: [
    NotificationsService,
    {
      provide: NotificationDispatcher,
      inject: [ConfigService],
      useFactory: (config: ConfigService) =>
        config.get<string>('NOTIFICATION_PROVIDER') === 'external'
          ? new ExternalNotificationDispatcher(config)
          : new MockNotificationDispatcher(),
    },
  ],
  exports: [NotificationsService],
})
export class NotificationsModule {}
