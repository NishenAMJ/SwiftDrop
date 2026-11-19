import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { PageResponse, Warehouse, WarehouseParcel } from '../warehouse.models';
import { WarehouseService } from '../warehouse.service';
import { ParcelIntake } from './parcel-intake';

const warehouse = {
  id: 'warehouse-1',
  code: 'CMB-01',
  name: 'Colombo Hub',
  status: 'ACTIVE',
} as Warehouse;
const page = {
  content: [warehouse],
  totalElements: 1,
  totalPages: 1,
  size: 20,
  number: 0,
  first: true,
  last: true,
  empty: false,
} satisfies PageResponse<Warehouse>;
const parcel = {
  id: 'parcel-1',
  parcelReference: 'SWD-1001',
  warehouseId: warehouse.id,
  warehouseCode: warehouse.code,
  state: 'ARRIVED',
  arrivedAt: '2026-11-19T09:00:00Z',
  notes: null,
  updatedAt: '2026-11-19T09:00:00Z',
} satisfies WarehouseParcel;

describe('ParcelIntake', () => {
  let fixture: ComponentFixture<ParcelIntake>;
  let service: {
    list: ReturnType<typeof vi.fn>;
    recordArrival: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    service = {
      list: vi.fn().mockReturnValue(of(page)),
      recordArrival: vi.fn().mockReturnValue(of(parcel)),
    };

    await TestBed.configureTestingModule({
      imports: [ParcelIntake],
      providers: [{ provide: WarehouseService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(ParcelIntake);
    fixture.detectChanges();
  });

  it('selects the only active warehouse and records an arrival', () => {
    const component = fixture.componentInstance as any;
    expect(component.form.controls.warehouseId.value).toBe(warehouse.id);
    component.form.patchValue({ parcelReference: ' swd-1001 ', notes: ' Seal checked ' });

    component.submit();

    expect(service.recordArrival).toHaveBeenCalledWith({
      parcelReference: 'SWD-1001',
      warehouseId: warehouse.id,
      arrivedAt: null,
      notes: 'Seal checked',
    });
    expect(component.recordedParcel()).toEqual(parcel);
  });

  it('does not submit an incomplete intake form', () => {
    const component = fixture.componentInstance as any;
    component.submit();
    expect(service.recordArrival).not.toHaveBeenCalled();
  });
});
