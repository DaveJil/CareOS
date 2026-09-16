import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { APP_INTERCEPTOR } from '@nestjs/core';
import { RequestLoggingInterceptor } from './common/logging/request-logging.interceptor';
import { validateEnvironment } from './config/env.validation';
import { AdminModule } from './modules/admin/admin.module';
import { AuthModule } from './modules/auth/auth.module';
import { ConsentModule } from './modules/consent/consent.module';
import { HealthModule } from './modules/health/health.module';
import { NotificationsModule } from './modules/notifications/notifications.module';
import { PatientModule } from './modules/patient/patient.module';
import { PaymentsModule } from './modules/payments/payments.module';
import { ProtectedModule } from './modules/protected/protected.module';
import { RbacModule } from './modules/rbac/rbac.module';
import { TelehealthModule } from './modules/telehealth/telehealth.module';
import { TriageModule } from './modules/triage/triage.module';
import { VaultModule } from './modules/vault/vault.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ['.env.local', '.env'],
      validate: validateEnvironment,
    }),
    AuthModule,
    ConsentModule,
    HealthModule,
    NotificationsModule,
    PatientModule,
    TelehealthModule,
    PaymentsModule,
    AdminModule,
    ProtectedModule,
    RbacModule,
    TriageModule,
    VaultModule,
  ],
  providers: [
    {
      provide: APP_INTERCEPTOR,
      useClass: RequestLoggingInterceptor,
    },
  ],
})
export class AppModule {}
