import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  ArrayMaxSize,
  IsArray,
  IsObject,
  IsOptional,
  IsString,
  MaxLength,
  MinLength,
} from 'class-validator';

export class SubmitTriageDto {
  @ApiProperty({
    example: 'I have fever, headache, body pain, and weakness since yesterday.',
  })
  @IsString()
  @MinLength(10)
  @MaxLength(4000)
  symptomsText!: string;

  @ApiPropertyOptional({
    example: {
      durationHours: 24,
      painScore: 6,
      temperatureCelsius: 38.4,
      breathingDifficulty: false,
    },
  })
  @IsOptional()
  @IsObject()
  questionnaire?: Record<string, unknown>;
}

export class TriageHistoryQueryDto {
  @ApiPropertyOptional({ example: 'headache' })
  @IsOptional()
  @IsString()
  query?: string;

  @ApiPropertyOptional({ example: ['high', 'medium'] })
  @IsOptional()
  @IsArray()
  @ArrayMaxSize(3)
  @IsString({ each: true })
  urgency?: string[];
}
