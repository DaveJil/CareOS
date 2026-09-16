import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { BloodGroup } from '@prisma/client';
import {
  IsDateString,
  IsEnum,
  IsInt,
  IsOptional,
  IsPhoneNumber,
  IsString,
  Min,
} from 'class-validator';

export class UpsertPatientProfileDto {
  @ApiProperty({ example: 'Samira' })
  @IsString()
  firstName!: string;

  @ApiProperty({ example: 'Adebayo' })
  @IsString()
  lastName!: string;

  @ApiPropertyOptional({ example: '1992-05-12' })
  @IsOptional()
  @IsDateString()
  dateOfBirth?: string;

  @ApiPropertyOptional({ example: 'female' })
  @IsOptional()
  @IsString()
  gender?: string;

  @ApiPropertyOptional({ example: '24 Admiralty Way' })
  @IsOptional()
  @IsString()
  address?: string;

  @ApiPropertyOptional({ example: 'Lagos' })
  @IsOptional()
  @IsString()
  city?: string;

  @ApiPropertyOptional({ example: 'Lagos' })
  @IsOptional()
  @IsString()
  state?: string;

  @ApiPropertyOptional({ example: 'NG' })
  @IsOptional()
  @IsString()
  country?: string;

  @ApiPropertyOptional({ enum: BloodGroup, example: BloodGroup.O_positive })
  @IsOptional()
  @IsEnum(BloodGroup)
  bloodGroup?: BloodGroup;

  @ApiPropertyOptional({ example: 'AA' })
  @IsOptional()
  @IsString()
  genotype?: string;

  @ApiPropertyOptional({ example: 'Musa Adebayo' })
  @IsOptional()
  @IsString()
  nextOfKinName?: string;

  @ApiPropertyOptional({ example: '+2348012345678' })
  @IsOptional()
  @IsPhoneNumber('NG')
  nextOfKinPhone?: string;

  @ApiPropertyOptional({ example: 'Brother' })
  @IsOptional()
  @IsString()
  nextOfKinRelationship?: string;
}

export class CreateEmergencyContactDto {
  @ApiProperty({ example: 'Musa Adebayo' })
  @IsString()
  name!: string;

  @ApiProperty({ example: '+2348012345678' })
  @IsPhoneNumber('NG')
  phone!: string;

  @ApiProperty({ example: 'Brother' })
  @IsString()
  relationship!: string;

  @ApiPropertyOptional({ example: 1 })
  @IsOptional()
  @IsInt()
  @Min(1)
  priority?: number;
}

export class CreateAllergyDto {
  @ApiProperty({ example: 'Penicillin' })
  @IsString()
  name!: string;

  @ApiPropertyOptional({ example: 'severe' })
  @IsOptional()
  @IsString()
  severity?: string;

  @ApiPropertyOptional({ example: 'Rash and breathing difficulty' })
  @IsOptional()
  @IsString()
  reaction?: string;
}

export class CreateChronicConditionDto {
  @ApiProperty({ example: 'Hypertension' })
  @IsString()
  name!: string;

  @ApiPropertyOptional({ example: '2020-02-10' })
  @IsOptional()
  @IsDateString()
  diagnosedAt?: string;

  @ApiPropertyOptional({ example: 'Controlled with medication' })
  @IsOptional()
  @IsString()
  notes?: string;
}
