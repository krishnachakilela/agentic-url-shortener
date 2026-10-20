import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { ApiService } from '../../app/core/api.service';
import { MetricsResponse } from '../../app/core/api.models';

@Component({
  selector: 'app-metrics-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './metrics-page.component.html',
  styleUrl: './metrics-page.component.css'
})
export class MetricsPageComponent implements OnInit {
  private readonly api = inject(ApiService);
  metrics: MetricsResponse | null = null;
  error = '';

  ngOnInit(): void {
    this.api.getMetrics().subscribe({
      next: (metrics) => (this.metrics = metrics),
      error: () => (this.error = 'Unable to load metrics')
    });
  }

  statusEntries(): Array<{ key: string; value: number }> {
    if (!this.metrics) {
      return [];
    }
    return Object.entries(this.metrics.runsByStatus).map(([key, value]) => ({ key, value }));
  }
}
