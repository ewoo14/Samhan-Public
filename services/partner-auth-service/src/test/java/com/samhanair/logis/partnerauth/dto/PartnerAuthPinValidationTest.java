package com.samhanair.logis.partnerauth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** BizGate 거래처 비밀번호는 로그인 ID(거래처코드/사업자번호)와 별개인 숫자 4자리 PIN 이다. */
class PartnerAuthPinValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    @DisplayName("SetPasswordRequest — 신규 비밀번호는 숫자 4자리 PIN 만 허용")
    void setPasswordRequest_newPassword_숫자_4자리_PIN만_허용() {
        assertThat(validator.validate(new SetPasswordRequest("1234567890", "1234", null))).isEmpty();

        assertThat(validator.validate(new SetPasswordRequest("1234567890", "123", null))).isNotEmpty();
        assertThat(validator.validate(new SetPasswordRequest("1234567890", "12345", null))).isNotEmpty();
        assertThat(validator.validate(new SetPasswordRequest("1234567890", "12a4", null))).isNotEmpty();
    }

    @Test
    @DisplayName("SetPasswordRequest — 현재 비밀번호가 있으면 숫자 4자리 PIN 만 허용")
    void setPasswordRequest_currentPassword_있으면_숫자_4자리_PIN만_허용() {
        assertThat(validator.validate(new SetPasswordRequest("1234567890", "5678", "1234"))).isEmpty();

        assertThat(validator.validate(new SetPasswordRequest("1234567890", "5678", "oldPw!1")))
                .isNotEmpty();
    }

    @Test
    @DisplayName("TryLoginRequest — partner-login 비밀번호는 숫자 4자리 PIN 만 허용")
    void tryLoginRequest_password_숫자_4자리_PIN만_허용() {
        assertThat(validator.validate(new TryLoginRequest("1234567890", "1234", false))).isEmpty();

        assertThat(validator.validate(new TryLoginRequest("1234567890", "123", false))).isNotEmpty();
        assertThat(validator.validate(new TryLoginRequest("1234567890", "abcd", false))).isNotEmpty();
    }
}
