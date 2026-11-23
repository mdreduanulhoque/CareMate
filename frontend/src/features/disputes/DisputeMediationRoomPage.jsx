import {
  ArrowLeft,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Lock,
  Scale,
  Send,
  ShieldCheck,
  Wifi,
  WifiOff,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as paymentDisputeApi from '../../api/paymentDisputeApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { useAuth } from '../../context/useAuth';
import { useWebSocket } from '../../context/useWebSocket';
import { formatDhakaChatTime, formatDhakaDateTime } from '../../utils/dhakaTime';

const MAX_MESSAGE_LENGTH = 2000;

const ROLE_STYLES = {
  CUSTOMER: {
    label: 'Relative',
    badge: 'bg-teal-100 text-teal-900 border-teal-200',
    bubbleOther: 'border-teal-200 bg-teal-50/70 text-slate-900',
  },
  CARETAKER: {
    label: 'Caretaker',
    badge: 'bg-sky-100 text-sky-900 border-sky-200',
    bubbleOther: 'border-sky-200 bg-sky-50/70 text-slate-900',
  },
  ADMIN: {
    label: 'Admin Mediator',
    badge: 'bg-indigo-100 text-indigo-900 border-indigo-300',
    bubbleOther: 'border-indigo-300 bg-indigo-50 text-indigo-950',
  },
};

function mergeMessagesById(existing, incomingList) {
  const byId = new Map();
  existing.forEach((m) => {
    if (m?.id != null) byId.set(m.id, m);
  });
  incomingList.forEach((m) => {
    if (m?.id != null) byId.set(m.id, m);
  });
  return Array.from(byId.values()).sort((a, b) => {
    const tDiff = new Date(a.sentAt).getTime() - new Date(b.sentAt).getTime();
    return tDiff !== 0 ? tDiff : a.id - b.id;
  });
}

export function DisputeMediationRoomPage() {
  const { contractId } = useParams();
  const { user } = useAuth();
  const { isConnected, connectCount, subscribe, publish } = useWebSocket();

  const isAdmin = user?.role === 'ADMIN';
  const backPath =
    user?.role === 'ADMIN'
      ? '/admin/disputes'
      : user?.role === 'CARETAKER'
        ? '/caretaker/bookings'
        : '/customer/hires';

  const [dispute, setDispute] = useState(null);
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [sendError, setSendError] = useState('');
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);

  const [showContext, setShowContext] = useState(true);
  const [showResolveBox, setShowResolveBox] = useState(false);
  const [resolutionNote, setResolutionNote] = useState('');
  const [resolving, setResolving] = useState(false);
  const [joining, setJoining] = useState(false);

  const [editingStatement, setEditingStatement] = useState(false);
  const [statementDraft, setStatementDraft] = useState('');
  const [savingStatement, setSavingStatement] = useState(false);

  const messagesEndRef = useRef(null);

  const loadRoom = useCallback(
    async ({ silent = false } = {}) => {
      if (!contractId) return;
      if (!silent) setLoading(true);
      setError('');
      try {
        const data = await paymentDisputeApi.fetchDisputeRoomDetail(contractId, 150);
        setDispute(data.dispute);
        setMessages((prev) => mergeMessagesById(prev, data.messages || []));
      } catch (err) {
        setError(err.message || 'Could not load 3-way dispute mediation room.');
      } finally {
        setLoading(false);
      }
    },
    [contractId]
  );

  useEffect(() => {
    setMessages([]);
    setDispute(null);
    loadRoom();
  }, [loadRoom]);

  useEffect(() => {
    if (connectCount > 1) {
      loadRoom({ silent: true });
    }
  }, [connectCount, loadRoom]);

  // Live 3-way dispute room STOMP subscription
  useEffect(() => {
    if (!contractId || !dispute) return undefined;
    return subscribe(`/topic/chat/dispute/${contractId}`, (incoming) => {
      if (incoming?.id != null) {
        setMessages((prev) => mergeMessagesById(prev, [incoming]));
      }
    });
  }, [contractId, dispute, subscribe]);

  // Refresh dispute header state live when Admin joins or resolves
  useEffect(() => {
    return subscribe('/user/queue/notifications', (event) => {
      if (
        String(event?.contractId) === String(contractId) &&
        (event?.eventType === 'DISPUTE_ADMIN_JOINED' || event?.eventType === 'DISPUTE_RESOLVED')
      ) {
        loadRoom({ silent: true });
      }
    });
  }, [contractId, subscribe, loadRoom]);

  useEffect(() => {
    return subscribe('/user/queue/errors', (errPayload) => {
      if (errPayload?.message) {
        setSendError(errPayload.message);
      }
    });
  }, [subscribe]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  async function handleSendMessage(event) {
    event?.preventDefault();
    const trimmed = draft.trim();
    if (!trimmed || sending || !dispute?.canSendMessages) return;

    setSendError('');
    setDraft('');

    if (isConnected) {
      const published = publish(`/app/chat/dispute/${dispute.contractId}`, { content: trimmed });
      if (published) {
        return;
      }
    }

    setSending(true);
    try {
      const saved = await paymentDisputeApi.sendDisputeMessageRest(dispute.contractId, trimmed);
      setMessages((prev) => mergeMessagesById(prev, [saved]));
    } catch (err) {
      setDraft(trimmed);
      setSendError(err.message || 'Could not send message right now.');
    } finally {
      setSending(false);
    }
  }

  async function handleJoinAsAdmin() {
    setJoining(true);
    setSendError('');
    try {
      const updated = await paymentDisputeApi.joinDisputeAsAdmin(dispute.contractId);
      setDispute(updated);
    } catch (err) {
      setSendError(err.message || 'Could not join dispute.');
    } finally {
      setJoining(false);
    }
  }

  async function handleResolveDispute(event) {
    event.preventDefault();
    if (resolutionNote.trim().length < 5) {
      setSendError('Please enter a clear resolution note (at least 5 characters).');
      return;
    }
    setResolving(true);
    setSendError('');
    try {
      const resolved = await paymentDisputeApi.resolveDisputeAsAdmin(
        dispute.contractId,
        resolutionNote.trim()
      );
      setDispute(resolved);
      setShowResolveBox(false);
      setResolutionNote('');
    } catch (err) {
      setSendError(err.message || 'Could not resolve dispute.');
    } finally {
      setResolving(false);
    }
  }

  async function handleSaveStatement(event) {
    event.preventDefault();
    if (statementDraft.trim().length < 5) {
      setSendError('Statement must be at least 5 characters.');
      return;
    }
    setSavingStatement(true);
    setSendError('');
    try {
      const updated = await paymentDisputeApi.updateDisputeStatement(
        dispute.contractId,
        statementDraft.trim()
      );
      setDispute(updated);
      setEditingStatement(false);
    } catch (err) {
      setSendError(err.message || 'Could not update statement.');
    } finally {
      setSavingStatement(false);
    }
  }

  if (loading && !dispute) {
    return (
      <div className="mx-auto max-w-3xl rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">
        Opening 3-Way Admin Dispute Mediation Room...
      </div>
    );
  }

  if (error || !dispute) {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <Link
          to={backPath}
          className="inline-flex min-h-[44px] items-center gap-1.5 rounded-xl px-2 text-sm font-semibold text-slate-700 hover:bg-slate-200/70"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          <span>Back</span>
        </Link>
        <AlertBanner variant="error" title="Dispute room unavailable">
          <div className="space-y-2">
            <p>{error || 'Unable to access this dispute room.'}</p>
            <Button variant="outline" onClick={() => loadRoom()}>
              Retry
            </Button>
          </div>
        </AlertBanner>
      </div>
    );
  }

  return (
    <div className="mx-auto flex h-[calc(100dvh-10.5rem)] max-w-4xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xs md:h-[calc(100dvh-7.5rem)]">
      {/* Header */}
      <header className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 bg-white px-3 py-2.5 sm:px-4">
        <div className="flex min-w-0 items-center gap-2">
          <Link
            to={backPath}
            aria-label="Back"
            className="inline-flex min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-xl text-slate-600 hover:bg-slate-100"
          >
            <ArrowLeft className="h-5 w-5" aria-hidden="true" />
          </Link>

          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-rose-100 text-rose-700">
            <Scale className="h-5 w-5" aria-hidden="true" />
          </div>

          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-1.5">
              <h1 className="truncate text-sm font-bold text-slate-900 sm:text-base">
                3-Way Dispute Room · Contract #{dispute.contractId}
              </h1>
              <StatusBadge status={dispute.contractStatus} />
            </div>
            <div className="flex items-center gap-2 text-[11px] text-slate-500">
              <span>৳{Number(dispute.agreedRate).toLocaleString('en-BD')}</span>
              <span>·</span>
              <span
                className={`inline-flex items-center gap-1 font-semibold ${
                  isConnected ? 'text-emerald-700' : 'text-amber-700'
                }`}
              >
                {isConnected ? <Wifi className="h-3 w-3" /> : <WifiOff className="h-3 w-3" />}
                {isConnected ? 'Live 3-Way Socket' : 'Reconnecting'}
              </span>
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={() => setShowContext((prev) => !prev)}
          className="inline-flex min-h-[40px] items-center gap-1 rounded-xl border border-slate-200 px-3 text-xs font-semibold text-slate-700 hover:bg-slate-50"
        >
          <span>Case Details</span>
          {showContext ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
        </button>
      </header>

      {/* Pinned contract & payment reference context */}
      {showContext && (
        <section className="max-h-56 overflow-y-auto border-b border-slate-200 bg-slate-50 px-4 py-3 text-xs space-y-2.5">
          <div className="grid gap-2 sm:grid-cols-3">
            <div className="rounded-xl border border-teal-200 bg-white p-2.5">
              <div className="font-bold text-teal-900">Relative: {dispute.customerFullName}</div>
              <div className="text-[11px] text-slate-500">{dispute.customerMobileNumber}</div>
              <p className="mt-1 text-slate-700">
                <strong>Payment Ref / Statement:</strong>{' '}
                {dispute.customerStatement || dispute.paymentReferenceNote || 'None provided yet'}
              </p>
            </div>

            <div className="rounded-xl border border-sky-200 bg-white p-2.5">
              <div className="font-bold text-sky-900">Caretaker: {dispute.caretakerFullName}</div>
              <div className="text-[11px] text-slate-500">{dispute.caretakerMobileNumber}</div>
              <p className="mt-1 text-slate-700">
                <strong>Dispute Statement:</strong> {dispute.caretakerStatement}
              </p>
            </div>

            <div className="rounded-xl border border-indigo-200 bg-white p-2.5">
              <div className="flex items-center gap-1 font-bold text-indigo-900">
                <ShieldCheck className="h-3.5 w-3.5" />
                <span>Admin Mediator</span>
              </div>
              <div className="text-[11px] text-slate-600">
                {dispute.assignedAdminName || 'Awaiting Admin join'} · Status: {dispute.status}
              </div>
              {dispute.adminResolutionNote && (
                <p className="mt-1 font-medium text-emerald-900">
                  <strong>Resolution:</strong> {dispute.adminResolutionNote}
                </p>
              )}
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {!isAdmin && dispute.canSendMessages && !editingStatement && (
              <button
                type="button"
                onClick={() => {
                  setStatementDraft(
                    user?.role === 'CUSTOMER'
                      ? dispute.customerStatement || ''
                      : dispute.caretakerStatement || ''
                  );
                  setEditingStatement(true);
                }}
                className="inline-flex min-h-[36px] items-center rounded-lg border border-slate-300 bg-white px-3 text-xs font-semibold text-slate-700 hover:bg-slate-100"
              >
                Update My Written Payment Statement
              </button>
            )}

            {isAdmin && dispute.status === 'OPEN' && (
              <Button variant="outline" onClick={handleJoinAsAdmin} loading={joining}>
                Claim &amp; Mark Under Review
              </Button>
            )}

            {isAdmin && dispute.status !== 'RESOLVED' && (
              <Button
                variant="primary"
                onClick={() => setShowResolveBox((prev) => !prev)}
                className="bg-indigo-700 hover:bg-indigo-800"
              >
                <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                <span>Record Final Resolution</span>
              </Button>
            )}
          </div>

          {editingStatement && (
            <form onSubmit={handleSaveStatement} className="space-y-2 rounded-xl border border-slate-200 bg-white p-3">
              <textarea
                rows={2}
                maxLength={2000}
                value={statementDraft}
                onChange={(e) => setStatementDraft(e.target.value)}
                placeholder="Share payment transaction ID, time sent, or bank/bKash reference..."
                className="w-full rounded-lg border border-slate-300 p-2 text-xs text-slate-900 focus:border-teal-600 focus:outline-none"
              />
              <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => setEditingStatement(false)}>
                  Cancel
                </Button>
                <Button type="submit" loading={savingStatement}>
                  Save Statement
                </Button>
              </div>
            </form>
          )}

          {showResolveBox && isAdmin && (
            <form
              onSubmit={handleResolveDispute}
              className="space-y-2 rounded-xl border border-indigo-200 bg-indigo-50/70 p-3"
            >
              <label htmlFor="admin-resolution-note" className="block font-bold text-indigo-950">
                Final Admin Resolution Decision (mandatory)
              </label>
              <textarea
                id="admin-resolution-note"
                rows={2}
                maxLength={2000}
                required
                value={resolutionNote}
                onChange={(e) => setResolutionNote(e.target.value)}
                placeholder="Summarize verified payment evidence and final mediation outcome..."
                className="w-full rounded-lg border border-indigo-300 bg-white p-2 text-xs text-slate-900 focus:border-indigo-600 focus:outline-none"
              />
              <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => setShowResolveBox(false)}>
                  Cancel
                </Button>
                <Button type="submit" loading={resolving} className="bg-indigo-700 hover:bg-indigo-800">
                  Close Case as Resolved
                </Button>
              </div>
            </form>
          )}
        </section>
      )}

      {/* 3-way message timeline */}
      <div className="flex-1 overflow-y-auto bg-slate-50/70 px-3 py-4 sm:px-5 space-y-3">
        {messages.length === 0 ? (
          <div className="mx-auto max-w-md rounded-xl border border-dashed border-slate-300 bg-white p-5 text-center text-xs text-slate-600">
            All three parties (Relative, Caretaker, and Admin Mediator) can share transaction IDs,
            timestamps, and statements here in real time.
          </div>
        ) : (
          messages.map((msg) => {
            const isOwn = msg.senderId === user?.id;
            const roleMeta = ROLE_STYLES[msg.senderRole] || ROLE_STYLES.CUSTOMER;
            const isAdminMsg = msg.senderRole === 'ADMIN';

            return (
              <div
                key={msg.id}
                className={`flex flex-col ${isOwn ? 'items-end' : 'items-start'}`}
              >
                <div
                  className={`max-w-[88%] rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed shadow-2xs sm:max-w-[78%] ${
                    isOwn
                      ? isAdminMsg
                        ? 'rounded-br-md bg-indigo-700 text-white'
                        : 'rounded-br-md bg-teal-700 text-white'
                      : `rounded-bl-md border ${roleMeta.bubbleOther}`
                  }`}
                >
                  <div className="mb-1 flex flex-wrap items-center gap-1.5">
                    <span
                      className={`text-[11px] font-bold ${isOwn ? 'text-white/95' : 'text-slate-900'}`}
                    >
                      {msg.senderName}
                    </span>
                    <span
                      className={`inline-flex items-center gap-0.5 rounded-full border px-2 py-0.25 text-[10px] font-bold ${
                        isOwn
                          ? 'border-white/30 bg-white/15 text-white'
                          : roleMeta.badge
                      }`}
                    >
                      {isAdminMsg && <ShieldCheck className="h-2.5 w-2.5" aria-hidden="true" />}
                      {roleMeta.label}
                    </span>
                  </div>
                  <p className="whitespace-pre-wrap break-words">{msg.content}</p>
                </div>
                <span className="mt-1 px-1 text-[10px] font-medium text-slate-400">
                  {formatDhakaChatTime(msg.sentAt)}
                </span>
              </div>
            );
          })
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Composer or Resolved Read-Only Footer */}
      <div className="border-t border-slate-200 bg-white p-2.5 sm:p-3 space-y-2">
        {sendError && <AlertBanner variant="error">{sendError}</AlertBanner>}

        {dispute.canSendMessages ? (
          <form onSubmit={handleSendMessage} className="flex items-end gap-2">
            <textarea
              rows={1}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSendMessage();
                }
              }}
              maxLength={MAX_MESSAGE_LENGTH}
              placeholder="Write a message in this 3-way mediation room..."
              aria-label="Dispute mediation message"
              className="block max-h-28 min-h-[44px] flex-1 resize-none rounded-xl border border-slate-300 bg-slate-50 px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-600 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-100"
            />
            <Button
              type="submit"
              disabled={!draft.trim() || sending}
              loading={sending}
              className="shrink-0 px-4"
            >
              <Send className="h-4 w-4" aria-hidden="true" />
              <span className="hidden sm:inline">Send</span>
            </Button>
          </form>
        ) : (
          <div className="flex items-center justify-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-2.5 text-xs font-semibold text-emerald-950">
            <Lock className="h-4 w-4 shrink-0 text-emerald-700" aria-hidden="true" />
            <span>
              Dispute resolved on {formatDhakaDateTime(dispute.resolvedAt)}. Room history is
              preserved read-only.
            </span>
          </div>
        )}
      </div>
    </div>
  );
}
