import { BloodGroup, MedicalDocumentType, Role } from '@prisma/client';
import { Buffer } from 'node:buffer';
import { PatientService } from '../src/modules/patient/patient.service';
import { VaultService } from '../src/modules/vault/vault.service';
import type { EncryptedStorageService } from '../src/modules/vault/encrypted-storage.service';

describe('Phase 2 PatientService', () => {
  it('creates and reads a patient profile plus supporting clinical facts', async () => {
    const { prisma, audit, patient } = createHarness();
    const user = { id: 'patient-1', role: Role.patient };

    const profile = await patient.upsertProfile(user, {
      firstName: 'Samira',
      lastName: 'Adebayo',
      dateOfBirth: '1992-05-12',
      bloodGroup: BloodGroup.O_positive,
      nextOfKinName: 'Musa Adebayo',
      nextOfKinPhone: '+2348012345678',
      nextOfKinRelationship: 'Brother',
    });
    const contact = await patient.addEmergencyContact(user, {
      name: 'Musa Adebayo',
      phone: '+2348012345678',
      relationship: 'Brother',
    });
    const allergy = await patient.addAllergy(user, {
      name: 'Penicillin',
      severity: 'severe',
    });
    const condition = await patient.addChronicCondition(user, {
      name: 'Hypertension',
      diagnosedAt: '2020-02-10',
    });

    expect(profile.firstName).toBe('Samira');
    expect(contact.relationship).toBe('Brother');
    expect(allergy.name).toBe('Penicillin');
    expect(condition.name).toBe('Hypertension');
    expect(await patient.listEmergencyContacts(user)).toHaveLength(1);
    expect(await patient.listAllergies(user)).toHaveLength(1);
    expect(await patient.listChronicConditions(user)).toHaveLength(1);
    expect(audit.records.map((record) => record.action)).toEqual([
      'patient.profile.upsert',
      'patient.emergency_contact.create',
      'patient.allergy.create',
      'patient.chronic_condition.create',
    ]);
    expect(prisma.patientProfiles).toHaveLength(1);
  });
});

describe('Phase 2 VaultService', () => {
  it('uploads encrypted document metadata and supports own-document search', async () => {
    const { audit, vault } = createHarness();
    const user = { id: 'patient-1', role: Role.patient };

    const uploaded = await vault.upload(user, {
      type: MedicalDocumentType.lab_result,
      title: 'Malaria test result',
      originalName: 'malaria.pdf',
      mimeType: 'application/pdf',
      contentBase64: Buffer.from('fake pdf bytes').toString('base64'),
      tags: ['lab', 'malaria'],
    });
    const documents = await vault.list(user, { query: 'malaria' });
    const viewed = await vault.get(user, uploaded.id);

    expect(uploaded.title).toBe('Malaria test result');
    expect('objectKey' in uploaded).toBe(false);
    expect(documents).toHaveLength(1);
    expect(viewed.id).toBe(uploaded.id);
    expect(audit.records.map((record) => record.action)).toEqual([
      'vault.document.upload',
      'vault.document.list',
      'vault.document.view',
    ]);
  });
});

function createHarness() {
  const prisma = createPrismaMock();
  const audit = {
    records: [] as Array<{ action: string; target: string }>,
    record: jest.fn(async (entry: { action: string; target: string }) => {
      audit.records.push(entry);
    }),
  };
  const storage = {
    storePatientDocument: jest.fn(async () => ({
      objectKey: 'patient-1/doc.enc',
      sizeBytes: 14,
      checksumSha256: 'abc123',
    })),
  } as unknown as EncryptedStorageService;

  return {
    prisma,
    audit,
    patient: new PatientService(prisma as never, audit as never),
    vault: new VaultService(prisma as never, storage, audit as never),
  };
}

function createPrismaMock() {
  const patientProfiles: Array<Record<string, unknown>> = [];
  const emergencyContacts: Array<Record<string, unknown>> = [];
  const allergies: Array<Record<string, unknown>> = [];
  const chronicConditions: Array<Record<string, unknown>> = [];
  const medicalDocuments: Array<Record<string, unknown>> = [];

  return {
    patientProfiles,
    patientProfile: {
      findUnique: jest.fn(
        async ({ where }) =>
          patientProfiles.find((profile) => profile.userId === where.userId) ?? null,
      ),
      upsert: jest.fn(async ({ where, create, update }) => {
        const existing = patientProfiles.find((profile) => profile.userId === where.userId);
        if (existing) {
          Object.assign(existing, update);
          return existing;
        }
        const profile = {
          id: `profile-${patientProfiles.length + 1}`,
          ...create,
          createdAt: new Date(),
          updatedAt: new Date(),
        };
        patientProfiles.push(profile);
        return profile;
      }),
    },
    emergencyContact: collectionModel(emergencyContacts),
    allergy: collectionModel(allergies),
    chronicCondition: collectionModel(chronicConditions),
    medicalDocument: {
      create: jest.fn(async ({ data }) => {
        const document = {
          id: `document-${medicalDocuments.length + 1}`,
          createdAt: new Date(),
          updatedAt: new Date(),
          ...data,
        };
        medicalDocuments.push(document);
        return document;
      }),
      findMany: jest.fn(async ({ where }) =>
        medicalDocuments.filter((document) => {
          const belongsToUser = document.userId === where.userId;
          const matchesType = !where.type || document.type === where.type;
          const query = where.OR?.[0]?.title?.contains as string | undefined;
          const matchesQuery =
            !query ||
            String(document.title).toLowerCase().includes(query.toLowerCase()) ||
            (document.tags as string[]).includes(query);
          return belongsToUser && matchesType && matchesQuery;
        }),
      ),
      findFirst: jest.fn(
        async ({ where }) =>
          medicalDocuments.find(
            (document) => document.id === where.id && document.userId === where.userId,
          ) ?? null,
      ),
    },
  };
}

function collectionModel(records: Array<Record<string, unknown>>) {
  return {
    create: jest.fn(async ({ data }) => {
      const record = {
        id: `record-${records.length + 1}`,
        createdAt: new Date(),
        updatedAt: new Date(),
        ...data,
      };
      records.push(record);
      return record;
    }),
    findMany: jest.fn(async ({ where }) =>
      records.filter((record) => record.userId === where.userId),
    ),
    findFirst: jest.fn(
      async ({ where }) =>
        records.find((record) => record.id === where.id && record.userId === where.userId) ?? null,
    ),
    delete: jest.fn(async ({ where }) => {
      const index = records.findIndex((record) => record.id === where.id);
      const [deleted] = records.splice(index, 1);
      return deleted;
    }),
  };
}
