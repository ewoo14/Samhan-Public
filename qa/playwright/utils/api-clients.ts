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

  private async request<T>(
    method: string,
    path: string,
    body?: unknown,
    params?: Record<string, string | number | undefined>,
  ): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    };
    if (this.token) headers.Authorization = `Bearer ${this.token}`;

    let url = `${this.base}${path}`;
    if (params) {
      const qs = new URLSearchParams();
      for (const [k, v] of Object.entries(params)) {
        if (v !== undefined && v !== null) qs.append(k, String(v));
      }
      const qsStr = qs.toString();
      if (qsStr) url += (url.includes('?') ? '&' : '?') + qsStr;
    }

    const res = await fetch(url, {
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

  get<T>(path: string, opts?: { params?: Record<string, string | number | undefined> }): Promise<T> {
    return this.request<T>('GET', path, undefined, opts?.params);
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

  /**
   * inventory-service: 재고 잔량 조회.
   *
   * Phase 7 3차 정정 (BE Critical) — 실제 inventory-service `GET /inventory/balances?productId=<UUID>`
   * 호출로 정합. 응답 = `ApiResponse<Page<StockBalanceResponse>>` 이며 `data.content[0]` 의 잔량을
   * 반환한다 (영업원 단일 창고 케이스).
   *
   * StockBalanceResponse schema (inventory-service web/dto/StockBalanceResponse.java):
   *   - availableQty : 할당 가능 수량 (totalQty - reservedQty)
   *   - reservedQty  : 주문 확정 시 예약된 수량 (출고 전)
   *   - totalQty     : 물리적 총 보유 수량
   *
   * reserve 단계  = reservedQty 증가 + availableQty 감소 (totalQty 불변)
   * deduct 단계   = totalQty 감소 + reservedQty 감소 (slip publish 시점)
   *
   * @param productId UUID — fixtures/products.json 의 model 코드는 productId 가 아니므로
   *                         productCode → productId 매핑은 호출자 책임 (lookupProductId).
   */
  async getStock(productId: string): Promise<StockSnapshot> {
    const res = await this.get<{ data: { content: Array<{ availableQty: number; reservedQty: number; totalQty: number }> } }>(
      `/inventory/balances`,
      { params: { productId } },
    );
    const first = res?.data?.content?.[0];
    if (!first) throw new Error(`Stock not found for productId=${productId}`);
    return {
      availableQty: first.availableQty,
      reservedQty: first.reservedQty,
      totalQty: first.totalQty,
    };
  }

  /**
   * fixtures/products.json 의 model 코드 → 실 backend productId (UUID) 조회 헬퍼.
   *
   * IT 환경에서는 backend product-service 가 실 UUID 를 반환하므로 fixture 의 `id`
   * (e.g. "PROD-001") 는 사용 불가. spec 별 setup 에서 이 헬퍼로 UUID 를 확보한 뒤
   * `getStock(uuid)` 를 호출한다.
   *
   * 향후 product-service 의 `GET /api/products/by-code/{code}` 가 도입되면 그쪽으로
   * 위임. 현재는 spec 단에서 직접 backend DB seed 의 UUID 를 환경변수로 주입하거나
   * skip-on-empty 패턴 사용.
   */
  async lookupProductIdByCode(productCode: string): Promise<string | null> {
    try {
      const res = await this.get<{ data?: { id?: string } }>(`/api/products/by-code/${encodeURIComponent(productCode)}`);
      return res?.data?.id ?? null;
    } catch {
      return null;
    }
  }
}

export interface StockSnapshot {
  availableQty: number;
  reservedQty: number;
  totalQty: number;
}
