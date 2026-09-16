export enum Role {
  Patient = 'patient',
  Clinician = 'clinician',
  HospitalAdmin = 'hospital_admin',
  HmoStaff = 'hmo_staff',
  FundAdmin = 'fund_admin',
  SuperAdmin = 'super_admin',
}

export enum Permission {
  ReadOwnProfile = 'profile:read:own',
  ManageClinicalTriage = 'triage:manage',
  ManageHospitalReferrals = 'referrals:manage:hospital',
  ManageHmoClaims = 'claims:manage:hmo',
  ManageFund = 'fund:manage',
  ManagePlatform = 'platform:manage',
}

export const ROLE_PERMISSIONS: Record<Role, ReadonlySet<Permission>> = {
  [Role.Patient]: new Set([Permission.ReadOwnProfile]),
  [Role.Clinician]: new Set([Permission.ReadOwnProfile, Permission.ManageClinicalTriage]),
  [Role.HospitalAdmin]: new Set([Permission.ReadOwnProfile, Permission.ManageHospitalReferrals]),
  [Role.HmoStaff]: new Set([Permission.ReadOwnProfile, Permission.ManageHmoClaims]),
  [Role.FundAdmin]: new Set([Permission.ReadOwnProfile, Permission.ManageFund]),
  [Role.SuperAdmin]: new Set(Object.values(Permission)),
};

export function hasPermission(role: Role, permission: Permission): boolean {
  return ROLE_PERMISSIONS[role]?.has(permission) ?? false;
}
