import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthLayout } from './auth-layout';

describe('AuthLayout', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [AuthLayout], providers: [provideRouter([])] });
  });

  it('marks the body as an auth page only while shown', () => {
    const fixture = TestBed.createComponent(AuthLayout);
    expect(document.body.classList).toContain('sd-auth-page');

    fixture.destroy();
    expect(document.body.classList).not.toContain('sd-auth-page');
  });

  it('keeps the decorative layers hidden from assistive technology', () => {
    const fixture = TestBed.createComponent(AuthLayout);
    const layers = fixture.nativeElement.querySelectorAll('.sd-aura__layer') as NodeListOf<HTMLElement>;
    expect(layers.length).toBe(2);
    layers.forEach((layer) => expect(layer.getAttribute('aria-hidden')).toBe('true'));
  });
});
