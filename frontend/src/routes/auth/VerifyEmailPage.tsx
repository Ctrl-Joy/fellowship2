import { CircleCheck, CircleX } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { buttonClasses } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { authApi } from '@/features/auth/api';
import { AuthCard } from '@/features/auth/components/AuthCard';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { errorMessage } from '@/services/apiError';
import { useAuthStore } from '@/store/authStore';

export default function VerifyEmailPage() {
  useDocumentTitle('Verify email');
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [state, setState] = useState<'pending' | 'ok' | 'error'>(token ? 'pending' : 'error');
  const [msg, setMsg] = useState(token ? '' : 'This link is missing its token.');
  const ran = useRef(false);

  useEffect(() => {
    if (!token || ran.current) return;
    ran.current = true;
    authApi
      .verifyEmail(token)
      .then(() => {
        setState('ok');
        const u = useAuthStore.getState().user;
        if (u) useAuthStore.getState().setUser({ ...u, emailVerified: true });
      })
      .catch((e: unknown) => {
        setState('error');
        setMsg(errorMessage(e));
      });
  }, [token]);

  return (
    <AuthCard title={state === 'ok' ? 'Email verified' : state === 'pending' ? 'Verifying…' : 'Verification failed'}>
      <div className="flex flex-col items-center gap-4 text-center text-sm text-muted">
        {state === 'pending' && <Spinner size={24} />}
        {state === 'ok' && <><CircleCheck size={30} className="text-accent" /><p>Thanks — your email address is confirmed.</p></>}
        {state === 'error' && <><CircleX size={30} className="text-danger" /><p>{msg || 'The link may have expired.'} You can request a new one from Settings.</p></>}
        <Link to="/home" className={buttonClasses('primary', 'sm')}>Continue</Link>
      </div>
    </AuthCard>
  );
}
