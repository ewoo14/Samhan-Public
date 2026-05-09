package com.samhanair.logis.notification.domain;

/**
 * 단톡방 매핑 출처 — NOTION_IMPORT (CSV 시드) / MANUAL (admin 단건 등록).
 *
 * <p>PR-D Part 2-3 — Samhan Public 프로그램 native 이식 시점에 Notion DB 111 매핑을 NOTION_IMPORT 로
 * 적재, 이후 운영 추가 매핑은 MANUAL 로 구분. 감사 / 마이그레이션 추적 용도.
 */
public enum MappingSource {

    /** Notion CSV import (PR-D 2-3 시드) */
    NOTION_IMPORT,

    /** admin 단건 등록 */
    MANUAL
}
