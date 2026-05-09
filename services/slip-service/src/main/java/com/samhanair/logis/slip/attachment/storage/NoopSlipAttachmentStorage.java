package com.samhanair.logis.slip.attachment.storage;

import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * No-op fallback — partner-service 의 NoopAttachmentStorage 패턴 일관.
 *
 * <p>{@code app.slip.minio.enabled=false} (default) 또는 MinIO 미가용 환경에서 upload 호출을
 * 단순 silent skip 처리하고 dummy URL 만 발급한다.
 *
 * <p>로컬 dev 시작 시 MinIO container 미기동 상태에서도 slip-service 가 정상 boot 가능하도록 보장.
 * 단위 테스트에서도 별도 mock 없이 빈 컨테이너로 동작.
 */
@Component
@ConditionalOnMissingBean(MinioSlipAttachmentStorage.class)
public class NoopSlipAttachmentStorage implements SlipAttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(NoopSlipAttachmentStorage.class);

    @Override
    public void upload(String storageKey, String contentType, long size, InputStream data) {
        log.warn("[noop-slip-storage] upload skipped — key={} size={} contentType={}",
                storageKey, size, contentType);
    }

    @Override
    public String presignedGetUrl(String storageKey) {
        return "noop://slip/" + storageKey;
    }
}
