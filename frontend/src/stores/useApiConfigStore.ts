import { defineStore } from 'pinia';
import { addApiKey, deleteApiKey, listApiConfigs, selectApiKey, verifyApiKey } from '../api/apiConfigs';
import type { ApiConfigSlot, ChainType } from '../types/api';

type AddKeyInput = {
  chainType: ChainType;
  capabilityType: string;
  provider: string;
  label: string;
  apiKey: string;
  model?: string;
};

export const useApiConfigStore = defineStore('apiConfigs', {
  state: () => ({
    slotsByChain: {
      IMAGE: [] as ApiConfigSlot[],
      VIDEO: [] as ApiConfigSlot[]
    },
    loading: false,
    errorMessage: ''
  }),
  getters: {
    hasMissingSelected:
      (state) =>
      (chainType: ChainType): boolean =>
        state.slotsByChain[chainType].some((slot) => slot.required && !slot.keys.some((key) => key.isSelected))
  },
  actions: {
    async load(chainType: ChainType) {
      this.loading = true;
      this.errorMessage = '';
      try {
        this.slotsByChain[chainType] = await listApiConfigs(chainType);
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '能力配置读取失败';
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async addKey(input: AddKeyInput) {
      this.loading = true;
      this.errorMessage = '';
      try {
        await addApiKey(input.chainType, input.capabilityType, {
          provider: input.provider,
          label: input.label,
          apiKey: input.apiKey,
          model: input.model
        });
        await this.load(input.chainType);
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : 'API key 添加失败';
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async verify(chainType: ChainType, apiKeyId: string) {
      this.loading = true;
      this.errorMessage = '';
      try {
        await verifyApiKey(apiKeyId);
        await this.load(chainType);
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : 'API key 测试失败';
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async select(chainType: ChainType, apiKeyId: string) {
      this.loading = true;
      this.errorMessage = '';
      try {
        await selectApiKey(apiKeyId);
        await this.load(chainType);
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : 'API key 选择失败';
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async remove(chainType: ChainType, apiKeyId: string) {
      this.loading = true;
      this.errorMessage = '';
      try {
        await deleteApiKey(apiKeyId);
        await this.load(chainType);
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : 'API key 删除失败';
        throw error;
      } finally {
        this.loading = false;
      }
    }
  }
});
