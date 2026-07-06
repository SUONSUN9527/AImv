export type ChainType = 'IMAGE' | 'VIDEO';

export type ChainRunStatus =
  | 'CREATED'
  | 'CONTEXT_LOADING'
  | 'RAG_RETRIEVING'
  | 'PREVIOUS_REVIEWING'
  | 'CAPABILITY_PLANNING'
  | 'EXECUTING'
  | 'QUALITY_REVIEWING'
  | 'HANDOFF_WRITING'
  | 'KNOWLEDGE_INGESTING'
  | 'SUCCEEDED'
  | 'WAITING_USER'
  | 'WAITING_CAPABILITY'
  | 'WAITING_REVIEW'
  | 'FAILED'
  | 'CANCELLED';

export type ApiEnvelope<T> = {
  success: boolean;
  data: T;
  error: null | {
    code: string;
    message: string;
  };
};

export type ApiKeySummary = {
  apiKeyId: string;
  chainType: ChainType;
  capabilityType: string;
  label: string;
  provider: string;
  model?: string;
  maskedKey: string;
  status: 'PENDING_VERIFY' | 'AVAILABLE' | 'ACTIVE' | 'INVALID' | 'DELETED';
  isSelected: boolean;
  lastVerifiedAt: string | null;
  freeModelGateStatus: 'PENDING_VERIFY' | 'PASSED' | 'FAILED';
};

export type ApiConfigSlot = {
  chainType: ChainType;
  capabilityType: string;
  label: string;
  required: boolean;
  keys: ApiKeySummary[];
};

export type ReviewReport = {
  passed: boolean;
  overallScore: number;
  rubricVersion: string;
  summary: string;
};

export type StageRun = {
  stageRunId: string;
  chainRunId: string;
  stageCode: string;
  stageName: string;
  status: string;
  reviewReport: ReviewReport;
  retrievalRecordId: string;
  handoffContextId: string;
  agentNodeRunIds: string[];
  freeModelGateIds: string[];
  providerJobIds: string[];
};

export type ExternalJob = {
  externalJobId: string;
  providerJobId: string;
  chainRunId: string;
  stageRunId: string;
  capabilityType: string;
  provider: string;
  status: 'SUBMITTED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
  retryPolicy: string;
  retryCount: number;
  requestHash: string;
  responseMetadata: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
};

export type FreeModelGate = {
  freeModelGateId: string;
  passed: boolean;
  provider: string;
  model: string | null;
  capabilityType: string;
  plan: string;
  paidFallbackAllowed: boolean;
  quotaSnapshot: string;
  checkedAt: string;
};

export type ApiSelectionSnapshot = {
  snapshotId: string;
  chainRunId: string;
  chainType: ChainType;
  capabilityType: string;
  provider: string;
  apiKeyId: string;
  maskedKey: string;
  model: string | null;
  freeModelGate: FreeModelGate;
  createdAt: string;
};

export type Artifact = {
  artifactId: string;
  chainRunId: string;
  chainType: ChainType;
  artifactKind:
    | 'FinalImageArtifact'
    | 'FinalVideoArtifact'
    | 'ImageCandidateAssets'
    | 'VideoCandidateAssets'
    | 'ImageReviewReport'
    | 'VideoReviewReport';
  displayName: string;
  url: string;
  contentHash: string;
  metadata: Record<string, unknown>;
  createdAt: string;
};

export type Project = {
  projectId: string;
  title: string;
  goal: string;
  createdAt: string;
  // 该项目最新一次链路ID；后端持久化后返回，前端据此把项目历史项做成可点击直达 workspace。
  latestChainRunId?: string | null;
  // 该项目最新一次链路的服务端真实状态（SUCCEEDED/FAILED/EXECUTING…）；
  // 侧边栏据此展示状态，避免仅凭本地缓存在链路已完成后仍显示「生成中」。
  latestChainRunStatus?: string | null;
  // 置顶时间；非空表示该对话已置顶，侧边栏按置顶时间倒序排在最前。
  pinnedAt?: string | null;
};

export type ChainRun = {
  chainRunId: string;
  projectId: string;
  chainType: ChainType;
  userGoal: string;
  status: ChainRunStatus;
  currentStageCode: string;
  stageRuns: StageRun[];
  artifacts: Artifact[];
  blockingReason: string | null;
  createdAt: string;
  updatedAt: string;
};
