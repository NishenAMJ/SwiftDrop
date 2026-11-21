import { Routes } from '@angular/router';

import { authGuard, dispatcherGuard } from '../../core/auth/auth.guards';
import { AppLayout } from '../../core/layout/app-layout/app-layout';

export const TRACKING_ROUTES: Routes = [
  {
    path: '',
    title: 'Track a parcel | SwiftDrop',
    loadComponent: () =>
      import('./public-tracking-page/public-tracking-page').then((m) => m.PublicTrackingPage),
  },
  {
    // Same URL as before, but inside the signed-in layout so the sidebar stays.
    path: 'reports',
    canActivate: [authGuard, dispatcherGuard],
    component: AppLayout,
    children: [
      {
        path: '',
        title: 'Tracking reports | SwiftDrop',
        loadComponent: () =>
          import('./reporting-page/reporting-page').then((m) => m.ReportingPage),
      },
    ],
  },
];
