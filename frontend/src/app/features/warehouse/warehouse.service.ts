import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CreateSortingRecordRequest,
  CreateTransferRequest,
  CreateWarehouseRequest,
  PageResponse,
  ParcelTransfer,
  ReceiveTransferRequest,
  RecordArrivalRequest,
  SortingRecord,
  TransferFilters,
  UpdateWarehouseRequest,
  Warehouse,
  WarehouseDashboardData,
  WarehouseParcel,
  WarehouseStatus,
} from './warehouse.models';

export const WAREHOUSE_API = '/api/v1/warehouses';
export const WAREHOUSE_PARCEL_API = '/api/v1/warehouse-parcels';
export const SORTING_API = '/api/v1/sorting-records';
export const TRANSFER_API = '/api/v1/transfers';

@Injectable({ providedIn: 'root' })
export class WarehouseService {
  private readonly http = inject(HttpClient);

  list(page = 0, size = 100): Observable<PageResponse<Warehouse>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', 'name');
    return this.http.get<PageResponse<Warehouse>>(WAREHOUSE_API, { params });
  }

  get(warehouseId: string): Observable<Warehouse> {
    return this.http.get<Warehouse>(`${WAREHOUSE_API}/${encodeURIComponent(warehouseId)}`);
  }

  create(request: CreateWarehouseRequest): Observable<Warehouse> {
    return this.http.post<Warehouse>(WAREHOUSE_API, request);
  }

  update(warehouseId: string, request: UpdateWarehouseRequest): Observable<Warehouse> {
    return this.http.put<Warehouse>(
      `${WAREHOUSE_API}/${encodeURIComponent(warehouseId)}`,
      request,
    );
  }

  changeStatus(warehouseId: string, status: WarehouseStatus): Observable<Warehouse> {
    return this.http.patch<Warehouse>(
      `${WAREHOUSE_API}/${encodeURIComponent(warehouseId)}/status`,
      { status },
    );
  }

  dashboard(warehouseId: string): Observable<WarehouseDashboardData> {
    return this.http.get<WarehouseDashboardData>(
      `${WAREHOUSE_API}/${encodeURIComponent(warehouseId)}/dashboard`,
    );
  }

  recordArrival(request: RecordArrivalRequest): Observable<WarehouseParcel> {
    return this.http.post<WarehouseParcel>(`${WAREHOUSE_PARCEL_API}/arrivals`, request);
  }

  getParcel(parcelReference: string): Observable<WarehouseParcel> {
    return this.http.get<WarehouseParcel>(
      `${WAREHOUSE_PARCEL_API}/${encodeURIComponent(parcelReference)}`,
    );
  }

  recordSorting(request: CreateSortingRecordRequest): Observable<SortingRecord> {
    return this.http.post<SortingRecord>(SORTING_API, request);
  }

  listTransfers(filters: TransferFilters = {}): Observable<PageResponse<ParcelTransfer>> {
    let params = new HttpParams()
      .set('page', filters.page ?? 0)
      .set('size', filters.size ?? 20)
      .set('sort', 'createdAt,desc');
    const parcelReference = filters.parcelReference?.trim();
    if (parcelReference) {
      params = params.set('parcelReference', parcelReference);
    }
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    return this.http.get<PageResponse<ParcelTransfer>>(TRANSFER_API, { params });
  }

  getTransfer(transferId: string): Observable<ParcelTransfer> {
    return this.http.get<ParcelTransfer>(`${TRANSFER_API}/${encodeURIComponent(transferId)}`);
  }

  createTransfer(request: CreateTransferRequest): Observable<ParcelTransfer> {
    return this.http.post<ParcelTransfer>(TRANSFER_API, request);
  }

  dispatchTransfer(transferId: string): Observable<ParcelTransfer> {
    return this.http.post<ParcelTransfer>(
      `${TRANSFER_API}/${encodeURIComponent(transferId)}/dispatch`,
      {},
    );
  }

  receiveTransfer(
    transferId: string,
    request: ReceiveTransferRequest,
  ): Observable<ParcelTransfer> {
    return this.http.post<ParcelTransfer>(
      `${TRANSFER_API}/${encodeURIComponent(transferId)}/receive`,
      request,
    );
  }

  cancelTransfer(transferId: string): Observable<ParcelTransfer> {
    return this.http.post<ParcelTransfer>(
      `${TRANSFER_API}/${encodeURIComponent(transferId)}/cancel`,
      {},
    );
  }
}
