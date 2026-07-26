import { describe, expect, it } from 'vitest';
import { displayFieldDailyStatus } from '../constants/displayLabels';

describe('field daily status labels', () => {
  it('shows danger as a first-class field status', () => {
    expect(displayFieldDailyStatus('DANGER')).toBe('위험');
  });
});
