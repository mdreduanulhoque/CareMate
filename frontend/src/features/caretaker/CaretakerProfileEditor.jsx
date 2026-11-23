import { Camera, CheckCircle2, Star, ThumbsUp, Upload } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import * as feedAndProfileApi from '../../api/feedAndProfileApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { CaretakerAvatar } from '../../components/ui/CaretakerAvatar';
import { FormField } from '../../components/ui/FormField';

const MAX_AVATAR_BYTES = 2 * 1024 * 1024;
const ALLOWED_MIME = ['image/jpeg', 'image/png', 'image/webp'];

export function CaretakerProfileEditor() {
  const fileInputRef = useRef(null);

  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [form, setForm] = useState({
    locationArea: '',
    rateAmount: '',
    rateType: 'DAILY',
    bio: '',
    available: true,
  });

  const [saving, setSaving] = useState(false);
  const [toggling, setToggling] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [successMessage, setSuccessMessage] = useState('');

  const loadProfile = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const data = await feedAndProfileApi.fetchMyCaretakerProfile();
      setProfile(data);
      setForm({
        locationArea: data.locationArea || '',
        rateAmount: data.rateAmount != null ? String(data.rateAmount) : '',
        rateType: data.rateType || 'DAILY',
        bio: data.bio || '',
        available: Boolean(data.available),
      });
    } catch (err) {
      setLoadError(err.message || 'Could not load your caretaker profile.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  async function handleToggleAvailability() {
    if (!profile) return;
    const nextValue = !profile.available;
    setToggling(true);
    setErrorMessage('');
    setSuccessMessage('');
    try {
      const updated = await feedAndProfileApi.updateMyAvailability(nextValue);
      setProfile(updated);
      setForm((prev) => ({ ...prev, available: updated.available }));
      setSuccessMessage(
        updated.available
          ? 'You are now listed as Available on the Hunters Feed.'
          : 'You are now marked Not Available. Families will not see you under Available Now.'
      );
    } catch (err) {
      setErrorMessage(err.message || 'Could not update availability.');
    } finally {
      setToggling(false);
    }
  }

  async function handleAvatarChange(event) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;

    setErrorMessage('');
    setSuccessMessage('');

    if (!ALLOWED_MIME.includes(file.type)) {
      setErrorMessage('Please upload a JPEG, PNG, or WebP image.');
      return;
    }
    if (file.size > MAX_AVATAR_BYTES) {
      setErrorMessage('Profile image must be 2 MB or smaller.');
      return;
    }

    setUploading(true);
    try {
      const updated = await feedAndProfileApi.uploadCaretakerAvatar(file);
      setProfile(updated);
      setSuccessMessage('Profile photo updated successfully.');
    } catch (err) {
      setErrorMessage(err.message || 'Could not upload profile photo.');
    } finally {
      setUploading(false);
    }
  }

  async function handleSaveProfile(event) {
    event.preventDefault();
    setErrorMessage('');
    setFieldErrors({});
    setSuccessMessage('');
    setSaving(true);

    try {
      const updated = await feedAndProfileApi.updateMyCaretakerProfile({
        locationArea: form.locationArea,
        rateAmount: Number(form.rateAmount),
        rateType: form.rateType,
        bio: form.bio.trim() || null,
        available: form.available,
      });
      setProfile(updated);
      setSuccessMessage('Public caretaker profile saved and live on the Hunters Feed.');
    } catch (err) {
      setErrorMessage(err.message || 'Could not save profile changes.');
      if (err.fieldErrors) setFieldErrors(err.fieldErrors);
    } finally {
      setSaving(false);
    }
  }

  if (loading && !profile) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 text-center text-sm text-slate-500">
        Loading your public caretaker profile...
      </div>
    );
  }

  if (loadError) {
    return (
      <AlertBanner variant="error" title="Could not load profile editor">
        <div className="space-y-2">
          <p>{loadError}</p>
          <Button variant="outline" onClick={loadProfile}>
            Retry
          </Button>
        </div>
      </AlertBanner>
    );
  }

  return (
    <section className="space-y-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-4">
        <div className="flex items-center gap-3.5">
          <CaretakerAvatar
            src={profile?.profileImageUrl}
            name={profile?.fullName}
            size="lg"
            showDot
            available={profile?.available}
          />
          <div className="space-y-1">
            <h2 className="text-base font-bold text-slate-900">Public Feed Profile</h2>
            <p className="text-xs text-slate-600">{profile?.primaryQualification}</p>
            <div>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={handleAvatarChange}
                className="hidden"
              />
              <button
                type="button"
                disabled={uploading}
                onClick={() => fileInputRef.current?.click()}
                className="inline-flex min-h-[44px] items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60"
              >
                {uploading ? (
                  <Upload className="h-3.5 w-3.5 animate-pulse" />
                ) : (
                  <Camera className="h-3.5 w-3.5" />
                )}
                <span>{uploading ? 'Uploading...' : 'Change Photo'}</span>
              </button>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-2 text-center text-xs">
          <div className="rounded-xl bg-slate-50 px-3 py-2">
            <div className="flex items-center justify-center gap-1 font-bold text-slate-900">
              <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" />
              <span>{Number(profile?.avgRating || 0).toFixed(1)}</span>
            </div>
            <div className="text-[11px] text-slate-500">Rating</div>
          </div>
          <div className="rounded-xl bg-slate-50 px-3 py-2">
            <div className="font-bold text-slate-900">{profile?.completedHiresCount ?? 0}</div>
            <div className="text-[11px] text-slate-500">Hires</div>
          </div>
          <div className="rounded-xl bg-slate-50 px-3 py-2">
            <div className="flex items-center justify-center gap-1 font-bold text-teal-800">
              <ThumbsUp className="h-3 w-3" />
              <span>{Math.round(Number(profile?.hireAgainPercentage || 0))}%</span>
            </div>
            <div className="text-[11px] text-slate-500">Hire Again</div>
          </div>
        </div>
      </div>

      <div className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 bg-slate-50/70 p-3.5">
        <div>
          <p className="text-sm font-bold text-slate-900">Availability Status</p>
          <p className="text-xs text-slate-600">
            {profile?.available
              ? 'Visible to families browsing Available Now'
              : 'Hidden from Available Now search results'}
          </p>
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={Boolean(profile?.available)}
          disabled={toggling}
          onClick={handleToggleAvailability}
          className={`inline-flex min-h-[44px] min-w-[132px] items-center justify-center gap-2 rounded-full px-4 text-xs font-bold transition-colors disabled:opacity-60 ${
            profile?.available
              ? 'bg-emerald-600 text-white hover:bg-emerald-700'
              : 'bg-slate-300 text-slate-800 hover:bg-slate-400'
          }`}
        >
          <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
          <span>{profile?.available ? 'Available' : 'Not Available'}</span>
        </button>
      </div>

      {successMessage && <AlertBanner variant="success">{successMessage}</AlertBanner>}
      {errorMessage && <AlertBanner variant="error">{errorMessage}</AlertBanner>}

      <form onSubmit={handleSaveProfile} className="space-y-4">
        <FormField
          id="profile-area"
          label="Service Area / Location"
          placeholder="e.g., Dhanmondi, Dhaka"
          value={form.locationArea}
          onChange={(e) => setForm((p) => ({ ...p, locationArea: e.target.value }))}
          error={fieldErrors.locationArea}
          hint="Families filter caretakers by hospital area or neighborhood"
          required
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            id="profile-rate"
            label="Service Rate (BDT ৳)"
            type="number"
            min="50"
            max="100000"
            step="10"
            inputMode="decimal"
            placeholder="e.g., 2000"
            value={form.rateAmount}
            onChange={(e) => setForm((p) => ({ ...p, rateAmount: e.target.value }))}
            error={fieldErrors.rateAmount}
            required
          />

          <div className="space-y-1.5">
            <span className="block text-sm font-medium text-slate-800">Rate Unit</span>
            <div className="grid grid-cols-2 gap-2">
              {[
                { value: 'HOURLY', label: 'Per Hour' },
                { value: 'DAILY', label: 'Per Day' },
              ].map((option) => (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => setForm((p) => ({ ...p, rateType: option.value }))}
                  className={`min-h-[44px] rounded-xl border text-sm font-semibold transition-colors ${
                    form.rateType === option.value
                      ? 'border-teal-700 bg-teal-700 text-white'
                      : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <label htmlFor="profile-bio" className="text-sm font-medium text-slate-800">
              Bio &amp; Care Specialties
            </label>
            <span className="text-xs text-slate-400">{form.bio.length}/2000</span>
          </div>
          <textarea
            id="profile-bio"
            rows={4}
            maxLength={2000}
            value={form.bio}
            onChange={(e) => setForm((p) => ({ ...p, bio: e.target.value }))}
            placeholder="Share your hospital ward experience, ICU/elderly care strengths, shift preferences, and languages..."
            className="block w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-100"
          />
          {fieldErrors.bio && <p className="text-xs font-medium text-rose-600">{fieldErrors.bio}</p>}
        </div>

        <Button type="submit" fullWidth loading={saving}>
          Save Public Profile
        </Button>
      </form>
    </section>
  );
}
