import { Body, Controller, Delete, Get, Param, Patch, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';
import { CurrentUser, RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import {
  CreateAllergyDto,
  CreateChronicConditionDto,
  CreateEmergencyContactDto,
  UpsertPatientProfileDto,
} from './dto/patient-profile.dto';
import { PatientService } from './patient.service';

@ApiTags('patient')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard)
@Controller('patient')
export class PatientController {
  constructor(private readonly patient: PatientService) {}

  @Get('profile')
  @ApiOkResponse({ description: 'Current patient profile.' })
  getProfile(@CurrentUser() user: RequestUser) {
    return this.patient.getProfile(user);
  }

  @Patch('profile')
  @ApiOkResponse({ description: 'Create or update the current patient profile.' })
  upsertProfile(@CurrentUser() user: RequestUser, @Body() dto: UpsertPatientProfileDto) {
    return this.patient.upsertProfile(user, dto);
  }

  @Post('emergency-contacts')
  addEmergencyContact(@CurrentUser() user: RequestUser, @Body() dto: CreateEmergencyContactDto) {
    return this.patient.addEmergencyContact(user, dto);
  }

  @Get('emergency-contacts')
  listEmergencyContacts(@CurrentUser() user: RequestUser) {
    return this.patient.listEmergencyContacts(user);
  }

  @Delete('emergency-contacts/:id')
  deleteEmergencyContact(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.patient.deleteEmergencyContact(user, id);
  }

  @Post('allergies')
  addAllergy(@CurrentUser() user: RequestUser, @Body() dto: CreateAllergyDto) {
    return this.patient.addAllergy(user, dto);
  }

  @Get('allergies')
  listAllergies(@CurrentUser() user: RequestUser) {
    return this.patient.listAllergies(user);
  }

  @Delete('allergies/:id')
  deleteAllergy(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.patient.deleteAllergy(user, id);
  }

  @Post('chronic-conditions')
  addChronicCondition(@CurrentUser() user: RequestUser, @Body() dto: CreateChronicConditionDto) {
    return this.patient.addChronicCondition(user, dto);
  }

  @Get('chronic-conditions')
  listChronicConditions(@CurrentUser() user: RequestUser) {
    return this.patient.listChronicConditions(user);
  }

  @Delete('chronic-conditions/:id')
  deleteChronicCondition(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.patient.deleteChronicCondition(user, id);
  }
}
