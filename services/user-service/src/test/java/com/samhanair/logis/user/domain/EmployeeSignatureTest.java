package com.samhanair.logis.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.common.security.Role;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Employee 서명 도메인 메서드 단위 테스트 - C1a. */
class EmployeeSignatureTest {

    private Employee newEmployee() {
        Department department = Department.create("SIG_TEST", "서명테스트팀", 950);
        return Employee.create(UUID.randomUUID(), "sig01", "서명자", "사원",
                Role.STAFF, department, false, LocalDate.of(2026, 1, 1), null, null);
    }

    @Test
    void registerSignature_4필드를_원자적으로_set한다() {
        Employee e = newEmployee();
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};

        e.registerSignature(png, "a".repeat(64), SignatureChannel.UPLOAD);

        assertThat(e.getSignaturePng()).isEqualTo(png);
        assertThat(e.getSignatureHash()).isEqualTo("a".repeat(64));
        assertThat(e.getSignatureChannel()).isEqualTo(SignatureChannel.UPLOAD);
        assertThat(e.getSignedAt()).isNotNull();
    }

    @Test
    void registerSignature_재등록은_기존_서명을_교체한다() {
        Employee e = newEmployee();
        e.registerSignature(new byte[] {1}, "a".repeat(64), SignatureChannel.UPLOAD);

        e.registerSignature(new byte[] {2, 3}, "b".repeat(64), SignatureChannel.MOBILE_CANVAS);

        assertThat(e.getSignaturePng()).containsExactly(2, 3);
        assertThat(e.getSignatureHash()).isEqualTo("b".repeat(64));
        assertThat(e.getSignatureChannel()).isEqualTo(SignatureChannel.MOBILE_CANVAS);
    }

    @Test
    void registerSignature_png_null이면_IllegalArgument() {
        Employee e = newEmployee();
        assertThatThrownBy(() -> e.registerSignature(null, "a".repeat(64), SignatureChannel.UPLOAD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidateSignature_4필드를_NULL로_만든다() {
        Employee e = newEmployee();
        e.registerSignature(new byte[] {1}, "a".repeat(64), SignatureChannel.UPLOAD);

        e.invalidateSignature("오등록 정정");

        assertThat(e.getSignaturePng()).isNull();
        assertThat(e.getSignatureHash()).isNull();
        assertThat(e.getSignatureChannel()).isNull();
        assertThat(e.getSignedAt()).isNull();
    }

    @Test
    void invalidateSignature_미등록_상태면_CONFLICT() {
        Employee e = newEmployee();
        assertThatThrownBy(() -> e.invalidateSignature("사유"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }
}
