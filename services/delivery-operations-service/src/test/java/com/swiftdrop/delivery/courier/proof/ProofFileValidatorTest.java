package com.swiftdrop.delivery.courier.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

/** What may be uploaded as proof: a real JPEG, PNG or PDF within the limit, judged by its bytes, not its labels. */
class ProofFileValidatorTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);

    private final ProofFileValidator validator = new ProofFileValidator(1024);

    private static MockMultipartFile file(String name, String declaredType, byte[] bytes) {
        return new MockMultipartFile("file", name, declaredType, bytes);
    }

    @Test
    void acceptsAJpegAPngAndAPdf() {
        assertThat(validator.validate(file("a.jpg", "image/jpeg", JPEG)).contentType()).isEqualTo("image/jpeg");
        assertThat(validator.validate(file("a.png", "image/png", PNG)).contentType()).isEqualTo("image/png");
        assertThat(validator.validate(file("a.pdf", "application/pdf", PDF)).contentType())
                .isEqualTo("application/pdf");
    }

    @Test
    void theTypeComesFromTheBytesNotTheUploadHeaderOrTheFileName() {
        // A PNG labelled as a PDF with an .exe name is stored and served as the PNG it really is.
        var accepted = validator.validate(file("invoice.exe", "application/pdf", PNG));

        assertThat(accepted.contentType()).isEqualTo("image/png");
        assertThat(accepted.displayName()).isEqualTo("invoice.png");
    }

    @Test
    void refusesFilesThatOnlyClaimToBeAllowedTypes() {
        for (var disguised : new byte[][]{
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8),
                "MZ\u0090\u0000 a Windows program".getBytes(StandardCharsets.ISO_8859_1),
                "GIF89a not allowed either".getBytes(StandardCharsets.US_ASCII),
                "plain text".getBytes(StandardCharsets.UTF_8)}) {
            assertThatThrownBy(() -> validator.validate(file("photo.jpg", "image/jpeg", disguised)))
                    .isInstanceOfSatisfying(ProofFileException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                        assertThat(e.getCode()).isEqualTo(ProofFileException.UNSUPPORTED_FILE_TYPE);
                    });
        }
    }

    @Test
    void refusesAnEmptyFileAndAMissingFile() {
        for (var empty : new MockMultipartFile[]{file("a.jpg", "image/jpeg", new byte[0]), null}) {
            assertThatThrownBy(() -> validator.validate(empty))
                    .isInstanceOfSatisfying(ProofFileException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.getCode()).isEqualTo(ProofFileException.FILE_EMPTY);
                    });
        }
    }

    @Test
    void refusesAFileOverTheLimitButAcceptsOneExactlyAtIt() {
        var atLimit = new byte[1024];
        System.arraycopy(JPEG, 0, atLimit, 0, JPEG.length);
        var over = new byte[1025];
        System.arraycopy(JPEG, 0, over, 0, JPEG.length);

        assertThat(validator.validate(file("a.jpg", "image/jpeg", atLimit)).bytes()).hasSize(1024);
        assertThatThrownBy(() -> validator.validate(file("a.jpg", "image/jpeg", over)))
                .isInstanceOfSatisfying(ProofFileException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(e.getCode()).isEqualTo(ProofFileException.FILE_TOO_LARGE);
                });
    }

    @Test
    void theDisplayNameIsSafeToShowAndDownload() {
        assertThat(ProofFileValidator.displayName("../../etc/passwd.jpg", "image/jpeg")).isEqualTo("passwd.jpg");
        assertThat(ProofFileValidator.displayName("C:\\Users\\me\\photo.jpg", "image/jpeg")).isEqualTo("photo.jpg");
        assertThat(ProofFileValidator.displayName("a\"b;c\r\nSet-Cookie: x.jpg", "image/jpeg"))
                .doesNotContain("\"").doesNotContain("\r").doesNotContain("\n").doesNotContain(";")
                .endsWith(".jpg");
        assertThat(ProofFileValidator.displayName(null, "application/pdf")).isEqualTo("proof.pdf");
        assertThat(ProofFileValidator.displayName("   ", "image/png")).isEqualTo("proof.png");
        assertThat(ProofFileValidator.displayName("x".repeat(300) + ".jpg", "image/jpeg").length())
                .isLessThanOrEqualTo(84);
    }
}
