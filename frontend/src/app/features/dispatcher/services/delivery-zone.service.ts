import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Service for managing delivery zones (dispatcher role).
 * Mirrors the patterns used in other feature services.
 */
@Injectable({
  providedIn: 'root',
})
export class DeliveryZoneService {
  private readonly baseUrl = '/api/zones';

  constructor(private http: HttpClient) {}

  /** Retrieve all delivery zones */
  getZones(): Observable<Zone[]> {
    return this.http.get<Zone[]>(this.baseUrl);
  }

  /** Create a new delivery zone */
  createZone(request: CreateZoneRequest): Observable<any> {
    return this.http.post<any>(this.baseUrl, request);
  }

  /** Update an existing delivery zone */
  updateZone(id: string, request: UpdateZoneRequest): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/${id}`, request);
  }

  /** Change status (activate/deactivate) of a zone */
  changeStatus(id: string, status: 'ACTIVE' | 'INACTIVE'): Observable<any> {
    return this.http.patch<any>(`${this.baseUrl}/${id}/status`, { status });
  }
}

/** Zone model returned by the backend */
export interface Zone {
  id: string;
  code: string;
  name: string;
  description?: string;
  status: 'ACTIVE' | 'INACTIVE';
}

/** Payload for creating a zone */
export interface CreateZoneRequest {
  code: string;
  name: string;
  description?: string;
  status?: 'ACTIVE' | 'INACTIVE';
}

/** Payload for updating a zone */
export interface UpdateZoneRequest {
  code?: string;
  name?: string;
  description?: string;
  status?: 'ACTIVE' | 'INACTIVE';
}
