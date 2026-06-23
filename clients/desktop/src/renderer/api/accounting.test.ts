import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import { apiClient } from './client'
import { postJournal } from './accounting'

vi.mock('./client', () => ({
  apiClient: {
    post: vi.fn(),
  },
}))

describe('accounting journal API error contract', () => {
  beforeEach(() => {
    vi.mocked(apiClient.post).mockReset()
  })

  it('postJournal 은 BE ApiResponse.message 를 Error.message 로 전달한다', async () => {
    vi.mocked(apiClient.post).mockRejectedValueOnce(new AxiosError(
      'Request failed with status code 403',
      undefined,
      undefined,
      undefined,
      {
        data: {
          success: false,
          code: 'FORBIDDEN',
          message: '결재라인 결재자만 회계전표를 게시할 수 있습니다.',
        },
        status: 403,
        statusText: 'Forbidden',
        headers: {},
        config: {} as never,
      },
    ))

    await expect(postJournal('journal-1')).rejects.toThrow(
      '결재라인 결재자만 회계전표를 게시할 수 있습니다.',
    )
  })
})
