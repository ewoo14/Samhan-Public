package com.samhanair.logis.slip.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 전표 일자별 채번 보조 — {@link Slip#getSlipNo()} 의 {@code yyyy/MM/dd-NNN} 순번을
 * atomic 하게 관리한다. 동시 충돌은 {@code slips(slip_no) WHERE is_deleted=false} 의
 * partial unique 인덱스로 백업 (서비스 레이어가 충돌 시 재시도 정책 결정).
 */
@Entity
@Getter
@Table(name = "slip_number_sequences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class SlipNumberSequence extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "slip_date", nullable = false, unique = true)
    private LocalDate slipDate;

    @Column(name = "last_seq", nullable = false)
    private int lastSeq;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private SlipNumberSequence(LocalDate slipDate) {
        this.slipDate = slipDate;
        this.lastSeq = 0;
        this.version = 0L;
    }

    /**
     * 새 날짜 시퀀스를 생성한다. lastSeq=0 으로 시작 — 다음 호출은 {@link #next()} 로 1 부터 부여.
     *
     * @param slipDate 채번 기준 날짜
     * @return lastSeq=0 의 신규 SlipNumberSequence
     */
    public static SlipNumberSequence create(LocalDate slipDate) {
        return new SlipNumberSequence(slipDate);
    }

    /**
     * 다음 순번을 계산한다. lastSeq 를 +1 증가시킨 뒤 그 값을 반환.
     *
     * @return 부여된 새 순번 (1, 2, 3, ...)
     */
    public int next() {
        this.lastSeq++;
        return this.lastSeq;
    }
}
