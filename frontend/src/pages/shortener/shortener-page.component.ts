import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../app/core/api.service';
import { AnalyticsView, ShortUrlResponse } from '../../app/core/api.models';

@Component({
  selector: 'app-shortener-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './shortener-page.component.html',
  styleUrl: './shortener-page.component.css'
})
export class ShortenerPageComponent {
  private readonly api = inject(ApiService);

  url = 'https://example.com/agentic-demo';
  expiresAtLocal = '';
  created: ShortUrlResponse | null = null;
  analytics: AnalyticsView | null = null;
  error = '';

  readonly minExpiresAt = ShortenerPageComponent.toDateTimeLocalValue(new Date());

  create(): void {
    this.error = '';
    const expiresAt = this.toIsoInstant(this.expiresAtLocal);
    if (this.expiresAtLocal && !expiresAt) {
      return;
    }
    this.api.createShortUrl(this.url, expiresAt).subscribe({
      next: (created) => {
        this.created = created;
        this.loadAnalytics(created.shortCode);
      },
      error: () => (this.error = 'Create failed. Check URL scheme/host policy.')
    });
  }

  clearExpiry(): void {
    this.expiresAtLocal = '';
  }

  loadAnalytics(code: string): void {
    this.api.getAnalytics(code).subscribe({
      next: (analytics) => (this.analytics = analytics),
      error: () => (this.error = 'Analytics load failed')
    });
  }

  private toIsoInstant(localValue: string): string | undefined {
    if (!localValue) {
      return undefined;
    }
    const parsed = new Date(localValue);
    if (Number.isNaN(parsed.getTime())) {
      this.error = 'Invalid expiration date.';
      return undefined;
    }
    return parsed.toISOString();
  }

  private static toDateTimeLocalValue(date: Date): string {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }
}
