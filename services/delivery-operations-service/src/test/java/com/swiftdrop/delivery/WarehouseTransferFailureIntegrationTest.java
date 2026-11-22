package com.swiftdrop.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.delivery.api.CreateSortingRecordRequest;
import com.swiftdrop.delivery.api.CreateTransferRequest;
import com.swiftdrop.delivery.api.CreateWarehouseRequest;
import com.swiftdrop.delivery.api.RecordArrivalRequest;
import com.swiftdrop.delivery.api.WarehouseResponse;
import com.swiftdrop.delivery.domain.TransferStatus;
import com.swiftdrop.delivery.domain.WarehouseStatus;
import com.swiftdrop.delivery.exception.ConflictException;
import com.swiftdrop.delivery.exception.InvalidOperationException;
import com.swiftdrop.delivery.exception.ResourceNotFoundException;
import com.swiftdrop.delivery.service.WarehouseOperationsService;
import com.swiftdrop.delivery.service.WarehouseService;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

/** Failed warehouse operations: wrong warehouse, duplicate arrivals/transfers and inactive warehouses. */
@SpringBootTest
class WarehouseTransferFailureIntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger(500);

    @Autowired private WarehouseService warehouses;
    @Autowired private WarehouseOperationsService operations;

    private WarehouseResponse warehouse(String city) {
        String code = "WTF-" + SEQ.incrementAndGet();
        return warehouses.create(new CreateWarehouseRequest(code, city + " Hub " + code, "1 Main Street", null, city,
                city, null, "Operations Manager", "+94 11 000 0000", "ops@example.com"));
    }

    private static String reference() {
        return "SWD-2026-" + (700000 + SEQ.incrementAndGet());
    }

    private String sortedParcelAt(WarehouseResponse source, WarehouseResponse next) {
        String ref = reference();
        operations.recordArrival(new RecordArrivalRequest(ref, source.id(), null, null), "token");
        operations.sort(new CreateSortingRecordRequest(ref, source.id(), next.id(), "Route", null, null));
        return ref;
    }

    @Test
    void sameParcelCannotArriveTwice() {
        var hub = warehouse("Colombo");
        String ref = reference();
        operations.recordArrival(new RecordArrivalRequest(ref, hub.id(), null, null), "token");
        assertThatThrownBy(() -> operations.recordArrival(new RecordArrivalRequest(ref.toLowerCase(), hub.id(), null, null), "token"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void parcelsCannotArriveAtAnInactiveWarehouse() {
        var closed = warehouse("Matara");
        warehouses.changeStatus(closed.id(), WarehouseStatus.MAINTENANCE);
        assertThatThrownBy(() -> operations.recordArrival(new RecordArrivalRequest(reference(), closed.id(), null, null), "token"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void sortingAtTheWrongWarehouseOrToTheSameWarehouseIsRejected() {
        var here = warehouse("Galle");
        var elsewhere = warehouse("Kandy");
        String ref = reference();
        operations.recordArrival(new RecordArrivalRequest(ref, here.id(), null, null), "token");

        assertThatThrownBy(() -> operations.sort(new CreateSortingRecordRequest(ref, elsewhere.id(), here.id(), "x", null, null)))
                .isInstanceOf(InvalidOperationException.class).hasMessageContaining("not currently at");
        assertThatThrownBy(() -> operations.sort(new CreateSortingRecordRequest(ref, here.id(), here.id(), "x", null, null)))
                .isInstanceOf(InvalidOperationException.class).hasMessageContaining("must differ");
    }

    @Test
    void transferValidatesWarehousesAndParcelLocation() {
        var source = warehouse("Jaffna");
        var destination = warehouse("Trinco");
        var stranger = warehouse("Batticaloa");
        String ref = sortedParcelAt(source, destination);

        assertThatThrownBy(() -> operations.createTransfer(new CreateTransferRequest(ref, source.id(), source.id()), "token"))
                .isInstanceOf(InvalidOperationException.class).hasMessageContaining("must differ");
        assertThatThrownBy(() -> operations.createTransfer(new CreateTransferRequest(ref, stranger.id(), destination.id()), "token"))
                .isInstanceOf(InvalidOperationException.class).hasMessageContaining("source warehouse");
        assertThatThrownBy(() -> operations.createTransfer(new CreateTransferRequest(reference(), source.id(), destination.id()), "token"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void transferToAnInactiveDestinationIsRejected() {
        var source = warehouse("Negombo");
        var destination = warehouse("Chilaw");
        String ref = sortedParcelAt(source, destination);
        warehouses.changeStatus(destination.id(), WarehouseStatus.INACTIVE);
        assertThatThrownBy(() -> operations.createTransfer(new CreateTransferRequest(ref, source.id(), destination.id()), "token"))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void parcelCannotHaveTwoActiveTransfersOrBeSortedWhileInTransfer() {
        var source = warehouse("Kurunegala");
        var destination = warehouse("Anuradhapura");
        String ref = sortedParcelAt(source, destination);
        var transfer = operations.createTransfer(new CreateTransferRequest(ref, source.id(), destination.id()), "token");

        assertThatThrownBy(() -> operations.createTransfer(new CreateTransferRequest(ref, source.id(), destination.id()), "token"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("active transfer");

        operations.dispatch(transfer.id());
        assertThatThrownBy(() -> operations.sort(new CreateSortingRecordRequest(ref, source.id(), destination.id(), "x", null, null)))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void transfersCanBeListedByParcelOrStatusAndDashboardCountsThem() {
        var source = warehouse("Ratnapura");
        var destination = warehouse("Badulla");
        String ref = sortedParcelAt(source, destination);
        var transfer = operations.createTransfer(new CreateTransferRequest(ref, source.id(), destination.id()), "token");
        operations.dispatch(transfer.id());

        assertThat(operations.getTransfer(transfer.id()).status()).isEqualTo(TransferStatus.IN_TRANSIT);
        var page = PageRequest.of(0, 50);
        assertThat(operations.listTransfers(ref.toLowerCase(), null, page).getContent()).extracting(t -> t.id())
                .containsExactly(transfer.id());
        assertThat(operations.listTransfers(" ", TransferStatus.IN_TRANSIT, page).getContent()).extracting(t -> t.id())
                .contains(transfer.id());
        assertThat(operations.listTransfers(null, null, page).getTotalElements()).isPositive();

        var outgoing = operations.dashboard(source.id());
        assertThat(outgoing.outgoingTransfers()).isEqualTo(1);
        assertThat(outgoing.recentOutgoingTransfers()).extracting(t -> t.id()).contains(transfer.id());
        var incoming = operations.dashboard(destination.id());
        assertThat(incoming.incomingTransfers()).isEqualTo(1);
        assertThat(incoming.recentIncomingTransfers()).extracting(t -> t.id()).contains(transfer.id());
        assertThatThrownBy(() -> operations.getTransfer(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }
}
