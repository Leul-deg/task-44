import { describe, it, expect } from 'vitest';
import {
  validatePasswordComplexity,
  getPasswordChecklist,
  PASSWORD_RULES
} from '@/constants/statuses';

describe('Password complexity validation', () => {
  it('rejects password shorter than 12 characters', () => {
    expect(validatePasswordComplexity('Abc!1')).toBe(false);
  });

  it('rejects password without uppercase letter', () => {
    expect(validatePasswordComplexity('abcdefghijk1!')).toBe(false);
  });

  it('rejects password without lowercase letter', () => {
    expect(validatePasswordComplexity('ABCDEFGHIJK1!')).toBe(false);
  });

  it('rejects password without digit', () => {
    expect(validatePasswordComplexity('Abcdefghijk!!')).toBe(false);
  });

  it('rejects password without special character', () => {
    expect(validatePasswordComplexity('Abcdefghijk12')).toBe(false);
  });

  it('accepts a valid complex password', () => {
    expect(validatePasswordComplexity('Admin@123456789')).toBe(true);
  });

  it('accepts another valid password at boundary length', () => {
    expect(validatePasswordComplexity('Abcdefghij1!')).toBe(true);
  });

  it('getPasswordChecklist returns per-rule status', () => {
    const checklist = getPasswordChecklist('short');
    expect(checklist.length).toBe(false);
    expect(checklist.lower).toBe(true);
    expect(checklist.upper).toBe(false);
    expect(checklist.digit).toBe(false);
    expect(checklist.special).toBe(false);
  });

  it('getPasswordChecklist all true for valid password', () => {
    const checklist = getPasswordChecklist('Admin@123456789');
    expect(Object.values(checklist).every(Boolean)).toBe(true);
  });

  it('PASSWORD_RULES has 5 rules', () => {
    expect(PASSWORD_RULES).toHaveLength(5);
  });
});
