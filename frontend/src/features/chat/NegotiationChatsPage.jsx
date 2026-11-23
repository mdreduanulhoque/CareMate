import {
  ChevronRight,
  MapPin,
  MessageSquare,
  Phone,
  RefreshCw,
  Search,
  ShieldAlert,
  User,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as contactAndChatApi from '../../api/contactAndChatApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { CaretakerAvatar } from '../../components/ui/CaretakerAvatar';
import { useAuth } from '../../context/useAuth';
import { useWebSocket } from '../../context/useWebSocket';
import { formatDhakaChatTime } from '../../utils/dhakaTime';

function formatRateCompact(rateAmount, rateType) {
  if (rateAmount == null) return 'Negotiable';
  const formatted = Number(rateAmount).toLocaleString('en-BD');
  return rateType === 'HOURLY' ? `৳${formatted}/hr` : `৳${formatted}/day`;
}

export function NegotiationChatsPage() {
  const { user } = useAuth();
  const { isConnected, subscribe } = useWebSocket();

  const isCustomer = user?.role === 'CUSTOMER';
  const isUnverifiedCaretaker = user?.role === 'CARETAKER' && !user?.caretakerApproved;
  const baseChatPath = isCustomer ? '/customer/chats' : '/caretaker/chats';

  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(!isUnverifiedCaretaker);
  const [error, setError] = useState('');

  const loadRooms = useCallback(
    async ({ silent = false } = {}) => {
      if (isUnverifiedCaretaker) {
        setLoading(false);
        return;
      }
      if (!silent) {
        setLoading(true);
      }
      setError('');
      try {
        const data = await contactAndChatApi.fetchNegotiationRooms();
        setRooms(data);
      } catch (err) {
        setError(err.message || 'Could not load your negotiation chats right now.');
      } finally {
        setLoading(false);
      }
    },
    [isUnverifiedCaretaker]
  );

  useEffect(() => {
    loadRooms();
  }, [loadRooms]);

  // Refresh inbox live whenever a new message or new contact unlock notification arrives
  useEffect(() => {
    if (isUnverifiedCaretaker) {
      return undefined;
    }
    return subscribe('/user/queue/notifications', (event) => {
      if (event?.eventType === 'NEW_CHAT_MESSAGE' || event?.eventType === 'CONTACT_UNLOCKED') {
        loadRooms({ silent: true });
      }
    });
  }, [isUnverifiedCaretaker, subscribe, loadRooms]);

  // Keep caretaker availability & rates updated live in Customer's chats inbox
  useEffect(() => {
    if (!isCustomer) {
      return undefined;
    }
    return subscribe('/topic/feed/availability', (event) => {
      if (!event?.caretakerUserId) {
        return;
      }
      setRooms((prev) =>
        prev.map((room) =>
          room.caretakerUserId === event.caretakerUserId
            ? {
                ...room,
                counterpartAvailable: event.available,
                counterpartRateAmount: event.rateAmount,
                counterpartRateType: event.rateType,
                counterpartLocationArea: event.locationArea,
              }
            : room
        )
      );
    });
  }, [isCustomer, subscribe]);

  if (isUnverifiedCaretaker) {
    return (
      <div className="mx-auto max-w-2xl space-y-4">
        <div className="rounded-2xl border border-amber-200 bg-amber-50/70 p-6 space-y-3">
          <div className="flex items-start gap-3">
            <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-amber-700" aria-hidden="true" />
            <div className="space-y-1">
              <h1 className="text-lg font-bold text-amber-950">Verification Required for Chats</h1>
              <p className="text-sm leading-relaxed text-amber-900">
                Once an Admin approves your caretaker verification application, relatives will be
                able to discover your profile, unlock your contact, and start live negotiation chats
                here.
              </p>
            </div>
          </div>
          <Link to="/caretaker/requests" className="block">
            <Button fullWidth>Go to Verification Status</Button>
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold tracking-tight text-slate-900">Negotiation Chats</h1>
            {isConnected && (
              <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-0.5 text-[11px] font-bold text-emerald-800 border border-emerald-200">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                Live
              </span>
            )}
          </div>
          <p className="text-sm text-slate-600">
            {isCustomer
              ? 'Discuss patient condition, hospital ward, shift timing, and agreed BDT rate.'
              : 'Respond promptly to relatives looking for verified bedside hospital care.'}
          </p>
        </div>

        <Button variant="outline" onClick={() => loadRooms()} disabled={loading}>
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
          <span>Refresh</span>
        </Button>
      </div>

      {error && (
        <AlertBanner variant="error" title="Unable to load chats">
          <div className="space-y-2">
            <p>{error}</p>
            <Button variant="outline" onClick={() => loadRooms()}>
              Try Again
            </Button>
          </div>
        </AlertBanner>
      )}

      {loading && rooms.length === 0 ? (
        <div className="space-y-3">
          {[1, 2, 3].map((item) => (
            <div
              key={item}
              className="h-24 animate-pulse rounded-2xl border border-slate-200 bg-white p-4"
            >
              <div className="flex items-center gap-3.5">
                <div className="h-12 w-12 rounded-2xl bg-slate-200" />
                <div className="flex-1 space-y-2">
                  <div className="h-4 w-1/2 rounded bg-slate-200" />
                  <div className="h-3 w-3/4 rounded bg-slate-100" />
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : rooms.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center space-y-3">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-teal-50 text-teal-700">
            <MessageSquare className="h-6 w-6" aria-hidden="true" />
          </div>
          <h2 className="text-base font-bold text-slate-900">No negotiation chats yet</h2>
          <p className="mx-auto max-w-md text-sm text-slate-600">
            {isCustomer
              ? 'Browse verified caretakers on the Hunters Feed and tap Contact on any profile to reveal their phone number and open a live negotiation chat.'
              : 'When a patient’s relative unlocks your contact from the Hunters Feed, your live conversation will appear here immediately.'}
          </p>
          {isCustomer && (
            <div className="pt-1">
              <Link to="/customer/feed" className="inline-block">
                <Button>
                  <Search className="h-4 w-4" aria-hidden="true" />
                  <span>Browse Hunters Feed</span>
                </Button>
              </Link>
            </div>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          {rooms.map((room) => {
            const roomUrl = `${baseChatPath}/${encodeURIComponent(room.roomId)}`;
            const activityTime = formatDhakaChatTime(room.lastMessageAt || room.unlockedAt);
            const lastPreview = room.lastMessageContent
              ? `${room.lastMessageSenderId === user?.id ? 'You: ' : ''}${room.lastMessageContent}`
              : 'Contact unlocked — tap to start negotiating shift terms';

            return (
              <div
                key={room.roomId}
                className="flex items-center gap-2 rounded-2xl border border-slate-200 bg-white p-3.5 shadow-xs transition-colors hover:border-teal-300"
              >
                <Link
                  to={roomUrl}
                  className="flex min-w-0 flex-1 items-center gap-3.5 rounded-xl p-1"
                >
                  {isCustomer ? (
                    <CaretakerAvatar
                      src={room.counterpartAvatarUrl}
                      name={room.counterpartName}
                      size="sm"
                      showDot
                      available={Boolean(room.counterpartAvailable)}
                    />
                  ) : (
                    <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-teal-700 text-base font-bold text-white">
                      {room.counterpartName?.slice(0, 1)?.toUpperCase() || (
                        <User className="h-5 w-5" />
                      )}
                    </div>
                  )}

                  <div className="min-w-0 flex-1 space-y-1">
                    <div className="flex items-center justify-between gap-2">
                      <h2 className="truncate text-sm font-bold text-slate-900">
                        {room.counterpartName}
                      </h2>
                      <span className="shrink-0 text-[11px] font-medium text-slate-500">
                        {activityTime}
                      </span>
                    </div>

                    <div className="flex flex-wrap items-center gap-x-2.5 gap-y-0.5 text-xs text-slate-500">
                      {isCustomer && room.counterpartQualification && (
                        <span className="truncate font-semibold text-teal-800">
                          {room.counterpartQualification}
                        </span>
                      )}
                      {room.counterpartLocationArea && (
                        <span className="inline-flex items-center gap-0.5 truncate">
                          <MapPin className="h-3 w-3 shrink-0" aria-hidden="true" />
                          <span className="truncate">{room.counterpartLocationArea}</span>
                        </span>
                      )}
                      {isCustomer && room.counterpartRateAmount != null && (
                        <span className="font-semibold text-slate-700">
                          {formatRateCompact(room.counterpartRateAmount, room.counterpartRateType)}
                        </span>
                      )}
                    </div>

                    <p className="truncate text-xs text-slate-600">{lastPreview}</p>
                  </div>

                  <ChevronRight className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
                </Link>

                {room.counterpartMobileNumber && (
                  <a
                    href={`tel:${room.counterpartMobileNumber}`}
                    aria-label={`Call ${room.counterpartName}`}
                    title={`Call ${room.counterpartMobileNumber}`}
                    className="inline-flex min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-xl border border-teal-200 bg-teal-50 text-teal-800 transition-colors hover:bg-teal-100"
                  >
                    <Phone className="h-4 w-4" aria-hidden="true" />
                  </a>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
