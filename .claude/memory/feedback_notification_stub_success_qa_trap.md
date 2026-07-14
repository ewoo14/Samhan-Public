---
name: feedback_notification_stub_success_qa_trap
description: arologis 알림 QA 시 dev 'SUCCESS'는 AligoSmsAdapter stub이지 실 전달 아님 — 정직 표기 필수
metadata:
  type: feedback
---

arologis→notification-service 알림 발송 라이브 QA 에서 **"발송 성공"(SUCCESS) 이 실 문자 전달 증거가 아니다.**

**Why:** 로컬/dev 스택은 `SAMHAN_AROLOGIS_CLIENT_SKELETON_MODE=false`(compose 기설정)라 arologis 는 실제로 notification-service 를 호출하지만, notification-service 의 `AligoSmsAdapter.isPlaceholder()` 가 Aligo creds(`SAMHAN_ALIGO_KEY/USERID/SENDER`) 가 blank 이면(dev 기본·SP-09 시크릿 정책상 repo 미포함) **외부 호출 없이 stub SUCCESS(`aligo-stub-*`) 를 반환**한다. 따라서 `NotificationRequest.status=SENT` → arologis 기록 `ArologisNotifyStatus.SUCCESS`. arologis 레이어의 조작-SUCCESS 는 #816 에서 제거됐으나(skeleton 시 attempted=false 미기록), **한 단계 안쪽 vendor adapter 의 stub-success 는 여전히 살아있다.**

**How to apply:** 알림 발송이력(`dispatch_notifications`·notifyResults) 라이브 QA 캡처의 "발송 성공"은 **end-to-end 기록·렌더 경로 실증**으로만 보고하고, **실 문자 전달 증거로 오보하지 말 것**([[feedback_no_fake_data_ever]]). 레코드 자체는 실 autoMatch 경로 실데이터라 조작 아님 — 이 구분을 dev-report/PR 에 명시. 실 전달 검증은 실 Aligo creds 주입 시 별도. 또한 SMS(Aligo) 채널은 notification-service W3 가 1회 시도(`NotificationGatewayResult.failure` retryable=false)라 **DELAYED(RETRYING/PENDING) 도달 불가**(SENT/FAILED 만) — DELAYED 매핑/문구는 W10-2 실 재시도 배선까지 예약. (2026-07-15 #816 ③-B)
