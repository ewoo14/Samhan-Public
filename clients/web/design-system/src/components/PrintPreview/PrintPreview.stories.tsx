import type { Meta, StoryObj } from '@storybook/react'
import { PrintPreview } from './PrintPreview'

/**
 * `<PrintPreview>` Storybook 등재 — 인쇄 미리보기 wrapper.
 *
 * 출처: migration/analysis/06-frontend-design.md §3.2 / DECISIONS.md F3
 */
const meta: Meta<typeof PrintPreview> = {
  title: 'Components/PrintPreview',
  component: PrintPreview,
  parameters: {
    docs: {
      description: {
        component:
          '인쇄 미리보기 wrapper. F3 react-pdf 채택 + fallback 브라우저 print. design-system 패키지는 react-pdf 직접 import 안함 — 호출자가 pdfRenderer prop 으로 주입.',
      },
    },
  },
}
export default meta

type Story = StoryObj<typeof PrintPreview>

const sampleEstimate = (
  <div style={{ padding: 16, fontFamily: 'sans-serif', color: '#000' }}>
    <h2 style={{ margin: 0, marginBottom: 8 }}>견적서 (샘플)</h2>
    <p style={{ margin: '4px 0', fontSize: 12 }}>견적번호: EST-2026-001</p>
    <p style={{ margin: '4px 0', fontSize: 12 }}>거래처: 한솔 종합건설(주)</p>
    <table
      style={{
        width: '100%',
        marginTop: 16,
        borderCollapse: 'collapse',
        fontSize: 11,
      }}
    >
      <thead>
        <tr style={{ background: '#f0f0f0' }}>
          <th style={{ border: '1px solid #000', padding: 4 }}>모델</th>
          <th style={{ border: '1px solid #000', padding: 4 }}>수량</th>
          <th style={{ border: '1px solid #000', padding: 4 }}>단가</th>
          <th style={{ border: '1px solid #000', padding: 4 }}>금액</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td style={{ border: '1px solid #000', padding: 4 }}>AC180RXADKG</td>
          <td style={{ border: '1px solid #000', padding: 4, textAlign: 'right' }}>2</td>
          <td style={{ border: '1px solid #000', padding: 4, textAlign: 'right' }}>2,700,000</td>
          <td style={{ border: '1px solid #000', padding: 4, textAlign: 'right' }}>5,400,000</td>
        </tr>
      </tbody>
    </table>
  </div>
)

/** A4 세로 + 브라우저 print fallback. */
export const A4PortraitBrowser: Story = {
  name: 'A4 세로 / 브라우저 print',
  render: () => (
    <PrintPreview mode="browser" paperSize="A4" orientation="portrait">
      {sampleEstimate}
    </PrintPreview>
  ),
}

/** A4 가로. */
export const A4LandscapeBrowser: Story = {
  name: 'A4 가로 / 브라우저 print',
  render: () => (
    <PrintPreview mode="browser" paperSize="A4" orientation="landscape">
      {sampleEstimate}
    </PrintPreview>
  ),
}

/** A5 세로. */
export const A5PortraitBrowser: Story = {
  name: 'A5 세로 / 브라우저 print',
  render: () => (
    <PrintPreview mode="browser" paperSize="A5" orientation="portrait">
      {sampleEstimate}
    </PrintPreview>
  ),
}

/** mode='pdf' 인데 pdfRenderer 미주입 → 자동 브라우저 fallback (toolbar 메타에 'fallback' 표시). */
export const PdfModeFallback: Story = {
  name: 'mode=pdf / pdfRenderer 미주입 → fallback',
  render: () => (
    <PrintPreview mode="pdf" paperSize="A4" orientation="portrait">
      {sampleEstimate}
    </PrintPreview>
  ),
}

/** mode='pdf' + 가짜 renderer (실제 react-pdf 대신 placeholder iframe). */
export const PdfModeWithRenderer: Story = {
  name: 'mode=pdf / 가짜 renderer 주입',
  render: () => (
    <PrintPreview
      mode="pdf"
      paperSize="A4"
      orientation="portrait"
      pdfRenderer={(node) => (
        <div
          style={{
            background: '#fff',
            border: '1px dashed #aaa',
            padding: 16,
            minHeight: 400,
          }}
        >
          <div style={{ fontSize: 11, color: '#888', marginBottom: 8 }}>
            [react-pdf PDFViewer placeholder]
          </div>
          {node}
        </div>
      )}
    >
      {sampleEstimate}
    </PrintPreview>
  ),
}
