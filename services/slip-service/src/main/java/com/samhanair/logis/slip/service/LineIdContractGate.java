package com.samhanair.logis.slip.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;

/**
 * [D-R8-9] 요청 레벨 <b>lineId 계약 마커</b> 게이트 — 매입 전표 / 매출 전표 / 견적 <b>공용 단일 구현</b>.
 *
 * <p><b>왜 마커인가 (D-R8-6 → D-R8-9 판정 기준 이전)</b>: D-R8-6 은 "계보 보유 문서인데 요청의
 * 전 라인이 lineId 미전송" 을 구 클라이언트로 보고 400 을 냈다. 그러나 그 기준은
 * <b>계보 보유 전표에서 전 라인을 지우고 전부 새 라인으로 교체하는 정상 저장</b>을 함께 막았다 —
 * 그 요청도 lineId 가 0개라 구 클라이언트의 통째 PUT 과 구분되지 않기 때문이다. 신규 라인의
 * {@code lineId == null} 은 정상값이므로, <b>라인을 세어서는</b> "계약을 아는 클라이언트" 와
 * "계약을 모르는 클라이언트" 를 영원히 구분할 수 없다.
 *
 * <p>따라서 판정 기준을 <b>"lineId 개수" → "요청 레벨 마커 유무"</b> 로 옮긴다. 마커는 클라이언트가
 * <i>자기 자신에 대해</i> 하는 선언이므로 라인 내용과 독립이다:
 * <ul>
 *   <li>마커 <b>부재</b> = 구 클라이언트 → <b>400</b></li>
 *   <li>마커 <b>존재</b> = lineId 계약 활성 → lineId 0개(전 라인 교체)도 <b>정상 허용</b></li>
 * </ul>
 *
 * <p><b>D-R8-9 근거 — 구버전 desktop 은 사실상 없다(개발책임자 확인, 전원 최신본).</b> 점진
 * 마이그레이션 창이 불필요하므로 마커를 <b>즉시 필수화</b>한다. 호환 창을 두면 그 창이 곧
 * R8-QA-1 이 실증한 조용한 파괴 경로로 남는다.
 *
 * <p><b>D-R8-6 은 유지된다</b> — 파괴 경로(무수정 왕복 PUT 이 계보 전량 파괴)는 여전히 차단된다.
 * 구 클라이언트는 마커를 보내지 않으므로 라인을 한 줄도 건드리기 전에 거부되고, 신 클라이언트는
 * 기존 라인의 lineId 를 Y.Doc 에서 직독해 되돌려 보내므로 계보가 승계된다.
 *
 * <p><b>왜 문서 계보 여부를 보지 않는가 (무조건 필수)</b>: 계보 보유 문서로 게이트를 한정하면
 * 구 클라이언트가 평면 문서에서는 200 을 받는다. 그 200 은 무해하지 않다 —
 * 구 클라이언트는 {@code partnerId}(D-R8-7 신규) 도 보내지 않으므로, 거래처를 바꿔 저장하면
 * {@code partner_id} 가 불변인 채 가격기억이 <b>원 거래처</b>에 각인된다(R8-QA-3 라이브 실증).
 * 즉 계보 유무와 무관하게 구 클라이언트의 쓰기는 전부 위험하다. 마커는 문서 상태가 아니라
 * <b>클라이언트 버전</b>을 판정하므로 게이트도 문서 상태와 무관해야 한다.
 *
 * <p><b>왜 공용 클래스인가</b>: 이 PR 은 전표/견적(그리고 매입/매출) <b>비대칭</b>을 8라운드째
 * 반복 적발했다. 미러 3벌을 각자 구현하면 언젠가 한 벌이 뒤처진다. 판정과 메시지를 이 클래스
 * 하나로 좁혀 <b>드리프트를 구조적으로 불가능</b>하게 한다 — 각 서비스는 이 게이트를 호출만 한다.
 */
public final class LineIdContractGate {

    /**
     * 400 사유 — 원인 · 결과 · 조치를 한국어로 명시한다.
     *
     * <p>이 메시지는 사용자가 직접 본다. "잘못된 요청" 류의 기술 메시지는 사용자가 무엇을 해야
     * 하는지 알려주지 않는다. 구 클라이언트를 쓰는 사용자에게 필요한 정보는 단 하나 —
     * <b>앱을 업데이트하라</b>는 것이다.
     */
    static final String REJECTION_MESSAGE =
            "구버전 앱에서 보낸 저장 요청입니다. 이대로 저장하면 세트 구성품 정보가 사라질 수 있어 "
                    + "요청을 거부했습니다. 앱을 업데이트한 뒤 다시 저장해 주세요.";

    private LineIdContractGate() {
    }

