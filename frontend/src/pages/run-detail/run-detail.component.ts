import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../app/core/api.service';
import { AuditResponse , RunResponse, StageResponse } from '../../app/core/api.models';

@Component({
  selector: 'app-run-detail',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './run-detail.component.html',
  styleUrl: './run-detail.component.css'
})
export class RunDetailComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private timer: ReturnType<typeof setInterval> | null = null;

  run: RunResponse | null = null;
  audit: AuditResponse[] = [];
  reason = 'Approved after review';
  decidedBy = 'reviewer';
  replanText = '';
  error = '';

  ngOnInit(): void {
    this.load();
    this.timer = setInterval(() => this.load(), 2000);
  }

  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  get pendingApprovals(): StageResponse[] {
    return this.run?.stages.filter((stage) => stage.status === 'AWAITING_APPROVAL') ?? [];
  }

  load(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      return;
    }
    this.api.getRun(id).subscribe({
      next: (run) => {
        this.run = run;
        this.replanText = run.requirementText;
      },
      error: () => (this.error = 'Unable to load run')
    });
    this.api.getAudit(id).subscribe({
      next: (audit) => (this.audit = audit),
      error: () => undefined
    });
  }

  decide(stage: StageResponse, approved: boolean): void {
    if (!this.run) {
      return;
    }
    this.api.decideGate(this.run.id, stage.id, approved, this.decidedBy, this.reason).subscribe({
      next: (run) => (this.run = run),
      error: () => (this.error = 'Gate decision failed')
    });
  }

  safeStop(): void {
    if (!this.run) {
      return;
    }
    this.api.safeStop(this.run.id, this.decidedBy).subscribe({
      next: (run) => (this.run = run),
      error: () => (this.error = 'Safe-stop failed')
    });
  }

  rollback(): void {
    if (!this.run) {
      return;
    }
    this.api.rollback(this.run.id, this.decidedBy).subscribe({
      next: (run) => (this.run = run),
      error: () => (this.error = 'Rollback failed')
    });
  }

  replan(): void {
    if (!this.run) {
      return;
    }
    this.api.replan(this.run.id, this.replanText, this.decidedBy).subscribe({
      next: (run) => (this.run = run),
      error: () => (this.error = 'Replan failed')
    });
  }
}
