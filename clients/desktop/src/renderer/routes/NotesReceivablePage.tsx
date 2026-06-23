import { useMemo, useState } from 'react'
import type React from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AsyncAutocomplete,
  Button,
  Card,
  DataTable,
  Input,
  Select,
  Spinner,
  type DataTableColumn,
} from '@samhan/design-system'
import {
  NOTE_STATUS_LABEL,
  NOTE_TYPE_LABEL,
  listNotesReceivable,
  registerNotesReceivable,
  searchJournalStatusPartners,
  updateNotesReceivableStatus,
  type CreateNotesReceivablePayload,
  type JournalStatusPartnerOption,
  type NoteStatus,
  type NoteType,
  type NotesReceivableRow,
} from '../api/accounting'
import { usePageTitle } from '../hooks/usePageTitle'

const NOTE_STATUS_OPTIONS: NoteStatus[] = ['BOARDING', 'COLLECTING', 'SETTLED', 'DISHONORED']
const NOTE_TYPE_OPTIONS: NoteType[] = ['PROMISSORY', 'BILL_OF_EXCHANGE']
const TRANSITION_OPTIONS: NoteStatus[] = ['COLLECTING', 'SETTLED', 'DISHONORED']

function todayIso(): string {
  return new Date().toISOString().slice(0, 10)
}

function nextMonthIso(): string {
  const d = new Date()
  d.setMonth(d.getMonth() + 1)
  return d.toISOString().slice(0, 10)
}

function formatKrw(raw: string | number | null | undefined): string {
  if (raw === null || raw === undefined || raw === '') return '—'
  const n = Number(raw)
  if (!Number.isFinite(n)) return String(raw)
  if (n === 0) return '—'
  const abs = Math.abs(Math.round(n)).toLocaleString('ko-KR')
  return n < 0 ? `-${abs}` : abs
}

function amountStyle(raw: string | number): React.CSSProperties {
  return {
    color: Number(raw) < 0 ? 'var(--state-danger)' : undefined,
    fontVariantNumeric: 'tabular-nums',
    fontWeight: 600,
  }
}

function statusStyle(status: NoteStatus): React.CSSProperties {
  const colors: Record<NoteStatus, { bg: string; fg: string }> = {
    BOARDING: { bg: 'var(--color-primary-50)', fg: 'var(--color-primary-700)' },
    COLLECTING: { bg: 'var(--state-warning-bg)', fg: 'var(--state-warning)' },
    SETTLED: { bg: 'var(--state-success-bg)', fg: 'var(--state-success)' },
    DISHONORED: { bg: 'var(--state-danger-bg)', fg: 'var(--state-danger)' },
  }
  const color = colors[status]
  return {
    display: 'inline-flex',
    alignItems: 'center',
    height: 24,
    padding: '0 8px',
    borderRadius: 6,
    background: color.bg,
    color: color.fg,
    fontSize: 12,
    fontWeight: 700,
  }
}

