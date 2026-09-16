import { Body, Controller, Get, Param, Post, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';
import { CurrentUser, RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { SearchDocumentsQueryDto, UploadDocumentDto } from './dto/vault.dto';
import { VaultService } from './vault.service';

@ApiTags('medical-vault')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard)
@Controller('vault/documents')
export class VaultController {
  constructor(private readonly vault: VaultService) {}

  @Post()
  @ApiOkResponse({ description: 'Encrypted patient document uploaded.' })
  upload(@CurrentUser() user: RequestUser, @Body() dto: UploadDocumentDto) {
    return this.vault.upload(user, dto);
  }

  @Get()
  @ApiOkResponse({ description: 'Current patient documents, optionally filtered.' })
  list(@CurrentUser() user: RequestUser, @Query() query: SearchDocumentsQueryDto) {
    return this.vault.list(user, query);
  }

  @Get(':id')
  @ApiOkResponse({ description: 'Current patient document metadata.' })
  get(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.vault.get(user, id);
  }
}
