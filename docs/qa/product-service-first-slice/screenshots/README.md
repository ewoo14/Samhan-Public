# Product Service First Slice — 스크린샷 디렉터리

본 디렉터리의 PNG 파일들은 **PM 통합 후** 다음 절차로 생성됩니다.

## 자동 생성 (IT 실행)

`gradle :services:product-service:test` 실행 결과를 IT report (HTML) 캡처:
- `01_it_repository_pass.png` — Repository IT 6개 통과 화면
- `02_it_controller_pass.png` — Controller IT 5개 통과 화면

## 수동 생성 (시나리오 시연)

`docker-compose up` 풀스택 부팅 후 `../fixtures/products.http` 의 10개 요청을 IntelliJ HTTP Client / VS Code REST Client / Edge headless 로 시연하며 응답 본문 캡처:

| 파일명 | 시나리오 |
|--------|---------|
| `01_category_tree.png` | 카테고리 트리 조회 (SALES) |
| `02_product_create_success.png` | 품목 등록 happy path (MANAGER) |
| `03_duplicate_model_name_409.png` | 모델명 중복 차단 (409) |
| `04_discontinue_then_active_filter.png` | 단종 후 ACTIVE 필터 |
| `05_softdelete_reuse_modelname.png` | soft-delete 후 동일 modelName 재등록 |
| `06_tags_contains_query.png` | 태그 contains 검색 (전압=220V) |
| `07_sales_post_forbidden.png` | SALES 의 POST → 403 |
| `08_negative_price_400.png` | 음수 가격 400 |
| `09_category_has_children_409.png` | 카테고리 자식 존재 시 삭제 차단 |
| `10_lookup_batch.png` | lookup batch (미존재 ID 무시) |

## 방침

- 캡처 시 인증 토큰 등 secret 은 마스킹.
- 1280x800 이상 해상도 권장 (qa_report.md 본문에서 inline 표시 가능).
- PM 이 본 README 를 캡처 완료 후 갱신 또는 삭제.
