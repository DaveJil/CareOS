import { Controller, Get, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOkResponse, ApiTags } from '@nestjs/swagger';
import { RequirePermissions } from '../rbac/permissions.decorator';
import { Permission } from '../rbac/roles';
import { RbacGuard } from '../rbac/rbac.guard';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { CurrentUser, RequestUser } from '../auth/current-user.decorator';

@ApiTags('protected')
@ApiBearerAuth()
@Controller('protected')
export class ProtectedController {
  @Get('profile')
  @UseGuards(JwtAuthGuard, RbacGuard)
  @RequirePermissions(Permission.ReadOwnProfile)
  @ApiOkResponse({ description: 'Sample protected endpoint for RBAC verification.' })
  profile(@CurrentUser() user: RequestUser) {
    return {
      id: user.id,
      role: user.role,
      allowed: true,
    };
  }
}
