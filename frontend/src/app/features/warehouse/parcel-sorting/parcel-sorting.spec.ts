import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { PageResponse, SortingRecord, Warehouse, WarehouseParcel } from '../warehouse.models';
import { WarehouseService } from '../warehouse.service';
import { ParcelSorting } from './parcel-sorting';

const source = {
  id: 'warehouse-1',
  code: 'CMB-01',
  name: 'Colombo Hub',
  status: 'ACTIVE',
} as Warehouse;
const destination = {
  id: 'warehouse-2',
  code: 'KND-01',
  name: 'Kandy Hub',
  status: 'ACTIVE',
} as Warehouse;
const page = {
  content: [source, destination],
  totalElements: 2,
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
  warehouseId: source.id,
  warehouseCode: source.code,
  state: 'ARRIVED',
  arrivedAt: '2026-11-19T09:00:00Z',
  notes: null,
  updatedAt: '2026-11-19T09:00:00Z',
} satisfies WarehouseParcel;
const sorting = {
  id: 'sorting-1',
  parcelReference: parcel.parcelReference,
  warehouseId: source.id,
  nextWarehouseId: destination.id,
  destination: 'Kandy',
  notes: null,
  sortedAt: '2026-11-19T10:00:00Z',
} satisfies SortingRecord;

describe('ParcelSorting', () => {
  let fixture: ComponentFixture<ParcelSorting>;
  let service: {
    list: ReturnType<typeof vi.fn>;
    getParcel: ReturnType<typeof vi.fn>;
    recordSorting: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    service = {
      list: vi.fn().mockReturnValue(of(page)),
      getParcel: vi.fn().mockReturnValue(of(parcel)),
      recordSorting: vi.fn().mockReturnValue(of(sorting)),
    };

    await TestBed.configureTestingModule({
      imports: [ParcelSorting],
      providers: [{ provide: WarehouseService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(ParcelSorting);
    fixture.detectChanges();
  });

  it('looks up a parcel and uses its current warehouse', () => {
    const component = fixture.componentInstance as any;
    component.form.controls.parcelReference.setValue(' swd-1001 ');
    component.lookupParcel();

    expect(service.getParcel).toHaveBeenCalledWith('SWD-1001');
    expect(component.form.controls.warehouseId.value).toBe(source.id);
  });

  it('records a valid sorting decision', () => {
    const component = fixture.componentInstance as any;
    component.form.setValue({
      parcelReference: 'SWD-1001',
      warehouseId: source.id,
      destination: ' Kandy ',
      nextWarehouseId: destination.id,
      sortedAt: '',
      notes: '',
    });

    component.submit();

    expect(service.recordSorting).toHaveBeenCalledWith({
      parcelReference: 'SWD-1001',
      warehouseId: source.id,
      destination: 'Kandy',
      nextWarehouseId: destination.id,
      sortedAt: null,
      notes: null,
    });
  });

  it('rejects a next warehouse that matches the current warehouse', () => {
    const component = fixture.componentInstance as any;
    component.form.setValue({
      parcelReference: 'SWD-1001',
      warehouseId: source.id,
      destination: 'Colombo',
      nextWarehouseId: source.id,
      sortedAt: '',
      notes: '',
    });

    component.submit();

    expect(service.recordSorting).not.toHaveBeenCalled();
    expect(component.error()).toContain('different');
  });
});
