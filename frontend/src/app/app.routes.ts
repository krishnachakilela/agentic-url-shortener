import { Routes } from '@angular/router';
import { DashboardComponent } from '../pages/dashboard/dashboard';
import { MetricsPageComponent } from '../pages/metrics/metrics-page.component';
import { RunDetailComponent } from '../pages/run-detail/run-detail.component';
import { ShortenerPageComponent } from '../pages/shortener/shortener-page.component';

export const routes: Routes = [
  { path: '', component: DashboardComponent },
  { path: 'shortener', component: ShortenerPageComponent },
  { path: 'runs/:id', component: RunDetailComponent },
  { path: 'metrics', component: MetricsPageComponent },
  { path: '**', redirectTo: '' }
];
