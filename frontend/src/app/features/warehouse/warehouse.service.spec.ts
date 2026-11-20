import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import {
  CreateSortingRecordRequest,
  CreateTransferRequest,
  CreateWarehouseRequest,
  ReceiveTransferRequest,
  RecordArrivalRequest,
} from './warehouse.models';
import {
  SORTING_API,
  TRANSFER_API,
  WAREHOUSE_API,
  WAREHOUSE_PARCEL_API,
  WarehouseService,
} from './warehouse.service';

describe('WarehouseService', () => {
  let service: WarehouseService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), WarehouseService],
    });
    service = TestBed.inject(WarehouseService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists warehouses with stable paging and sorting', () => {
    service.list(2, 25).subscribe();

    const request = http.expectOne(
      (candidate) => candidate.url === WAREHOUSE_API && candidate.params.get('page') === '2',
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('size')).toBe('25');
    expect(request.request.params.get('sort')).toBe('name');
    request.flush({ content: [], totalElements: 0 });
  });

  it('creates, updates and changes a warehouse status', () => {
    const create: CreateWarehouseRequest = {
      code: 'CMB-01',
      name: 'Colombo Central',
      addressLine1: 'Operations Road',
      addressLine2: null,
      city: 'Colombo',
      district: 'Colombo',
      postalCode: null,
      contactName: 'Operations lead',
      contactPhone: '0110000000',
      contactEmail: null,
    };

    service.create(create).subscribe();
    const createRequest = http.expectOne(WAREHOUSE_API);
    expect(createRequest.request.method).toBe('POST');
    expect(createRequest.request.body).toEqual(create);
    createRequest.flush({});

    const { code: _code, ...update } = create;
    service.update('warehouse/1', update).subscribe();
    const updateRequest = http.expectOne(`${WAREHOUSE_API}/warehouse%2F1`);
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body).toEqual(update);
    updateRequest.flush({});

    service.changeStatus('warehouse-1', 'MAINTENANCE').subscribe();
    const statusRequest = http.expectOne(`${WAREHOUSE_API}/warehouse-1/status`);
    expect(statusRequest.request.method).toBe('PATCH');
    expect(statusRequest.request.body).toEqual({ status: 'MAINTENANCE' });
    statusRequest.flush({});
  });

  it('records arrivals, parcel lookups and sorting decisions', () => {
    const arrival: RecordArrivalRequest = {
      parcelReference: 'SD-100',
      warehouseId: 'warehouse-1',
      arrivedAt: null,
      notes: null,
    };
    service.recordArrival(arrival).subscribe();
    const arrivalRequest = http.expectOne(`${WAREHOUSE_PARCEL_API}/arrivals`);
    expect(arrivalRequest.request.body).toEqual(arrival);
    arrivalRequest.flush({});

    service.getParcel('SD/100').subscribe();
    const parcelRequest = http.expectOne(`${WAREHOUSE_PARCEL_API}/SD%2F100`);
    expect(parcelRequest.request.method).toBe('GET');
    parcelRequest.flush({});

    const sorting: CreateSortingRecordRequest = {
      parcelReference: 'SD-100',
      warehouseId: 'warehouse-1',
      nextWarehouseId: 'warehouse-2',
      destination: 'Kandy',
      notes: null,
      sortedAt: null,
    };
    service.recordSorting(sorting).subscribe();
    const sortingRequest = http.expectOne(SORTING_API);
    expect(sortingRequest.request.method).toBe('POST');
    expect(sortingRequest.request.body).toEqual(sorting);
    sortingRequest.flush({});
  });

  it('filters and performs transfer lifecycle actions', () => {
    service
      .listTransfers({ parcelReference: ' SD-100 ', status: 'IN_TRANSIT', page: 1, size: 10 })
      .subscribe();
    const listRequest = http.expectOne(
      (candidate) => candidate.url === TRANSFER_API && candidate.params.get('page') === '1',
    );
    expect(listRequest.request.params.get('parcelReference')).toBe('SD-100');
    expect(listRequest.request.params.get('status')).toBe('IN_TRANSIT');
    expect(listRequest.request.params.get('sort')).toBe('createdAt,desc');
    listRequest.flush({ content: [], totalElements: 0 });

    const create: CreateTransferRequest = {
      parcelReference: 'SD-100',
      sourceWarehouseId: 'warehouse-1',
      destinationWarehouseId: 'warehouse-2',
    };
    service.createTransfer(create).subscribe();
    const createRequest = http.expectOne(TRANSFER_API);
    expect(createRequest.request.body).toEqual(create);
    createRequest.flush({});

    service.dispatchTransfer('transfer-1').subscribe();
    const dispatchRequest = http.expectOne(`${TRANSFER_API}/transfer-1/dispatch`);
    expect(dispatchRequest.request.method).toBe('POST');
    dispatchRequest.flush({});

    const receive: ReceiveTransferRequest = {
      receivedAt: null,
      receivedBy: 'Warehouse officer',
      notes: null,
    };
    service.receiveTransfer('transfer-1', receive).subscribe();
    const receiveRequest = http.expectOne(`${TRANSFER_API}/transfer-1/receive`);
    expect(receiveRequest.request.body).toEqual(receive);
    receiveRequest.flush({});

    service.cancelTransfer('transfer-2').subscribe();
    const cancelRequest = http.expectOne(`${TRANSFER_API}/transfer-2/cancel`);
    expect(cancelRequest.request.method).toBe('POST');
    cancelRequest.flush({});
  });
});
