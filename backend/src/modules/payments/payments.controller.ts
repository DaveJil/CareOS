import { Body, Controller, Get, Headers, Param, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';
import { CurrentUser, RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { CreateChargeDto, PaymentWebhookDto } from './dto/payments.dto';
import { PaymentsService } from './payments.service';

@ApiTags('payments')
@Controller('payments')
export class PaymentsController {
  constructor(private readonly payments: PaymentsService) {}

  @Post('charges')
  @ApiBearerAuth()
  @UseGuards(JwtAuthGuard)
  @ApiOkResponse({ description: 'Create or return an idempotent booking charge.' })
  createCharge(@CurrentUser() user: RequestUser, @Body() dto: CreateChargeDto) {
    return this.payments.createCharge(user, dto);
  }

  @Post('webhook')
  @ApiOkResponse({ description: 'Payment provider webhook confirmation endpoint.' })
  webhook(@Body() dto: PaymentWebhookDto, @Headers('x-paystack-signature') signature?: string) {
    return this.payments.handleWebhook(dto, signature);
  }

  @Get('admin/status')
  @ApiBearerAuth()
  @UseGuards(JwtAuthGuard)
  status(@CurrentUser() user: RequestUser) {
    return this.payments.listPaymentStatus(user);
  }

  @Get(':id/receipt')
  @ApiBearerAuth()
  @UseGuards(JwtAuthGuard)
  receipt(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.payments.receipt(user, id);
  }
}
