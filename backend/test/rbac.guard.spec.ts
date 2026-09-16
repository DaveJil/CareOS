import { ForbiddenException } from '@nestjs/common';
import type { Reflector } from '@nestjs/core';
import { RbacGuard } from '../src/modules/rbac/rbac.guard';
import { Permission, Role, hasPermission } from '../src/modules/rbac/roles';

describe('RBAC permissions', () => {
  it.each([
    [Role.Patient, Permission.ReadOwnProfile, true],
    [Role.Patient, Permission.ManagePlatform, false],
    [Role.Clinician, Permission.ManageClinicalTriage, true],
    [Role.HospitalAdmin, Permission.ManageHospitalReferrals, true],
    [Role.HmoStaff, Permission.ManageHmoClaims, true],
    [Role.FundAdmin, Permission.ManageFund, true],
    [Role.SuperAdmin, Permission.ManagePlatform, true],
    [Role.SuperAdmin, Permission.ManageFund, true],
  ])('checks %s for %s as %s', (role, permission, expected) => {
    expect(hasPermission(role, permission)).toBe(expected);
  });
});

describe('RbacGuard', () => {
  const reflector = {
    getAllAndOverride: jest.fn(),
  } as unknown as jest.Mocked<Reflector>;

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('allows routes without explicit permissions', () => {
    reflector.getAllAndOverride.mockReturnValue(undefined);
    const guard = new RbacGuard(reflector);

    expect(guard.canActivate(mockExecutionContext())).toBe(true);
  });

  it('allows a user with the required permission', () => {
    reflector.getAllAndOverride.mockReturnValue([Permission.ManageClinicalTriage]);
    const guard = new RbacGuard(reflector);

    expect(
      guard.canActivate(
        mockExecutionContext({ user: { id: 'clinician-1', role: Role.Clinician } }),
      ),
    ).toBe(true);
  });

  it('denies a user without the required permission', () => {
    reflector.getAllAndOverride.mockReturnValue([Permission.ManagePlatform]);
    const guard = new RbacGuard(reflector);

    expect(() =>
      guard.canActivate(mockExecutionContext({ user: { id: 'patient-1', role: Role.Patient } })),
    ).toThrow(ForbiddenException);
  });

  it('denies missing users on protected routes', () => {
    reflector.getAllAndOverride.mockReturnValue([Permission.ReadOwnProfile]);
    const guard = new RbacGuard(reflector);

    expect(() => guard.canActivate(mockExecutionContext())).toThrow(ForbiddenException);
  });
});

function mockExecutionContext(request: Record<string, unknown> = {}) {
  return {
    getHandler: jest.fn(),
    getClass: jest.fn(),
    switchToHttp: () => ({
      getRequest: () => request,
    }),
  } as never;
}
