package com.samhanair.logis.notification.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 거래처 ↔ 카톡 단톡방 매핑 1건 (PR-D Part 2-3 — Samhan Public native 이식).
 *
 * <p>Notion DB "단톡방리스트" 111 매핑을 source-of-truth 로 import. partner_code 가 진짜 키 (논리 FK,
 * partner-service DB 분리이므로 물리 FK 미설정), partner_business_name_snapshot 은 import 시점 사업자명
 * (감사용 — partner-service 측 거래처 리네임 시 drift 무시).
 *
 * <p>사용자 명시 (PR-D 2-3 작업 지시): "추후 거래처명이 아니라 거래처코드로 매핑할 수 있도록"
 * → partner_code 만 발주 라우팅에 사용, business_name 은 표시/감사 only.
 *
 * <p>UUID 비공개 가드 — 사용자 노출 = partner_business_name_snapshot + chat_room_name (UUID 미노출).
 *
 * <p>매핑 카디널리티 = N:M (1 거래처가 여러 발주방 + 1 발주방에 여러 거래처). partial unique index
 * (partner_code, chat_room_name) WHERE is_deleted=FALSE 가 활성 중복만 방지.
 */
@Entity
@Getter
@Table(name = "partner_chat_room_mappings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class PartnerChatRoomMapping extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** partner-service 의 partner_code (논리 FK — DB 분리로 물리 FK 미설정). source-of-truth. */
    @Column(name = "partner_code", nullable = false, length = 50, updatable = false)
    private String partnerCode;

    /** import 시점 매칭된 사업자명 (감사용 snapshot). 거래처 리네임 시에도 본 행은 갱신 안함. */
    @Column(name = "partner_business_name_snapshot", nullable = false, length = 200)
    private String partnerBusinessNameSnapshot;

    /** 카톡방 이름 (예: "에어디자이너(구 지에스) 발주방"). 발주 시 그룹 라우팅 키. */
    @Column(name = "chat_room_name", nullable = false, length = 200)
    private String chatRoomName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20, updatable = false)
    private MappingSource source;

    /** Notion DB "생성 일시" 컬럼 — import 시 파싱하여 보존 (감사/시퀀스 용). null 허용 (MANUAL 등록 시). */
    @Column(name = "notion_created_at", updatable = false)
    private LocalDateTime notionCreatedAt;

    /** 거래처코드 연결 상태 — 미연결 alias도 화면·데이터에서 보존한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "partner_link_status", nullable = false, length = 32)
    private PartnerLinkStatus partnerLinkStatus;

    /** 연결 또는 미연결 판정 근거. */
    @Column(name = "partner_link_reason", length = 255)
    private String partnerLinkReason;

    private PartnerChatRoomMapping(String partnerCode,
                                   String partnerBusinessNameSnapshot,
                                   String chatRoomName,
                                   MappingSource source,
                                   LocalDateTime notionCreatedAt) {
        if (partnerCode == null || partnerCode.isBlank()) {
            throw new IllegalArgumentException("partnerCode 필수");
        }
        if (chatRoomName == null || chatRoomName.isBlank()) {
            throw new IllegalArgumentException("chatRoomName 필수");
        }
        if (partnerBusinessNameSnapshot == null || partnerBusinessNameSnapshot.isBlank()) {
            throw new IllegalArgumentException("partnerBusinessNameSnapshot 필수");
        }
        if (source == null) {
            throw new IllegalArgumentException("source 필수");
        }
        this.partnerCode = partnerCode;
        this.partnerBusinessNameSnapshot = partnerBusinessNameSnapshot;
        this.chatRoomName = chatRoomName;
        this.source = source;
        this.notionCreatedAt = notionCreatedAt;
        this.partnerLinkStatus = PartnerLinkStatus.UNLINKED;
    }

    /** Notion CSV import 경유 신규 매핑 (source=NOTION_IMPORT). */
    public static PartnerChatRoomMapping fromNotionImport(String partnerCode,
                                                          String partnerBusinessNameSnapshot,
                                                          String chatRoomName,
                                                          LocalDateTime notionCreatedAt) {
        return new PartnerChatRoomMapping(partnerCode, partnerBusinessNameSnapshot, chatRoomName,
                MappingSource.NOTION_IMPORT, notionCreatedAt);
    }

    /** admin 단건 등록 신규 매핑 (source=MANUAL). */
    public static PartnerChatRoomMapping manual(String partnerCode,
                                                String partnerBusinessNameSnapshot,
                                                String chatRoomName) {
        return new PartnerChatRoomMapping(partnerCode, partnerBusinessNameSnapshot, chatRoomName,
                MappingSource.MANUAL, null);
    }

    /** snapshot 사업자명 갱신 (admin 수정 — 매핑 본질은 partner_code, snapshot 만 보정). */
    public void updateBusinessNameSnapshot(String newSnapshot) {
        if (newSnapshot == null || newSnapshot.isBlank()) {
            throw new IllegalArgumentException("partnerBusinessNameSnapshot 필수");
        }
        this.partnerBusinessNameSnapshot = newSnapshot;
    }
}
