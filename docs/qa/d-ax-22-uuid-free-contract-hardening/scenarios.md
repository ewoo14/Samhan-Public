# D-AX-22 UUID-free contract hardening QA 시나리오

## 목적

D-AX-22 는 기사-facing API 와 모바일/데스크톱 표시 영역에서 내부 UUID, raw `downloadUrl`, object `storageKey` 가 새어 나오지 않도록 계약을 강화한다. 특히 `sourceWarehouseName` 은 창고명이어야 하며 `sourceWarehouseId.toString()` placeholder 또는 UUID 문자열이면 실패로 본다.

## 공통 금지 패턴

| 항목 | 금지 기준 |
|---|---|
| UUID 값 | `[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}` |
| 원본 URL | `downloadUrl`, `http://`, `https://`, `X-Amz-`, `presigned` |
| 저장 키 | `storageKey`, `objectKey`, `slip-attachments/`, `partner-attachments/` |
| 내부 ID 필드 | `id`, `dispatchId`, `vehicleId`, `stopId`, `slipId`, `attachmentId`, `signatureId` 화면 표시 |

> 예외: `X-User-Id` 인증 헤더와 서버 내부 repository key 는 내부 처리/호환 영역이다. driver-facing 응답 body/header, 모바일 파일명, toast, 화면, 접근성 라벨, 캡처에는 서명/GPS/전표 내부키를 표시하지 않는다.

## 시나리오 매트릭스

| ID | 범위 | 시나리오 | 핵심 assertion | 권장 테스트 |
|---|---|---|---|---|
| D-AX22-01 | BE API | `GET /driver-app/arologis/dispatches/today` 는 오늘 본인 배차만 반환한다. | `$.data[*].dispatchId`, `vehicleId`, `stopId`, `driverId` 미존재. 정차 target 은 `dispatchType + vehicleSequence + stopSequence + parsedKakaoSeq` 만 사용. | `ArologisDriverAppControllerIT.today_with_internal_driver_returns_200` 보강 |
| D-AX22-02 | BE API | `GET /driver-app/arologis/dispatches/today/{dispatchType}/vehicles/{vehicleSeq}/stops/{stopSeq}/slip-detail` 정상 응답. | 공개 필드만 존재: `slipNo`, `partnerName`, `deliveryAddress`, `sourceWarehouseName`, `lines`. JSON 전체에 UUID/downloadUrl/storageKey 금지 패턴 0건. | `ArologisDriverAppControllerTest.slipDetailToday_returns_uuid_free_read_model` 보강 + MockMvc JSON string scan |
| D-AX22-03 | BE API | `sourceWarehouseName` 이 UUID placeholder 로 내려오는 회귀 차단. | `sourceWarehouseName` 은 표시명 또는 중립 fallback(`창고명 확인 필요`)만 허용. UUID regex 또는 `null` fallback 남발 금지. | slip-service `SlipInternalController.findFullDetail` 단위/MockMvc 테스트 |
| D-AX22-04 | BE API | `POST /driver-app/arologis/locations` GPS 보고 성공. | 요청 latitude/longitude/capturedAt/source 저장. 응답은 `capturedAt`, `source`, `accepted` 만 공개한다. 잘못된 source 는 `APP_GPS_ACTIVE` fallback. | `ArologisDriverAppControllerIT.report_location_with_background_source_returns_200` 보강 |
| D-AX22-05 | BE API | GPS 보고 권한/미등록 기사. | `X-User-Id` 누락은 403, 미등록 appUserId 는 404/NOT_FOUND. 응답 body 에 내부 driver UUID 포함 금지. | `ArologisDriverAppControllerIT` 신규 negative |
| D-AX22-06 | BE API | `sign-and-send-copy` today UUID-free 경로 성공. | 요청 path 에 UUID 없음. 200 `image/png`. body/파일명/모바일 표시에는 UUID 없음. header 의 `X-Copy-Recipient-Phone-Masked` 는 마스킹. | `SignAndSendCopyIT` today 경로 variant 추가 |
| D-AX22-07 | BE API | 인수자 번호 없음. | 200 JSON, `copySent=false`, `copyFailureReason=RECIPIENT_PHONE_MISSING`, renderer 미호출. JSON body 에 `downloadUrl/storageKey/copyImagePath` 없음. | `SignatureCopyMissingPhoneIT` body scan 보강 |
| D-AX22-08 | BE API | renderer timeout / renderer error. | 200 JSON, `copyFailureReason=RENDERER_TIMEOUT` 또는 `RENDERER_ERROR`, retry 가능 UI를 위한 상태만 공개. `copyImagePath`, storage path, stack trace 미노출. | `SignatureCopyRendererTimeoutIT` 보강 + renderer error case |
| D-AX22-09 | BE API | slip-service bridge 실패. | 422 JSON, `error=SIGNATURE_BRIDGE_FAILED:*`, `retryable=true`, signature insert rollback. 내부 slipId/dispatchId/stopId 메시지 금지. | `SignatureCopyAtomicFailIT` 보강 |
| D-AX22-10 | BE API | 이미 발송된 정차 duplicate. | 409 JSON, `COPY_ALREADY_SENT`, `previousCopySentAt` 만 공개. 기존 `copyImagePath`, `signatureId`, storage key 미노출. | `SignatureCopyDuplicateIT` 보강 |
| D-AX22-11 | Mobile API | `fetchStopSlipDetail` 는 서버가 실수로 내부 필드를 포함해도 normalize 결과에서 제거한다. | raw fixture 에 UUID/downloadUrl/storageKey 를 넣고 `JSON.stringify(result)` 에 금지 패턴 0건. `sourceWarehouseName` 이 UUID면 `창고명 확인 필요` 또는 null-safe 표시로 치환. | `clients/arologis-mobile/src/__tests__/api/arologisSlipDetail.test.ts` |
| D-AX22-12 | Mobile UI | 전표 상세 화면 표시. | 화면 텍스트/JSON tree 에 UUID/downloadUrl/storageKey 0건. `출고창고` 값은 창고명 또는 `창고명 확인 필요`, UUID 문자열 금지. | `DriverSlipDetailScreen.test.tsx` |
| D-AX22-13 | Mobile UI | GPS 탭 보고. | 시작/1회 보고 시 body 는 `latitude`, `longitude`, `capturedAt`, `source=APP_GPS_ACTIVE`. 화면에는 `locationId` UUID 미표시, 성공 toast 는 시각/source 수준만. | `DriverLocationTrackingScreen` Jest 또는 Detox |
| D-AX22-14 | Mobile UI | 서명 성공 share. | API 호출 path 는 today target. 캐시 파일명은 `arologis-signature-copy-{dispatchType}-v{vehicleSeq}-s{stopSeq}-{timestamp}.png`; UUID, downloadUrl, storageKey 미포함. | `DriverSignatureScreen.test.tsx` |
| D-AX22-15 | Mobile UI | 서명 실패/duplicate/bridge UI. | 실패 toast 와 retry CTA 에 내부 ID, URL, storage key 미표시. duplicate 은 재시도 버튼 없음. bridge/renderer retry 만 재시도 버튼 노출. | `DriverSignatureScreen.test.tsx` |
| D-AX22-16 | Mobile UI | 사진 업로드 응답 normalize. | raw `downloadUrl`, `storageKey`, `attachmentId`, `slipId` 주입 시 결과/화면에서 제거. GPS EXIF 값은 API body 로만 전달하고 목록에는 좌표 원문 대신 성공/없음 상태만 표시. | `arologisPhotoUpload.test.ts`, `DriverPhotoScreen.test.tsx` |
| D-AX22-17 | Desktop | 아로로지스/전표/사진 감사 화면에 내부 식별자 미표시. | rendered text, accessibility name, tooltip, toast, table cell 에 UUID/downloadUrl/storageKey 0건. | Playwright static + render guard |
| D-AX22-18 | Desktop | desktop attachment/download 사용처 회귀 구분. | 운영자용 preview 내부 state 에 `downloadUrl` 이 있어도 화면 라벨/캡처/CSV export 에 raw URL 미표시. 사용자가 볼 링크는 proxy/action label 만. | `clients/desktop/playwright` 신규 privacy spec |
| D-AX22-19 | Screenshot guard | PR 첨부 캡처. | `docs/qa/d-ax-22-uuid-free-contract-hardening/screenshots/*.png` 생성 시 캡처 HTML/text source 에 금지 패턴 0건. PR 본문 최소 1장 인라인. | screenshot generator 또는 Playwright artifact |

