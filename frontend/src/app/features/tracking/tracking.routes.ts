import { Routes } from '@angular/router';

import { authGuard, dispatcherGuard } from '../../core/auth/auth.guards';

export const TRACKING_ROUTES: Routes = [
  {
    path: '',
    title: 'Track a parcel | SwiftDrop',
    loadComponent: () =>
      import('./public-tracking-page/public-tracking-page').then((m) => m.PublicTrackingPage),
  },
  {
    path: 'reports',
    title: 'Tracking reports | SwiftDrop',
    canActivate: [authGuard, dispatcherGuard],
    loadComponent: () => import('./reporting-page/reporting-page').then((m) => m.ReportingPage),
  },
];
