import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { PageResponse, Warehouse, WarehouseDashboardData } from './warehouse.models';

const WAREHOUSE_API = '/api/v1/warehouses';

@Injectable({ providedIn: 'root' })
export class WarehouseService {
  private readonly http = inject(HttpClient);

  list(): Observable<PageResponse<Warehouse>> {
    const params = new HttpParams().set('size', 100).set('sort', 'name');
    return this.http.get<PageResponse<Warehouse>>(WAREHOUSE_API, { params });
  }

  dashboard(warehouseId: string): Observable<WarehouseDashboardData> {
    return this.http.get<WarehouseDashboardData>(`${WAREHOUSE_API}/${warehouseId}/dashboard`);
  }
}
