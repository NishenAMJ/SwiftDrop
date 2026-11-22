import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AssignParcelRequest,
  AssignmentFilters,
  AssignmentStatus,
  CategoryStatus,
  CourierCategory,
  CourierProfile,
  CourierProfileFilters,
  CourierProfileStatus,
  CreateCourierCategoryRequest,
  CreateCourierProfileRequest,
  DeliveryAssignment,
  DeliveryAttempt,
  DeliveryIssue,
  DeliveryPage,
  IssueFilters,
  ProofOfDelivery,
  StatusChange,
  RecordAttemptRequest,
  RecordProofRequest,
  ReportIssueRequest,
  ReviewIssueRequest,
} from './courier-delivery.models';

export const COURIER_CATEGORY_API = '/api/v1/courier-categories';
// Not /api/v1/couriers: that prefix belongs to identity-merchant-service (applications, approvals).
export const COURIER_PROFILE_API = '/api/v1/courier-profiles';
export const ASSIGNMENT_API = '/api/v1/assignments';
export const DELIVERY_ISSUE_API = '/api/v1/delivery-issues';

const id = encodeURIComponent;

/** Courier categories, courier profiles, assignments, proofs and issues (delivery-operations-service). */
@Injectable({ providedIn: 'root' })
export class CourierDeliveryService {
  private readonly http = inject(HttpClient);

  // Categories
  listCategories(page = 0, size = 100): Observable<DeliveryPage<CourierCategory>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'name');
    return this.http.get<DeliveryPage<CourierCategory>>(COURIER_CATEGORY_API, { params });
  }

  createCategory(request: CreateCourierCategoryRequest): Observable<CourierCategory> {
    return this.http.post<CourierCategory>(COURIER_CATEGORY_API, request);
  }

  changeCategoryStatus(categoryId: string, status: CategoryStatus): Observable<CourierCategory> {
    return this.http.patch<CourierCategory>(`${COURIER_CATEGORY_API}/${id(categoryId)}/status`, {
      status,
    });
  }

  // Courier profiles
  listCouriers(filters: CourierProfileFilters = {}): Observable<DeliveryPage<CourierProfile>> {
    let params = new HttpParams()
      .set('page', filters.page ?? 0)
      .set('size', filters.size ?? 20)
      .set('sort', 'fullName');
    if (filters.status) params = params.set('status', filters.status);
    if (filters.categoryId) params = params.set('categoryId', filters.categoryId);
    return this.http.get<DeliveryPage<CourierProfile>>(COURIER_PROFILE_API, { params });
  }

  getCourierByIdentity(identityUserId: string): Observable<CourierProfile> {
    return this.http.get<CourierProfile>(`${COURIER_PROFILE_API}/by-identity/${id(identityUserId)}`);
  }

  createCourier(request: CreateCourierProfileRequest): Observable<CourierProfile> {
    return this.http.post<CourierProfile>(COURIER_PROFILE_API, request);
  }

  changeCourierStatus(courierId: string, status: CourierProfileStatus): Observable<CourierProfile> {
    return this.http.patch<CourierProfile>(`${COURIER_PROFILE_API}/${id(courierId)}/status`, {
      status,
    });
  }

  // Assignments
  listAssignments(filters: AssignmentFilters = {}): Observable<DeliveryPage<DeliveryAssignment>> {
    let params = new HttpParams()
      .set('page', filters.page ?? 0)
      .set('size', filters.size ?? 20)
      .set('sort', 'createdAt,desc');
    const reference = filters.parcelReference?.trim();
    if (reference) params = params.set('parcelReference', reference);
    if (filters.courierId) params = params.set('courierId', filters.courierId);
    if (filters.status) params = params.set('status', filters.status);
    return this.http.get<DeliveryPage<DeliveryAssignment>>(ASSIGNMENT_API, { params });
  }

  assign(request: AssignParcelRequest): Observable<DeliveryAssignment> {
    return this.http.post<DeliveryAssignment>(ASSIGNMENT_API, request);
  }

  updateAssignmentStatus(
    assignmentId: string,
    status: AssignmentStatus,
  ): Observable<DeliveryAssignment> {
    return this.http.patch<DeliveryAssignment>(`${ASSIGNMENT_API}/${id(assignmentId)}/status`, {
      status,
    });
  }

  recordFailedAttempt(
    assignmentId: string,
    request: RecordAttemptRequest,
  ): Observable<DeliveryAttempt> {
    return this.http.post<DeliveryAttempt>(`${ASSIGNMENT_API}/${id(assignmentId)}/attempts`, request);
  }

  /** With a file the request is sent as a form (the details as JSON, then the file); without one, as plain JSON. */
  recordProof(
    assignmentId: string,
    request: RecordProofRequest,
    file: File | null = null,
  ): Observable<ProofOfDelivery> {
    const url = `${ASSIGNMENT_API}/${id(assignmentId)}/proof`;
    if (!file) {
      return this.http.post<ProofOfDelivery>(url, request);
    }
    const form = new FormData();
    form.append('proof', new Blob([JSON.stringify(request)], { type: 'application/json' }));
    form.append('file', file, file.name);
    return this.http.post<ProofOfDelivery>(url, form);
  }

  /** The uploaded proof file. Only the owning courier and dispatchers are allowed; it needs the login token. */
  getProofFile(assignmentId: string): Observable<Blob> {
    return this.http.get(`${ASSIGNMENT_API}/${id(assignmentId)}/proof/file`, { responseType: 'blob' });
  }

  listAttempts(assignmentId: string): Observable<DeliveryAttempt[]> {
    return this.http.get<DeliveryAttempt[]>(`${ASSIGNMENT_API}/${id(assignmentId)}/attempts`);
  }

  /** Who moved the assignment through each status, oldest first. Dispatchers only. */
  listHistory(assignmentId: string): Observable<StatusChange[]> {
    return this.http.get<StatusChange[]>(`${ASSIGNMENT_API}/${id(assignmentId)}/history`);
  }

  getProof(assignmentId: string): Observable<ProofOfDelivery> {
    return this.http.get<ProofOfDelivery>(`${ASSIGNMENT_API}/${id(assignmentId)}/proof`);
  }

  // Issues
  listIssues(filters: IssueFilters = {}): Observable<DeliveryPage<DeliveryIssue>> {
    let params = new HttpParams()
      .set('page', filters.page ?? 0)
      .set('size', filters.size ?? 20)
      .set('sort', 'reportedAt,desc');
    if (filters.status) params = params.set('status', filters.status);
    if (filters.courierId) params = params.set('courierId', filters.courierId);
    return this.http.get<DeliveryPage<DeliveryIssue>>(DELIVERY_ISSUE_API, { params });
  }

  reportIssue(request: ReportIssueRequest): Observable<DeliveryIssue> {
    return this.http.post<DeliveryIssue>(DELIVERY_ISSUE_API, request);
  }

  reviewIssue(issueId: string, request: ReviewIssueRequest): Observable<DeliveryIssue> {
    return this.http.patch<DeliveryIssue>(`${DELIVERY_ISSUE_API}/${id(issueId)}/review`, request);
  }
}
