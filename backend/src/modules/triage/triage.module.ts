import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaModule } from '../../common/prisma/prisma.module';
import { AuditModule } from '../audit/audit.module';
import { AuthModule } from '../auth/auth.module';
import {
  AiTriageProvider,
  ExternalAiTriageProvider,
  MockAiTriageProvider,
} from './ai-triage.provider';
import { TriageController } from './triage.controller';
import { TriageService } from './triage.service';

@Module({
  imports: [PrismaModule, AuditModule, AuthModule],
  controllers: [TriageController],
  providers: [
    TriageService,
    {
      provide: AiTriageProvider,
      inject: [ConfigService],
      useFactory: (config: ConfigService) =>
        config.get<string>('AI_TRIAGE_PROVIDER') === 'external'
          ? new ExternalAiTriageProvider(config)
          : new MockAiTriageProvider(),
    },
  ],
  exports: [TriageService],
})
export class TriageModule {}
