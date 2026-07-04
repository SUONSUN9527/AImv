export function maskProviderJobId(providerJobId: string) {
  const prefix = 'provider-job-';
  if (providerJobId.startsWith(prefix) && providerJobId.length > prefix.length + 8) {
    return `${prefix}${providerJobId.slice(prefix.length, prefix.length + 4)}...${providerJobId.slice(-4)}`;
  }
  if (providerJobId.length > 16) {
    return `${providerJobId.slice(0, 8)}...${providerJobId.slice(-4)}`;
  }
  return providerJobId;
}
