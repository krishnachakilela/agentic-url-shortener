import {HttpClient} from '@angular/common/http';
import {Injectable, inject} from '@angular/core';
import {Observable} from 'rxjs';
import {environment} from '../../environments/environment';
import {
  AnalyticsView,
  AuditResponse,
  MetricsResponse,
  RunResponse,
  ScenarioType,
  ShortUrlResponse
} from './api.models'

@Injectable({providedIn: 'root'})
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  createShortUrl(url: string, expiresAt?: string): Observable<ShortUrlResponse> {
    return this.http.post<ShortUrlResponse>(`${this.base}/api/v1/urls`, {url, expiresAt});
  }

  getAnalytics(code: string): Observable<AnalyticsView> {
    return this.http.get<AnalyticsView>(`${this.base}/api/v1/urls/${code}/analytics`);
  }

  listRuns(): Observable<RunResponse[]> {
    return this.http.get<RunResponse[]>(`${this.base}/api/v1/orchestration/runs`);
  }

  getRun(runId: string): Observable<RunResponse> {
    return this.http.get<RunResponse>(`${this.base}/api/v1/orchestration/runs/${runId}`);
  }

  startRun(scenario: ScenarioType, title: string, requirementText: string): Observable<RunResponse> {
    return this.http.post<RunResponse>(`${this.base}/api/v1/orchestration/runs`, {
      scenario,
      title,
      requirementText
    });
  }

  getAudit(runId: string): Observable<AuditResponse[]> {
    return this.http.get<AuditResponse[]>(`${this.base}/api/v1/orchestration/runs/${runId}/audit`);
  }

  decideGate(
    runId: string,
    stageId: string,
    approved: boolean,
    decidedBy: string,
    reason: string
  ): Observable<RunResponse> {
    return this.http.post<RunResponse>(
      `${this.base}/api/v1/orchestration/runs/${runId}/stages/${stageId}/decision`,
      {approved, decidedBy, reason}
    );
  }

  safeStop(runId: string, actor: string): Observable<RunResponse> {
    return this.http.post<RunResponse>(`${this.base}/api/v1/orchestration/runs/${runId}/safe-stop`, {
      actor
    });
  }

  rollback(runId: string, actor: string): Observable<RunResponse> {
    return this.http.post<RunResponse>(`${this.base}/api/v1/orchestration/runs/${runId}/rollback`, {
      actor
    });
  }

  replan(runId: string, updatedRequirement: string, actor: string): Observable<RunResponse> {
    return this.http.post<RunResponse>(`${this.base}/api/v1/orchestration/runs/${runId}/replan`, {
      updatedRequirement,
      actor
    });
  }

  getMetrics(): Observable<MetricsResponse> {
    return this.http.get<MetricsResponse>(`${this.base}/api/v1/orchestration/metrics`);
  }
}
