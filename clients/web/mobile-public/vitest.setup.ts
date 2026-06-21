import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

afterEach(() => {
  cleanup()
})

// jsdom 은 HTMLCanvasElement.prototype.getContext 를 구현하지 않음.
// SignaturePad 는 canvas.getContext('2d') 가 null 이면 early-return 하므로
// toDataURL 은 빈 문자열('')을 반환해 handleSubmit 이 조기 종료된다.
// 테스트에서 만료 분기를 단언하려면 toDataURL 이 truthy 한 값을 반환해야 함.
const FAKE_DATA_URL = 'data:image/png;base64,AAAA'
HTMLCanvasElement.prototype.getContext = function () {
  return null
}
HTMLCanvasElement.prototype.toDataURL = function () {
  return FAKE_DATA_URL
}
