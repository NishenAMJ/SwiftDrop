import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { WarehouseDashboardData } from '../warehouse.models';
import { WarehouseService } from '../warehouse.service';
import { WarehouseDashboard } from './warehouse-dashboard';

const dashboard: WarehouseDashboardData = {
  warehouse: {
    id: 'warehouse-1',
    code: 'CMB-01',
    name: 'Colombo Central',
    city: 'Colombo',
    district: 'Colombo',
    status: 'ACTIVE',
  },
  arrivedParcels: 4,
  sortedParcels: 7,
  parcelsInTransfer: 2,
  incomingTransfers: 1,
  outgoingTransfers: 3,
  totalSortingRecords: 18,
  recentParcels: [],
  recentIncomingTransfers: [],
  recentOutgoingTransfers: [],
};

describe('WarehouseDashboard', () => {
  let fixture: ComponentFixture<WarehouseDashboard>;
  let warehouseService: { list: ReturnType<typeof vi.fn>; dashboard: ReturnType<typeof vi.fn> };

  async function createPage(): Promise<HTMLElement> {
    fixture = TestBed.createComponent(WarehouseDashboard);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    warehouseService = {
      list: vi.fn().mockReturnValue(of({ content: [dashboard.warehouse], totalElements: 1 })),
      dashboard: vi.fn().mockReturnValue(of(dashboard)),
    };

    TestBed.configureTestingModule({
      imports: [WarehouseDashboard],
      providers: [provideRouter([]), { provide: WarehouseService, useValue: warehouseService }],
    });
  });

  it('shows the selected warehouse metrics and empty activity states', async () => {
    const page = await createPage();

    expect(page.querySelector('h1')?.textContent).toContain('Warehouse dashboard');
    expect(page.textContent).toContain('Colombo Central');
    expect(page.textContent).toContain('Awaiting sorting');
    expect(page.textContent).toContain('No parcel activity has been recorded');
    expect(warehouseService.dashboard).toHaveBeenCalledWith('warehouse-1');
  });

  it('shows a safe retry message when warehouses cannot be loaded', async () => {
    warehouseService.list.mockReturnValueOnce(
      throwError(() => new Error('private server exception')),
    );
    const page = await createPage();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain(
      'We could not load the warehouses',
    );
    expect(page.textContent).not.toContain('private server exception');
  });
});
