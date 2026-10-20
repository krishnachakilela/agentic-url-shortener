import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MetricsPageComponent } from './metrics-page.component';

describe('Metrics', () => {
  let component: MetricsPageComponent;
  let fixture: ComponentFixture<MetricsPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MetricsPageComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MetricsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
