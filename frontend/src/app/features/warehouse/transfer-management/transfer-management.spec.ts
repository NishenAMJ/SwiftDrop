import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { PageResponse, ParcelTransfer, Warehouse } from '../warehouse.models';
import { WarehouseService } from '../warehouse.service';
import { TransferManagement } from './transfer-management';

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
const created: ParcelTransfer = {
  id: 'transfer-1',
  parcelReference: 'SWD-1001',
  sourceWarehouseId: source.id,
  sourceWarehouseCode: source.code,
  destinationWarehouseId: destination.id,
  destinationWarehouseCode: destination.code,
  status: 'CREATED',
  transferDate: null,
  receivedAt: null,
  receivedBy: null,
  receivingNotes: null,
  createdAt: '2026-11-19T09:00:00Z',
  updatedAt: '2026-11-19T09:00:00Z',
};
const inTransit: ParcelTransfer = {
  ...created,
  status: 'IN_TRANSIT',
  transferDate: '2026-11-19T10:00:00Z',
};
const received: ParcelTransfer = {
  ...inTransit,
  status: 'RECEIVED',
  receivedAt: '2026-11-19T12:00:00Z',
  receivedBy: 'Kandy operations',
};
const warehousePage = {
  content: [source, destination],
  totalElements: 2,
  totalPages: 1,
  size: 100,
  number: 0,
  first: true,
  last: true,
  empty: false,
} satisfies PageResponse<Warehouse>;
const transferPage = {
  content: [created, inTransit],
  totalElements: 2,
  totalPages: 1,
  size: 20,
  number: 0,
  first: true,
  last: true,
  empty: false,
} satisfies PageResponse<ParcelTransfer>;

describe('TransferManagement', () => {
  let fixture: ComponentFixture<TransferManagement>;
  let service: {
    list: ReturnType<typeof vi.fn>;
    listTransfers: ReturnType<typeof vi.fn>;
    createTransfer: ReturnType<typeof vi.fn>;
    dispatchTransfer: ReturnType<typeof vi.fn>;
    receiveTransfer: ReturnType<typeof vi.fn>;
    cancelTransfer: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    service = {
      list: vi.fn().mockReturnValue(of(warehousePage)),
      listTransfers: vi.fn().mockReturnValue(of(transferPage)),
      createTransfer: vi.fn().mockReturnValue(of(created)),
      dispatchTransfer: vi.fn().mockReturnValue(of(inTransit)),
      receiveTransfer: vi.fn().mockReturnValue(of(received)),
      cancelTransfer: vi.fn().mockReturnValue(of({ ...created, status: 'CANCELLED' })),
    };

    await TestBed.configureTestingModule({
      imports: [TransferManagement],
      providers: [{ provide: WarehouseService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(TransferManagement);
    fixture.detectChanges();
  });

  it('loads active warehouses and transfer activity', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('SWD-1001');
    expect(text).toContain('CMB-01');
    expect(text).toContain('In transit');
    expect(service.listTransfers).toHaveBeenCalledWith({
      parcelReference: '',
      status: '',
      page: 0,
      size: 20,
    });
  });

  it('creates a normalized transfer between different warehouses', () => {
    const component = fixture.componentInstance as any;
    component.createForm.setValue({
      parcelReference: ' swd-1001 ',
      sourceWarehouseId: source.id,
      destinationWarehouseId: destination.id,
    });

    component.createTransfer();

    expect(service.createTransfer).toHaveBeenCalledWith({
      parcelReference: 'SWD-1001',
      sourceWarehouseId: source.id,
      destinationWarehouseId: destination.id,
    });
  });

  it('requires explicit confirmation before cancelling a transfer', () => {
    const component = fixture.componentInstance as any;
    component.cancel(created);
    expect(service.cancelTransfer).not.toHaveBeenCalled();

    component.requestCancel(created);
    component.cancel(created);
    expect(service.cancelTransfer).toHaveBeenCalledWith(created.id);
  });

  it('records receipt details for an in-transit transfer', () => {
    const component = fixture.componentInstance as any;
    component.beginReceive(inTransit);
    component.receiveForm.setValue({
      receivedBy: ' Kandy operations ',
      receivedAt: '',
      notes: ' Seal verified ',
    });

    component.receive(inTransit);

    expect(service.receiveTransfer).toHaveBeenCalledWith(inTransit.id, {
      receivedBy: 'Kandy operations',
      receivedAt: null,
      notes: 'Seal verified',
    });
    expect(component.receivingId()).toBeNull();
  });
});
