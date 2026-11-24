package com.swiftdrop.delivery.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.swiftdrop.delivery.courier.api.AssignParcelRequest;
import com.swiftdrop.delivery.courier.api.CreateCourierCategoryRequest;
import com.swiftdrop.delivery.courier.api.CreateCourierRequest;
import com.swiftdrop.delivery.courier.api.RecordProofRequest;
import com.swiftdrop.delivery.courier.api.UpdateAssignmentStatusRequest;
import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.events.DeliveryEventRecorder;
import com.swiftdrop.delivery.courier.proof.ProofFileValidator;
import com.swiftdrop.delivery.courier.proof.ValidatedProofFile;
import com.swiftdrop.delivery.courier.service.CourierCategoryService;
import com.swiftdrop.delivery.courier.service.CourierService;
import com.swiftdrop.delivery.courier.service.DeliveryAssignmentService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The upload over a real HTTP connection to a real embedded server, which a mock request cannot show: the server's
 * own multipart parsing, its 5 MB cap and our answer to it, and the clean-up of a stored file when the delivery
 * then fails.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProofUploadRealServerTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    private static final Path STORAGE = Path.of("target/test-proofs");

    @LocalServerPort private int port;
    @Autowired private CourierCategoryService categoryService;
    @Autowired private CourierService courierService;
    @Autowired private DeliveryAssignmentService assignmentService;
    @Autowired private ProofFileValidator validator;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private DeliveryEventRecorder events;

    private final UUID courierUser = UUID.randomUUID();
    private UUID assignmentId;

    @BeforeEach
    void setUp() {
        var suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var category = categoryService.create(new CreateCourierCategoryRequest("RS-" + suffix, "Real " + suffix, null,
                new BigDecimal("30.00")));
        var courier = courierService.create(new CreateCourierRequest(courierUser, "Real Courier", "+94 77 000 0009",
                null, category.id()));
        var tracking = "SWD-2026-" + (Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000) + 100_000);
        var assignment = assignmentService.assign(new AssignParcelRequest(tracking, courier.id(), "Dispatcher", null),
                "token");
        for (var next : List.of(AssignmentStatus.PICKED_UP, AssignmentStatus.OUT_FOR_DELIVERY)) {
            assignmentService.updateStatus(assignment.id(), new UpdateAssignmentStatusRequest(next, null));
        }
        assignmentId = assignment.id();
        // Any bearer token is accepted as this courier, so the real security rules run without a real identity server.
        when(jwtDecoder.decode(any())).thenReturn(Jwt.withTokenValue("t").header("alg", "none")
                .subject(courierUser.toString()).claim("roles", List.of("COURIER"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build());
    }

    private HttpResponse<String> upload(byte[] fileBytes, String fileName, String fileType) throws Exception {
        var boundary = "----proof" + UUID.randomUUID();
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"proof\"\r\n"
                + "Content-Type: application/json\r\n\r\n{\"recipientName\":\"Kamala Silva\"}\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
                + "\"\r\nContent-Type: " + fileType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(fileBytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/assignments/"
                        + assignmentId + "/proof"))
                .header("Authorization", "Bearer any")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static long storedFiles() throws IOException {
        if (!Files.exists(STORAGE)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(STORAGE)) {
            return files.count();
        }
    }

    @Test
    void aRealMultipartUploadIsParsedAndStored() throws Exception {
        var before = storedFiles();

        var response = upload(JPEG, "front-door.jpg", "image/jpeg");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"hasFile\":true").contains("\"recipientName\":\"Kamala Silva\"")
                .doesNotContain("fileKey");
        assertThat(storedFiles()).isEqualTo(before + 1);
    }

    @Test
    void theServersOwnSizeCapAnswersWithTheSameClearRefusal() throws Exception {
        var before = storedFiles();
        var tooBig = new byte[6 * 1024 * 1024];
        System.arraycopy(JPEG, 0, tooBig, 0, JPEG.length);

        var response = upload(tooBig, "big.jpg", "image/jpeg");

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("FILE_TOO_LARGE");
        assertThat(storedFiles()).isEqualTo(before);
        assertThat(assignmentService.get(assignmentId).status()).isEqualTo(AssignmentStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void aFileStoredForADeliveryThatThenFailsIsRemovedAgain() throws Exception {
        var before = storedFiles();
        // The file is written while the delivery is recorded; the later event step fails, so everything rolls back.
        doThrow(new IllegalStateException("event store down")).when(events)
                .progressed(any(), eq(AssignmentStatus.OUT_FOR_DELIVERY), any());
        var file = new ValidatedProofFile(JPEG, "image/jpeg", "door.jpg");

        try {
            assignmentService.recordProof(assignmentId, new RecordProofRequest("Kamala Silva", null, null, null, null),
                    file);
        } catch (IllegalStateException expected) {
            // the failure is the point of this test
        }

        assertThat(assignmentService.get(assignmentId).status()).isEqualTo(AssignmentStatus.OUT_FOR_DELIVERY);
        assertThat(storedFiles()).isEqualTo(before);
    }
}
