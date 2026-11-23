import { ArrowLeft, Clock, Eye, EyeOff } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { Button } from '../../components/ui/Button';
import { FormField } from '../../components/ui/FormField';
import { getHomePathForRole, useAuth } from '../../context/useAuth';

export function CustomerRegisterPage() {
  const navigate = useNavigate();
  const { registerCustomer } = useAuth();

  const [form, setForm] = useState({
    fullName: '',
    mobileNumber: '',
    address: '',
    email: '',
    password: '',
  });
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});

  function updateField(field) {
    return (event) => {
      setForm((prev) => ({ ...prev, [field]: event.target.value }));
      setFieldErrors((prev) => ({ ...prev, [field]: undefined }));
    };
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setErrorMessage('');
    setFieldErrors({});
    setSubmitting(true);

    try {
      const user = await registerCustomer({
        fullName: form.fullName,
        mobileNumber: form.mobileNumber,
        address: form.address,
        email: form.email.trim() || null,
        password: form.password,
      });
      navigate(getHomePathForRole(user.role), { replace: true });
    } catch (error) {
      setErrorMessage(error.message || 'Could not create your account.');
      if (error.fieldErrors) {
        setFieldErrors(error.fieldErrors);
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="min-h-dvh bg-gradient-to-b from-teal-50/70 via-slate-50 to-slate-50 px-4 py-6">
      <div className="mx-auto w-full max-w-md space-y-5">
        <div className="flex items-center justify-between">
          <Link
            to="/login"
            className="inline-flex min-h-[44px] items-center gap-1.5 rounded-xl px-2 text-sm font-semibold text-slate-700 hover:bg-slate-200/60"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            <span>Back to Login</span>
          </Link>
          <BrandLogo compact />
        </div>

        <div className="space-y-1.5">
          <div className="inline-flex items-center gap-1.5 rounded-full bg-teal-100 px-3 py-1 text-xs font-semibold text-teal-800">
            <Clock className="h-3.5 w-3.5" aria-hidden="true" />
            <span>Takes under 30 seconds</span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Quick Family Registration
          </h1>
          <p className="text-sm text-slate-600">
            Just 4 essentials so you can immediately find a verified caretaker for your loved one.
          </p>
        </div>

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            {errorMessage && (
              <AlertBanner variant="error" title="Registration failed">
                {errorMessage}
              </AlertBanner>
            )}

            <FormField
              id="customer-name"
              label="Full Name"
              autoComplete="name"
              placeholder="e.g., Nusrat Jahan"
              value={form.fullName}
              onChange={updateField('fullName')}
              error={fieldErrors.fullName}
              required
            />

            <FormField
              id="customer-mobile"
              label="Mobile Number"
              type="tel"
              inputMode="tel"
              autoComplete="tel"
              placeholder="01712345678"
              value={form.mobileNumber}
              onChange={updateField('mobileNumber')}
              error={fieldErrors.mobileNumber}
              hint="Used to sign in and coordinate with caretakers"
              required
            />

            <FormField
              id="customer-address"
              label="Area / Address"
              autoComplete="street-address"
              placeholder="e.g., Dhanmondi, Dhaka"
              value={form.address}
              onChange={updateField('address')}
              error={fieldErrors.address}
              required
            />

            <FormField
              id="customer-password"
              label="Password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="At least 8 characters"
              value={form.password}
              onChange={updateField('password')}
              error={fieldErrors.password}
              required
              trailing={
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  className="inline-flex h-10 w-10 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 hover:text-slate-800"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              }
            />

            <FormField
              id="customer-email"
              label="Email Address"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              optional
              value={form.email}
              onChange={updateField('email')}
              error={fieldErrors.email}
            />

            <Button type="submit" fullWidth loading={submitting}>
              Create Family Account
            </Button>
          </form>
        </section>
      </div>
    </div>
  );
}
