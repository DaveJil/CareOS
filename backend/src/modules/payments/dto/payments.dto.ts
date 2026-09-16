import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsObject, IsOptional, IsString } from 'class-validator';

export class CreateChargeDto {
  @ApiProperty({ example: 'booking-id' })
  @IsString()
  bookingId!: string;

  @ApiProperty({ example: 'idem-booking-id-1' })
  @IsString()
  idempotencyKey!: string;
}

export class PaymentWebhookDto {
  @ApiProperty({ example: 'paystack' })
  @IsOptional()
  @IsString()
  gateway?: string;

  @ApiProperty({ example: 'careos_booking_123' })
  @IsOptional()
  @IsString()
  providerReference?: string;

  @ApiProperty({ example: 'success' })
  @IsOptional()
  @IsString()
  status?: string;

  @ApiPropertyOptional({ example: { event: 'charge.success' } })
  @IsOptional()
  @IsObject()
  raw?: Record<string, unknown>;

  @ApiPropertyOptional({ example: 'charge.success' })
  @IsOptional()
  @IsString()
  event?: string;

  @ApiPropertyOptional({ example: { reference: 'careos_booking_123', status: 'success' } })
  @IsOptional()
  @IsObject()
  data?: Record<string, unknown>;
}
