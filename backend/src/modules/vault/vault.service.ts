import { Injectable, NotFoundException } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AuditService } from '../audit/audit.service';
import { RequestUser } from '../auth/current-user.decorator';
import { EncryptedStorageService } from './encrypted-storage.service';
import { SearchDocumentsQueryDto, UploadDocumentDto } from './dto/vault.dto';

@Injectable()
export class VaultService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly storage: EncryptedStorageService,
    private readonly audit: AuditService,
  ) {}

  async upload(user: RequestUser, dto: UploadDocumentDto) {
    const stored = await this.storage.storePatientDocument({
      patientId: user.id,
      originalName: dto.originalName,
      mimeType: dto.mimeType,
      contentBase64: dto.contentBase64,
    });

    const document = await this.prisma.medicalDocument.create({
      data: {
        userId: user.id,
        type: dto.type,
        title: dto.title,
        description: dto.description,
        objectKey: stored.objectKey,
        originalName: dto.originalName,
        mimeType: dto.mimeType,
        sizeBytes: stored.sizeBytes,
        checksumSha256: stored.checksumSha256,
        tags: dto.tags ?? [],
        encrypted: true,
        scanStatus: 'clean',
      },
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'vault.document.upload',
      target: `medical_document:${document.id}`,
      metadata: {
        type: document.type,
        sizeBytes: document.sizeBytes,
        mimeType: document.mimeType,
      } as Prisma.InputJsonValue,
    });

    return this.withoutStorageSecret(document);
  }

  async list(user: RequestUser, query: SearchDocumentsQueryDto) {
    const documents = await this.prisma.medicalDocument.findMany({
      where: {
        userId: user.id,
        ...(query.type ? { type: query.type } : {}),
        ...(query.query
          ? {
              OR: [
                { title: { contains: query.query, mode: 'insensitive' } },
                { description: { contains: query.query, mode: 'insensitive' } },
                { tags: { has: query.query } },
              ],
            }
          : {}),
      },
      orderBy: { createdAt: 'desc' },
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'vault.document.list',
      target: `patient:${user.id}`,
      metadata: { query: { type: query.type, query: query.query } } as Prisma.InputJsonValue,
    });

    return documents.map((document) => this.withoutStorageSecret(document));
  }

  async get(user: RequestUser, id: string) {
    const document = await this.prisma.medicalDocument.findFirst({
      where: { id, userId: user.id },
    });

    if (!document) {
      throw new NotFoundException('Document not found.');
    }

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'vault.document.view',
      target: `medical_document:${id}`,
    });

    return this.withoutStorageSecret(document);
  }

  private withoutStorageSecret<T extends { objectKey: string }>(document: T): Omit<T, 'objectKey'> {
    const { objectKey: _objectKey, ...publicDocument } = document;
    return publicDocument;
  }
}
