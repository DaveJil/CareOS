import { Module, forwardRef } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaModule } from '../../common/prisma/prisma.module';
import { AuditModule } from '../audit/audit.module';
import { AuthModule } from '../auth/auth.module';
import { TelehealthModule } from '../telehealth/telehealth.module';
import {
  MockPaystackGatewayProvider,
  PaymentGatewayProvider,
  PaystackGatewayProvider,
} from './payment-gateway.provider';
import { PaymentsController } from './payments.controller';
import { PaymentsService } from './payments.service';

@Module({
  imports: [PrismaModule, AuditModule, AuthModule, forwardRef(() => TelehealthModule)],
  controllers: [PaymentsController],
  providers: [
    PaymentsService,
    {
      provide: PaymentGatewayProvider,
      inject: [ConfigService],
      useFactory: (config: ConfigService) =>
        config.get<string>('PAYMENT_PROVIDER') === 'paystack'
          ? new PaystackGatewayProvider(config)
          : new MockPaystackGatewayProvider(),
    },
  ],
  exports: [PaymentsService],
})
export class PaymentsModule {}
