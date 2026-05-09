import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import {
  CsvUploadDialog,
  type RejectedRow,
  type UploadResult,
} from './CsvUploadDialog'

/**
 * 기본 mock 결과 — 신규 142 / 갱신 8 / 거부 0.
 */
const SUCCESS_RESULT: UploadResult = {
  inserted: 142,
  updated: 8,
  skipped: 0,
  rejected: [],
}

/**
 * 10건 거부 mock — 다양한 거부 사유 (필수값 누락 / 중복 / 형식 오류 / 외부키 없음).
 */
const REJECTED_ROWS: RejectedRow[] = [
  {
    rowNumber: 3,
    inputData: { 거래처명: '삼한공조 강남점', 지역코드: 'GN-01', 전화번호: '02-1234-5678' },
    reason: '지역코드 GN-01 가 존재하지 않습니다',
  },
  {
    rowNumber: 7,
    inputData: { 거래처명: '서울에어컨', 지역코드: '', 전화번호: '02-9876-5432' },
    reason: '지역코드 필수 항목이 비어 있습니다',
  },
  {
    rowNumber: 12,
    inputData: { 거래처명: '강서냉동', 지역코드: 'GS-02', 전화번호: '010-1234' },
    reason: '전화번호 형식이 올바르지 않습니다 (010-XXXX-XXXX)',
  },
  {
    rowNumber: 18,
    inputData: { 거래처명: '동대문에어컨', 지역코드: 'DD-03', 전화번호: '02-2222-3333' },
    reason: '동일 거래처명이 이미 등록되어 있습니다 (id=8821)',
  },
  {
    rowNumber: 24,
    inputData: { 거래처명: '', 지역코드: 'YS-04', 전화번호: '02-3333-4444' },
    reason: '거래처명 필수 항목이 비어 있습니다',
  },
  {
    rowNumber: 31,
    inputData: { 거래처명: '마포에어컨', 지역코드: 'MP-05', 전화번호: 'abc-defg' },
    reason: '전화번호 형식이 올바르지 않습니다 (010-XXXX-XXXX)',
  },
  {
    rowNumber: 38,
    inputData: { 거래처명: '송파공조', 지역코드: 'SP-06', 전화번호: '02-5555-6666' },
    reason: '필수 컬럼 거래처유형 이 누락되었습니다',
  },
  {
    rowNumber: 45,
    inputData: { 거래처명: '광진에어컨', 지역코드: 'GJ-07', 전화번호: '02-6666-7777' },
    reason: '거래처유형 값이 enum 에 없습니다 (입력값: 일반)',
  },
  {
    rowNumber: 52,
    inputData: { 거래처명: '성동냉동', 지역코드: 'SD-08', 전화번호: '02-7777-8888' },
    reason: '담당지점 값 이 매핑된 지점 목록에 없습니다',
  },
  {
    rowNumber: 60,
    inputData: { 거래처명: '용산에어컨', 지역코드: 'YS-09', 전화번호: '02-8888-9999' },
    reason: '대표자명이 너무 깁니다 (50자 초과)',
  },
]

const REJECTED_RESULT: UploadResult = {
  inserted: 50,
  updated: 5,
  skipped: 2,
  rejected: REJECTED_ROWS,
}

const meta: Meta<typeof CsvUploadDialog> = {
  title: 'PR-D Phase B/CsvUploadDialog',
  component: CsvUploadDialog,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component:
          'PR-D Phase B 4 admin 페이지 (Regions / DcConfig / ChatRooms / BlockedPartners) 공통 사용. 노션에서 다운로드한 CSV 를 native import 하는 핵심 UX 컴포넌트. 3 단계 (선택 → 업로드 → 결과) + reject 보고서 다운로드.',
      },
    },
  },
}
export default meta

type Story = StoryObj<typeof CsvUploadDialog>

/**
 * default — 정상 업로드 (모든 행 신규/갱신, 거부 0).
 */
export const Default: Story = {
  render: function DefaultRender() {
    const [open, setOpen] = useState(true)
    return (
      <div style={{ padding: 32 }}>
        <button type="button" onClick={() => setOpen(true)}>
          CSV 업로드 다이얼로그 열기
        </button>
        <CsvUploadDialog
          open={open}
          onClose={() => setOpen(false)}
          title="단톡방 매핑 일괄 등록"
          description="노션에서 다운로드한 CSV 를 업로드하여 단톡방 매핑을 일괄 등록합니다."
          onUpload={async () => {
            // 1.5 초 시뮬레이션
            await new Promise((r) => setTimeout(r, 1500))
            return SUCCESS_RESULT
          }}
          sampleDownloadUrl="/samples/chat-rooms-sample.csv"
        />
      </div>
    )
  },
}

/**
 * rejected — 10건 거부 발생, 거부 보고서 표 + CSV 다운로드 버튼 노출.
 */
export const Rejected: Story = {
  render: function RejectedRender() {
    const [open, setOpen] = useState(true)
    return (
      <div style={{ padding: 32 }}>
        <button type="button" onClick={() => setOpen(true)}>
          CSV 업로드 다이얼로그 열기
        </button>
        <CsvUploadDialog
          open={open}
          onClose={() => setOpen(false)}
          title="거래처 일괄 등록"
          description="CSV 의 행별 검증 결과를 확인하세요. 거부된 행은 보고서를 다운로드해 수정 후 재업로드 가능합니다."
          onUpload={async () => {
            await new Promise((r) => setTimeout(r, 1500))
            return REJECTED_RESULT
          }}
        />
      </div>
    )
  },
}

/**
 * error — 5MB 초과 / 확장자 오류 등 검증 실패. 사용자가 직접 잘못된 파일을
 * 선택해야 재현되므로 본 story 는 안내 텍스트로 시나리오를 설명한다.
 */
export const ErrorStory: Story = {
  name: 'Error',
  render: function ErrorRender() {
    const [open, setOpen] = useState(true)
    return (
      <div style={{ padding: 32 }}>
        <div
          style={{
            padding: 16,
            background: '#FEF3C7',
            border: '1px solid #F59E0B',
            borderRadius: 8,
            marginBottom: 16,
            maxWidth: 720,
            fontSize: 13,
            lineHeight: 1.6,
            color: '#92400E',
          }}
        >
          <strong>QA 시나리오</strong>
          <ul style={{ margin: '8px 0 0', paddingLeft: 20 }}>
            <li>5MB 초과 파일 선택 → 빨강 에러 배너 + 업로드 버튼 비활성</li>
            <li>.txt 확장자 파일 선택 → 빨강 에러 배너</li>
            <li>업로드 후 server error → 다이얼로그가 select 단계로 복귀, 에러 배너 노출</li>
          </ul>
        </div>
        <button type="button" onClick={() => setOpen(true)}>
          CSV 업로드 다이얼로그 열기
        </button>
        <CsvUploadDialog
          open={open}
          onClose={() => setOpen(false)}
          title="DC 설정 일괄 등록"
          description="2MB 가드 — 더 큰 파일은 거부됩니다."
          maxFileSizeMB={2}
          acceptExtensions={['.csv']}
          onUpload={async () => {
            await new Promise((r) => setTimeout(r, 800))
            throw new Error('서버 처리 중 오류가 발생했습니다 (HTTP 500)')
          }}
        />
      </div>
    )
  },
}
