import { Routes } from '@angular/router';

export const WAREHOUSE_ROUTES: Routes = [
  {
    path: '',
    title: 'Warehouse operations | SwiftDrop',
    loadComponent: () =>
      import('./warehouse-dashboard/warehouse-dashboard').then((m) => m.WarehouseDashboard),
  },
  {
    path: 'warehouses',
    title: 'Manage warehouses | SwiftDrop',
    loadComponent: () =>
      import('./warehouse-management/warehouse-management').then((m) => m.WarehouseManagement),
  },
  {
    path: 'intake',
    title: 'Parcel intake | SwiftDrop',
    loadComponent: () => import('./parcel-intake/parcel-intake').then((m) => m.ParcelIntake),
  },
  {
    path: 'sorting',
    title: 'Parcel sorting | SwiftDrop',
    loadComponent: () => import('./parcel-sorting/parcel-sorting').then((m) => m.ParcelSorting),
  },
  {
    path: 'transfers',
    title: 'Parcel transfers | SwiftDrop',
    loadComponent: () =>
      import('./transfer-management/transfer-management').then((m) => m.TransferManagement),
  },
];
