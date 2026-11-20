package com.swiftdrop.communication.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneMaskerTest {

    @Test
    void maskStandardPhoneNumber() {
        String masked = PhoneMasker.mask("+94771234567");
        assertThat(masked).isEqualTo("+9477****567");
    }

    @Test
    void maskShortNumber() {
        assertThat(PhoneMasker.mask("1234567")).isEqualTo("****");
        assertThat(PhoneMasker.mask("123")).isEqualTo("****");
    }

    @Test
    void maskNullAndBlank() {
        assertThat(PhoneMasker.mask(null)).isEqualTo("****");
        assertThat(PhoneMasker.mask("")).isEqualTo("****");
        assertThat(PhoneMasker.mask("   ")).isEqualTo("****");
    }
}
