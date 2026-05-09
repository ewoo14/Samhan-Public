/**
 * 세금계산서 인쇄 미리보기 — `/sales/:id/print/tax-invoice`.
 *
 * P0-4 인쇄 양식 5건 1차 mock — Designer 단계 신규.
 * 한국 국세청 (NTS) 전자세금계산서 표준 양식을 모사한다.
 *
 * 표준 구성 (e-Tax 표준):
 * - 빨간색 "세금계산서 (공급받는자 보관용)" 타이틀
 * - 책번호 / 일련번호 (좌상단)
 * - 공급자 박스 (좌): 등록번호 / 종사업장번호 / 상호 / 성명 / 사업장 주소 / 업태 / 종목
 * - 공급받는자 박스 (우): 동일 7항목
 * - 작성일자 (연/월/일 셀 분리)
 * - 공급가액 / 세액 (조-천억-...-원 셀 분리, 11자리)
 * - 합계금액 (한글 + 숫자)
 * - 라인 표 (월/일/품목/규격/수량/단가/공급가/세액/비고)
 * - 받은이 / 청구 체크박스
 * - 비고 / 영수 / 청구
 *
 * 출처: `docs/manual/06-트러블슈팅/03-인쇄-안됨.md` §3 (P0-4 세금계산서).
 *
 * Iteration 가드 (memory `feedback_print_design_iteration.md`):
 * 본 1차 mock — 사용자 Edge 캡처 검토 후 2~5차 갱신 예정.
 *
 * Note — 본 mock 은 slip-service 의 SlipDetail 데이터로 시연한다. 정식 운영 시
 * accounting-service 의 TaxInvoice 도메인 (별도 entity) 데이터로 교체.
 */
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getSlip, type SlipDetail } from '../api/slip'
import { usePageTitle } from '../hooks/usePageTitle'
import { PrintLayout, COMPANY, krDate, toKoreanAmount, calcAmounts } from './PrintLayout'

/**
 * 정수 → 11자리 셀 분리 (조-천억-백억-십억-억-천만-백만-십만-만-천-백-십-원).
 * e-Tax 표준 11자리 — 공급가액/세액 셀에 사용. 빈자리는 공백.
 *
 * @example splitDigits(1234567) → ['', '', '', '', '', '', '1', '2', '3', '4', '5', '6', '7']
 *   (실제 13칸 — 조 1, 억 4, 만 4, 원 4 = 13 자리. 본 양식은 11자리 표준 사용)
 *
 * 본 mock 은 11자리만 사용 (백억 단위까지) — 조 단위는 후속 iteration.
 */
function splitDigits11(n: number): string[] {
  const s = String(Math.max(0, Math.floor(n)))
  const padded = s.padStart(11, ' ')
  return padded.split('')
}

/**
 * "YYYY-MM-DD" → { year, month, day } 분리 (작성일자 셀 분리용).
 */
function splitDate(iso: string | null | undefined): { year: string; month: string; day: string } {
  if (!iso) return { year: '', month: '', day: '' }
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  if (!m) return { year: '', month: '', day: '' }
  return { year: m[1] ?? '', month: m[2] ?? '', day: m[3] ?? '' }
}

/** 공급/세액 11자리 셀 — e-Tax 표준 라벨 (조-천억-백억-십억-억-천만-백만-십만-만-천-백-십-원). */
const DIGIT_LABELS = ['천억', '백억', '십억', '억', '천만', '백만', '십만', '만', '천', '백', '십', '원']

