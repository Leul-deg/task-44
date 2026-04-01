import api from './axios';

export const fetchCategories = () => api.get('/categories');
export const fetchStates = () => api.get('/locations/states');
export const fetchLocations = (state) => api.get('/locations', { params: state ? { state } : {} });

export default {
  fetchCategories,
  fetchStates,
  fetchLocations
};
