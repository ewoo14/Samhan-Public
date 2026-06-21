package com.samhanair.logis.approval;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * shared:approval-core 자동 설정 진입점(모듈 경계 anchor).
 *
 * <p>본 module 은 @MappedSuperclass 베이스 + 제네릭 서비스만 제공한다. 구체 서비스 bean 은 소비
 * service 가 자기 entity/repository 타입으로 등록한다(collab-core 와 동일). collab-core 와 달리
 * realtime broker 의존이 없으므로 조건부 빈을 두지 않는다(결재 알림은 소비 서비스가 배선).
 */
@AutoConfiguration
public class ApprovalCoreAutoConfiguration {
}
