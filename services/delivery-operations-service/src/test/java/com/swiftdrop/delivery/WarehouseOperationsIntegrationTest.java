package com.swiftdrop.delivery;

import com.swiftdrop.delivery.api.CreateSortingRecordRequest;
import com.swiftdrop.delivery.api.CreateTransferRequest;
import com.swiftdrop.delivery.api.CreateWarehouseRequest;
import com.swiftdrop.delivery.api.ReceiveTransferRequest;
import com.swiftdrop.delivery.api.RecordArrivalRequest;
import com.swiftdrop.delivery.domain.TransferStatus;
import com.swiftdrop.delivery.domain.WarehouseParcelState;
import com.swiftdrop.delivery.exception.ConflictException;
import com.swiftdrop.delivery.exception.InvalidOperationException;
import com.swiftdrop.delivery.service.WarehouseOperationsService;
import com.swiftdrop.delivery.service.WarehouseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class WarehouseOperationsIntegrationTest {

    @Autowired
    private WarehouseService warehouseService;

    @Autowired
    private WarehouseOperationsService operationsService;

    @Test
    void parcelMovesThroughArrivalSortingDispatchAndReceipt() {
        var source = createWarehouse("CMB-01", "Colombo Hub", "Colombo");
        var destination = createWarehouse("KDY-01", "Kandy Hub", "Kandy");

        var arrival = operationsService.recordArrival(
                new RecordArrivalRequest("swd-2026-000125", source.id(), null, "Received at loading bay"));
        assertThat(arrival.state()).isEqualTo(WarehouseParcelState.ARRIVED);

        operationsService.sort(new CreateSortingRecordRequest(arrival.parcelReference(), source.id(),
                destination.id(), "Kandy", null, null));
        assertThat(operationsService.getParcel(arrival.parcelReference()).state())
                .isEqualTo(WarehouseParcelState.SORTED);

        var transfer = operationsService.createTransfer(
                new CreateTransferRequest(arrival.parcelReference(), source.id(), destination.id()));
        assertThat(transfer.status()).isEqualTo(TransferStatus.CREATED);

        transfer = operationsService.dispatch(transfer.id());
        assertThat(transfer.status()).isEqualTo(TransferStatus.IN_TRANSIT);

        transfer = operationsService.receive(transfer.id(), new ReceiveTransferRequest(null, "Warehouse Officer", null));
        assertThat(transfer.status()).isEqualTo(TransferStatus.RECEIVED);

        var parcel = operationsService.getParcel(arrival.parcelReference());
        assertThat(parcel.warehouseId()).isEqualTo(destination.id());
        assertThat(parcel.state()).isEqualTo(WarehouseParcelState.RECEIVED);
    }

    @Test
    void duplicateWarehouseCodesAreRejectedIgnoringCase() {
        createWarehouse("GLL-01", "Galle Hub", "Galle");

        assertThatThrownBy(() -> createWarehouse("gll-01", "Another Hub", "Galle"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("GLL-01");
    }

    @Test
    void parcelMustBeSortedBeforeTransfer() {
        var source = createWarehouse("JAF-01", "Jaffna Hub", "Jaffna");
        var destination = createWarehouse("VAV-01", "Vavuniya Hub", "Vavuniya");
        operationsService.recordArrival(new RecordArrivalRequest("SWD-2026-000999", source.id(), null, null));

        assertThatThrownBy(() -> operationsService.createTransfer(
                new CreateTransferRequest("SWD-2026-000999", source.id(), destination.id())))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("sorted");
    }

    private com.swiftdrop.delivery.api.WarehouseResponse createWarehouse(String code, String name, String city) {
        return warehouseService.create(new CreateWarehouseRequest(code, name, "1 Main Street", null, city,
                city, null, "Operations Manager", "+94 11 000 0000", "ops@example.com"));
    }
}
