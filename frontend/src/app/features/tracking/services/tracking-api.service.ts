import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin } from 'rxjs';

import {
  DeliveryCounts,
  DeliveryInterval,
  ReportDashboardData,
  ReportFilters,
  ReportSummary,
  StatusBreakdown,
  TrackingHistory,
  TrackingResult,
  TrackingSummary,
} from '../models/tracking.models';

const PUBLIC_TRACKING_API = '/api/v1/public/tracking';
const REPORTS_API = '/api/v1/reports';

@Injectable({ providedIn: 'root' })
export class TrackingApiService {
  private readonly http = inject(HttpClient);

  getPublicTracking(trackingNumber: string): Observable<TrackingResult> {
    const encoded = encodeURIComponent(trackingNumber);
    return forkJoin({
      summary: this.http.get<TrackingSummary>(`${PUBLIC_TRACKING_API}/${encoded}`),
      history: this.http.get<TrackingHistory>(`${PUBLIC_TRACKING_API}/${encoded}/history`),
    });
  }

  getReports(filters: ReportFilters, interval: DeliveryInterval): Observable<ReportDashboardData> {
    const params = this.reportParams(filters);
    return forkJoin({
      summary: this.http.get<ReportSummary>(`${REPORTS_API}/summary`, { params }),
      statusBreakdown: this.http.get<StatusBreakdown>(`${REPORTS_API}/status-breakdown`, {
        params,
      }),
      deliveryCounts: this.http.get<DeliveryCounts>(`${REPORTS_API}/deliveries/${interval}`, {
        params,
      }),
    });
  }

  private reportParams(filters: ReportFilters): HttpParams {
    let params = new HttpParams();
    if (filters.startDate) {
      params = params.set('startDate', filters.startDate);
    }
    if (filters.endDate) {
      params = params.set('endDate', filters.endDate);
    }
    return params;
  }
}
