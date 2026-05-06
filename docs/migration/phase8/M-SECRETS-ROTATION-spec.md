# M-SECRETS-ROTATION-spec — AWS Secrets Manager rotation lambda 사양

본 문서는 Phase 10 AWS cutover 시점에 활성화될 Secrets Manager rotation lambda 의
사양을 정리한다. 본 슬라이스 (Phase 8 2차) 는 spec 문서 + 환경변수 표준 통일만
포함하며, 실 lambda 코드는 Phase 10 에서 발행한다.

---

## 1. 대상 secrets

| Secret 이름 (Secrets Manager) | 환경변수 매핑 | rotation 주기 | rotation 주체 |
|---|---|---|---|
| `samhan/<env>/db/password` | `SAMHAN_DB_PASSWORD` (Phase 10) / 현재 = `DB_PASSWORD` | 30 일 | lambda 자동 |
| `samhan/<env>/internal-token` | `SAMHAN_INTERNAL_TOKEN` (Phase 8 2차 표준) / 현재 = `INTERNAL_AUTH_TOKEN` legacy | 90 일 | lambda 자동 |
| `samhan/<env>/jwt-secret` | `SAMHAN_JWT_SECRET` (Phase 8 2차 표준) / 현재 = `JWT_SECRET` legacy | 90 일 | lambda 자동 |
| `samhan/<env>/google/service-account-key` | `SAMHAN_GOOGLE_SERVICE_ACCOUNT_KEY` (binary) / 현재 = `GOOGLE_SERVICE_ACCOUNT_KEY` (file path) | 사용자 직접 (GCP 콘솔) | manual |
| `samhan/<env>/aligo/api-key` | `ALIGO_API_KEY` | 분기별 (90 일) | manual (Aligo 콘솔) |
| `samhan/<env>/slack/webhook-url` | `SAMHAN_SLACK_WEBHOOK_URL` | 분기별 (90 일) | manual |
| `samhan/<env>/rabbitmq/password` | `RABBIT_PASSWORD` | 90 일 | lambda 자동 (AWS MQ engine) |

> **legacy 폐기 대상** — `SAMHAN_NOTION_TOKEN_*` (Phase 6 product-service 도입 시점에 폐기 결정, 현재 미사용).

---

## 2. lambda 구조

### 2-1. 함수 스펙

```text
함수 이름        : samhan-secrets-rotation
런타임           : Python 3.12 (또는 nodejs20.x)
트리거           : Secrets Manager rotation event (CloudWatch Events 자동)
타임아웃         : 30 초
메모리           : 256 MB
환경변수         : SECRET_ROTATION_ENV (값: prod / staging / dev)
```

