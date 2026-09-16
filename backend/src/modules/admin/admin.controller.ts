import { Controller, Get, Header, Param, Patch, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';
import { CurrentUser, RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { AdminService } from './admin.service';

@ApiTags('admin')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard)
@Controller('admin')
export class AdminController {
  constructor(private readonly admin: AdminService) {}

  @Get('patients')
  @ApiOkResponse({ description: 'List registered patients and their profile summaries.' })
  listPatients(@CurrentUser() user: RequestUser) {
    return this.admin.listPatients(user);
  }

  @Get('clinicians')
  @ApiOkResponse({ description: 'List clinicians and approval profiles.' })
  listClinicians(@CurrentUser() user: RequestUser) {
    return this.admin.listClinicians(user);
  }

  @Patch('clinicians/:id/approve')
  approveClinician(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.admin.approveClinician(user, id);
  }

  @Get('triage/high-urgency')
  highUrgencyQueue(@CurrentUser() user: RequestUser) {
    return this.admin.highUrgencyQueue(user);
  }

  @Get('payments')
  paymentSummary(@CurrentUser() user: RequestUser) {
    return this.admin.paymentSummary(user);
  }

  @Get('audit.csv')
  @Header('Content-Type', 'text/csv')
  auditCsv(@CurrentUser() user: RequestUser) {
    return this.admin.auditCsv(user);
  }
}
