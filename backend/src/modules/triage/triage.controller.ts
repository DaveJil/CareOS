import { Body, Controller, Get, Param, Patch, Post, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';
import { CurrentUser, RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { SubmitTriageDto, TriageHistoryQueryDto } from './dto/triage.dto';
import { TriageService } from './triage.service';

@ApiTags('triage')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard)
@Controller('triage')
export class TriageController {
  constructor(private readonly triage: TriageService) {}

  @Post()
  @ApiOkResponse({ description: 'Submit symptoms and receive triage result.' })
  submit(@CurrentUser() user: RequestUser, @Body() dto: SubmitTriageDto) {
    return this.triage.submit(user, dto);
  }

  @Get('history')
  @ApiOkResponse({ description: 'Current patient triage history.' })
  history(@CurrentUser() user: RequestUser, @Query() query: TriageHistoryQueryDto) {
    return this.triage.history(user, query);
  }

  @Get('review-queue')
  @ApiOkResponse({ description: 'High-urgency triage cases awaiting clinician review.' })
  queue(@CurrentUser() user: RequestUser) {
    return this.triage.clinicianQueue(user);
  }

  @Patch(':id/reviewed')
  @ApiOkResponse({ description: 'Mark a high-urgency triage case as reviewed.' })
  markReviewed(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.triage.markReviewed(user, id);
  }
}
