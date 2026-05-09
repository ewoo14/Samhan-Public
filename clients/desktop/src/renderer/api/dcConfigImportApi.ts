/**
 * DC 거래처 할인 정보 CSV import API 클라이언트 (PR-D Phase B FE-C).
 *
 * <p>BE-C ({@code dc-config-service} commit b44ede3) 의
 * {@code POST /api/v1/dc-config/admin/import} multipart endpoint 호출 wrapper.
 *
 * <h2>호출 흐름</h2>
 * <ol>
 *   <li>Notion 에서 다운받은 "거래처 DC정보" CSV 를 사용자 선택</li>
 *   <li>{@link importDcConfigCsv} 가 multipart/form-data 로 전송</li>
 *   <li>응답 {@link DcConfigImportResult} 를 design-system {@code CsvUploadDialog}
 *       의 {@code UploadResult} 형태로 매핑하여 반환</li>
 * </ol>
 *
 * <h2>접근 제어</h2>
 * <ul>
 *   <li>endpoint 자체가 MASTER role 만 허용 ({@code @PreAuthorize("hasRole('MASTER')")} on BE)</li>
 *   <li>FE 도 호출자 화면에서 MASTER 가드를 적용해 버튼 노출 자체를 제한</li>
 * </ul>
 *
 * <p>UUID 비공개 — 응답에 partner UUID 가 포함되지 않으며, 거부 보고서의
 * {@code partnerCode} / {@code businessName} 만 사용자에게 노출한다.
 */
import type { UploadResult } from '@samhan/design-system'
import { apiClient, type ApiEnvelope } from './client'

/**
 * BE {@code DcConfigImportResult.RejectedRow} 와 1:1.
 */
export interface DcConfigRejectedRow {
  /** CSV row 번호 (1-base, header 제외). */
  rowNumber: number
  /** CSV `거래처코드` 값 (없으면 빈 문자열). */
  partnerCode: string
  /** CSV `업체명` 값. */
  businessName: string
  /** 거부 사유 (한국어). */
  reason: string
}

/**
 * BE {@code DcConfigImportResult} record 와 1:1.
 */
export interface DcConfigImportResult {
  /** 신규 생성된 dc_configs row 수. */
  inserted: number
  /** 갱신 row 수 (partner_code 매칭 → upsert). */
  updated: number
  /** 변동 없이 skip 된 row 수 (예: 빈 라인). */
  skipped: number
  /** partner_code 부재 등으로 거부된 row 의 상세 보고. */
  rejected: DcConfigRejectedRow[]
}

/**
 * Notion CSV 를 multipart/form-data 로 업로드하여 dc_configs 일괄 upsert.
 *
 * <p>{@code CsvUploadDialog} 의 {@code onUpload} prop 시그니처에 맞춰
 * {@link UploadResult} 를 반환한다 ({@code rowNumber} / {@code inputData} /
 * {@code reason} 형태로 거부 보고서 변환).
 *
 * @param file 사용자가 선택한 CSV 파일 (UTF-8, BOM 허용, 5MB 이하 권장)
 * @return CsvUploadDialog 가 단계 3 결과 표시에 사용하는 통계 + 거부 보고서
 */
export async function importDcConfigCsv(file: File): Promise<UploadResult> {
  const form = new FormData()
  form.append('file', file)
  const res = await apiClient.post<ApiEnvelope<DcConfigImportResult>>(
    '/api/v1/dc-config/admin/import',
    form,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      // 대용량 CSV 대비 — apiClient 기본 10s 보다 여유.
      timeout: 60_000,
    },
  )
  const data = res.data.data
  return {
    inserted: data.inserted,
    updated: data.updated,
    skipped: data.skipped,
    rejected: data.rejected.map((r) => ({
      rowNumber: r.rowNumber,
      inputData: {
        거래처코드: r.partnerCode,
        업체명: r.businessName,
      },
      reason: r.reason,
    })),
  }
}
