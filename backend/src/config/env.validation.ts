import { plainToInstance } from 'class-transformer';
import { IsIn, IsInt, IsOptional, IsString, IsUrl, Min, validateSync } from 'class-validator';

class EnvironmentVariables {
  @IsIn(['local', 'test', 'staging', 'production'])
  NODE_ENV: string = 'local';

  @IsInt()
  @Min(1)
  PORT: number = 3000;

  @IsString()
  SERVICE_NAME: string = 'careos-api';

  @IsString()
  API_VERSION: string = 'v1';

  @IsString()
  DATABASE_URL!: string;

  @IsString()
  @IsOptional()
  REDIS_URL?: string;

  @IsOptional()
  @IsUrl({ require_tld: false })
  UPSTASH_REDIS_REST_URL?: string;

  @IsOptional()
  @IsString()
  UPSTASH_REDIS_REST_TOKEN?: string;

  @IsUrl({ require_tld: false })
  OBJECT_STORAGE_ENDPOINT!: string;

  @IsString()
  OBJECT_STORAGE_BUCKET!: string;

  @IsString()
  OBJECT_STORAGE_REGION!: string;

  @IsString()
  OBJECT_STORAGE_ACCESS_KEY!: string;

  @IsString()
  OBJECT_STORAGE_SECRET_KEY!: string;

  @IsString()
  DOCUMENT_ENCRYPTION_KEY!: string;

  @IsString()
  JWT_ACCESS_TOKEN_SECRET!: string;

  @IsString()
  JWT_REFRESH_TOKEN_SECRET!: string;

  @IsOptional()
  @IsUrl({ require_tld: false })
  SENTRY_DSN?: string;

  @IsIn(['debug', 'info', 'warn', 'error'])
  LOG_LEVEL: string = 'info';

  @IsIn(['mock', 'external'])
  AI_TRIAGE_PROVIDER: string = 'mock';

  @IsOptional()
  @IsUrl({ require_tld: false })
  AI_TRIAGE_API_URL?: string;

  @IsOptional()
  @IsString()
  AI_TRIAGE_API_KEY?: string;

  @IsOptional()
  @IsString()
  GEMINI_API_KEY?: string;

  @IsOptional()
  @IsString()
  GEMINI_MODEL?: string;

  @IsIn(['mock', 'external'])
  VIDEO_PROVIDER: string = 'mock';

  @IsOptional()
  @IsUrl({ require_tld: false })
  VIDEO_API_URL?: string;

  @IsOptional()
  @IsString()
  VIDEO_API_KEY?: string;

  @IsOptional()
  @IsString()
  DAILY_API_KEY?: string;

  @IsOptional()
  @IsString()
  DAILY_DOMAIN?: string;

  @IsOptional()
  @IsInt()
  @Min(60)
  DAILY_ROOM_EXPIRY_SECONDS?: number;

  @IsIn(['mock', 'paystack'])
  PAYMENT_PROVIDER: string = 'mock';

  @IsOptional()
  @IsString()
  PAYSTACK_SECRET_KEY?: string;

  @IsOptional()
  @IsString()
  PAYSTACK_WEBHOOK_SECRET?: string;

  @IsOptional()
  @IsString()
  PAYSTACK_PUBLIC_KEY?: string;

  @IsOptional()
  @IsUrl({ require_tld: false })
  PAYSTACK_CALLBACK_URL?: string;

  @IsIn(['mock', 'external'])
  NOTIFICATION_PROVIDER: string = 'mock';

  @IsOptional()
  @IsUrl({ require_tld: false })
  NOTIFICATION_API_URL?: string;

  @IsOptional()
  @IsString()
  NOTIFICATION_API_KEY?: string;

  @IsOptional()
  @IsString()
  SENDCHAMP_API_KEY?: string;

  @IsOptional()
  @IsString()
  SENDCHAMP_SENDER_ID?: string;

  @IsOptional()
  @IsString()
  RESEND_API_KEY?: string;

  @IsOptional()
  @IsString()
  RESEND_FROM_EMAIL?: string;
}

export function validateEnvironment(config: Record<string, unknown>): EnvironmentVariables {
  const validatedConfig = plainToInstance(EnvironmentVariables, config, {
    enableImplicitConversion: true,
  });
  const errors = validateSync(validatedConfig, { skipMissingProperties: false });

  if (errors.length > 0) {
    throw new Error(errors.toString());
  }

  if (validatedConfig.NODE_ENV === 'production') {
    const productionErrors = productionReadinessErrors(validatedConfig);
    if (productionErrors.length > 0) {
      throw new Error(`Production readiness configuration failed: ${productionErrors.join('; ')}`);
    }
  }

  return validatedConfig;
}

function productionReadinessErrors(config: EnvironmentVariables): string[] {
  const errors: string[] = [];
  if (!config.REDIS_URL && (!config.UPSTASH_REDIS_REST_URL || !config.UPSTASH_REDIS_REST_TOKEN)) {
    errors.push('Redis URL or Upstash REST URL/token are required');
  }
  if (config.AI_TRIAGE_PROVIDER === 'mock') {
    errors.push('AI_TRIAGE_PROVIDER must not be mock');
  }
  if (!config.GEMINI_API_KEY) {
    errors.push('Gemini API key is required');
  }
  if (config.VIDEO_PROVIDER === 'mock') {
    errors.push('VIDEO_PROVIDER must not be mock');
  }
  if (!config.DAILY_API_KEY || !config.DAILY_DOMAIN) {
    errors.push('Daily API key and domain are required');
  }
  if (config.PAYMENT_PROVIDER !== 'paystack') {
    errors.push('PAYMENT_PROVIDER must be paystack');
  }
  if (!config.PAYSTACK_SECRET_KEY || !config.PAYSTACK_WEBHOOK_SECRET) {
    errors.push('Paystack live secret and webhook secret are required');
  }
  if (config.NOTIFICATION_PROVIDER === 'mock') {
    errors.push('NOTIFICATION_PROVIDER must not be mock');
  }
  if (!config.SENDCHAMP_API_KEY || !config.SENDCHAMP_SENDER_ID) {
    errors.push('Sendchamp API key and sender ID are required');
  }
  if (!config.RESEND_API_KEY || !config.RESEND_FROM_EMAIL) {
    errors.push('Resend API key and sender email/domain are required');
  }
  return errors;
}
