import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { ConsentType } from '@prisma/client';
import { IsEnum, IsObject, IsOptional, IsString } from 'class-validator';

export class GrantConsentDto {
  @ApiProperty({ enum: ConsentType, example: ConsentType.medical_records })
  @IsEnum(ConsentType)
  type!: ConsentType;

  @ApiProperty({ example: '2026-09-12' })
  @IsString()
  version!: string;

  @ApiPropertyOptional({ example: { source: 'mobile_app' } })
  @IsOptional()
  @IsObject()
  metadata?: Record<string, unknown>;
}

export class RevokeConsentDto {
  @ApiProperty({ enum: ConsentType, example: ConsentType.ai_triage })
  @IsEnum(ConsentType)
  type!: ConsentType;
}
