import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { NotificationChannel } from '@prisma/client';
import { IsEnum, IsObject, IsOptional, IsString } from 'class-validator';

export class DispatchNotificationDto {
  @ApiPropertyOptional({ example: 'user-123' })
  @IsOptional()
  @IsString()
  userId?: string;

  @ApiProperty({ enum: NotificationChannel, example: NotificationChannel.sms })
  @IsEnum(NotificationChannel)
  channel!: NotificationChannel;

  @ApiProperty({ example: '+2348012345678' })
  @IsString()
  target!: string;

  @ApiProperty({ example: 'booking_confirmation' })
  @IsString()
  template!: string;

  @ApiPropertyOptional({ example: { bookingId: 'booking-1' } })
  @IsOptional()
  @IsObject()
  payload?: Record<string, unknown>;
}
