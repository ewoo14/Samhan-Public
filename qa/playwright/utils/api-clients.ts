/**
 * 백엔드 REST API 호출 helper.
 * 시나리오 setup/teardown 에서 사용 (draft 생성, slip 발행 검증 등).
 *
 * QA_API_BASE_URL 환경 변수로 base URL 지정 (기본 http://localhost:8080).
 */
export class ApiClient {
  private readonly base: string;
  private readonly token?: string;

  constructor(opts: { baseUrl?: string; token?: string } = {}) {
    this.base = opts.baseUrl ?? process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    this.token = opts.token;
  }

  private async request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    };
    if (this.token) headers.Authorization = `Bearer ${this.token}`;

    const res = await fetch(`${this.base}${path}`, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(10_000),
    });
    if (!res.ok) {
      const text = await res.text().catch(() => '');
      throw new Error(`${method} ${path} → ${res.status}: ${text}`);
    }
    return res.json() as Promise<T>;
  }

  get<T>(path: string): Promise<T> {
    return this.request<T>('GET', path);
  }

  post<T>(path: string, body: unknown): Promise<T> {
    return this.request<T>('POST', path, body);
  }

  delete<T>(path: string): Promise<T> {
    return this.request<T>('DELETE', path);
  }

  /** partner-order-service: 임시저장 draft 생성 */
  createDraft(payload: Record<string, unknown>): Promise<{ id: string }> {
    return this.post<{ id: string }>('/api/partner-orders/drafts', payload);
  }

  /** partner-order-service: draft 조회 */
  getDraft(id: string): Promise<Record<string, unknown>> {
    return this.get<Record<string, unknown>>(`/api/partner-orders/drafts/${id}`);
  }

  /** slip-service: 발행 검증 */
  getSlip(slipNo: string): Promise<Record<string, unknown>> {
    return this.get<Record<string, unknown>>(`/api/slips/${slipNo}`);
  }

  /** inventory-service: 재고 조회 */
  getStock(productCode: string): Promise<{ qty: number }> {
    return this.get<{ qty: number }>(`/api/inventory/stock/${productCode}`);
  }
}
