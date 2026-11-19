import { Routes } from '@angular/router';

export const CUSTOMER_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'dashboard',
  },
  {
    path: 'dashboard',
    title: 'Customer Dashboard | SwiftDrop',
    loadComponent: () =>
      import('./customer-dashboard/customer-dashboard.component').then(
        (m) => m.CustomerDashboardComponent,
      ),
  },
  {
    path: 'book',
    title: 'Book Parcel | SwiftDrop',
    loadComponent: () =>
      import('./book-parcel/book-parcel.component').then((m) => m.BookParcelComponent),
  },
  {
    path: 'bookings',
    title: 'My Bookings | SwiftDrop',
    loadComponent: () =>
      import('./booking-history/booking-history.component').then(
        (m) => m.BookingHistoryComponent,
      ),
  },
  {
    path: 'receipt/:id',
    title: 'Booking Receipt | SwiftDrop',
    loadComponent: () =>
      import('./booking-receipt/booking-receipt.component').then(
        (m) => m.BookingReceiptComponent,
      ),
  },
  {
    path: 'account',
    title: 'Customer Profile | SwiftDrop',
    loadComponent: () =>
      import('./customer-account/customer-account.component').then(
        (m) => m.CustomerAccountComponent,
      ),
  },
];

