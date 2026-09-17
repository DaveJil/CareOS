import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Role } from '@prisma/client';
import {
  IsEmail,
  IsEnum,
  IsIn,
  IsOptional,
  IsPhoneNumber,
  IsString,
  Length,
  MinLength,
} from 'class-validator';

export class RegisterDto {
  @ApiProperty({ example: 'patient@example.com' })
  @IsEmail()
  email!: string;

  @ApiPropertyOptional({ example: '+2348012345678' })
  @IsOptional()
  @IsPhoneNumber('NG')
  phone?: string;

  @ApiProperty({ minLength: 8, example: 'Use-a-real-secret-123' })
  @IsString()
  @MinLength(8)
  password!: string;

  @ApiProperty({ enum: [Role.patient, Role.clinician], example: Role.patient })
  @IsIn([Role.patient, Role.clinician])
  role!: Role;

  @ApiPropertyOptional({ example: 'iphone-15-samira' })
  @IsOptional()
  @IsString()
  deviceId?: string;

  @ApiPropertyOptional({ example: 'Samira iPhone' })
  @IsOptional()
  @IsString()
  deviceName?: string;
}

export class LoginDto {
  @ApiProperty({ example: 'patient@example.com' })
  @IsEmail()
  email!: string;

  @ApiProperty({ example: 'Use-a-real-secret-123' })
  @IsString()
  password!: string;

  @ApiPropertyOptional({ example: 'iphone-15-samira' })
  @IsOptional()
  @IsString()
  deviceId?: string;

  @ApiPropertyOptional({ example: 'Samira iPhone' })
  @IsOptional()
  @IsString()
  deviceName?: string;
}

export class VerifyOtpDto {
  @ApiProperty({ example: '+2348012345678' })
  @IsPhoneNumber('NG')
  phone!: string;

  @ApiProperty({ minLength: 6, maxLength: 6, example: '123456' })
  @IsString()
  @Length(6, 6)
  code!: string;
}

export class RefreshDto {
  @ApiProperty()
  @IsString()
  refreshToken!: string;
}

export class LogoutDto extends RefreshDto {}

export class PasswordResetRequestDto {
  @ApiProperty({ example: 'patient@example.com' })
  @IsEmail()
  email!: string;
}

export class PasswordResetConfirmDto {
  @ApiProperty({ example: 'patient@example.com' })
  @IsEmail()
  email!: string;

  @ApiProperty({ minLength: 6, maxLength: 6, example: '123456' })
  @IsString()
  @Length(6, 6)
  code!: string;

  @ApiProperty({ minLength: 8, example: 'A-new-real-secret-123' })
  @IsString()
  @MinLength(8)
  newPassword!: string;
}

export class BiometricExchangeDto {
  @ApiProperty()
  @IsString()
  refreshToken!: string;

  @ApiProperty({ example: 'iphone-15-samira' })
  @IsString()
  deviceId!: string;
}
