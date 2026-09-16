import { Body, Controller, Get, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { CurrentUser, RequestUser } from '../auth/current-user.decorator';
import { ConsentService } from './consent.service';
import { GrantConsentDto, RevokeConsentDto } from './dto/consent.dto';

@ApiTags('consent')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard)
@Controller('consents')
export class ConsentController {
  constructor(private readonly consent: ConsentService) {}

  @Post()
  @ApiOkResponse({ description: 'Consent granted and auditable history entry stored.' })
  grant(@CurrentUser() user: RequestUser, @Body() dto: GrantConsentDto) {
    return this.consent.grant(user, dto);
  }

  @Get()
  @ApiOkResponse({ description: 'Consent history for the current user.' })
  list(@CurrentUser() user: RequestUser) {
    return this.consent.list(user);
  }

  @Post('revoke')
  @ApiOkResponse({ description: 'Latest active consent of the requested type revoked.' })
  revoke(@CurrentUser() user: RequestUser, @Body() dto: RevokeConsentDto) {
    return this.consent.revoke(user, dto.type);
  }
}
