import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { PageResponse, Warehouse } from '../warehouse.models';
import { WarehouseService } from '../warehouse.service';
import { WarehouseManagement } from './warehouse-management';

const warehouse: Warehouse = {
  id: 'warehouse-1',
  code: 'CMB-01',
  name: 'Colombo Hub',
  addressLine1: '1 Depot Road',
  addressLine2: null,
  city: 'Colombo',
  district: 'Colombo',
  postalCode: '00100',
  contactName: 'Operations desk',
  contactPhone: '0112345678',
  contactEmail: 'ops@example.com',
  status: 'ACTIVE',
  createdAt: '2026-11-19T08:00:00Z',
  updatedAt: '2026-11-19T08:00:00Z',
};

const page: PageResponse<Warehouse> = {
  content: [warehouse],
  totalElements: 1,
  totalPages: 1,
  size: 20,
  number: 0,
  first: true,
  last: true,
  empty: false,
};

describe('WarehouseManagement', () => {
  let fixture: ComponentFixture<WarehouseManagement>;
  let service: {
    list: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    changeStatus: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    service = {
      list: vi.fn().mockReturnValue(of(page)),
      create: vi.fn(),
      update: vi.fn(),
      changeStatus: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [WarehouseManagement],
      providers: [{ provide: WarehouseService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(WarehouseManagement);
    fixture.detectChanges();
  });

  it('loads warehouses and renders their operational status', () => {
    expect(fixture.nativeElement.textContent).toContain('Colombo Hub');
    expect(fixture.nativeElement.textContent).toContain('Active');
  });

  it('normalizes and creates a warehouse from a valid form', () => {
    service.create.mockReturnValue(of(warehouse));
    const component = fixture.componentInstance as any;
    component.beginCreate();
    component.form.setValue({
      code: 'cmb-01',
      name: ' Colombo Hub ',
      addressLine1: ' 1 Depot Road ',
      addressLine2: '',
      city: ' Colombo ',
      district: ' Colombo ',
      postalCode: ' 00100 ',
      contactName: ' Operations desk ',
      contactPhone: ' 0112345678 ',
      contactEmail: 'ops@example.com',
    });

    component.submit();

    expect(service.create).toHaveBeenCalledWith(
      expect.objectContaining({ code: 'CMB-01', addressLine2: null, city: 'Colombo' }),
    );
  });
});
