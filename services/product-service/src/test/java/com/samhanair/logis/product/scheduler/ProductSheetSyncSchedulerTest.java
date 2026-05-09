package com.samhanair.logis.product.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * ProductSheetSyncScheduler cron expression 단위 테스트 — PR-D Part 1.
 *
 * <p>핵심 검증: scheduledSync() 의 {@link Scheduled#cron()} 이 5분 default 표현식
 * ({@code 0 *&#47;5 * * * *}) 으로 정정되어 있는지. application.yml override key 와의
 * 일치 가드. (PR-D Part 1 회고: 1시간 → 5분 단축, 6 tab × 12회/시간 = 72 read/시간 quota 안전)
 */
class ProductSheetSyncSchedulerTest {

    @Test
    void scheduledSync_cron_default가_5분_표현식() throws NoSuchMethodException {
        Method method = ProductSheetSyncScheduler.class.getMethod("scheduledSync");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        // application.yml 의 app.scheduling.product-sync-cron override key + 5분 default 동시 보장
        assertThat(scheduled.cron())
                .isEqualTo("${app.scheduling.product-sync-cron:0 */5 * * * *}");
    }

    @Test
    void scheduledSync_cron_default_5분_표현식_파싱가능() throws NoSuchMethodException {
        Method method = ProductSheetSyncScheduler.class.getMethod("scheduledSync");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        // default 부분 (placeholder 안의 fallback) 추출 — Spring CronExpression 으로 검증
        String cronExpr = scheduled.cron();
        int colonIdx = cronExpr.indexOf(':');
        int closeIdx = cronExpr.indexOf('}');
        String defaultCron = cronExpr.substring(colonIdx + 1, closeIdx);

        assertThat(defaultCron).isEqualTo("0 */5 * * * *");
        // 6-필드 spring scheduler cron — Spring CronExpression 으로 정상 파싱
        org.springframework.scheduling.support.CronExpression parsed =
                org.springframework.scheduling.support.CronExpression.parse(defaultCron);
        assertThat(parsed).isNotNull();
    }
}
