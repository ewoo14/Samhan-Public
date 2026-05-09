package com.samhanair.logis.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

/** Phase 10 P0-2 — 정책 시나리오 (memory feedback_function_documentation 의무 4 scenarios 중 1). */
class PasswordPolicyTest {

    @Test
    void validate_acceptsCompliantPassword() {
        // given/when/then — 영문 + 숫자 + 특수문자 + 8자 이상
        PasswordPolicy.validate("Abcdef1!");
    }

    @Test
    void validate_rejectsTooShort() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Ab1!"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void validate_rejectsMissingDigit() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Abcdefgh!"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void validate_rejectsMissingSpecial() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Abcdefg1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void validate_rejectsMissingLetter() {
        assertThatThrownBy(() -> PasswordPolicy.validate("12345678!"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void validate_rejectsNull() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void describe_returnsKoreanDescription() {
        assertThat(PasswordPolicy.describe()).contains("영문", "숫자", "특수문자");
    }
}
