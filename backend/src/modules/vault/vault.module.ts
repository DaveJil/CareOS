import { Module } from '@nestjs/common';
import { PrismaModule } from '../../common/prisma/prisma.module';
import { AuditModule } from '../audit/audit.module';
import { AuthModule } from '../auth/auth.module';
import { EncryptedStorageService } from './encrypted-storage.service';
import { VaultController } from './vault.controller';
import { VaultService } from './vault.service';

@Module({
  imports: [PrismaModule, AuditModule, AuthModule],
  controllers: [VaultController],
  providers: [EncryptedStorageService, VaultService],
  exports: [VaultService],
})
export class VaultModule {}
