import { Body, Controller, Get, Param, Patch, Post, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';
import { CurrentUser, RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import {
  CancelBookingDto,
  CreateBookingDto,
  RescheduleBookingDto,
  SearchSpecialistsQueryDto,
  SendChatMessageDto,
  UpsertClinicianProfileDto,
} from './dto/telehealth.dto';
import { TelehealthService } from './telehealth.service';

@ApiTags('telehealth')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard)
@Controller('telehealth')
export class TelehealthController {
  constructor(private readonly telehealth: TelehealthService) {}

  @Patch('clinician-profile')
  upsertClinicianProfile(@CurrentUser() user: RequestUser, @Body() dto: UpsertClinicianProfileDto) {
    return this.telehealth.upsertClinicianProfile(user, dto);
  }

  @Get('specialists')
  @ApiOkResponse({ description: 'Search approved specialists by specialty and availability.' })
  searchSpecialists(@Query() query: SearchSpecialistsQueryDto) {
    return this.telehealth.searchSpecialists(query);
  }

  @Post('bookings')
  createBooking(@CurrentUser() user: RequestUser, @Body() dto: CreateBookingDto) {
    return this.telehealth.createBooking(user, dto);
  }

  @Get('bookings/:id')
  getBooking(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.telehealth.getBooking(user, id);
  }

  @Patch('bookings/:id/reschedule')
  reschedule(
    @CurrentUser() user: RequestUser,
    @Param('id') id: string,
    @Body() dto: RescheduleBookingDto,
  ) {
    return this.telehealth.rescheduleBooking(user, id, dto.startsAt);
  }

  @Patch('bookings/:id/cancel')
  cancel(@CurrentUser() user: RequestUser, @Param('id') id: string, @Body() dto: CancelBookingDto) {
    return this.telehealth.cancelBooking(user, id, dto);
  }

  @Post('bookings/:id/chat')
  sendChat(
    @CurrentUser() user: RequestUser,
    @Param('id') id: string,
    @Body() dto: SendChatMessageDto,
  ) {
    return this.telehealth.sendChatMessage(user, id, dto);
  }

  @Get('bookings/:id/chat')
  listChat(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.telehealth.listChatMessages(user, id);
  }
}
