import { describe, it, expect } from 'vitest';
import {
  JOB_STATUS_TYPE,
  APPEAL_STATUS_TYPE,
  REVIEW_ACTION_TYPE,
  USER_ROLE_TYPE,
  USER_STATUS_TYPE,
  BACKUP_STATUS_TYPE
} from '@/constants/statuses';

describe('Shared status constants', () => {
  it('JOB_STATUS_TYPE covers all job lifecycle statuses', () => {
    expect(JOB_STATUS_TYPE).toHaveProperty('DRAFT');
    expect(JOB_STATUS_TYPE).toHaveProperty('PENDING_REVIEW');
    expect(JOB_STATUS_TYPE).toHaveProperty('APPROVED');
    expect(JOB_STATUS_TYPE).toHaveProperty('PUBLISHED');
    expect(JOB_STATUS_TYPE).toHaveProperty('UNPUBLISHED');
    expect(JOB_STATUS_TYPE).toHaveProperty('TAKEN_DOWN');
    expect(JOB_STATUS_TYPE).toHaveProperty('APPEAL_PENDING');
  });

  it('APPEAL_STATUS_TYPE has PENDING, GRANTED, DENIED', () => {
    expect(Object.keys(APPEAL_STATUS_TYPE)).toEqual(
      expect.arrayContaining(['PENDING', 'GRANTED', 'DENIED'])
    );
  });

  it('REVIEW_ACTION_TYPE has APPROVED, REJECTED, TAKEN_DOWN', () => {
    expect(REVIEW_ACTION_TYPE.APPROVED).toBe('success');
    expect(REVIEW_ACTION_TYPE.REJECTED).toBe('danger');
    expect(REVIEW_ACTION_TYPE.TAKEN_DOWN).toBe('danger');
  });

  it('USER_ROLE_TYPE maps all three roles', () => {
    expect(USER_ROLE_TYPE.EMPLOYER).toBe('info');
    expect(USER_ROLE_TYPE.REVIEWER).toBe('warning');
    expect(USER_ROLE_TYPE.ADMIN).toBe('success');
  });

  it('USER_STATUS_TYPE maps active, locked, disabled', () => {
    expect(USER_STATUS_TYPE.ACTIVE).toBe('success');
    expect(USER_STATUS_TYPE.LOCKED).toBe('warning');
    expect(USER_STATUS_TYPE.DISABLED).toBe('danger');
  });

  it('BACKUP_STATUS_TYPE covers completed, failed, in_progress', () => {
    expect(BACKUP_STATUS_TYPE.COMPLETED).toBe('success');
    expect(BACKUP_STATUS_TYPE.FAILED).toBe('danger');
    expect(BACKUP_STATUS_TYPE.IN_PROGRESS).toBe('warning');
  });
});