export function TaxInvoiceView() {
  const params = useParams<{ id: string }>()
  const id = params.id ?? ''
  const detailQuery = useQuery({
    queryKey: ['slip', id],
    queryFn: () => getSlip(id),
    enabled: !!id,
  })

  usePageTitle('세금계산서', detailQuery.data?.slipNo)

  if (!id) return null
  if (detailQuery.isLoading) return <p>불러오는 중...</p>
  if (detailQuery.isError || !detailQuery.data) {
    return (
      <div className="error-banner" role="alert">
        전표를 불러오지 못했습니다.
      </div>
    )
  }

  const slip: SlipDetail = detailQuery.data
  const totalSupply = slip.lines.reduce((sum, l) => sum + Number(l.lineTotal), 0)
  const { supply, vat, total } = calcAmounts(totalSupply)
  const writeDate = splitDate(slip.slipDate)

  // 책번호 / 일련번호 — placeholder (정식 운영 시 NTS 발급 식별자 사용)
  const bookNo = '권              호'
  const serialNo = '일련번호                    -'

  return (
    <PrintLayout paper="a4-portrait" backTo={`/sales/${id}`}>
      <div className="tax-invoice-page">
        {/* 상단 — 책번호 / 일련번호 / 빨간 타이틀 */}
        <header className="tax-invoice-top">
          <div className="tax-invoice-book">
            <div>{bookNo}</div>
            <div>{serialNo}</div>
          </div>
          <h1 className="tax-invoice-title">세 금 계 산 서 <span className="tax-invoice-title-sub">(공급받는자 보관용)</span></h1>
        </header>

        {/* 공급자 + 공급받는자 박스 */}
        <table className="tax-invoice-parties">
          <tbody>
            <tr>
              <td className="party-side party-supplier" rowSpan={5}>공<br />급<br />자</td>
              <th>등록번호</th>
              <td className="party-regno">{COMPANY.businessRegNo}</td>
              <td className="party-side party-receiver" rowSpan={5}>공<br />급<br />받<br />는<br />자</td>
              <th>등록번호</th>
              <td className="party-regno">- - -</td>
            </tr>
            <tr>
              <th>상호<br />(법인명)</th>
              <td>{COMPANY.legalName}</td>
              <th>성명</th>
              <td className="seal-cell">{COMPANY.ceo}<span className="party-seal">(인)</span></td>
              <th>상호<br />(법인명)</th>
              <td>{slip.partnerName ?? '-'}</td>
            </tr>
            <tr>
              <th>사업장<br />주소</th>
              <td colSpan={3}>{COMPANY.address}</td>
              <th>사업장<br />주소</th>
              <td>{slip.shippingAddress ?? '-'}</td>
            </tr>
            <tr>
              <th>업태</th>
              <td>{COMPANY.businessType}</td>
              <th>종목</th>
              <td>{COMPANY.businessItem}</td>
              <th>업태</th>
              <td>-</td>
            </tr>
            <tr>
              <th>종사업장<br />번호</th>
              <td>{COMPANY.subBusinessNo}</td>
              <th>전화</th>
              <td className="num">{COMPANY.tel}</td>
              <th>종목</th>
              <td>-</td>
            </tr>
          </tbody>
        </table>

        {/* 작성일자 + 공급가액 + 세액 (11자리 셀 분리) */}
        <table className="tax-invoice-amounts">
          <thead>
            <tr>
              <th rowSpan={2} className="col-write-date">작성</th>
              <th colSpan={12}>공 급 가 액</th>
              <th colSpan={12}>세 액</th>
              <th rowSpan={2} className="col-remark">비 고</th>
            </tr>
            <tr>
              {DIGIT_LABELS.map((lbl) => (
                <th key={`s-${lbl}`} className="digit-label">{lbl}</th>
              ))}
              {DIGIT_LABELS.map((lbl) => (
                <th key={`v-${lbl}`} className="digit-label">{lbl}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            <tr>
              <td className="write-date">
                <div>{writeDate.year}</div>
                <div>{writeDate.month}.{writeDate.day}</div>
              </td>
              {splitDigits11(supply).map((d, i) => (
                <td key={`sd-${i}`} className="digit-cell">{d}</td>
              ))}
              {splitDigits11(vat).map((d, i) => (
                <td key={`vd-${i}`} className="digit-cell">{d}</td>
              ))}
              <td className="tax-invoice-remark">{slip.memo ?? ''}</td>
            </tr>
          </tbody>
        </table>

        {/* 라인 표 — 월/일/품목/규격/수량/단가/공급가/세액/비고 */}
        <table className="tax-invoice-lines">
          <thead>
            <tr>
              <th className="col-month">월</th>
              <th className="col-day">일</th>
              <th className="col-product">품 목</th>
              <th className="col-spec">규 격</th>
              <th className="col-qty">수 량</th>
              <th className="col-price">단 가</th>
              <th className="col-supply">공 급 가 액</th>
              <th className="col-vat">세 액</th>
              <th className="col-note">비 고</th>
            </tr>
          </thead>
          <tbody>
            {slip.lines.map((l) => {
              const lineSupply = Number(l.lineTotal)
              const lineVat = Math.floor(lineSupply * 0.1)
              const productLabel = l.modelName
                ? `${l.modelName}${l.productName ? ` (${l.productName})` : ''}`
                : (l.productName ?? '-')
              return (
                <tr key={l.id}>
                  <td className="col-month num">{writeDate.month}</td>
                  <td className="col-day num">{writeDate.day}</td>
                  <td className="col-product">{productLabel}</td>
                  <td className="col-spec">{l.specification ?? ''}</td>
                  <td className="col-qty num">{l.quantity.toLocaleString()}</td>
                  <td className="col-price num">{lineSupply > 0 ? Math.round(lineSupply / Math.max(1, l.quantity)).toLocaleString() : ''}</td>
                  <td className="col-supply num">{lineSupply.toLocaleString()}</td>
                  <td className="col-vat num">{lineVat.toLocaleString()}</td>
                  <td className="col-note">{l.note ?? ''}</td>
                </tr>
              )
            })}
            {Array.from({ length: Math.max(0, 4 - slip.lines.length) }).map((_, i) => (
              <tr key={`pad-${i}`} className="pad-row">
                <td>&nbsp;</td>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
              </tr>
            ))}
          </tbody>
        </table>

        {/* 합계금액 + 현금/수표/어음/외상/영수/청구 */}
        <table className="tax-invoice-bottom">
          <thead>
            <tr>
              <th className="col-total-label">합계금액</th>
              <th className="col-cash">현 금</th>
              <th className="col-check">수 표</th>
              <th className="col-bill">어 음</th>
              <th className="col-credit">외상미수금</th>
              <th rowSpan={2} className="col-receipt-claim">
                <div>이 금액을</div>
                <div className="receipt-claim-options">
                  <span className="check-box">□ 영수</span>
                  <span className="check-box">■ 청구</span>
                </div>
                <div>함</div>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td className="col-total-amount num strong">{total.toLocaleString()}</td>
              <td className="num">&nbsp;</td>
              <td className="num">&nbsp;</td>
              <td className="num">&nbsp;</td>
              <td className="num">{total.toLocaleString()}</td>
            </tr>
          </tbody>
        </table>

        {/* 한글 합계 (보조) */}
        <div className="tax-invoice-korean-total">
          <span className="label">금액(한글)</span>
          <span className="value">{toKoreanAmount(total)}</span>
        </div>

        {/* 발행일 */}
        <div className="tax-invoice-issue-date">
          작성일자: {krDate(slip.slipDate)}
        </div>
      </div>
    </PrintLayout>
  )
}
