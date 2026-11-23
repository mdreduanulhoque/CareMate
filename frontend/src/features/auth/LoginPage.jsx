import { Eye, EyeOff, ShieldCheck, Stethoscope, UserPlus } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { Button } from '../../components/ui/Button';
import { FormField } from '../../components/ui/FormField';
import { getHomePathForRole, useAuth } from '../../context/useAuth';

export function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [mobileNumber, setMobileNumber] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});

  async function handleSubmit(event) {
    event.preventDefault();
    setErrorMessage('');
    setFieldErrors({});
    setSubmitting(true);

    try {
      const user = await login({ mobileNumber, password });
      navigate(getHomePathForRole(user.role), { replace: true });
    } catch (error) {
      setErrorMessage(error.message || 'Unable to sign in. Please try again.');
      if (error.fieldErrors) {
        setFieldErrors(error.fieldErrors);
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-dvh flex-col justify-center bg-gradient-to-b from-teal-50/70 via-slate-50 to-slate-50 px-4 py-8">
      <div className="mx-auto w-full max-w-md space-y-6">
        <div className="space-y-2 text-center">
          <div className="flex justify-center">
            <BrandLogo />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Trusted care at the hospital bedside
          </h1>
          <p className="text-sm leading-relaxed text-slate-600">
            Sign in with your mobile number to find verified nurses and hospital caretakers, or
            manage your shifts.
          </p>
        </div>

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            {errorMessage && (
              <AlertBanner variant="error" title="Could not sign in">
                {errorMessage}
              </AlertBanner>
            )}

            <FormField
              id="login-mobile"
              label="Mobile Number"
              type="tel"
              inputMode="tel"
              autoComplete="tel"
              placeholder="01712345678"
              value={mobileNumber}
              onChange={(e) => setMobileNumber(e.target.value)}
              error={fieldErrors.mobileNumber}
              hint="Bangladeshi mobile format (01XXXXXXXXX or +8801XXXXXXXXX)"
              required
            />

            <FormField
              id="login-password"
              label="Password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="Enter your password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
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

            <Button type="submit" fullWidth loading={submitting}>
              Sign In
            </Button>
          </form>
        </section>

        <section className="space-y-3">
          <p className="text-center text-xs font-semibold uppercase tracking-wider text-slate-500">
            New to CareBridge?
          </p>

          <div className="grid gap-3">
            <Link
              to="/register/customer"
              className="flex min-h-[60px] items-center gap-3.5 rounded-2xl border border-teal-200 bg-white p-4 shadow-xs transition-colors hover:border-teal-400 hover:bg-teal-50/40"
            >
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-teal-100 text-teal-800">
                <UserPlus className="h-5 w-5" aria-hidden="true" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-sm font-bold text-slate-900">
                  I Need a Caretaker (Family Signup)
                </div>
                <div className="text-xs text-slate-600">
                  Quick 4-field registration — start browsing in under 30 seconds
                </div>
              </div>
            </Link>

            <Link
              to="/register/caretaker"
              className="flex min-h-[60px] items-center gap-3.5 rounded-2xl border border-slate-200 bg-white p-4 shadow-xs transition-colors hover:border-sky-300 hover:bg-sky-50/40"
            >
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-sky-100 text-sky-800">
                <Stethoscope className="h-5 w-5" aria-hidden="true" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-sm font-bold text-slate-900">
                  Join as a Caretaker / Nurse
                </div>
                <div className="text-xs text-slate-600">
                  Create your provider account and get verified to receive hospital shifts
                </div>
              </div>
            </Link>
          </div>
        </section>

        <div className="flex items-center justify-center gap-2 text-xs text-slate-500">
          <ShieldCheck className="h-4 w-4 text-teal-700" aria-hidden="true" />
          <span>Every caretaker is manually verified by CareBridge Admins</span>
        </div>
      </div>
    </div>
  );
}
