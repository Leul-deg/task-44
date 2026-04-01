import { defineStore } from 'pinia';
import dictionaryApi from '@/api/dictionaries';

export const useDictionaryStore = defineStore('dictionaries', {
  state: () => ({
    categories: [],
    states: [],
    citiesByState: {},
    loading: false
  }),
  actions: {
    async ensureCategories() {
      if (this.categories.length) return;
      const { data } = await dictionaryApi.fetchCategories();
      this.categories = data;
    },
    async ensureStates() {
      if (this.states.length) return;
      const { data } = await dictionaryApi.fetchStates();
      this.states = data;
    },
    async loadCities(state) {
      if (!state) return [];
      if (this.citiesByState[state]) {
        return this.citiesByState[state];
      }
      const { data } = await dictionaryApi.fetchLocations(state);
      this.citiesByState[state] = data;
      return data;
    },
    async loadAllCities() {
      const { data } = await dictionaryApi.fetchLocations();
      this.citiesByState['*'] = data;
      return data;
    }
  }
});
