import { Body, Controller, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { DispatchNotificationDto } from './dto/notifications.dto';
import { NotificationsService } from './notifications.service';

@ApiTags('notifications')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard)
@Controller('notifications')
export class NotificationsController {
  constructor(private readonly notifications: NotificationsService) {}

  @Post('dispatch')
  @ApiOkResponse({ description: 'Internal MVP notification dispatch endpoint.' })
  dispatch(@Body() dto: DispatchNotificationDto) {
    return this.notifications.dispatch(dto);
  }

  @Post('retry-failed')
  @ApiOkResponse({ description: 'Retry failed notification deliveries.' })
  retryFailed() {
    return this.notifications.retryFailed();
  }
}