### 2-2. IAM 정책 (역할 부여)

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "RotateSecret",
            "Effect": "Allow",
            "Action": [
                "secretsmanager:DescribeSecret",
                "secretsmanager:GetSecretValue",
                "secretsmanager:PutSecretValue",
                "secretsmanager:UpdateSecretVersionStage"
            ],
            "Resource": "arn:aws:secretsmanager:<region>:<account>:secret:samhan/*"
        },
        {
            "Sid": "GenerateRandomPassword",
            "Effect": "Allow",
            "Action": "secretsmanager:GetRandomPassword",
            "Resource": "*"
        },
        {
            "Sid": "ModifyRDS",
            "Effect": "Allow",
            "Action": [
                "rds:DescribeDBInstances",
                "rds:ModifyDBInstance"
            ],
            "Resource": "arn:aws:rds:<region>:<account>:db:samhan-*"
        },
        {
            "Sid": "DescribeRabbit",
            "Effect": "Allow",
            "Action": [
                "mq:DescribeBroker",
                "mq:UpdateUser"
            ],
            "Resource": "arn:aws:mq:<region>:<account>:broker:samhan-*:*"
        }
    ]
}
```

### 2-3. CloudWatch Events 트리거

```text
Source           : aws.secretsmanager
DetailType       : Secrets Manager Rotation Started
Resources        : arn:aws:secretsmanager:<region>:<account>:secret:samhan/*
Schedule (each)  : Secrets Manager rotation 설정 시 자동 (`AutomaticallyAfterDays`)
```

---

## 3. 4 단계 rotation step (AWS 표준)

본 lambda 는 AWS Secrets Manager rotation 표준 4 단계를 구현한다:

### 3-1. createSecret

신규 secret 후보 값 생성. AWS 의 `GetRandomPassword` 호출 (32 byte 영숫자 + 특수문자).
RDS 의 경우 `MasterUserPassword` 정책 (8~128 문자, `/`, `@`, `"`, ` ` 제외) 준수.

```python
def create_secret(service_client, arn, token):
    try:
        service_client.get_secret_value(SecretId=arn, VersionId=token, VersionStage="AWSPENDING")
    except service_client.exceptions.ResourceNotFoundException:
        passwd = service_client.get_random_password(
            PasswordLength=32,
            ExcludeCharacters='/@" ',
            RequireEachIncludedType=True
        )
        service_client.put_secret_value(
            SecretId=arn,
            ClientRequestToken=token,
            SecretString=passwd['RandomPassword'],
            VersionStages=['AWSPENDING']
        )
```

### 3-2. setSecret

신규 secret 을 backend 에 적용 (예: RDS `ModifyDBInstance` `MasterUserPassword`,
AWS MQ `UpdateUser` `Password`). lambda 자체 secret (`internal-token`,
`jwt-secret`) 의 경우 backend 적용 단계 X — Secrets Manager 가 source-of-truth,
service 가 부팅 시 fetch.

```python
def set_secret(service_client, arn, token):
    pending = service_client.get_secret_value(SecretId=arn, VersionId=token, VersionStage="AWSPENDING")
    secret_name = service_client.describe_secret(SecretId=arn)['Name']

    if secret_name.endswith('/db/password'):
        rds = boto3.client('rds')
        rds.modify_db_instance(
            DBInstanceIdentifier=resolve_rds_id(secret_name),
            MasterUserPassword=pending['SecretString'],
            ApplyImmediately=True
        )
    elif secret_name.endswith('/rabbitmq/password'):
        mq = boto3.client('mq')
        mq.update_user(
            BrokerId=resolve_mq_id(secret_name),
            Username='samhan',
            Password=pending['SecretString']
        )
    # internal-token / jwt-secret 의 경우 backend 적용 X (서비스가 부팅 시 fetch)
```

### 3-3. testSecret

신규 secret 으로 backend 연결 테스트. RDS 의 경우 `psycopg2.connect` 시도,
MQ 의 경우 STOMP / AMQP 연결 시도.

```python
def test_secret(service_client, arn, token):
    pending = service_client.get_secret_value(SecretId=arn, VersionId=token, VersionStage="AWSPENDING")
    secret_name = service_client.describe_secret(SecretId=arn)['Name']

    if secret_name.endswith('/db/password'):
        import psycopg2
        conn = psycopg2.connect(
            host=resolve_rds_host(secret_name),
            user='samhan',
            password=pending['SecretString'],
            dbname='postgres',
            connect_timeout=5
        )
        conn.close()
    # 기타 backend 동일 패턴
```

### 3-4. finishSecret

`AWSPENDING` → `AWSCURRENT` 전환. AWS Secrets Manager 가 자동 처리.

```python
def finish_secret(service_client, arn, token):
    metadata = service_client.describe_secret(SecretId=arn)
    current_version = next(
        v for v, s in metadata['VersionIdsToStages'].items() if 'AWSCURRENT' in s
    )
    service_client.update_secret_version_stage(
        SecretId=arn,
        VersionStage='AWSCURRENT',
        MoveToVersionId=token,
        RemoveFromVersionId=current_version
    )
```

---

## 4. service 측 fetch 패턴 (Phase 10 cutover 시점 적용)

### 4-1. spring-cloud-aws-starter-secrets-manager (권장)

```yaml
# Phase 10 application.yml
spring:
  config:
    import:
      - aws-secretsmanager:samhan/${SAMHAN_ENV}/db/password
      - aws-secretsmanager:samhan/${SAMHAN_ENV}/internal-token
      - aws-secretsmanager:samhan/${SAMHAN_ENV}/jwt-secret
```

→ 환경변수 `SAMHAN_DB_PASSWORD`, `SAMHAN_INTERNAL_TOKEN`, `SAMHAN_JWT_SECRET`
이 자동으로 Secrets Manager 값으로 resolve.

### 4-2. 로컬 fallback (개발자 머신)

`.env` 파일 또는 `infrastructure/env-templates/<service>.env` 의 `SAMHAN_*`
환경변수 값을 그대로 사용 (Secrets Manager fetch 비활성).

---

## 5. monitoring + alert

| Metric | CloudWatch Alarm | Slack 알림 |
|---|---|---|
| `RotationSucceeded` | rate > 0 | OK |
| `RotationFailed` | rate > 0 (5분 윈도우) | 즉시 #ops-alert |
| Lambda `Errors` | > 0 (5분) | 즉시 #ops-alert |
| Lambda `Throttles` | > 0 (5분) | 24h 윈도우 |
| Lambda `Duration` | > 25s | 24h 윈도우 |

→ Phase 10 진입 시 `Phase 10 5차 모니터링 alert` 슬라이스에서 Grafana 대시보드 +
Slack webhook 연동 진행.

---

## 6. Phase 10 cutover 활성 절차

### 6-1. 사전 작업 (Phase 8 ~ 9 동안)

- 본 spec 문서 보존 (코드 X, docs only)
- 환경변수 표준 적용 (`SAMHAN_INTERNAL_TOKEN`, `SAMHAN_JWT_SECRET`) — 본 슬라이스 (Phase 8 2차) 진행
- service 측 yml 의 chained-default 패턴 적용 — 본 슬라이스 진행

### 6-2. Phase 10 cutover 단계

1. AWS account 발급 + IAM baseline 정의
2. Secrets Manager 에 6 secret 등록 (`samhan/prod/db/password` ...)
3. lambda 코드 작성 + 배포 (본 spec 따라)
4. lambda → Secrets Manager rotation schedule 등록
5. service 측 yml 에 `spring.config.import` 추가 (Phase 10 별도 PR)
6. ECS Fargate task 재배포 → service 가 Secrets Manager 에서 fetch

### 6-3. legacy fallback 폐기

Phase 10 cutover 완료 후 1 분기 (Phase 11 진입 시점) 까지 legacy env var
(`INTERNAL_AUTH_TOKEN`, `JWT_SECRET`, `INTERNAL_TOKEN`) fallback 보존,
이후 yml 정정 + env template 정정으로 폐기.

---

## 7. 참조

- AWS 호환성 가드: `docs/migration/phase8/M-AWS-COMPATIBILITY-guards.md`
- 환경변수 표준화: `docs/migration/phase8/M-ENV-STANDARDIZATION.md`
- Phase 8 2차 dev report: `docs/dev-reports/phase8-step-2-discovery-secrets.md`
- 누적 결정: `migration/decisions/DECISIONS.md` (D-P8-07 ~ D-P8-09)
- AWS Secrets Manager rotation 공식 문서: https://docs.aws.amazon.com/secretsmanager/latest/userguide/rotating-secrets.html
