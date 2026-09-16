import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  ArrayMaxSize,
  IsArray,
  IsBoolean,
  IsDateString,
  IsInt,
  IsObject,
  IsOptional,
  IsString,
  MaxLength,
  Min,
} from 'class-validator';

export class UpsertClinicianProfileDto {
  @ApiProperty({ example: 'Dr. Grace Adebiran' })
  @IsString()
  displayName!: string;

  @ApiProperty({ example: 'General Practice' })
  @IsString()
  specialty!: string;

  @ApiPropertyOptional({ example: 'Primary care doctor with telehealth experience.' })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  bio?: string;

  @ApiProperty({ example: 1500000 })
  @IsInt()
  @Min(0)
  priceKobo!: number;

  @ApiProperty({
    example: {
      weekdays: ['monday', 'wednesday'],
      slots: ['09:00', '10:00', '11:00'],
    },
  })
  @IsObject()
  availability!: Record<string, unknown>;

  @ApiPropertyOptional({ example: true })
  @IsOptional()
  @IsBoolean()
  approved?: boolean;
}

export class SearchSpecialistsQueryDto {
  @ApiPropertyOptional({ example: 'General Practice' })
  @IsOptional()
  @IsString()
  specialty?: string;

  @ApiPropertyOptional({ example: '2026-09-13T09:00:00.000Z' })
  @IsOptional()
  @IsDateString()
  startsAt?: string;
}

export class CreateBookingDto {
  @ApiProperty({ example: 'clinician-user-id' })
  @IsString()
  clinicianId!: string;

  @ApiProperty({ example: '2026-09-13T09:00:00.000Z' })
  @IsDateString()
  startsAt!: string;
}

export class RescheduleBookingDto {
  @ApiProperty({ example: '2026-09-13T10:00:00.000Z' })
  @IsDateString()
  startsAt!: string;
}

export class CancelBookingDto {
  @ApiPropertyOptional({ example: 'Patient unavailable' })
  @IsOptional()
  @IsString()
  @MaxLength(240)
  reason?: string;
}

export class SendChatMessageDto {
  @ApiProperty({ example: 'Hello doctor, I have uploaded my lab result.' })
  @IsString()
  @MaxLength(2000)
  message!: string;
}

export class BatchSeedCliniciansDto {
  @ApiProperty({ type: [UpsertClinicianProfileDto] })
  @IsArray()
  @ArrayMaxSize(20)
  clinicians!: UpsertClinicianProfileDto[];
}
