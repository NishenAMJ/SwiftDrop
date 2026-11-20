export type WarehouseStatus = 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE';
export type WarehouseParcelState = 'ARRIVED' | 'SORTED' | 'IN_TRANSFER' | 'RECEIVED';
export type TransferStatus = 'CREATED' | 'IN_TRANSIT' | 'RECEIVED' | 'CANCELLED';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface Warehouse {
  id: string;
  code: string;
  name: string;
  addressLine1: string;
  addressLine2: string | null;
  city: string;
  district: string;
  postalCode: string | null;
  contactName: string;
  contactPhone: string;
  contactEmail: string | null;
  status: WarehouseStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateWarehouseRequest {
  code: string;
  name: string;
  addressLine1: string;
  addressLine2: string | null;
  city: string;
  district: string;
  postalCode: string | null;
  contactName: string;
  contactPhone: string;
  contactEmail: string | null;
}

export type UpdateWarehouseRequest = Omit<CreateWarehouseRequest, 'code'>;

export interface WarehouseParcel {
  id: string;
  parcelReference: string;
  warehouseId: string;
  warehouseCode: string;
  state: WarehouseParcelState;
  arrivedAt: string;
  notes: string | null;
  updatedAt: string;
}

export interface RecordArrivalRequest {
  parcelReference: string;
  warehouseId: string;
  arrivedAt: string | null;
  notes: string | null;
}

export interface SortingRecord {
  id: string;
  parcelReference: string;
  warehouseId: string;
  nextWarehouseId: string | null;
  destination: string;
  notes: string | null;
  sortedAt: string;
}

export interface CreateSortingRecordRequest {
  parcelReference: string;
  warehouseId: string;
  nextWarehouseId: string | null;
  destination: string;
  notes: string | null;
  sortedAt: string | null;
}

export interface ParcelTransfer {
  id: string;
  parcelReference: string;
  sourceWarehouseId: string;
  sourceWarehouseCode: string;
  destinationWarehouseId: string;
  destinationWarehouseCode: string;
  status: TransferStatus;
  transferDate: string | null;
  receivedAt: string | null;
  receivedBy: string | null;
  receivingNotes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TransferFilters {
  parcelReference?: string;
  status?: TransferStatus | '';
  page?: number;
  size?: number;
}

export interface CreateTransferRequest {
  parcelReference: string;
  sourceWarehouseId: string;
  destinationWarehouseId: string;
}

export interface ReceiveTransferRequest {
  receivedAt: string | null;
  receivedBy: string;
  notes: string | null;
}

export interface WarehouseDashboardData {
  warehouse: Warehouse;
  arrivedParcels: number;
  sortedParcels: number;
  parcelsInTransfer: number;
  incomingTransfers: number;
  outgoingTransfers: number;
  totalSortingRecords: number;
  recentParcels: WarehouseParcel[];
  recentIncomingTransfers: ParcelTransfer[];
  recentOutgoingTransfers: ParcelTransfer[];
}
