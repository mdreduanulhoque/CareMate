package com.carebridge.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberNormalizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "01712345678",
            "+8801712345678",
            "8801712345678",
            "+880 1712-345678",
            "01712 345 678"
    })
    void normalizesValidBangladeshiNumbersToCanonicalLocalFormat(String raw) {
        assertThat(PhoneNumberNormalizer.normalize(raw)).isEqualTo("01712345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "01212345678",
            "0171234567",
            "017123456789",
            "abcdefghijk",
            "+14155552671"
    })
    void rejectsInvalidMobileNumbers(String invalid) {
        assertThatThrownBy(() -> PhoneNumberNormalizer.normalize(invalid))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsNullMobileNumber() {
        assertThatThrownBy(() -> PhoneNumberNormalizer.normalize(null))
                .isInstanceOf(BadRequestException.class);
    }
}
