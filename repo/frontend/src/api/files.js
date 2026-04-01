import api from './axios';

export const uploadFile = (file, entityType, entityId) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('entityType', entityType);
  formData.append('entityId', entityId);
  return api.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

export const downloadFile = (id, exportPdf = false) =>
  api.get(`/files/${id}/download`, {
    params: exportPdf ? { export: true } : {},
    responseType: 'blob'
  });

export const getEntityFiles = (entityType, entityId) =>
  api.get(`/files/entity/${entityType}/${entityId}`);

export const getQuarantinedFiles = () =>
  api.get('/files/quarantined');

export const releaseFile = (id) =>
  api.put(`/files/${id}/release`);

export const deleteFile = (id) =>
  api.delete(`/files/${id}`);

export default {
  uploadFile,
  downloadFile,
  getEntityFiles,
  getQuarantinedFiles,
  releaseFile,
  deleteFile
};
