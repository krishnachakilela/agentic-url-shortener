import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([])]
    }).compileComponents();
  });

  it('should render brand title', () => {
    // Arrange
    const fixture = TestBed.createComponent(AppComponent);

    // Act
    fixture.detectChanges();

    // Assert
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Agentic URL Shortener');
  });
});
