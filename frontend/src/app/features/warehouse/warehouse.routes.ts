import { Routes } from '@angular/router';

export const WAREHOUSE_ROUTES: Routes = [
  {
    path: '',
    title: 'Warehouse operations | SwiftDrop',
    loadComponent: () =>
      import('./warehouse-dashboard/warehouse-dashboard').then((m) => m.WarehouseDashboard),
  },
];
