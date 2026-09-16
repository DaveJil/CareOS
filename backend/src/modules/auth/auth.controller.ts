import { Body, Controller, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiCreatedResponse, ApiOkResponse, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from './jwt-auth.guard';
import { AuthService } from './auth.service';
import { CurrentUser, RequestUser } from './current-user.decorator';
import {
  BiometricExchangeDto,
  LoginDto,
  LogoutDto,
  PasswordResetConfirmDto,
  PasswordResetRequestDto,
  RefreshDto,
  RegisterDto,
  VerifyOtpDto,
} from './dto/auth.dto';

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(private readonly auth: AuthService) {}

  @Post('register')
  @ApiCreatedResponse({ description: 'Account created and token pair returned.' })
  register(@Body() dto: RegisterDto) {
    return this.auth.register(dto);
  }

  @Post('login')
  @ApiOkResponse({ description: 'Credentials verified and token pair returned.' })
  login(@Body() dto: LoginDto) {
    return this.auth.login(dto);
  }

  @Post('otp/verify-phone')
  @ApiOkResponse({ description: 'Phone OTP verified.' })
  verifyPhoneOtp(@Body() dto: VerifyOtpDto) {
    return this.auth.verifyPhoneOtp(dto);
  }

  @Post('refresh')
  @ApiOkResponse({ description: 'Refresh token rotated and new token pair returned.' })
  refresh(@Body() dto: RefreshDto) {
    return this.auth.refresh(dto);
  }

  @Post('biometric/exchange')
  @ApiOkResponse({ description: 'Trusted biometric device exchanged for a token pair.' })
  exchangeBiometric(@Body() dto: BiometricExchangeDto) {
    return this.auth.exchangeBiometric(dto);
  }

  @Post('logout')
  @ApiOkResponse({ description: 'Refresh token revoked.' })
  logout(@Body() dto: LogoutDto) {
    return this.auth.logout(dto);
  }

  @Post('logout-all')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOkResponse({ description: 'All refresh tokens for the current user revoked.' })
  logoutAll(@CurrentUser() user: RequestUser) {
    return this.auth.logoutAll(user.id, user.role);
  }

  @Post('password-reset/request')
  @ApiOkResponse({ description: 'Password reset OTP sent if the account exists.' })
  requestPasswordReset(@Body() dto: PasswordResetRequestDto) {
    return this.auth.requestPasswordReset(dto);
  }

  @Post('password-reset/confirm')
  @ApiOkResponse({ description: 'Password reset confirmed and all sessions revoked.' })
  confirmPasswordReset(@Body() dto: PasswordResetConfirmDto) {
    return this.auth.confirmPasswordReset(dto);
  }
}
