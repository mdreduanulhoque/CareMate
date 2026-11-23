import {
  ArrowLeft,
  BriefcaseMedical,
  CheckCircle2,
  MapPin,
  Phone,
  Send,
  User,
  Wifi,
  WifiOff,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as contactAndChatApi from '../../api/contactAndChatApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { CaretakerAvatar } from '../../components/ui/CaretakerAvatar';
import { useAuth } from '../../context/useAuth';
import { useWebSocket } from '../../context/useWebSocket';
import { formatDhakaChatTime } from '../../utils/dhakaTime';
import { HireRequestModal } from '../hiring/HireRequestModal';

const MAX_MESSAGE_LENGTH = 2000;

const CUSTOMER_QUICK_PROMPTS = [
  'Hello, my family member is admitted in hospital. Are you available for a shift?',
  'Could you please confirm your BDT rate and earliest arrival time?',
];

function mergeMessagesById(existing, incomingList) {
  const byId = new Map();
  existing.forEach((msg) => {
    if (msg?.id != null) {
      byId.set(msg.id, msg);
    }
  });
  incomingList.forEach((msg) => {
    if (msg?.id != null) {
      byId.set(msg.id, msg);
    }
  });
  return Array.from(byId.values()).sort((a, b) => {
    const timeDiff = new Date(a.sentAt).getTime() - new Date(b.sentAt).getTime();
    if (timeDiff !== 0) {
      return timeDiff;
    }
    return a.id - b.id;
  });
}

function formatRateCompact(rateAmount, rateType) {
  if (rateAmount == null) return 'Rate negotiable';
  const formatted = Number(rateAmount).toLocaleString('en-BD');
  return rateType === 'HOURLY' ? `৳${formatted}/hr` : `৳${formatted}/day`;
}

export function NegotiationChatRoomPage() {
  const { roomId: encodedRoomId } = useParams();
  const roomId = encodedRoomId ? decodeURIComponent(encodedRoomId) : '';
  const { user } = useAuth();
  const { isConnected, connectCount, subscribe, publish } = useWebSocket();

  const isCustomer = user?.role === 'CUSTOMER';
  const inboxPath = isCustomer ? '/customer/chats' : '/caretaker/chats';

  const [room, setRoom] = useState(null);
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [sendError, setSendError] = useState('');
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [showHireModal, setShowHireModal] = useState(false);
  const [hireSuccess, setHireSuccess] = useState(null);

  const messagesEndRef = useRef(null);

  const loadRoomDetail = useCallback(
    async ({ silent = false } = {}) => {
      if (!roomId) {
        setError('Invalid chat room link.');
        setLoading(false);
        return;
      }
      if (!silent) {
        setLoading(true);
      }
      setError('');
      try {
        const data = await contactAndChatApi.fetchNegotiationRoomDetail(roomId, 100);
        setRoom(data.room);
        setMessages((prev) => mergeMessagesById(prev, data.messages || []));
      } catch (err) {
        setError(err.message || 'Could not open this negotiation chat.');
      } finally {
        setLoading(false);
      }
    },
    [roomId]
  );

  // Initial load
  useEffect(() => {
    setMessages([]);
    setRoom(null);
    loadRoomDetail();
  }, [loadRoomDetail]);

  // Resync persisted history cleanly after any WebSocket reconnection on mobile data
  useEffect(() => {
    if (connectCount > 1) {
      loadRoomDetail({ silent: true });
    }
  }, [connectCount, loadRoomDetail]);

  // Live room subscription with strict server-id deduplication
  useEffect(() => {
    if (!roomId || !room) {
      return undefined;
    }
    return subscribe(`/topic/chat/negotiation/${roomId}`, (incoming) => {
      if (incoming?.id != null) {
        setMessages((prev) => mergeMessagesById(prev, [incoming]));
      }
    });
  }, [roomId, room, subscribe]);

  // Surface any STOMP validation/authorization errors sent to /user/queue/errors
  useEffect(() => {
    return subscribe('/user/queue/errors', (errPayload) => {
      if (errPayload?.message) {
        setSendError(errPayload.message);
      }
    });
  }, [subscribe]);

  // Live caretaker availability & rate updates in room header
  useEffect(() => {
    if (!isCustomer || !room?.caretakerUserId) {
      return undefined;
    }
    return subscribe('/topic/feed/availability', (event) => {
      if (event?.caretakerUserId === room.caretakerUserId) {
        setRoom((prev) =>
          prev
            ? {
                ...prev,
                counterpartAvailable: event.available,
                counterpartRateAmount: event.rateAmount,
                counterpartRateType: event.rateType,
                counterpartLocationArea: event.locationArea,
              }
            : prev
        );
      }
    });
  }, [isCustomer, room?.caretakerUserId, subscribe]);

  // Auto-scroll to latest message
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  async function handleSendMessage(event) {
    event?.preventDefault();
    const trimmed = draft.trim();
    if (!trimmed || sending || !room) {
      return;
    }
    if (trimmed.length > MAX_MESSAGE_LENGTH) {
      setSendError('Message must be 2000 characters or fewer.');
      return;
    }

    setSendError('');
    setDraft('');

    // Primary path: live STOMP WebSocket send; graceful REST fallback while reconnecting
    if (isConnected) {
      const published = publish(`/app/chat/negotiation/${room.roomId}`, { content: trimmed });
      if (published) {
        return;
      }
    }

    setSending(true);
    try {
      const saved = await contactAndChatApi.sendNegotiationMessageRest(room.roomId, trimmed);
      setMessages((prev) => mergeMessagesById(prev, [saved]));
    } catch (err) {
      setDraft(trimmed);
      setSendError(err.message || 'Could not send message. Please try again.');
    } finally {
      setSending(false);
    }
  }

  function handleKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      handleSendMessage();
    }
  }

  if (loading && !room) {
    return (
      <div className="mx-auto max-w-3xl space-y-3">
        <Link
          to={inboxPath}
          className="inline-flex min-h-[44px] items-center gap-1.5 rounded-xl px-2 text-sm font-semibold text-slate-700 hover:bg-slate-200/70"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          <span>Back to Chats</span>
        </Link>
        <div className="rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">
          Opening live negotiation room...
        </div>
      </div>
    );
  }

  if (error || !room) {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <Link
          to={inboxPath}
          className="inline-flex min-h-[44px] items-center gap-1.5 rounded-xl px-2 text-sm font-semibold text-slate-700 hover:bg-slate-200/70"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          <span>Back to Chats</span>
        </Link>
        <AlertBanner variant="error" title="Chat room unavailable">
          <div className="space-y-2">
            <p>{error || 'You do not have access to this negotiation room.'}</p>
            <Button variant="outline" onClick={() => loadRoomDetail()}>
              Retry
            </Button>
          </div>
        </AlertBanner>
      </div>
    );
  }

  return (
    <div className="mx-auto flex h-[calc(100dvh-10.5rem)] max-w-3xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xs md:h-[calc(100dvh-7.5rem)]">
      {/* Room header */}
      <header className="flex items-center justify-between gap-2 border-b border-slate-200 bg-white px-3 py-2.5 sm:px-4">
        <div className="flex min-w-0 items-center gap-2.5">
          <Link
            to={inboxPath}
            aria-label="Back to chats"
            className="inline-flex min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-xl text-slate-600 hover:bg-slate-100 hover:text-slate-900"
          >
            <ArrowLeft className="h-5 w-5" aria-hidden="true" />
          </Link>

          {isCustomer ? (
            <CaretakerAvatar
              src={room.counterpartAvatarUrl}
              name={room.counterpartName}
              size="sm"
              showDot
              available={Boolean(room.counterpartAvailable)}
            />
          ) : (
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-teal-700 text-sm font-bold text-white">
              {room.counterpartName?.slice(0, 1)?.toUpperCase() || <User className="h-5 w-5" />}
            </div>
          )}

          <div className="min-w-0">
            <div className="flex items-center gap-1.5">
              <h1 className="truncate text-sm font-bold text-slate-900 sm:text-base">
                {room.counterpartName}
              </h1>
              {isCustomer && (
                <span
                  className={`hidden shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold sm:inline-flex ${
                    room.counterpartAvailable
                      ? 'bg-emerald-100 text-emerald-800'
                      : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  <CheckCircle2 className="h-3 w-3" />
                  {room.counterpartAvailable ? 'Available' : 'Unavailable'}
                </span>
              )}
            </div>

            <div className="flex flex-wrap items-center gap-x-2 text-[11px] text-slate-500">
              {isCustomer ? (
                <>
                  <span className="truncate font-medium text-teal-800">
                    {formatRateCompact(room.counterpartRateAmount, room.counterpartRateType)}
                  </span>
                  {room.counterpartLocationArea && (
                    <span className="hidden items-center gap-0.5 truncate sm:inline-flex">
                      <MapPin className="h-3 w-3" aria-hidden="true" />
                      {room.counterpartLocationArea}
                    </span>
                  )}
                </>
              ) : (
                <span className="truncate">Patient&apos;s Relative · {room.counterpartLocationArea}</span>
              )}
              <span
                className={`inline-flex items-center gap-1 font-semibold ${
                  isConnected ? 'text-emerald-700' : 'text-amber-700'
                }`}
              >
                {isConnected ? (
                  <>
                    <Wifi className="h-3 w-3" aria-hidden="true" />
                    <span>Live</span>
                  </>
                ) : (
                  <>
                    <WifiOff className="h-3 w-3" aria-hidden="true" />
                    <span>Syncing</span>
                  </>
                )}
              </span>
            </div>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-1.5">
          {isCustomer && (
            <button
              type="button"
              onClick={() => setShowHireModal(true)}
              className="inline-flex min-h-[44px] shrink-0 items-center gap-1.5 rounded-xl bg-teal-700 px-3.5 text-xs font-bold text-white shadow-xs transition-colors hover:bg-teal-800"
            >
              <BriefcaseMedical className="h-4 w-4" aria-hidden="true" />
              <span>Hire</span>
            </button>
          )}
          {room.counterpartMobileNumber && (
            <a
              href={`tel:${room.counterpartMobileNumber}`}
              aria-label={`Call ${room.counterpartName}`}
              className={`inline-flex min-h-[44px] shrink-0 items-center gap-1.5 rounded-xl px-3 text-xs font-bold transition-colors ${
                isCustomer
                  ? 'border border-teal-200 bg-teal-50 text-teal-800 hover:bg-teal-100'
                  : 'bg-teal-700 text-white shadow-xs hover:bg-teal-800'
              }`}
            >
              <Phone className="h-4 w-4" aria-hidden="true" />
              <span className="hidden sm:inline">{room.counterpartMobileNumber}</span>
              <span className="sm:hidden">Call</span>
            </a>
          )}
        </div>
      </header>

      {/* Message stream */}
      <div className="flex-1 overflow-y-auto bg-slate-50/80 px-3 py-4 sm:px-5 space-y-3">
        <div className="mx-auto max-w-md rounded-xl border border-teal-200/80 bg-teal-50/70 px-3.5 py-2 text-center text-xs text-teal-900">
          {isCustomer
            ? 'Discuss hospital ward, patient condition, shift hours, and exact BDT rate — then tap Hire above to send a formal booking.'
            : 'Coordinate hospital ward, shift timing, and exact BDT rate here. Formal hire requests appear in your Requests tab.'}
        </div>

        {hireSuccess && (
          <AlertBanner variant="success" title="Hire Request Submitted">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span>
                Contract #{hireSuccess.id} sent to {room.counterpartName}.
              </span>
              <Link to="/customer/hires" className="font-bold underline">
                View My Hires
              </Link>
            </div>
          </AlertBanner>
        )}

        {messages.length === 0 ? (
          <div className="pt-4 text-center space-y-3">
            <p className="text-sm font-medium text-slate-500">
              No messages yet. Say hello to start coordinating care.
            </p>
            {isCustomer && (
              <div className="mx-auto flex max-w-md flex-col gap-2">
                {CUSTOMER_QUICK_PROMPTS.map((prompt) => (
                  <button
                    key={prompt}
                    type="button"
                    onClick={() => setDraft(prompt)}
                    className="min-h-[44px] rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-left text-xs font-medium text-slate-700 transition-colors hover:border-teal-400 hover:bg-teal-50/50"
                  >
                    &ldquo;{prompt}&rdquo;
                  </button>
                ))}
              </div>
            )}
          </div>
        ) : (
          messages.map((msg) => {
            const isOwn = msg.senderId === user?.id;
            return (
              <div
                key={msg.id}
                className={`flex flex-col ${isOwn ? 'items-end' : 'items-start'}`}
              >
                <div
                  className={`max-w-[85%] rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed shadow-2xs sm:max-w-[75%] ${
                    isOwn
                      ? 'rounded-br-md bg-teal-700 text-white'
                      : 'rounded-bl-md border border-slate-200 bg-white text-slate-900'
                  }`}
                >
                  {!isOwn && (
                    <div className="mb-0.5 text-[11px] font-bold text-teal-800">
                      {msg.senderName}
                    </div>
                  )}
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

      {/* Composer */}
      <div className="border-t border-slate-200 bg-white p-2.5 sm:p-3 space-y-2">
        {sendError && (
          <AlertBanner variant="error">{sendError}</AlertBanner>
        )}

        <form onSubmit={handleSendMessage} className="flex items-end gap-2">
          <div className="relative flex-1">
            <textarea
              rows={1}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={handleKeyDown}
              maxLength={MAX_MESSAGE_LENGTH}
              placeholder="Write a message about hospital ward, shift timing, or BDT rate..."
              aria-label="Negotiation chat message"
              className="block max-h-32 min-h-[44px] w-full resize-none rounded-xl border border-slate-300 bg-slate-50 px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-600 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-100"
            />
            {draft.length > 1600 && (
              <span className="mt-1 block text-right text-[11px] text-slate-500">
                {draft.length}/{MAX_MESSAGE_LENGTH}
              </span>
            )}
          </div>

          <Button
            type="submit"
            disabled={!draft.trim() || sending}
            loading={sending}
            aria-label="Send message"
            className="shrink-0 px-4"
          >
            <Send className="h-4 w-4" aria-hidden="true" />
            <span className="hidden sm:inline">Send</span>
          </Button>
        </form>
      </div>

      {showHireModal && isCustomer && (
        <HireRequestModal
          caretakerUserId={room.caretakerUserId}
          caretakerName={room.counterpartName}
          defaultRateAmount={room.counterpartRateAmount}
          defaultRateType={room.counterpartRateType}
          onClose={() => setShowHireModal(false)}
          onSuccess={(created) => {
            setShowHireModal(false);
            setHireSuccess(created);
          }}
        />
      )}
    </div>
  );
}
