import { Module, forwardRef } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaModule } from '../../common/prisma/prisma.module';
import { AuditModule } from '../audit/audit.module';
import { AuthModule } from '../auth/auth.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { TelehealthController } from './telehealth.controller';
import { TelehealthService } from './telehealth.service';
import {
  ExternalVideoSessionProvider,
  MockVideoSessionProvider,
  VideoSessionProvider,
} from './video-session.provider';

@Module({
  imports: [PrismaModule, AuditModule, AuthModule, forwardRef(() => NotificationsModule)],
  controllers: [TelehealthController],
  providers: [
    TelehealthService,
    {
      provide: VideoSessionProvider,
      inject: [ConfigService],
      useFactory: (config: ConfigService) =>
        config.get<string>('VIDEO_PROVIDER') === 'external'
          ? new ExternalVideoSessionProvider(config)
          : new MockVideoSessionProvider(),
    },
  ],
  exports: [TelehealthService],
})
export class TelehealthModule {}