    /**
     * 요청이 lineId 계약을 선언했는지 검증한다. 선언하지 않았으면 400 으로 거부한다.
     *
     * <p><b>Jackson 함정과 이 시그니처의 관계</b>: Jackson 은 record 를 canonical 생성자로
     * 역직렬화하므로 <b>"필드 부재" 와 "명시적 null" 이 모두 {@code null} 로 도착</b>하며 둘을
     * 구분할 수 없다. 이 게이트는 그 구분을 <b>애초에 필요로 하지 않는다</b> — 부재 · 명시적 null ·
     * {@code false} 세 가지가 전부 "계약 미선언" 이라는 <b>같은 거부 결과</b>로 수렴하기 때문이다.
     * 통과하는 값은 오직 {@code true} 하나다. {@code Boolean.TRUE.equals} 는 세 경우 모두에서
     * 언박싱 NPE 없이 {@code false} 를 돌려주므로 <b>기본값이 곧 거부</b>(fail-closed)다.
     *
     * <p>원시 {@code boolean} 을 썼다면 부재 → {@code false} 로 코어싱되어 역시 거부로 수렴하나,
     * "말하지 않았다"({@code null})와 "아니라고 말했다"({@code false})를 로그·진단에서 구분할 수
     * 없다. 계약 위반을 조사할 때 그 구분이 사라지면 안 되므로 래퍼를 쓴다.
     *
     * @param lineIdContract 요청 레벨 마커. {@code true} 만 계약 선언으로 인정한다.
     * @throws BusinessException 400 INVALID_INPUT — 마커 부재/null/false
     */
    public static void require(Boolean lineIdContract) {
        if (!Boolean.TRUE.equals(lineIdContract)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, REJECTION_MESSAGE);
        }
    }

    /**
     * [D-R8-13] 계보 보유 문서에서 마커가 lineId 파괴를 <b>우회</b>할 때의 400 사유.
     *
     * <p>{@link #REJECTION_MESSAGE} 와 <b>다른</b> 사유다. 여기서 거부되는 클라이언트는 마커를
     * 보낸 <b>최신 앱</b>이지만, 화면에 떠 있는 라인이 서버 최신 상태와 어긋나(스테일) 세트 구성품
     * 계보를 보유한 문서에 lineId 를 하나도 싣지 못했다. 따라서 조치는 "앱 업데이트" 가 아니라
     * <b>화면 새로고침</b>이다 — 새로고침이 서버 최신 라인(과 그 lineId)을 다시 불러온다.
     */
    static final String LINEAGE_REJECTION_MESSAGE =
            "세트 구성품이 포함된 전표는 기존 라인 정보(lineId) 없이 전체 교체할 수 없습니다. "
                    + "이대로 저장하면 세트 구성품 계보가 사라질 수 있어 요청을 거부했습니다. "
                    + "화면을 새로고침해 최신 라인을 불러온 뒤 다시 저장해 주세요.";

    /**
     * [D-R8-13] 마커가 계보 파괴를 <b>우회하지 못하게</b> 한다 — 마커를 라인 내용과 대조한다.
     *
     * <p><b>왜 마커만으로는 부족한가</b>: {@link #require} 의 마커는 "이 클라이언트는 lineId 계약을
     * 안다" 는 <b>자기신고</b>일 뿐이다. 스테일/악성 클라이언트가 {@code lineIdContract=true} 를 실은
     * 채 <b>계보 보유 문서</b>에서 lineId 를 한 개도 안 실으면, 서버는 마커만 보고 전 라인 교체를
     * 수행해 <b>200</b> 을 돌려주고 세트 계보를 전량 파괴한다(R8-QA-13 라이브 실증:
     * {@code parent→NULL}·{@code set_head→false}). 이는 R8-QA-1(무수정 왕복 PUT 이 계보 파괴)을
     * <b>마커라는 다른 문</b>으로 재개방한 것이다. 그래서 서버가 마커를 <b>문서 내용과 대조</b>한다.
     *
     * <p><b>오탐 방지가 판정의 핵심</b>: 다음 두 경우는 정상 저장이므로 반드시 통과시킨다.
     * <ul>
     *   <li>계보 <b>없는</b> 평면 문서 + lineId 0개 = 전 라인 교체(정상) → {@code documentHasLineage == false}</li>
     *   <li>계보 보유 문서 + lineId <b>1개 이상</b> = 부분 편집(정상) → {@code requestedLineIdCount > 0}</li>
     * </ul>
     * 오직 <b>"계보 보유" 와 "lineId 전무" 가 동시에 성립</b>할 때만 거부한다.
     *
     * @param documentHasLineage 기존 문서가 세트 계보(BUNDLE_SET) 를 보유하는지
     *        ({@link BundleLineageResolver#hasBundleLineage()})
     * @param requestedLineIdCount 요청이 실은 non-null lineId 개수
     * @throws BusinessException 400 INVALID_INPUT — 계보 보유 문서인데 요청 non-null lineId 가 0개
     */
    public static void requireLineIdsForLineage(boolean documentHasLineage, int requestedLineIdCount) {
        if (documentHasLineage && requestedLineIdCount == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, LINEAGE_REJECTION_MESSAGE);
        }
    }
}
