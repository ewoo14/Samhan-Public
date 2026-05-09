package com.samhanair.logis.partner.service;

import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * No-op fallback. {@code app.partner.minio.enabled=false} (default) 또는 MinIO 미가용 환경에서
 * upload 호출을 단순 silent skip 처리하고 dummy URL 만 발급한다.
 *
 * <p>로컬 dev 시작 시 MinIO container 미기동 상태에서도 partner-service 가 정상 boot 가능하도록 보장.
 * 단위 테스트에서도 별도 mock 없이 빈 컨테이너로 동작.
 */
@Component
@ConditionalOnMissingBean(MinioAttachmentStorage.class)
public class NoopAttachmentStorage implements AttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(NoopAttachmentStorage.class);

    @Override
    public void upload(String storageKey, String contentType, long size, InputStream data) {
        log.warn("[noop-storage] upload skipped — key={} size={} contentType={}",
                storageKey, size, contentType);
    }

    @Override
    public String presignedGetUrl(String storageKey) {
        // local-noop dummy URL — 클라이언트가 401 받게 되어 자연스럽게 운영 환경 활성 필요성 인지
        return "noop://local/" + storageKey;
    }
}
