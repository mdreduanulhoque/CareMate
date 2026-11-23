import {
  Award,
  CheckCircle2,
  ChevronRight,
  MapPin,
  RefreshCw,
  RotateCcw,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Star,
  ThumbsUp,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as feedAndProfileApi from '../../api/feedAndProfileApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { CaretakerAvatar } from '../../components/ui/CaretakerAvatar';
import { useWebSocket } from '../../context/useWebSocket';

const DEFAULT_FILTERS = {
  availableOnly: true,
  rateType: '',
  maxBudget: '',
  area: '',
  minRating: '',
  keyword: '',
  sort: 'rating',
  page: 0,
  size: 12,
};

function formatRate(rateAmount, rateType) {
  if (rateAmount == null) {
    return 'Rate negotiable';
  }
  const formatted = Number(rateAmount).toLocaleString('en-BD');
  return rateType === 'HOURLY' ? `৳${formatted}/hr` : `৳${formatted}/day`;
}

export function HuntersFeedPage() {
  const { isConnected, subscribe } = useWebSocket();

  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [searchInput, setSearchInput] = useState('');
  const [showMoreFilters, setShowMoreFilters] = useState(false);

  const [feedPage, setFeedPage] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [hasNewLiveUpdates, setHasNewLiveUpdates] = useState(false);

  const loadFeed = useCallback(async (activeFilters) => {
    setLoading(true);
    setError('');
    setHasNewLiveUpdates(false);
    try {
      const data = await feedAndProfileApi.fetchHuntersFeed(activeFilters);
      setFeedPage(data);
    } catch (err) {
      setError(err.message || 'Could not load verified caretakers right now.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadFeed(filters);
  }, [filters, loadFeed]);

  // Real-time availability & rate updates pushed over STOMP /topic/feed/availability
  useEffect(() => {
    return subscribe('/topic/feed/availability', (event) => {
      if (!event?.caretakerUserId) {
        return;
      }
      setFeedPage((prev) => {
        if (!prev?.content) {
          return prev;
        }
        let foundInList = false;
        const updatedContent = prev.content.map((card) => {
          if (card.caretakerUserId !== event.caretakerUserId) {
            return card;
          }
          foundInList = true;
          return {
            ...card,
            available: event.available,
            rateAmount: event.rateAmount,
            rateType: event.rateType,
            locationArea: event.locationArea,
            todayBookingsCount: event.todayBookingsCount ?? card.todayBookingsCount,
            dailyCapReachedToday: event.dailyCapReachedToday ?? card.dailyCapReachedToday,
          };
        });

        if (!foundInList && event.available) {
          setHasNewLiveUpdates(true);
        }

        return foundInList ? { ...prev, content: updatedContent } : prev;
      });
    });
  }, [subscribe]);

  function applyFilterPatch(patch) {
    setFilters((prev) => ({ ...prev, ...patch, page: 0 }));
  }

  function handleSearchSubmit(event) {
    event.preventDefault();
    applyFilterPatch({ keyword: searchInput });
  }

  function handleResetFilters() {
    setSearchInput('');
    setFilters(DEFAULT_FILTERS);
  }

  const advancedCount = [
    filters.maxBudget,
    filters.area,
    filters.minRating,
    filters.sort !== 'rating' ? filters.sort : '',
  ].filter(Boolean).length;

  const cards = feedPage?.content || [];

  return (
    <div className="space-y-4">
      <section className="sticky top-[68px] z-20 -mx-4 border-b border-slate-200 bg-slate-50/95 px-4 pb-3 pt-1 backdrop-blur sm:static sm:mx-0 sm:rounded-2xl sm:border sm:bg-white sm:p-4 sm:shadow-xs">
        <form onSubmit={handleSearchSubmit} className="flex gap-2">
          <div className="relative flex-1">
            <Search
              className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
              aria-hidden="true"
            />
            <input
              type="search"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Search nurse, ICU, elderly care, area..."
              aria-label="Search verified caretakers"
              className="block min-h-[44px] w-full rounded-xl border border-slate-300 bg-white pl-10 pr-3.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-100"
            />
          </div>
          <Button type="submit" className="shrink-0">
            Search
          </Button>
        </form>

        <div className="no-scrollbar mt-2.5 flex items-center gap-2 overflow-x-auto pb-0.5">
          <button
            type="button"
            onClick={() => applyFilterPatch({ availableOnly: !filters.availableOnly })}
            className={`inline-flex min-h-[40px] shrink-0 items-center gap-1.5 rounded-full border px-3.5 text-xs font-semibold transition-colors ${
              filters.availableOnly
                ? 'border-emerald-700 bg-emerald-700 text-white'
                : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
            }`}
          >
            <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
            <span>Available Now</span>
          </button>

          {[
            { key: '', label: 'All Rates' },
            { key: 'HOURLY', label: 'Hourly' },
            { key: 'DAILY', label: 'Daily' },
          ].map((chip) => (
            <button
              key={chip.key || 'ALL'}
              type="button"
              onClick={() => applyFilterPatch({ rateType: chip.key })}
              className={`min-h-[40px] shrink-0 rounded-full border px-3.5 text-xs font-semibold transition-colors ${
                filters.rateType === chip.key
                  ? 'border-teal-700 bg-teal-700 text-white'
                  : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
              }`}
            >
              {chip.label}
            </button>
          ))}

          <button
            type="button"
            onClick={() => setShowMoreFilters((prev) => !prev)}
            className={`inline-flex min-h-[40px] shrink-0 items-center gap-1.5 rounded-full border px-3.5 text-xs font-semibold transition-colors ${
              showMoreFilters || advancedCount > 0
                ? 'border-slate-900 bg-slate-900 text-white'
                : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
            }`}
          >
            <SlidersHorizontal className="h-3.5 w-3.5" aria-hidden="true" />
            <span>Filters{advancedCount > 0 ? ` (${advancedCount})` : ''}</span>
          </button>
        </div>

        {showMoreFilters && (
          <div className="mt-3 grid gap-3 rounded-xl border border-slate-200 bg-slate-50 p-3.5 sm:grid-cols-2 lg:grid-cols-4">
            <div className="space-y-1">
              <label htmlFor="filter-budget" className="text-xs font-semibold text-slate-700">
                Max Budget (৳ BDT)
              </label>
              <input
                id="filter-budget"
                type="number"
                min="50"
                step="50"
                placeholder="e.g., 2500"
                value={filters.maxBudget}
                onChange={(e) => applyFilterPatch({ maxBudget: e.target.value })}
                className="min-h-[44px] w-full rounded-xl border border-slate-300 bg-white px-3 text-sm focus:border-teal-600 focus:outline-none"
              />
            </div>

            <div className="space-y-1">
              <label htmlFor="filter-area" className="text-xs font-semibold text-slate-700">
                Hospital Area / Location
              </label>
              <input
                id="filter-area"
                type="text"
                placeholder="e.g., Dhanmondi, Uttara"
                value={filters.area}
                onChange={(e) => applyFilterPatch({ area: e.target.value })}
                className="min-h-[44px] w-full rounded-xl border border-slate-300 bg-white px-3 text-sm focus:border-teal-600 focus:outline-none"
              />
            </div>

            <div className="space-y-1">
              <label htmlFor="filter-rating" className="text-xs font-semibold text-slate-700">
                Minimum Rating
              </label>
              <select
                id="filter-rating"
                value={filters.minRating}
                onChange={(e) => applyFilterPatch({ minRating: e.target.value })}
                className="min-h-[44px] w-full rounded-xl border border-slate-300 bg-white px-3 text-sm focus:border-teal-600 focus:outline-none"
              >
                <option value="">Any rating</option>
                <option value="3">3.0+ stars</option>
                <option value="4">4.0+ stars</option>
                <option value="4.5">4.5+ stars</option>
              </select>
            </div>

            <div className="space-y-1">
              <label htmlFor="filter-sort" className="text-xs font-semibold text-slate-700">
                Sort By
              </label>
              <select
                id="filter-sort"
                value={filters.sort}
                onChange={(e) => applyFilterPatch({ sort: e.target.value })}
                className="min-h-[44px] w-full rounded-xl border border-slate-300 bg-white px-3 text-sm focus:border-teal-600 focus:outline-none"
              >
                <option value="rating">Top Rated</option>
                <option value="rate_asc">Lowest BDT Rate</option>
                <option value="rate_desc">Highest BDT Rate</option>
                <option value="newest">Newly Verified</option>
              </select>
            </div>

            <div className="sm:col-span-2 lg:col-span-4 flex justify-end">
              <button
                type="button"
                onClick={handleResetFilters}
                className="inline-flex min-h-[40px] items-center gap-1.5 rounded-lg px-3 text-xs font-semibold text-slate-600 hover:bg-slate-200/70 hover:text-slate-900"
              >
                <RotateCcw className="h-3.5 w-3.5" />
                <span>Reset Filters</span>
              </button>
            </div>
          </div>
        )}
      </section>

      <div className="flex flex-wrap items-center justify-between gap-2 text-xs font-medium text-slate-600">
        <span>
          {loading
            ? 'Finding verified caretakers...'
            : `${feedPage?.totalElements ?? 0} verified caretaker${
                feedPage?.totalElements === 1 ? '' : 's'
              } found`}
        </span>
        <div className="flex items-center gap-2.5">
          {isConnected && (
            <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-semibold text-emerald-800 border border-emerald-200">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
              Live Availability
            </span>
          )}
          <span className="inline-flex items-center gap-1 text-teal-800">
            <ShieldCheck className="h-3.5 w-3.5" />
            <span>Admin-verified only</span>
          </span>
        </div>
      </div>

      {hasNewLiveUpdates && (
        <button
          type="button"
          onClick={() => loadFeed(filters)}
          className="flex min-h-[44px] w-full items-center justify-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 text-xs font-bold text-emerald-900 transition-colors hover:bg-emerald-100"
        >
          <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
          <span>New verified caretaker just became available — tap to refresh feed</span>
        </button>
      )}

      {error && (
        <AlertBanner variant="error" title="Could not load Hunters Feed">
          <div className="space-y-2">
            <p>{error}</p>
            <Button variant="outline" onClick={() => loadFeed(filters)}>
              Try Again
            </Button>
          </div>
        </AlertBanner>
      )}

      {loading && cards.length === 0 ? (
        <div className="grid gap-3.5 lg:grid-cols-2">
          {[1, 2, 3, 4].map((skeleton) => (
            <div
              key={skeleton}
              className="h-44 animate-pulse rounded-2xl border border-slate-200 bg-white p-4"
            >
              <div className="flex gap-3.5">
                <div className="h-16 w-16 rounded-2xl bg-slate-200" />
                <div className="flex-1 space-y-2.5 py-1">
                  <div className="h-4 w-2/3 rounded bg-slate-200" />
                  <div className="h-3 w-1/2 rounded bg-slate-100" />
                  <div className="h-3 w-1/3 rounded bg-slate-100" />
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : cards.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center space-y-3">
          <p className="text-base font-bold text-slate-900">No matching caretakers right now</p>
          <p className="text-sm text-slate-600">
            Try turning off &ldquo;Available Now&rdquo;, increasing your BDT budget, or clearing
            area filters.
          </p>
          <div className="flex flex-wrap justify-center gap-2 pt-1">
            {filters.availableOnly && (
              <Button
                variant="outline"
                onClick={() => applyFilterPatch({ availableOnly: false })}
              >
                Show Unavailable Too
              </Button>
            )}
            <Button variant="primary" onClick={handleResetFilters}>
              Reset All Filters
            </Button>
          </div>
        </div>
      ) : (
        <div className="grid gap-3.5 lg:grid-cols-2">
          {cards.map((caretaker) => (
            <Link
              key={caretaker.profileId}
              to={`/customer/caretakers/${caretaker.caretakerUserId}`}
              className="group block rounded-2xl border border-slate-200 bg-white p-4 shadow-xs transition-all hover:border-teal-400 hover:shadow-md"
            >
              <div className="flex items-start gap-3.5">
                <CaretakerAvatar
                  src={caretaker.profileImageUrl}
                  name={caretaker.fullName}
                  size="md"
                  showDot
                  available={caretaker.available}
                />

                <div className="min-w-0 flex-1 space-y-1">
                  <div className="flex items-start justify-between gap-2">
                    <h2 className="truncate text-base font-bold text-slate-900 group-hover:text-teal-800">
                      {caretaker.fullName}
                    </h2>
                    <span
                      className={`shrink-0 rounded-full px-2.5 py-0.5 text-[11px] font-bold ${
                        !caretaker.available
                          ? 'bg-slate-100 text-slate-600'
                          : caretaker.dailyCapReachedToday
                            ? 'bg-amber-100 text-amber-900'
                            : 'bg-emerald-100 text-emerald-800'
                      }`}
                    >
                      {!caretaker.available
                        ? 'Unavailable'
                        : caretaker.dailyCapReachedToday
                          ? '2/2 Booked Today'
                          : 'Available'}
                    </span>
                  </div>

                  <p className="truncate text-sm font-medium text-teal-800">
                    {caretaker.primaryQualification}
                  </p>

                  <p className="inline-flex items-center gap-1 text-xs text-slate-600">
                    <MapPin className="h-3.5 w-3.5 shrink-0 text-slate-400" aria-hidden="true" />
                    <span className="truncate">{caretaker.locationArea}</span>
                  </p>
                </div>
              </div>

              <div className="mt-3.5 flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 pt-3">
                <div className="flex flex-wrap items-center gap-2 text-xs">
                  <span className="inline-flex items-center gap-1 rounded-lg bg-amber-50 px-2 py-1 font-bold text-amber-900">
                    <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" />
                    {caretaker.completedHiresCount > 0
                      ? Number(caretaker.avgRating).toFixed(1)
                      : 'New'}
                  </span>

                  <span className="inline-flex items-center gap-1 rounded-lg bg-teal-50 px-2 py-1 font-semibold text-teal-900">
                    <ThumbsUp className="h-3 w-3" />
                    {caretaker.completedHiresCount > 0
                      ? `${Math.round(Number(caretaker.hireAgainPercentage))}% Hire Again`
                      : 'Verified'}
                  </span>

                  {caretaker.verifiedCertificationsCount > 0 && (
                    <span className="inline-flex items-center gap-1 rounded-lg bg-sky-50 px-2 py-1 font-semibold text-sky-900">
                      <Award className="h-3 w-3" />
                      +{caretaker.verifiedCertificationsCount} Cert
                      {caretaker.verifiedCertificationsCount > 1 ? 's' : ''}
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-1 text-sm font-bold text-slate-900">
                  <span>{formatRate(caretaker.rateAmount, caretaker.rateType)}</span>
                  <ChevronRight className="h-4 w-4 text-slate-400 group-hover:translate-x-0.5 group-hover:text-teal-700" />
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}

      {feedPage && feedPage.totalPages > 1 && (
        <div className="flex items-center justify-between gap-3 pt-2">
          <Button
            variant="outline"
            disabled={filters.page === 0}
            onClick={() => setFilters((prev) => ({ ...prev, page: Math.max(0, prev.page - 1) }))}
          >
            Previous
          </Button>
          <span className="text-xs font-semibold text-slate-600">
            Page {feedPage.page + 1} of {feedPage.totalPages}
          </span>
          <Button
            variant="outline"
            disabled={!feedPage.hasNext}
            onClick={() => setFilters((prev) => ({ ...prev, page: prev.page + 1 }))}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  );
}
