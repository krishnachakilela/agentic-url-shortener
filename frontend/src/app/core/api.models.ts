export type ScenarioType = 'GREENFIELD' | 'BROWNFIELD' | 'AMBIGUOUS';
export type RunStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'AWAITING_APPROVAL'
  | 'REPLANNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'ROLLED_BACK'
  | 'SAFE_STOPPED';

export interface StageResponse {
  id: string;
  stageType: string;
  stageKey: string;
  status: string;
  sequenceOrder: number;
  requiresApproval: boolean;
  parallelGroup: boolean;
  dependsOn: string;
  outputJson: string | null;
  artifactPath: string | null;
  attempt: number;
}

export interface RunResponse {
  id: string;
  scenario: ScenarioType;
  title: string;
  requirementText: string;
  status: RunStatus;
  assumptionsJson: string;
  summaryMarkdown: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  retryCount: number;
  rollbackCount: number;
  stages: StageResponse[];
}

export interface AuditResponse {
  id: string;
  stageKey: string | null;
  eventType: string;
  actor: string;
  detailJson: string;
  occurredAt: string;
}

export interface MetricsResponse {
  successRate: number;
  retryCount: number;
  rollbackCount: number;
  mttrMillis: number;
  meanLatencyMillis: number;
  runsByStatus: Record<string, number>;
}

export interface ShortUrlResponse {
  shortCode: string;
  shortUrl: string;
  originalUrl: string;
  createdAt: string;
  expiresAt: string | null;
  clickCount: number;
}

export interface AnalyticsView {
  shortCode: string;
  originalUrl: string;
  totalClicks: number;
  clicksLast24h: number;
  createdAt: string;
  expiresAt: string | null;
  recentClicks: Array<{ occurredAt: string; userAgent: string; referrer: string }>;
}
