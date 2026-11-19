/** Shapes from contracts/openapi/identity-merchant-service.yaml (Couriers). */
import { ApplicationStatus, StatusDecision } from '../../shared/models/application';

export type VehicleType = 'BICYCLE' | 'MOTORCYCLE' | 'THREE_WHEELER' | 'CAR' | 'VAN' | 'TRUCK';

export const VEHICLE_TYPE_LABELS: Record<VehicleType, string> = {
  BICYCLE: 'Bicycle',
  MOTORCYCLE: 'Motorcycle',
  THREE_WHEELER: 'Three-wheeler',
  CAR: 'Car',
  VAN: 'Van',
  TRUCK: 'Truck',
};

/** Every vehicle except a bicycle needs a driving licence and a registration number. */
export function needsVehicleDocuments(type: VehicleType | '' | null | undefined): boolean {
  return !!type && type !== 'BICYCLE';
}

export interface Courier {
  /** A courier is identified by their user ID. */
  userId: string;
  fullName: string;
  nationalIdNumber?: string | null;
  vehicleType: VehicleType;
  drivingLicenceNumber?: string | null;
  vehicleRegistrationNumber?: string | null;
  status: ApplicationStatus;
  lastDecision?: StatusDecision | null;
  submittedAt: string;
  createdAt: string;
}

export interface CourierApplicationRequest {
  nationalIdNumber: string;
  vehicleType: VehicleType;
  drivingLicenceNumber?: string;
  vehicleRegistrationNumber?: string;
}
