import {CommonModule} from '@angular/common';
import {Component, OnInit, inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';
import { ApiService } from '../../app/core/api.service';
import { RunResponse, ScenarioType } from '../../app/core/api.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);

  runs: RunResponse[] = [];
  loading = false;
  error = '';
  scenario: ScenarioType = 'GREENFIELD';
  title = '';
  requirementText = '';

  readonly scenarios: Array<{ value: ScenarioType; label: string; preset: string }> = [
    {
      value: 'GREENFIELD',
      label: 'Greenfield',
      preset: 'Build a URL shortener with core APIs, analytics, and reliability features from scratch.'
    },
    {
      value: 'BROWNFIELD',
      label: 'Brownfield',
      preset: 'Add link expiration and click analytics export to the existing URL shortener service.'
    },
    {
      value: 'AMBIGUOUS',
      label: 'Ambiguous',
      preset: 'Make the shortener more reliable and enterprise ready.'
    }
  ];

  ngOnInit(): void {
    this.applyPreset();
    this.refresh();
  }

  applyPreset(): void {
    const selected = this.scenarios.find((item) => item.value === this.scenario);
    this.title = `${selected?.label ?? 'Run'} demo`;
    this.requirementText = selected?.preset ?? '';
  }

  refresh(): void {
    this.loading = true;
    this.error = '';
    this.api.listRuns().subscribe({
      next: (runs) => {
        this.runs = runs;
        this.loading = false;
      },
      error: () => {
        this.error = 'Unable to load runs. Is the backend running on :8080?';
        this.loading = false;
      }
    });
  }

  startRun(): void {
    this.loading = true;
    this.error = '';
    this.api.startRun(this.scenario, this.title, this.requirementText).subscribe({
      next: () => {
        setTimeout(() => this.refresh(), 800);
      },
      error: () => {
        this.error = 'Failed to start orchestration run.';
        this.loading = false;
      }
    });
  }
}
