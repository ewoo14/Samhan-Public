import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import { Modal } from './Modal'
import { Button } from '../Button/Button'
import { Input } from '../Input/Input'

const meta: Meta<typeof Modal> = {
  title: 'Components/Modal',
  component: Modal,
}
export default meta

type Story = StoryObj<typeof Modal>

export const Basic: Story = {
  render: () => {
    const [open, setOpen] = useState(false)
    return (
      <>
        <Button onClick={() => setOpen(true)}>모달 열기</Button>
        <Modal
          open={open}
          onClose={() => setOpen(false)}
          title="삭제 확인"
          description="이 의뢰서를 정말 삭제하시겠습니까?"
          footer={
            <>
              <Button variant="secondary" onClick={() => setOpen(false)}>취소</Button>
              <Button variant="danger" onClick={() => setOpen(false)}>삭제</Button>
            </>
          }
        >
          <p>삭제된 항목은 복구할 수 없습니다.</p>
        </Modal>
      </>
    )
  },
}

export const WithForm: Story = {
  render: () => {
    const [open, setOpen] = useState(false)
    return (
      <>
        <Button onClick={() => setOpen(true)}>거래처 추가</Button>
        <Modal
          open={open}
          onClose={() => setOpen(false)}
          title="거래처 추가"
          size="md"
          footer={
            <>
              <Button variant="secondary" onClick={() => setOpen(false)}>취소</Button>
              <Button onClick={() => setOpen(false)}>저장</Button>
            </>
          }
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Input label="거래처명" required placeholder="(주)삼한물류" />
            <Input label="대표자" placeholder="홍길동" />
            <Input label="이메일" type="email" placeholder="contact@example.com" />
          </div>
        </Modal>
      </>
    )
  },
}

export const Large: Story = {
  render: () => {
    const [open, setOpen] = useState(false)
    return (
      <>
        <Button onClick={() => setOpen(true)}>큰 모달</Button>
        <Modal open={open} onClose={() => setOpen(false)} title="상세 보기" size="lg">
          <p>큰 사이즈 모달 콘텐츠 영역입니다.</p>
        </Modal>
      </>
    )
  },
}
