package com.samhanair.logis.slip.attachment.storage;

import java.io.InputStream;

/**
 * 슬립 첨부 파일 객체 스토리지 추상화 — partner-service 의 AttachmentStorage 패턴 일관.
 *
 * <p>운영/dev 환경: {@link MinioSlipAttachmentStorage} 가 io.minio client 로 실 업로드.
 * 테스트/CI 환경 (MinIO 미가용): {@link NoopSlipAttachmentStorage} fallback.
 *
 * <p>presigned URL 정책:
 * <ul>
 *   <li>유효기간 = 1시간 (사용자 다운로드 1회성 + 만료 후 재발급 패턴)</li>
 *   <li>method = GET</li>
 *   <li>bucket = "slip-attachments" (DevOps 가 별도 dispatch 로 생성)</li>
 * </ul>
 */
public interface SlipAttachmentStorage {

    /**
     * 객체 업로드.
     *
     * @param storageKey object key (예: "slip-attachments/{slipId}/{uuid}.jpg")
     * @param contentType MIME
     * @param size byte 크기
     * @param data 입력 stream (호출 측에서 close 책임)
     */
    void upload(String storageKey, String contentType, long size, InputStream data);

    /**
     * presigned GET URL 발급 (1시간 유효).
     *
     * @param storageKey object key
     * @return presigned URL (만료 1시간)
     */
    String presignedGetUrl(String storageKey);
}
