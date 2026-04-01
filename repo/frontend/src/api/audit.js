import api from './axios';

export const getAuditLogs = (params) => api.get('/admin/audit-logs', { params });

export const getAuditLogDetail = (id) => api.get(`/admin/audit-logs/${id}`);
