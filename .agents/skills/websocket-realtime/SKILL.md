---
name: websocket-realtime
description: >-
  Use this skill when implementing or debugging Spring WebSocket (STOMP + SockJS),
  STOMP JWT authentication/authorization, live negotiation chat, 3-way dispute rooms,
  feed availability broadcasts, or React STOMP client integration.
---

# CareMate WebSocket Real-Time Networking Skill

## 1. Server Topology (Spring WebSocket + STOMP + SockJS)
- **Endpoint:** `/ws` with SockJS fallback enabled and explicit frontend origin allowlist (never `*` with credentials).
- **Application Destination Prefix:** `/app`
- **Broker Prefixes:** `/topic` (broadcasts & room channels), `/queue` (private per-user delivery)
- **User Destination Prefix:** `/user`

### Canonical Channels
| Channel | Purpose | Strict Subscriber Authorization |
| :--- | :--- | :--- |
| `/topic/feed/availability` | Live caretaker availability / daily-cap-full updates | Any authenticated user |
| `/topic/chat/negotiation/{roomId}` | 1-on-1 Negotiation Chat (`NEGOTIATION:{customerId}:{caretakerId}`) | Only that `customerId` or `caretakerId` AND a `contact_unlocks` record must exist |
| `/topic/chat/dispute/{contractId}` | 3-Way Admin Dispute Mediation Room | Only contract's `customerId`, contract's `caretakerId`, or `ROLE_ADMIN` while contract is `PAYMENT_DISPUTED` / `DISPUTE_RESOLVED` |
| `/user/queue/notifications` | Hire requests, contract transitions, feedback prompts, payment alerts | Automatically scoped to authenticated user principal |
| `/topic/admin/alerts` | New applications, re-applications, new certifications, payment disputes | `ROLE_ADMIN` exclusively |

## 2. Mandatory STOMP Security (`ChannelInterceptor`)
Register an inbound `ChannelInterceptor` on `configureClientInboundChannel`:
1. **`StompCommand.CONNECT`:** Extract Bearer JWT from native header `Authorization`, validate via `JwtService`, build `Authentication` (`Principal` = canonical user ID or mobile number, consistently matched with `/user` routing), and call `accessor.setUser(auth)`. Reject missing/invalid tokens.
2. **`StompCommand.SUBSCRIBE`:** Inspect `accessor.getDestination()` and `accessor.getUser()`. Explicitly authorize every `/topic/chat/negotiation/**`, `/topic/chat/dispute/**`, and `/topic/admin/**` subscription against DB/role rules. Throw `AccessDeniedException` on unauthorized attempts.
3. **`StompCommand.SEND`:** Re-verify sender participation in the room before persisting `chat_messages` and broadcasting. Never trust a client-supplied `senderId`—always derive sender from `Principal`.

## 3. Message Persistence & Payload Hygiene
- Persist every negotiation and dispute chat message to `chat_messages` inside a transaction before/as it broadcasts so users reconnecting on weak hospital mobile data never lose history.
- Sanitize / trim content, reject blank or oversized messages (e.g., > 2000 chars), and render text safely in React (no `dangerouslySetInnerHTML`).

## 4. React STOMP Client Standards
- Use `@stomp/stompjs` with `sockjs-client` wrapped in a dedicated React context/hook (`WebSocketProvider`).
- Pass JWT in `connectHeaders: { Authorization: 'Bearer ' + token }`.
- Enable exponential/bounded `reconnectDelay` (e.g., 3000ms) and expose `connectionStatus` (`CONNECTED`, `RECONNECTING`, `DISCONNECTED`) for a subtle mobile status banner.
- Always clean up STOMP subscriptions on component unmount and deduplicate incoming messages by server `id` when merging with REST history.

## 5. Verification Checklist
- Unauthorized STOMP `CONNECT` (missing/expired JWT) fails cleanly.
- User C attempting to subscribe to User A & User B's negotiation room or dispute room is rejected.
- Non-admin attempting to subscribe to `/topic/admin/alerts` is rejected.
- Real-time delivery works across two active browser sessions without manual page refresh.
