package com.samhanair.logis.partner.service;

import java.io.InputStream;

/**
 * 첨부 파일 객체 스토리지 추상화 (MinIO / S3 / local-noop 어떤 백엔드든 교체 가능).
 *
 * <p>운영/dev 환경: {@link MinioAttachmentStorage} 가 io.minio client 로 실제 업로드.
 * 테스트/CI 환경 (MinIO 미가용): 주입된 spy/mock 으로 대체 가능.
 *
 * <p>presigned URL 정책:
 * <ul>
 *   <li>유효기간 = 1시간 (memory: 사용자 다운로드 1회성 + 만료 후 재발급 패턴)</li>
 *   <li>method = GET</li>
 *   <li>bucket = "partner-attachments"</li>
 * </ul>
 */
public interface AttachmentStorage {

    /**
     * 객체 업로드.
     *
     * @param storageKey object key (예: "partner-attachments/{partnerId}/{uuid}.png")
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
