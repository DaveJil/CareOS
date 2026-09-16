import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { MedicalDocumentType } from '@prisma/client';
import {
  ArrayMaxSize,
  IsArray,
  IsBase64,
  IsEnum,
  IsOptional,
  IsString,
  MaxLength,
} from 'class-validator';

export class UploadDocumentDto {
  @ApiProperty({ enum: MedicalDocumentType, example: MedicalDocumentType.lab_result })
  @IsEnum(MedicalDocumentType)
  type!: MedicalDocumentType;

  @ApiProperty({ example: 'Malaria test result' })
  @IsString()
  @MaxLength(160)
  title!: string;

  @ApiPropertyOptional({ example: 'Uploaded before consultation' })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  description?: string;

  @ApiProperty({ example: 'malaria-result.pdf' })
  @IsString()
  originalName!: string;

  @ApiProperty({ example: 'application/pdf' })
  @IsString()
  mimeType!: string;

  @ApiProperty({ description: 'Base64 encoded file payload.' })
  @IsBase64()
  contentBase64!: string;

  @ApiPropertyOptional({ example: ['lab', 'malaria'] })
  @IsOptional()
  @IsArray()
  @ArrayMaxSize(12)
  @IsString({ each: true })
  tags?: string[];
}

export class SearchDocumentsQueryDto {
  @ApiPropertyOptional({ enum: MedicalDocumentType })
  @IsOptional()
  @IsEnum(MedicalDocumentType)
  type?: MedicalDocumentType;

  @ApiPropertyOptional({ example: 'malaria' })
  @IsOptional()
  @IsString()
  query?: string;
}
