---
name: Domain & Subdomain Strategy
description: samhan-air.com subdomain layout, TLS, and Nginx reverse-proxy mapping
type: project
---

**Owned domain**: `samhan-air.com` (company-owned).

**Subdomain map** (plan §4):
- `samhan-air.com` — public website
- `app.samhan-air.com` — internal web app (JWT login)
- `api.samhan-air.com` — API Gateway entry (JWT / API key)
- `order.samhan-air.com` — partner order links (token-based, no login)
- `sign.samhan-air.com` — e-signature delivery completion (one-time JWT)
- `chat.samhan-air.com` — internal messenger WebSocket endpoint
- `files.samhan-air.com` — MinIO proxy
- `monitor.samhan-air.com` — Grafana

**TLS**: wildcard `*.samhan-air.com` (Let's Encrypt or paid CA), TLS 1.2 minimum, `sign.*` enforces TLS 1.3, HSTS on all subdomains.

**Reverse proxy**: Nginx in front of Docker containers, routing per the table above. Internal ports: gateway 8080, web 3000, order app 3001, sign app 3002, chat ws 8081, MinIO 9000, Grafana 3100.

**How to apply**: When wiring CORS, OAuth callbacks, cookie domain, or generated links, use these subdomains — do not invent new ones. Local dev maps via `127.0.0.1` + hosts file or `*.localhost`.
