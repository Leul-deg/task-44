import api from './axios';

export const listBackups = () => api.get('/admin/backup/list');

export const triggerBackup = (stepUpPassword) =>
  api.post('/admin/backup/trigger', { stepUpPassword });

export const restoreBackup = (id, stepUpPassword) =>
  api.post(`/admin/backup/restore/${id}`, { stepUpPassword, confirm: 'RESTORE' });
