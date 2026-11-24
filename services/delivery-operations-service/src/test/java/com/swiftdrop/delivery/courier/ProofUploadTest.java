package com.swiftdrop.delivery.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.delivery.courier.api.AssignParcelRequest;
import com.swiftdrop.delivery.courier.api.AssignmentResponse;
import com.swiftdrop.delivery.courier.api.CreateCourierCategoryRequest;
import com.swiftdrop.delivery.courier.api.CreateCourierRequest;
import com.swiftdrop.delivery.courier.api.UpdateAssignmentStatusRequest;
import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.events.OutboxEventRepository;
import com.swiftdrop.delivery.courier.service.CourierCategoryService;
import com.swiftdrop.delivery.courier.service.CourierService;
import com.swiftdrop.delivery.courier.service.DeliveryAssignmentService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Uploading proof of delivery: who may upload, what is accepted, who may read the file back, and that a refused
 * upload never marks the parcel delivered or leaves a file behind.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProofUploadTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    private static final Path STORAGE = Path.of("target/test-proofs");
    private static final String PROOF_JSON = "{\"recipientName\":\"Kamala Silva\",\"notes\":\"Left with the guard\"}";

    @Autowired private MockMvc mvc;
    @Autowired private CourierCategoryService categoryService;
    @Autowired private CourierService courierService;
    @Autowired private DeliveryAssignmentService assignmentService;
    @Autowired private OutboxEventRepository outbox;

    private final UUID aliceUser = UUID.randomUUID();
    private final UUID bobUser = UUID.randomUUID();
    private final UUID dispatcherUser = UUID.randomUUID();
    private AssignmentResponse aliceParcel;

    @BeforeEach
    void setUp() {
        var suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var category = categoryService.create(new CreateCourierCategoryRequest("PRF-" + suffix, "Proof " + suffix,
                null, new BigDecimal("30.00")));
        var alice = courierService.create(new CreateCourierRequest(aliceUser, "Alice", "+94 77 000 0001", null,
                category.id()));
        courierService.create(new CreateCourierRequest(bobUser, "Bob", "+94 77 000 0002", null, category.id()));
        var base = Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000) + 100_000;
        aliceParcel = outForDelivery("SWD-2026-" + base, alice.id());
    }

    private AssignmentResponse outForDelivery(String tracking, UUID courierId) {
        var assignment = assignmentService.assign(new AssignParcelRequest(tracking, courierId, "Dispatcher", null),
                "token");
        for (var next : List.of(AssignmentStatus.PICKED_UP, AssignmentStatus.OUT_FOR_DELIVERY)) {
            assignmentService.updateStatus(assignment.id(), new UpdateAssignmentStatusRequest(next, null));
        }
        return assignmentService.get(assignment.id());
    }

    private RequestPostProcessor courier(UUID user) {
        return jwt().jwt(t -> t.subject(user.toString()).claim("roles", List.of("COURIER")))
                .authorities(new SimpleGrantedAuthority("ROLE_COURIER"));
    }

    private RequestPostProcessor dispatcher() {
        return jwt().jwt(t -> t.subject(dispatcherUser.toString()).claim("roles", List.of("DISPATCHER")))
                .authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER"));
    }

    private RequestPostProcessor customer() {
        return jwt().jwt(t -> t.subject(UUID.randomUUID().toString()).claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static MockMultipartFile proofPart() {
        return new MockMultipartFile("proof", "", MediaType.APPLICATION_JSON_VALUE, PROOF_JSON.getBytes());
    }

    private String proofUrl(UUID assignmentId) {
        return "/api/v1/assignments/" + assignmentId + "/proof";
    }

    private static long storedFiles() throws IOException {
        if (!Files.exists(STORAGE)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(STORAGE)) {
            return files.count();
        }
    }

    private void assertStillOutForDelivery(UUID assignmentId) {
        assertThat(assignmentService.get(assignmentId).status()).isEqualTo(AssignmentStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void theOwnerUploadsAJpegAndGetsItBackWithSafeHeaders() throws Exception {
        var before = storedFiles();

        var created = mvc.perform(multipart(proofUrl(aliceParcel.id()))
                        .file(proofPart())
                        .file(new MockMultipartFile("file", "front-door.jpg", "image/jpeg", JPEG))
                        .with(courier(aliceUser)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipientName").value("Kamala Silva"))
                .andExpect(jsonPath("$.hasFile").value(true))
                .andExpect(jsonPath("$.fileName").value("front-door.jpg"))
                .andExpect(jsonPath("$.fileContentType").value("image/jpeg"))
                .andExpect(jsonPath("$.fileSizeBytes").value(JPEG.length))
                .andReturn();

        // The secure reference stays on the server: the response has no key, path or storage detail.
        var body = created.getResponse().getContentAsString();
        assertThat(body).doesNotContain("fileKey").doesNotContain("file_key").doesNotContain("test-proofs");
        assertThat(assignmentService.get(aliceParcel.id()).status()).isEqualTo(AssignmentStatus.DELIVERED);
        assertThat(storedFiles()).isEqualTo(before + 1);

        mvc.perform(get(proofUrl(aliceParcel.id()) + "/file").with(courier(aliceUser)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
    }

    @Test
    void aDispatcherCanReadTheFileButNobodyElseCan() throws Exception {
        mvc.perform(multipart(proofUrl(aliceParcel.id())).file(proofPart())
                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", JPEG)).with(courier(aliceUser)))
                .andExpect(status().isCreated());
        var url = proofUrl(aliceParcel.id()) + "/file";

        mvc.perform(get(url).with(dispatcher())).andExpect(status().isOk()).andExpect(content().bytes(JPEG));
        mvc.perform(get(url).with(courier(bobUser))).andExpect(status().isForbidden());
        mvc.perform(get(url).with(customer())).andExpect(status().isForbidden());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void thereIsNoFileToReadWhenNoneWasUploadedOrTheAssignmentIsUnknown() throws Exception {
        mvc.perform(post(proofUrl(aliceParcel.id())).with(courier(aliceUser)).contentType(MediaType.APPLICATION_JSON)
                        .content(PROOF_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasFile").value(false))
                .andExpect(jsonPath("$.fileName").doesNotExist());

        mvc.perform(get(proofUrl(aliceParcel.id()) + "/file").with(dispatcher())).andExpect(status().isNotFound());
        mvc.perform(get(proofUrl(UUID.randomUUID()) + "/file").with(dispatcher())).andExpect(status().isNotFound());
    }

    @Test
    void aCourierWhoDoesNotOwnTheAssignmentCannotUploadAndNothingIsStored() throws Exception {
        var before = storedFiles();

        mvc.perform(multipart(proofUrl(aliceParcel.id())).file(proofPart())
                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", JPEG)).with(courier(bobUser)))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(proofUrl(aliceParcel.id())).file(proofPart())
                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", JPEG)).with(dispatcher()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(proofUrl(aliceParcel.id())).file(proofPart())
                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", JPEG)))
                .andExpect(status().isUnauthorized());

        assertStillOutForDelivery(aliceParcel.id());
        assertThat(storedFiles()).isEqualTo(before);
    }

    @Test
    void aFileThatIsNotReallyAnImageOrPdfIsRefusedAndTheParcelIsNotDelivered() throws Exception {
        var before = storedFiles();

        mvc.perform(multipart(proofUrl(aliceParcel.id())).file(proofPart())
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg",
                                "<script>alert(1)</script>".getBytes()))
                        .with(courier(aliceUser)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));

        assertStillOutForDelivery(aliceParcel.id());
        assertThat(storedFiles()).isEqualTo(before);
    }

    @Test
    void anEmptyFileIsRefused() throws Exception {
        mvc.perform(multipart(proofUrl(aliceParcel.id())).file(proofPart())
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0]))
                        .with(courier(aliceUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_EMPTY"));

        assertStillOutForDelivery(aliceParcel.id());
    }

    @Test
    void aFileOverFiveMegabytesIsRefused() throws Exception {
        var before = storedFiles();
        var tooBig = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(JPEG, 0, tooBig, 0, JPEG.length);

        mvc.perform(multipart(proofUrl(aliceParcel.id())).file(proofPart())
                        .file(new MockMultipartFile("file", "big.jpg", "image/jpeg", tooBig))
                        .with(courier(aliceUser)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        assertStillOutForDelivery(aliceParcel.id());
        assertThat(storedFiles()).isEqualTo(before);
    }

    @Test
    void aUploadForAParcelThatIsNotOutForDeliveryStoresNoFile() throws Exception {
        var tracking = "SWD-2026-" + (Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000) + 100_000);
        var courierId = aliceParcel.courierId();
        // Assigned only: it cannot be delivered yet, so the proof must be refused with no file left behind.
        var notYet = assignmentService.assign(new AssignParcelRequest(tracking, courierId, "Dispatcher", null), "token");
        var before = storedFiles();

        mvc.perform(multipart(proofUrl(notYet.id())).file(proofPart())
                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", JPEG)).with(courier(aliceUser)))
                .andExpect(status().is4xxClientError());

        assertThat(assignmentService.get(notYet.id()).status()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(storedFiles()).isEqualTo(before);
    }

    @Test
    void theFileNeverLeavesThroughAnEventSoPublicTrackingCannotSeeIt() throws Exception {
        mvc.perform(multipart(proofUrl(aliceParcel.id())).file(proofPart())
                        .file(new MockMultipartFile("file", "secret-name.jpg", "image/jpeg", JPEG))
                        .with(courier(aliceUser)))
                .andExpect(status().isCreated());

        var payloads = outbox.findAllByOrderByCreatedAtAsc().stream()
                .map(event -> event.getPayload())
                .filter(body -> body.contains(aliceParcel.parcelReference()))
                .toList();

        assertThat(payloads).isNotEmpty();
        assertThat(payloads).noneMatch(body -> body.contains("secret-name") || body.contains("fileKey")
                || body.contains("image/jpeg") || body.contains("Kamala") || body.contains("Left with the guard"));
    }

    @Test
    void aProofWithoutAnyFilePartStillWorksAsMultipart() throws Exception {
        mvc.perform(multipart(proofUrl(aliceParcel.id())).file(proofPart()).with(courier(aliceUser)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasFile").value(false));
    }
}
