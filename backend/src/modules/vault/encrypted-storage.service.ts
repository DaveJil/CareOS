import { BadRequestException, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Buffer } from 'node:buffer';
import { createCipheriv, createHash, randomBytes } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

const MAX_FILE_BYTES = 10 * 1024 * 1024;
const ALLOWED_MIME_TYPES = new Set(['application/pdf', 'image/jpeg', 'image/png', 'image/webp']);

export type StoredEncryptedObject = {
  objectKey: string;
  sizeBytes: number;
  checksumSha256: string;
};

@Injectable()
export class EncryptedStorageService {
  constructor(private readonly config: ConfigService) {}

  async storePatientDocument(input: {
    patientId: string;
    originalName: string;
    mimeType: string;
    contentBase64: string;
  }): Promise<StoredEncryptedObject> {
    if (!ALLOWED_MIME_TYPES.has(input.mimeType)) {
      throw new BadRequestException('Unsupported document file type.');
    }

    const plainBuffer = Buffer.from(input.contentBase64, 'base64');
    if (plainBuffer.length === 0 || plainBuffer.length > MAX_FILE_BYTES) {
      throw new BadRequestException('Document file size is invalid.');
    }

    const checksumSha256 = createHash('sha256').update(plainBuffer).digest('hex');
    const key = this.encryptionKey();
    const iv = randomBytes(12);
    const cipher = createCipheriv('aes-256-gcm', key, iv);
    const encrypted = Buffer.concat([cipher.update(plainBuffer), cipher.final()]);
    const authTag = cipher.getAuthTag();
    const payload = Buffer.concat([iv, authTag, encrypted]);
    const objectKey = `${input.patientId}/${Date.now()}-${randomBytes(8).toString('hex')}.enc`;
    const fullPath = join(process.cwd(), 'storage', 'vault', objectKey);

    await mkdir(join(process.cwd(), 'storage', 'vault', input.patientId), { recursive: true });
    await writeFile(fullPath, payload, { flag: 'wx' });

    return {
      objectKey,
      sizeBytes: plainBuffer.length,
      checksumSha256,
    };
  }

  private encryptionKey(): Buffer {
    const secret = this.config.get<string>('DOCUMENT_ENCRYPTION_KEY');
    if (!secret || secret.length < 32) {
      throw new BadRequestException('Document encryption key is not configured correctly.');
    }
    return createHash('sha256').update(secret).digest();
  }
}
