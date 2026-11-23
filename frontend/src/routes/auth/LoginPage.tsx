import { Link, useLocation, useNavigate } from 'react-router-dom';
import { safeNext } from '@/app/guards';
import { AuthCard } from '@/features/auth/components/AuthCard';
import { LoginForm } from '@/features/auth/components/LoginForm';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';

export default function LoginPage() {
  useDocumentTitle('Log in');
  const navigate = useNavigate();
  const { search } = useLocation();
  return (
    <AuthCard
      title="Welcome back"
      subtitle="Log in to vote, comment and follow your communities."
      footer={<>New here? <Link to={`/register${search}`} className="font-medium text-accent hover:underline">Create an account</Link></>}
    >
      <LoginForm onSuccess={(me) => navigate(me.onboardingCompleted ? safeNext(search) : '/onboarding', { replace: true })} />
    </AuthCard>
  );
}
