import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Route, Router, RouterStateSnapshot, Routes, UrlTree, provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { Role } from './core/auth/auth.models';
import { AuthService } from './core/auth/auth.service';
import { exampleUser } from './testing/auth-fixtures';

/**
 * The route table, not the guard functions: every protected URL must run the guards that keep
 * other roles out. Lazy child routes are loaded and matched segment by segment.
 */
type Outcome = 'allowed' | 'login' | 'forbidden' | string;

async function childrenOf(route: Route): Promise<Routes> {
  if (route.children) return route.children;
  if (route.loadChildren) {
    const loaded = await (route.loadChildren as () => Promise<Routes>)();
    return loaded;
  }
  return [];
}

/** All guards on the first matching route chain for the URL, or null when nothing matches. */
async function guardsFor(segments: string[], table: Routes): Promise<CanActivateFn[] | null> {
  for (const route of table) {
    if (route.redirectTo !== undefined) continue;
    const pattern = (route.path ?? '').split('/').filter(Boolean);
    if (pattern.length > segments.length) continue;
    if (!pattern.every((part, i) => part.startsWith(':') || part === segments[i])) continue;
    const rest = segments.slice(pattern.length);
    const own = (route.canActivate ?? []) as CanActivateFn[];
    if (rest.length === 0 && (route.loadComponent || route.component)) {
      const children = await childrenOf(route);
      const index = children.find((child) => (child.path ?? '') === '' && !child.redirectTo);
      const inner = index ? ((index.canActivate ?? []) as CanActivateFn[]) : [];
      return [...own, ...inner];
    }
    const children = await childrenOf(route);
    if (children.length === 0) continue;
    const nested = await guardsFor(rest, children);
    if (nested) return [...own, ...nested];
  }
  return null;
}

async function visit(url: string, roles: Role[] | null): Promise<Outcome> {
  const user = roles ? { ...exampleUser, roles } : null;
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: AuthService, useValue: { user: () => user, isSignedIn: () => !!user, hasRole: (r: Role) => !!roles?.includes(r) } },
    ],
  });
  const guards = await guardsFor(url.split('/').filter(Boolean), routes);
  if (!guards) throw new Error(`No route matches ${url}`);
  const router = TestBed.inject(Router);
  for (const guard of guards) {
    const result = TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot));
    if (result === true) continue;
    const tree = router.serializeUrl(result as UrlTree);
    if (tree.startsWith('/login')) return 'login';
    if (tree.includes('notice=forbidden')) return 'forbidden';
    return tree;
  }
  return 'allowed';
}

describe('Route table guards', () => {
  it.each([
    '/dispatcher', '/dispatcher/payments', '/dispatcher/cash-reconciliation', '/dispatcher/users',
    '/warehouse', '/tracking/reports', '/merchant/create-order', '/merchant/finance',
    '/courier/cash-handovers', '/customer/bookings', '/customer/receipt/abc', '/account', '/webhooks',
  ])('sends signed-out visitors from %s to login', async (url) => {
    expect(await visit(url, null)).toBe('login');
  });

  it.each([
    ['/dispatcher/payments'], ['/dispatcher/merchant-finance'], ['/dispatcher/pricing-rules'],
    ['/warehouse'], ['/tracking/reports'], ['/merchant/create-order'], ['/merchant/api-keys'],
    ['/courier/deliveries'], ['/courier/cash-handovers'],
  ])('keeps a plain customer out of %s', async (url) => {
    expect(await visit(url, ['CUSTOMER'])).toBe('forbidden');
  });

  it.each([
    ['/dispatcher/payments', ['DISPATCHER']],
    ['/dispatcher/cash-reconciliation', ['DISPATCHER']],
    ['/warehouse', ['DISPATCHER']],
    ['/tracking/reports', ['DISPATCHER']],
    ['/merchant/create-order', ['MERCHANT']],
    ['/merchant/receipt/abc', ['MERCHANT']],
    ['/courier/cash-handovers', ['COURIER']],
    ['/customer/bookings', ['CUSTOMER']],
    ['/merchant/application', ['CUSTOMER']],
    ['/courier/application', ['CUSTOMER']],
    ['/account', ['COURIER']],
  ] as [string, Role[]][])('lets the right role into %s', async (url, roles) => {
    expect(await visit(url, roles)).toBe('allowed');
  });

  it.each([
    ['/customer/bookings', ['DISPATCHER']],
    ['/dispatcher/users', ['MERCHANT']],
    ['/merchant/finance', ['COURIER']],
    ['/courier/deliveries', ['MERCHANT']],
  ] as [string, Role[]][])('keeps other roles out of %s', async (url, roles) => {
    expect(await visit(url, roles)).toBe('forbidden');
  });

  it('leaves public tracking open to everyone', async () => {
    expect(await visit('/tracking', null)).toBe('allowed');
  });

  it('sends signed-in users away from login and register', async () => {
    expect(await visit('/login', ['DISPATCHER'])).toBe('/dispatcher');
    expect(await visit('/register', ['MERCHANT'])).toBe('/merchant/dashboard');
    expect(await visit('/login', null)).toBe('allowed');
  });
});
