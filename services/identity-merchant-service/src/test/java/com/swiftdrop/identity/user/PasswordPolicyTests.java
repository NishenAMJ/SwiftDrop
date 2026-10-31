package com.swiftdrop.identity.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTests {

    @Test
    void acceptsLettersAndDigitsWithinLimits() {
        assertThat(PasswordPolicy.findProblem("abcdefg1")).isNull();
        assertThat(PasswordPolicy.findProblem("Pässwört-2026")).isNull();
        assertThat(PasswordPolicy.findProblem("a1".repeat(32))).isNull();
    }

    @Test
    void rejectsWeakOrOversizedPasswords() {
        assertThat(PasswordPolicy.findProblem(null)).isEqualTo("must not be empty");
        assertThat(PasswordPolicy.findProblem("abc1")).contains("between 8 and 64");
        assertThat(PasswordPolicy.findProblem("a1".repeat(33))).contains("between 8 and 64");
        assertThat(PasswordPolicy.findProblem("onlyletters")).contains("letter and one digit");
        assertThat(PasswordPolicy.findProblem("1234567890")).contains("letter and one digit");
    }

    @Test
    void rejectsPasswordsThatBcryptWouldTruncate() {
        // 38 characters (within 64), but each "é" is 2 bytes in UTF-8: 75 bytes in total.
        String password = "é".repeat(37) + "1";
        assertThat(password.codePointCount(0, password.length())).isLessThanOrEqualTo(64);
        assertThat(PasswordPolicy.findProblem(password)).contains("too long");
    }
}
