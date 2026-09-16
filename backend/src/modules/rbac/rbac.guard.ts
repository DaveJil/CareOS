import { CanActivate, ExecutionContext, ForbiddenException, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { CurrentUser } from './current-user';
import { PERMISSIONS_KEY } from './permissions.decorator';
import { Permission, Role, hasPermission } from './roles';

type AuthenticatedRequest = {
  user?: CurrentUser;
};

@Injectable()
export class RbacGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const requiredPermissions = this.reflector.getAllAndOverride<Permission[]>(PERMISSIONS_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);

    if (!requiredPermissions || requiredPermissions.length === 0) {
      return true;
    }

    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const user = request.user;

    if (!user || !this.isRole(user.role)) {
      throw new ForbiddenException('User does not have access to this resource.');
    }

    const allowed = requiredPermissions.every((permission) => hasPermission(user.role, permission));

    if (!allowed) {
      throw new ForbiddenException('User does not have access to this resource.');
    }

    return true;
  }

  private isRole(role: string): role is Role {
    return Object.values(Role).includes(role as Role);
  }
}
