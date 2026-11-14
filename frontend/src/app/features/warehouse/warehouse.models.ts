export type WarehouseStatus = 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE';
export type WarehouseParcelState = 'ARRIVED' | 'SORTED' | 'IN_TRANSFER' | 'RECEIVED';
export type TransferStatus = 'CREATED' | 'IN_TRANSIT' | 'RECEIVED' | 'CANCELLED';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
}

export interface Warehouse {
  id: string;
  code: string;
  name: string;
  city: string;
  district: string;
  status: WarehouseStatus;
}

export interface WarehouseParcel {
  id: string;
  parcelReference: string;
  warehouseCode: string;
  state: WarehouseParcelState;
  arrivedAt: string;
  updatedAt: string;
}

export interface ParcelTransfer {
  id: string;
  parcelReference: string;
  sourceWarehouseCode: string;
  destinationWarehouseCode: string;
  status: TransferStatus;
  transferDate: string | null;
  receivedAt: string | null;
  createdAt: string;
  updatedAt: string;
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
