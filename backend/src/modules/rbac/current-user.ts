import type { Role } from './roles';

export type CurrentUser = {
  id: string;
  role: Role;
};
