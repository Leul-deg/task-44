import api from './axios';

export const getScheduledReports = () => api.get('/reports/scheduled');
export const createScheduledReport = (payload) => api.post('/reports/scheduled', payload);
export const updateScheduledReport = (id, payload) => api.put(`/reports/scheduled/${id}`, payload);
export const deleteScheduledReport = (id) => api.delete(`/reports/scheduled/${id}`);
export const getExports = () => api.get('/reports/exports');
export const downloadExport = (id) => api.get(`/reports/exports/${id}/download`, { responseType: 'blob' });

export default {
  getScheduledReports,
  createScheduledReport,
  updateScheduledReport,
  deleteScheduledReport,
  getExports,
  downloadExport
};
