import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Courier, CourierApplicationRequest } from './courier.models';

const COURIERS_API = '/api/v1/couriers';

/** The signed-in user's own courier application (identity-merchant-service). */
@Injectable({ providedIn: 'root' })
export class CourierService {
  private readonly http = inject(HttpClient);

  /** The user's application in any status; 404 when they have never applied. */
  me(): Observable<Courier> {
    return this.http.get<Courier>(`${COURIERS_API}/me`);
  }

  /** First application, or resubmission after a rejection (same endpoint). */
  apply(request: CourierApplicationRequest): Observable<Courier> {
    return this.http.post<Courier>(COURIERS_API, request);
  }
}