## 테스트 assertion 세부안

### Backend MockMvc/JUnit

```java
String body = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
assertThat(body).doesNotContain("downloadUrl", "storageKey", "objectKey", "slip-attachments/");
assertThat(body).doesNotMatch("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");
```

```java
.andExpect(jsonPath("$.data[0].dispatchId").doesNotExist())
.andExpect(jsonPath("$.data[0].vehicleId").doesNotExist())
.andExpect(jsonPath("$.data[0].stops[0].stopId").doesNotExist())
```

```java
.andExpect(jsonPath("$.data.sourceWarehouseName").value("삼한 본창고"));
assertThat(sourceWarehouseName).doesNotMatch(UUID_REGEX);
```

### Mobile Jest/Detox

```ts
const treeText = textContent(utils.toJSON());
expect(treeText).not.toMatch(UUID_REGEX);
expect(treeText).not.toMatch(/downloadUrl|storageKey|objectKey|https?:\/\/|X-Amz-|presigned/i);
```

```ts
expect(api.signAndSendCopy).toHaveBeenCalledWith('jwt-x', 'NIGHT', 1, 1, expect.objectContaining({
  gpsLat: 37.1234567,
  gpsLng: 127.7654321,
  parsedKakaoSeq: 1234,
}));
expect((FileSystem.writeAsStringAsync as jest.Mock).mock.calls[0][0]).not.toMatch(UUID_REGEX);
```

### Desktop Playwright/static guard

```ts
const visible = await page.locator('body').innerText();
expect(visible).not.toMatch(UUID_REGEX);
expect(visible).not.toMatch(/downloadUrl|storageKey|objectKey|https?:\/\/|X-Amz-|presigned/i);
```

```ts
for (const file of guardedSourceFiles) {
  const source = read(file);
  expect(source).not.toMatch(/data-testid=.*(dispatchId|vehicleId|stopId|slipId|attachmentId)/);
}
```

## PASS 기준

- driver-facing today/slip-detail/photo/sign/gps API 응답 JSON body 에 내부 UUID, `downloadUrl`, `storageKey` 가 없다.
- `sourceWarehouseName` 은 UUID 문자열이 아니며, 화면에는 창고명 또는 명시적 확인 필요 문구만 표시된다.
- sign-and-send-copy 성공은 PNG + masked phone 만 공개하고, 실패 JSON 은 운영 원인 코드만 공개한다. 서명 내부키는 response header 에도 노출하지 않는다.
- 모바일 UI tree, 데스크톱 rendered text, QA 캡처 원본 텍스트에 금지 패턴이 0건이다.
- SpringBootTest IT 의 외부 RestClient 는 모두 `@MockBean` + lenient stub 으로 격리한다.
