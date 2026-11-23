import {
  ArrowLeft,
  Award,
  Check,
  CheckCircle2,
  Copy,
  Lock,
  MapPin,
  MessageSquare,
  Phone,
  ShieldCheck,
  Star,
  ThumbsUp,
  Unlock,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as contactAndChatApi from '../../api/contactAndChatApi';
import * as feedAndProfileApi from '../../api/feedAndProfileApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { CaretakerAvatar } from '../../components/ui/CaretakerAvatar';
import { useAuth } from '../../context/useAuth';
import { useWebSocket } from '../../context/useWebSocket';

function formatRate(rateAmount, rateType) {
  if (rateAmount == null) return 'Rate negotiable';
  const formatted = Number(rateAmount).toLocaleString('en-BD');
  return rateType === 'HOURLY' ? `৳${formatted} / hour` : `৳${formatted} / day`;
}

export function CaretakerPublicProfilePage() {
  const { caretakerUserId } = useParams();
  const { user } = useAuth();
  const { subscribe } = useWebSocket();

  const isCustomer = user?.role === 'CUSTOMER';

  const [detail, setDetail] = useState(null);
  const [unlockedContact, setUnlockedContact] = useState(null);
  const [loading, setLoading] = useState(true);
  const [unlocking, setUnlocking] = useState(false);
  const [error, setError] = useState('');
  const [unlockError, setUnlockError] = useState('');
  const [copiedPhone, setCopiedPhone] = useState(false);

  const loadDetail = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await feedAndProfileApi.fetchCaretakerPublicDetail(caretakerUserId);
      setDetail(data);

      if (isCustomer && data.contactUnlocked) {
        try {
          const contact = await contactAndChatApi.fetchUnlockedCaretakerContact(caretakerUserId);
          setUnlockedContact(contact);
        } catch {
          setUnlockedContact(null);
        }
      } else {
        setUnlockedContact(null);
      }
    } catch (err) {
      setError(err.message || 'Could not load caretaker profile.');
    } finally {
      setLoading(false);
    }
  }, [caretakerUserId, isCustomer]);

  useEffect(() => {
    loadDetail();
  }, [loadDetail]);

  // Live availability & rate updates while viewing this profile
  useEffect(() => {
    if (!detail?.caretakerUserId) {
      return undefined;
    }
    return subscribe('/topic/feed/availability', (event) => {
      if (event?.caretakerUserId === detail.caretakerUserId) {
        setDetail((prev) =>
          prev
            ? {
                ...prev,
                available: event.available,
                rateAmount: event.rateAmount,
                rateType: event.rateType,
                locationArea: event.locationArea,
              }
            : prev
        );
      }
    });
  }, [detail?.caretakerUserId, subscribe]);

  async function handleUnlockContact() {
    if (!detail || unlocking) {
      return;
    }
    setUnlocking(true);
    setUnlockError('');
    try {
      const revealed = await contactAndChatApi.unlockCaretakerContact(detail.caretakerUserId);
      setUnlockedContact(revealed);
      setDetail((prev) => (prev ? { ...prev, contactUnlocked: true } : prev));
    } catch (err) {
      setUnlockError(err.message || 'Could not unlock contact details right now. Please try again.');
    } finally {
      setUnlocking(false);
    }
  }

  async function handleCopyPhone() {
    if (!unlockedContact?.caretakerMobileNumber || !navigator.clipboard) {
      return;
    }
    try {
      await navigator.clipboard.writeText(unlockedContact.caretakerMobileNumber);
      setCopiedPhone(true);
      setTimeout(() => setCopiedPhone(false), 2000);
    } catch {
      // Clipboard API may be unavailable on non-HTTPS origins; user can still read/tap tel: link
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <Link
        to="/customer/feed"
        className="inline-flex min-h-[44px] items-center gap-1.5 rounded-xl px-2 text-sm font-semibold text-slate-700 hover:bg-slate-200/70"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        <span>Back to Hunters Feed</span>
      </Link>

      {loading && !detail ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">
          Loading verified caretaker profile...
        </div>
      ) : error ? (
        <AlertBanner variant="error" title="Profile unavailable">
          <div className="space-y-2">
            <p>{error}</p>
            <Button variant="outline" onClick={loadDetail}>
              Retry
            </Button>
          </div>
        </AlertBanner>
      ) : detail ? (
        <>
          <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
            <div className="flex items-start gap-4">
              <CaretakerAvatar
                src={detail.profileImageUrl}
                name={detail.fullName}
                size="lg"
                showDot
                available={detail.available}
              />

              <div className="min-w-0 flex-1 space-y-1.5">
                <div className="flex flex-wrap items-center gap-2">
                  <h1 className="text-xl font-bold text-slate-900">{detail.fullName}</h1>
                  <span className="inline-flex items-center gap-1 rounded-full bg-teal-100 px-2.5 py-0.5 text-xs font-bold text-teal-800">
                    <ShieldCheck className="h-3.5 w-3.5" />
                    Verified
                  </span>
                </div>

                <p className="text-sm font-semibold text-teal-800">
                  {detail.primaryQualification}
                </p>

                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-600">
                  <span className="inline-flex items-center gap-1">
                    <MapPin className="h-3.5 w-3.5 text-slate-400" />
                    {detail.locationArea}
                  </span>
                  <span
                    className={`inline-flex items-center gap-1 font-bold ${
                      detail.available ? 'text-emerald-700' : 'text-slate-500'
                    }`}
                  >
                    <CheckCircle2 className="h-3.5 w-3.5" />
                    {detail.available ? 'Available Now' : 'Currently Unavailable'}
                  </span>
                </div>

                <div className="pt-1 text-lg font-extrabold text-slate-900">
                  {formatRate(detail.rateAmount, detail.rateType)}
                </div>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2.5 border-t border-slate-100 pt-4 text-center">
              <div className="rounded-xl bg-amber-50/80 p-3">
                <div className="flex items-center justify-center gap-1 text-base font-bold text-amber-950">
                  <Star className="h-4 w-4 fill-amber-400 text-amber-400" />
                  <span>
                    {detail.completedHiresCount > 0 ? Number(detail.avgRating).toFixed(1) : 'New'}
                  </span>
                </div>
                <div className="text-[11px] font-medium text-amber-900">Star Rating</div>
              </div>

              <div className="rounded-xl bg-slate-50 p-3">
                <div className="text-base font-bold text-slate-900">
                  {detail.completedHiresCount}
                </div>
                <div className="text-[11px] font-medium text-slate-600">Completed Hires</div>
              </div>

              <div className="rounded-xl bg-teal-50/80 p-3">
                <div className="flex items-center justify-center gap-1 text-base font-bold text-teal-950">
                  <ThumbsUp className="h-3.5 w-3.5" />
                  <span>
                    {detail.completedHiresCount > 0
                      ? `${Math.round(Number(detail.hireAgainPercentage))}%`
                      : '—'}
                  </span>
                </div>
                <div className="text-[11px] font-medium text-teal-900">Would Hire Again</div>
              </div>
            </div>
          </section>

          <section className="space-y-2 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
            <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">
              About Caretaker
            </h2>
            <p className="whitespace-pre-line text-sm leading-relaxed text-slate-700">
              {detail.bio ||
                'This verified caretaker has not added a detailed bio yet. Unlock contact details below to discuss patient care needs directly.'}
            </p>
          </section>

          <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
            <div className="flex items-center gap-2">
              <Award className="h-4 w-4 text-teal-700" aria-hidden="true" />
              <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">
                Admin-Verified Qualifications
              </h2>
            </div>

            <div className="space-y-2">
              <div className="flex items-center gap-2.5 rounded-xl border border-emerald-200 bg-emerald-50/60 p-3 text-sm">
                <ShieldCheck className="h-4 w-4 shrink-0 text-emerald-700" />
                <div>
                  <div className="font-bold text-slate-900">{detail.primaryQualification}</div>
                  <div className="text-xs text-emerald-800">Primary verified qualification</div>
                </div>
              </div>

              {detail.verifiedCertifications?.map((cert) => (
                <div
                  key={cert.id}
                  className="flex items-center gap-2.5 rounded-xl border border-slate-200 bg-slate-50/70 p-3 text-sm"
                >
                  <Award className="h-4 w-4 shrink-0 text-teal-700" />
                  <div>
                    <div className="font-bold text-slate-900">{cert.title}</div>
                    <div className="text-xs text-slate-600">{cert.issuingOrganization}</div>
                  </div>
                </div>
              ))}
            </div>
          </section>

          {isCustomer && (
            <section className="space-y-3.5 rounded-2xl border border-teal-200 bg-teal-50/70 p-5 shadow-xs">
              {unlockedContact ? (
                <>
                  <div className="flex items-start gap-3">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-700 text-white">
                      <Unlock className="h-5 w-5" aria-hidden="true" />
                    </div>
                    <div className="space-y-0.5">
                      <h2 className="text-sm font-bold text-emerald-950">
                        Contact &amp; Live Negotiation Unlocked
                      </h2>
                      <p className="text-xs leading-relaxed text-emerald-900">
                        Call directly or use live negotiation chat to align on hospital ward, shift
                        timing, and BDT rate.
                      </p>
                    </div>
                  </div>

                  <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-emerald-200 bg-white px-4 py-3">
                    <div>
                      <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                        Caretaker Mobile Number
                      </div>
                      <div className="text-base font-extrabold tracking-wide text-slate-900">
                        {unlockedContact.caretakerMobileNumber}
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={handleCopyPhone}
                      className="inline-flex min-h-[44px] items-center gap-1.5 rounded-xl border border-slate-200 px-3 text-xs font-semibold text-slate-700 hover:bg-slate-100"
                    >
                      {copiedPhone ? (
                        <>
                          <Check className="h-4 w-4 text-emerald-700" aria-hidden="true" />
                          <span>Copied</span>
                        </>
                      ) : (
                        <>
                          <Copy className="h-4 w-4" aria-hidden="true" />
                          <span>Copy</span>
                        </>
                      )}
                    </button>
                  </div>

                  <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
                    <a
                      href={`tel:${unlockedContact.caretakerMobileNumber}`}
                      className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-xl border border-teal-700 bg-white px-4 py-2.5 text-sm font-semibold text-teal-800 transition-colors hover:bg-teal-50"
                    >
                      <Phone className="h-4 w-4" aria-hidden="true" />
                      <span>Call Now</span>
                    </a>

                    <Link
                      to={`/customer/chats/${encodeURIComponent(unlockedContact.roomId)}`}
                      className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-xl bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white shadow-xs transition-colors hover:bg-teal-800"
                    >
                      <MessageSquare className="h-4 w-4" aria-hidden="true" />
                      <span>Open Live Negotiation Chat</span>
                    </Link>
                  </div>
                </>
              ) : (
                <>
                  <div className="flex items-start gap-3">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-teal-700 text-white">
                      <Lock className="h-5 w-5" aria-hidden="true" />
                    </div>
                    <div className="space-y-1">
                      <h2 className="text-sm font-bold text-teal-950">
                        Privacy-Protected Contact &amp; Live Negotiation
                      </h2>
                      <p className="text-xs leading-relaxed text-teal-900">
                        To protect caretakers from spam, phone numbers stay hidden until you tap{' '}
                        <strong>Contact</strong>. Unlocking reveals their mobile number with one-tap
                        Call and opens a private real-time negotiation chat.
                      </p>
                    </div>
                  </div>

                  {unlockError && <AlertBanner variant="error">{unlockError}</AlertBanner>}

                  <Button fullWidth onClick={handleUnlockContact} loading={unlocking}>
                    <MessageSquare className="h-4 w-4" aria-hidden="true" />
                    <span>Contact &amp; Unlock Phone Number</span>
                  </Button>
                </>
              )}
            </section>
          )}
        </>
      ) : null}
    </div>
  );
}
