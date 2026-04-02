export const JOB_STATUS_TYPE = {
  DRAFT: 'info',
  PENDING_REVIEW: 'warning',
  APPROVED: '',
  PUBLISHED: 'success',
  UNPUBLISHED: 'info',
  TAKEN_DOWN: 'danger',
  APPEAL_PENDING: 'warning'
};

export const APPEAL_STATUS_TYPE = {
  PENDING: 'warning',
  GRANTED: 'success',
  DENIED: 'danger'
};

export const REVIEW_ACTION_TYPE = {
  APPROVED: 'success',
  REJECTED: 'danger',
  TAKEN_DOWN: 'danger'
};

export const USER_ROLE_TYPE = {
  EMPLOYER: 'info',
  REVIEWER: 'warning',
  ADMIN: 'success'
};

export const USER_STATUS_TYPE = {
  ACTIVE: 'success',
  LOCKED: 'warning',
  DISABLED: 'danger'
};

export const BACKUP_STATUS_TYPE = {
  COMPLETED: 'success',
  FAILED: 'danger',
  IN_PROGRESS: 'warning'
};

export const PASSWORD_RULES = [
  { key: 'length', label: 'At least 12 characters', test: (pw) => pw.length >= 12 },
  { key: 'upper', label: 'Contains uppercase letter', test: (pw) => /[A-Z]/.test(pw) },
  { key: 'lower', label: 'Contains lowercase letter', test: (pw) => /[a-z]/.test(pw) },
  { key: 'digit', label: 'Contains a number', test: (pw) => /\d/.test(pw) },
  { key: 'special', label: 'Contains special character', test: (pw) => /[^A-Za-z0-9]/.test(pw) }
];

export function validatePasswordComplexity(password) {
  return PASSWORD_RULES.every((rule) => rule.test(password));
}

export function getPasswordChecklist(password) {
  return Object.fromEntries(PASSWORD_RULES.map((rule) => [rule.key, rule.test(password)]));
}
