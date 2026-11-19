import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { LandingPage } from './landing-page';

describe('LandingPage', () => {
  let page: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [LandingPage],
      providers: [provideRouter([])],
    });
    const fixture = TestBed.createComponent(LandingPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
  });

  const links = (text: string) =>
    [...page.querySelectorAll('a')].filter((a) => a.textContent?.trim() === text);

  it('has one main heading and labelled landmarks', () => {
    expect(page.querySelectorAll('h1').length).toBe(1);
    expect(page.querySelector('h1')?.textContent).toContain('Send, track and deliver parcels');
    expect(page.querySelector('main')).not.toBeNull();
    expect(page.querySelector('nav[aria-label="Main"]')).not.toBeNull();
    expect(page.querySelector('nav[aria-label="Footer"]')).not.toBeNull();
  });

  it('links to sign-up, log in and public tracking', () => {
    expect(links('Create an account')[0].getAttribute('href')).toBe('/register');
    expect(links('Log in').every((a) => a.getAttribute('href') === '/login')).toBe(true);
    const track = links('Track a parcel');
    expect(track.length).toBeGreaterThan(0);
    expect(track.every((a) => a.getAttribute('href') === '/tracking')).toBe(true);
  });

  it('describes each kind of account', () => {
    const titles = [...page.querySelectorAll('.landing-audience__title')].map((h) => h.textContent?.trim());
    expect(titles).toEqual(['Customers', 'Merchants', 'Couriers']);
  });

  it('hides decorative icons and step numbers from assistive technology', () => {
    for (const icon of page.querySelectorAll('.landing-audience__icon, .landing-step__number')) {
      expect(icon.getAttribute('aria-hidden')).toBe('true');
    }
  });
});
