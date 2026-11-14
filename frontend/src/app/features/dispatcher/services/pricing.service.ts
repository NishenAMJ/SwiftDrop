import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Service for managing pricing rules (Dispatcher role).
 * Mirrors the backend PricingController endpoints.
 */
@Injectable({ providedIn: 'root' })
export class PricingService {
  private readonly baseUrl = '/api/pricing';

  constructor(private http: HttpClient) {}

  /** Get all pricing rules */
  getAllRules(): Observable<PricingRule[]> {
    return this.http.get<PricingRule[]>(`${this.baseUrl}/rules`);
  }

  /** Get a rule by id */
  getRule(id: number): Observable<PricingRule> {
    return this.http.get<PricingRule>(`${this.baseUrl}/rules/${id}`);
  }

  /** Create a new rule */
  createRule(req: PricingRuleRequest): Observable<PricingRule> {
    return this.http.post<PricingRule>(`${this.baseUrl}/rules`, req);
  }

  /** Update an existing rule */
  updateRule(id: number, req: PricingRuleRequest): Observable<PricingRule> {
    return this.http.put<PricingRule>(`${this.baseUrl}/rules/${id}`, req);
  }
}

/** Pricing rule model matching the backend DTO */
export interface PricingRule {
  id: number;
  ruleName: string;
  ruleCode: string;
  description?: string;
  baseFee: number; // using number for simplicity (bigdecimal)
  ratePerKg: number;
  defaultZoneCharge: number;
  urgentSurcharge?: number;
  urgentSurchargeEnabled?: boolean;
  minWeightKg: number;
  maxWeightKg: number;
  active?: boolean;
  isDefault?: boolean;
}

/** Payload for creating/updating a pricing rule */
export interface PricingRuleRequest {
  ruleName: string;
  ruleCode: string;
  description?: string;
  baseFee: number;
  ratePerKg: number;
  defaultZoneCharge: number;
  urgentSurcharge?: number;
  urgentSurchargeEnabled?: boolean;
  minWeightKg: number;
  maxWeightKg: number;
  active?: boolean;
  isDefault?: boolean;
}
