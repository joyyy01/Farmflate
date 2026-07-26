import type { ViewStep } from './farmflate';

export type NavigationFlow =
  | { kind: 'NONE' }
  | { kind: 'FIELD_REGISTRATION'; returnTo: 'myfield' }
  | { kind: 'FIELD_REGISTRATION_TIPS'; returnTo: 'crop_suitability_report' };

export interface NavigationState {
  step: ViewStep;
  flow: NavigationFlow;
}
