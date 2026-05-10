package com.samhanair.logis.arologis.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.arologis.domain.StopStatus;
import com.samhanair.logis.arologis.domain.VehicleStop;
import com.samhanair.logis.arologis.realtime.service.DispatchDerivedStatus;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * PR-H4b — DispatchDerivedStatus 단위 테스트.
 *
 * <p>stop status aggregate → derived status 산출 정확성 검증.
 */
class DispatchDerivedStatusTest {

    @Test
    void emptyStops_returnsPlanned() {
        assertThat(DispatchDerivedStatus.from(List.of())).isEqualTo(DispatchDerivedStatus.PLANNED);
    }

    @Test
    void allPending_returnsPlanned() {
        assertThat(DispatchDerivedStatus.from(List.of(
                stop(StopStatus.PENDING),
                stop(StopStatus.PENDING))))
                .isEqualTo(DispatchDerivedStatus.PLANNED);
    }

    @Test
    void anyArrived_returnsDispatched() {
        assertThat(DispatchDerivedStatus.from(List.of(
                stop(StopStatus.PENDING),
                stop(StopStatus.ARRIVED))))
                .isEqualTo(DispatchDerivedStatus.DISPATCHED);
    }

    @Test
    void mixedDeliveredAndPending_returnsDispatched() {
        assertThat(DispatchDerivedStatus.from(List.of(
                stop(StopStatus.DELIVERED),
                stop(StopStatus.PENDING))))
                .isEqualTo(DispatchDerivedStatus.DISPATCHED);
    }

    @Test
    void allDelivered_returnsDelivered() {
        assertThat(DispatchDerivedStatus.from(List.of(
                stop(StopStatus.DELIVERED),
                stop(StopStatus.DELIVERED))))
                .isEqualTo(DispatchDerivedStatus.DELIVERED);
    }

    @Test
    void deliveredAndFailed_returnsDelivered() {
        assertThat(DispatchDerivedStatus.from(List.of(
                stop(StopStatus.DELIVERED),
                stop(StopStatus.FAILED))))
                .isEqualTo(DispatchDerivedStatus.DELIVERED);
    }

    @Test
    void unparsedAndDelivered_returnsDelivered() {
        // UNPARSED 는 active 도 progress 도 아님 — DELIVERED 와 함께 있으면 모든 active=terminal → DELIVERED
        assertThat(DispatchDerivedStatus.from(List.of(
                stop(StopStatus.UNPARSED),
                stop(StopStatus.DELIVERED))))
                .isEqualTo(DispatchDerivedStatus.DELIVERED);
    }

    @Test
    void unparsedOnly_returnsPlanned() {
        // UNPARSED 만 있으면 진행 stop 없음 → PLANNED
        assertThat(DispatchDerivedStatus.from(List.of(stop(StopStatus.UNPARSED))))
                .isEqualTo(DispatchDerivedStatus.PLANNED);
    }

    private static VehicleStop stop(StopStatus status) {
        try {
            // VehicleStop protected ctor 우회 — status 만 reflection set
            java.lang.reflect.Constructor<VehicleStop> ctor = VehicleStop.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            VehicleStop s = ctor.newInstance();
            Field f = findField(VehicleStop.class, "status");
            f.setAccessible(true);
            f.set(s, status);
            Field idF = findField(VehicleStop.class, "id");
            idF.setAccessible(true);
            idF.set(s, UUID.randomUUID());
            return s;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> cur = clazz;
        while (cur != null) {
            try {
                return cur.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
