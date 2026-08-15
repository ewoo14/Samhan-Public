import { Input, type InputProps } from '@samhan/design-system'
import {
  adjustEditableAmountByArrow,
  formatEditableAmountInput,
  parseEditableAmountForServer,
} from '../../utils/editableAmountInput'

export interface EditableAmountInputProps extends Omit<InputProps, 'value' | 'onChange' | 'type'> {
  value: string
  onValueChange: (value: string) => void
  enableAmountKeyboardStep?: boolean
  amountStep?: number
}

/** 화면에는 천 단위 콤마를 표시하고 상태/서버에는 raw 숫자 문자열을 유지하는 입력. */
export function EditableAmountInput({
  value,
  onValueChange,
  inputMode = 'decimal',
  enableAmountKeyboardStep = true,
  amountStep = 1,
  ...props
}: EditableAmountInputProps) {
  return (
    <Input
      {...props}
      type="text"
      inputMode={inputMode}
      value={formatEditableAmountInput(value, null).displayValue}
      onKeyDown={(event) => {
        if (!enableAmountKeyboardStep || (event.key !== 'ArrowUp' && event.key !== 'ArrowDown')) return
        event.preventDefault()
        const next = adjustEditableAmountByArrow(value, event.key === 'ArrowUp' ? 'up' : 'down', amountStep)
        onValueChange(parseEditableAmountForServer(next))
      }}
      onChange={(event) => {
        const formatted = formatEditableAmountInput(event.target.value, event.target.selectionStart)
        onValueChange(parseEditableAmountForServer(formatted.displayValue))
        const input = event.currentTarget
        const restoreSelection = () => input.setSelectionRange(formatted.selectionStart, formatted.selectionStart)
        if (typeof requestAnimationFrame === 'function') requestAnimationFrame(restoreSelection)
        else setTimeout(restoreSelection, 0)
      }}
    />
  )
}
