import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AuditService } from '../audit/audit.service';
import { RequestUser } from '../auth/current-user.decorator';
import {
  CreateAllergyDto,
  CreateChronicConditionDto,
  CreateEmergencyContactDto,
  UpsertPatientProfileDto,
} from './dto/patient-profile.dto';

@Injectable()
export class PatientService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  async getProfile(user: RequestUser) {
    const profile = await this.prisma.patientProfile.findUnique({
      where: { userId: user.id },
      include: {
        user: { select: { id: true, email: true, phone: true, role: true } },
      },
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'patient.profile.view',
      target: `patient:${user.id}`,
    });

    return profile;
  }

  async upsertProfile(user: RequestUser, dto: UpsertPatientProfileDto) {
    const data = this.profileData(dto);
    const profile = await this.prisma.patientProfile.upsert({
      where: { userId: user.id },
      create: {
        userId: user.id,
        ...data,
      },
      update: data,
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'patient.profile.upsert',
      target: `patient:${user.id}`,
    });

    return profile;
  }

  async addEmergencyContact(user: RequestUser, dto: CreateEmergencyContactDto) {
    const contact = await this.prisma.emergencyContact.create({
      data: {
        userId: user.id,
        name: dto.name,
        phone: dto.phone,
        relationship: dto.relationship,
        priority: dto.priority ?? 1,
      },
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'patient.emergency_contact.create',
      target: `emergency_contact:${contact.id}`,
    });

    return contact;
  }

  async listEmergencyContacts(user: RequestUser) {
    return this.prisma.emergencyContact.findMany({
      where: { userId: user.id },
      orderBy: [{ priority: 'asc' }, { createdAt: 'desc' }],
    });
  }

  async deleteEmergencyContact(user: RequestUser, id: string) {
    await this.assertOwned('emergencyContact', user.id, id);
    await this.prisma.emergencyContact.delete({ where: { id } });
    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'patient.emergency_contact.delete',
      target: `emergency_contact:${id}`,
    });
    return { deleted: true };
  }

  async addAllergy(user: RequestUser, dto: CreateAllergyDto) {
    const allergy = await this.prisma.allergy.create({
      data: { userId: user.id, ...dto },
    });
    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'patient.allergy.create',
      target: `allergy:${allergy.id}`,
    });
    return allergy;
  }

  async listAllergies(user: RequestUser) {
    return this.prisma.allergy.findMany({
      where: { userId: user.id },
      orderBy: { createdAt: 'desc' },
    });
  }

  async deleteAllergy(user: RequestUser, id: string) {
    await this.assertOwned('allergy', user.id, id);
    await this.prisma.allergy.delete({ where: { id } });
    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'patient.allergy.delete',
      target: `allergy:${id}`,
    });
    return { deleted: true };
  }

  async addChronicCondition(user: RequestUser, dto: CreateChronicConditionDto) {
    const condition = await this.prisma.chronicCondition.create({
      data: {
        userId: user.id,
        name: dto.name,
        notes: dto.notes,
        diagnosedAt: dto.diagnosedAt ? new Date(dto.diagnosedAt) : undefined,
      },
    });
    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'patient.chronic_condition.create',
      target: `chronic_condition:${condition.id}`,
    });
    return condition;
  }

  async listChronicConditions(user: RequestUser) {
    return this.prisma.chronicCondition.findMany({
      where: { userId: user.id },
      orderBy: { createdAt: 'desc' },
    });
  }

  async deleteChronicCondition(user: RequestUser, id: string) {
    await this.assertOwned('chronicCondition', user.id, id);
    await this.prisma.chronicCondition.delete({ where: { id } });
    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'patient.chronic_condition.delete',
      target: `chronic_condition:${id}`,
    });
    return { deleted: true };
  }

  private profileData(dto: UpsertPatientProfileDto) {
    return {
      firstName: dto.firstName,
      lastName: dto.lastName,
      dateOfBirth: dto.dateOfBirth ? new Date(dto.dateOfBirth) : undefined,
      gender: dto.gender,
      address: dto.address,
      city: dto.city,
      state: dto.state,
      country: dto.country ?? 'NG',
      bloodGroup: dto.bloodGroup,
      genotype: dto.genotype,
      nextOfKinName: dto.nextOfKinName,
      nextOfKinPhone: dto.nextOfKinPhone,
      nextOfKinRelationship: dto.nextOfKinRelationship,
    };
  }

  private async assertOwned(
    model: 'emergencyContact' | 'allergy' | 'chronicCondition',
    userId: string,
    id: string,
  ) {
    const record =
      model === 'emergencyContact'
        ? await this.prisma.emergencyContact.findFirst({ where: { id, userId } })
        : model === 'allergy'
          ? await this.prisma.allergy.findFirst({ where: { id, userId } })
          : await this.prisma.chronicCondition.findFirst({ where: { id, userId } });
    if (!record) {
      throw new NotFoundException('Resource not found.');
    }
  }
}