export function NotesReceivablePage() {
  usePageTitle('받을어음', '등록/목록')

  const queryClient = useQueryClient()
  const [formPartner, setFormPartner] = useState<JournalStatusPartnerOption | null>(null)
  const [filterPartner, setFilterPartner] = useState<JournalStatusPartnerOption | null>(null)
  const [statusFilter, setStatusFilter] = useState<NoteStatus | ''>('')
  const [queryFilters, setQueryFilters] = useState<{
    status?: NoteStatus
    partnerCode?: string
  }>({})
  const [form, setForm] = useState({
    noteNo: '',
    issueDate: todayIso(),
    maturityDate: nextMonthIso(),
    amount: '',
    noteType: 'PROMISSORY' as NoteType,
    status: 'BOARDING' as NoteStatus,
    memo: '',
  })

  const notesQuery = useQuery({
    queryKey: ['accounting', 'notes-receivable', queryFilters.status ?? '', queryFilters.partnerCode ?? ''],
    queryFn: () => listNotesReceivable(queryFilters),
  })

  const registerMutation = useMutation({
    mutationFn: (payload: CreateNotesReceivablePayload) => registerNotesReceivable(payload),
    onSuccess: async () => {
      setForm({
        noteNo: '',
        issueDate: todayIso(),
        maturityDate: nextMonthIso(),
        amount: '',
        noteType: 'PROMISSORY',
        status: 'BOARDING',
        memo: '',
      })
      setFormPartner(null)
      await queryClient.invalidateQueries({ queryKey: ['accounting', 'notes-receivable'] })
    },
  })

  const statusMutation = useMutation({
    mutationFn: ({ noteNo, status }: { noteNo: string; status: NoteStatus }) =>
      updateNotesReceivableStatus(noteNo, status),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['accounting', 'notes-receivable'] })
    },
  })

  const columns = useMemo<DataTableColumn<NotesReceivableRow>[]>(() => [
    { key: 'noteNo', header: '어음번호', width: '120px', render: (row) => <strong>{row.noteNo}</strong> },
    {
      key: 'bizNo',
      header: '거래처코드',
      width: '128px',
      render: (row) => row.bizNo || '—',
    },
    {
      key: 'partnerName',
      header: '거래처명',
      width: '180px',
      render: (row) => (
        <span>
          {row.partnerName}
          <span style={{ color: 'var(--color-neutral-400)' }}> · {row.partnerCode}</span>
        </span>
      ),
    },
    { key: 'issueDate', header: '발행일', width: '110px' },
    { key: 'maturityDate', header: '만기일', width: '110px' },
    {
      key: 'amount',
      header: '금액',
      width: '130px',
      align: 'right',
      render: (row) => <span style={amountStyle(row.amount)}>{formatKrw(row.amount)}</span>,
    },
    {
      key: 'noteType',
      header: '종류',
      width: '100px',
      render: (row) => NOTE_TYPE_LABEL[row.noteType],
    },
    {
      key: 'status',
      header: '상태',
      width: '100px',
      render: (row) => <span style={statusStyle(row.status)}>{NOTE_STATUS_LABEL[row.status]}</span>,
    },
    {
      key: 'memo',
      header: '비고',
      render: (row) => row.memo || '—',
    },
    {
      key: 'actions',
      header: '상태전이',
      width: '230px',
      render: (row) => (
        <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
          {TRANSITION_OPTIONS.map((status) => (
            <Button
              key={status}
              size="sm"
              variant={row.status === status ? 'primary' : 'ghost'}
              disabled={row.status === status || statusMutation.isPending}
              onClick={() => statusMutation.mutate({ noteNo: row.noteNo, status })}
            >
              {NOTE_STATUS_LABEL[status]}
            </Button>
          ))}
        </div>
      ),
    },
  ], [statusMutation])

  const handleSearch = () => {
    setQueryFilters({
      status: statusFilter || undefined,
      partnerCode: filterPartner?.partnerCode || undefined,
    })
  }

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    if (!formPartner || !form.noteNo.trim() || !form.amount) return
    registerMutation.mutate({
      partnerCode: formPartner.partnerCode,
      noteNo: form.noteNo.trim(),
      issueDate: form.issueDate,
      maturityDate: form.maturityDate,
      amount: form.amount,
      noteType: form.noteType,
      status: form.status,
      memo: form.memo.trim() || undefined,
    })
  }

  const totalAmount = (notesQuery.data ?? []).reduce((sum, row) => sum + Number(row.amount || 0), 0)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700 }}>받을어음</h3>
          <div style={{ marginTop: 4, fontSize: 13, color: 'var(--color-neutral-500)' }}>
            합계 {formatKrw(totalAmount)}
          </div>
        </div>
        {notesQuery.isFetching ? <Spinner size="sm" /> : null}
      </div>

      <Card style={{ padding: 16 }}>
        <form onSubmit={handleSubmit} style={{ display: 'grid', gridTemplateColumns: 'minmax(220px, 1.4fr) repeat(6, minmax(120px, 1fr)) auto', gap: 10, alignItems: 'end' }}>
          <AsyncAutocomplete<JournalStatusPartnerOption>
            value={formPartner}
            onChange={setFormPartner}
            search={searchJournalStatusPartners}
            getKey={(partner) => partner.partnerCode}
            getInputLabel={(partner) => partner.name}
            listboxLabel="거래처 목록"
            renderOption={(partner) => (
              <>
                <span>{partner.name}</span>
                <span style={{ color: 'var(--color-neutral-400)' }}> · </span>
                <span style={{ color: 'var(--color-neutral-500)' }}>{partner.bizNo ?? partner.partnerCode}</span>
              </>
            )}
            label="거래처"
            placeholder="거래처명 또는 코드"
            inputTestId="notes-receivable-partner"
            minChars={1}
          />
          <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
            어음번호
            <Input value={form.noteNo} onChange={(event) => setForm((prev) => ({ ...prev, noteNo: event.target.value }))} />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
            발행일
            <Input type="date" value={form.issueDate} onChange={(event) => setForm((prev) => ({ ...prev, issueDate: event.target.value }))} />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
            만기일
            <Input type="date" value={form.maturityDate} onChange={(event) => setForm((prev) => ({ ...prev, maturityDate: event.target.value }))} />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
            금액
            <Input type="number" min="0" step="1" value={form.amount} onChange={(event) => setForm((prev) => ({ ...prev, amount: event.target.value }))} />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
            종류
            <Select value={form.noteType} onChange={(event) => setForm((prev) => ({ ...prev, noteType: event.target.value as NoteType }))}>
              {NOTE_TYPE_OPTIONS.map((type) => (
                <option key={type} value={type}>{NOTE_TYPE_LABEL[type]}</option>
              ))}
            </Select>
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
            상태
            <Select value={form.status} onChange={(event) => setForm((prev) => ({ ...prev, status: event.target.value as NoteStatus }))}>
              {NOTE_STATUS_OPTIONS.map((status) => (
                <option key={status} value={status}>{NOTE_STATUS_LABEL[status]}</option>
              ))}
            </Select>
          </label>
          <Button
            type="submit"
            variant="primary"
            disabled={!formPartner || !form.noteNo.trim() || !form.amount || registerMutation.isPending}
          >
            등록
          </Button>
          <label style={{ gridColumn: '1 / -1', display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
            비고
            <Input value={form.memo} onChange={(event) => setForm((prev) => ({ ...prev, memo: event.target.value }))} />
          </label>
        </form>
      </Card>

      <Card style={{ padding: 16 }}>
        <div style={{ display: 'flex', gap: 10, alignItems: 'end', flexWrap: 'wrap', marginBottom: 14 }}>
          <div style={{ minWidth: 220 }}>
            <AsyncAutocomplete<JournalStatusPartnerOption>
              value={filterPartner}
              onChange={setFilterPartner}
              search={searchJournalStatusPartners}
              getKey={(partner) => partner.partnerCode}
              getInputLabel={(partner) => partner.name}
              listboxLabel="거래처 목록"
              renderOption={(partner) => (
                <>
                  <span>{partner.name}</span>
                  <span style={{ color: 'var(--color-neutral-400)' }}> · </span>
                  <span style={{ color: 'var(--color-neutral-500)' }}>{partner.bizNo ?? partner.partnerCode}</span>
                </>
              )}
              label="거래처"
              placeholder="전체"
              inputTestId="notes-receivable-partner-filter"
              minChars={1}
            />
          </div>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
            상태
            <Select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as NoteStatus | '')}>
              <option value="">전체</option>
              {NOTE_STATUS_OPTIONS.map((status) => (
                <option key={status} value={status}>{NOTE_STATUS_LABEL[status]}</option>
              ))}
            </Select>
          </label>
          <Button size="sm" variant="primary" onClick={handleSearch} disabled={notesQuery.isFetching}>
            조회
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => {
              setStatusFilter('')
              setFilterPartner(null)
              setQueryFilters({})
            }}
          >
            초기화
          </Button>
        </div>

        <DataTable<NotesReceivableRow>
          columns={columns}
          rows={notesQuery.data ?? []}
          rowKey={(row) => row.noteNo}
          emptyMessage={notesQuery.isLoading ? '조회 중' : '등록된 받을어음이 없습니다'}
        />
      </Card>
    </div>
  )
}
