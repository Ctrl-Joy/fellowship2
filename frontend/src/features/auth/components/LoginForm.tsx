import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Field, FormAlert, Input } from '@/components/ui/Field';
import type { Me } from '@/types/api';
import { applyServerErrors } from '@/utils/forms';
import { loginSchema, type LoginValues } from '@/utils/validation';
import { useLogin } from '../hooks';

export function LoginForm({ onSuccess }: { onSuccess: (me: Me) => void }) {
  const login = useLogin();
  const [formError, setFormError] = useState<string | null>(null);
  const { register, handleSubmit, setError, formState } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });
  return (
    <form
      noValidate
      className="space-y-4"
      onSubmit={handleSubmit((v) => {
        setFormError(null);
        login.mutate(v, {
          onSuccess: (r) => onSuccess(r.user),
          onError: (e) => setFormError(applyServerErrors(e, setError, ['login', 'password'])),
        });
      })}
    >
      <FormAlert message={formError} />
      <Field label="Username or email" error={formState.errors.login?.message}>
        {(a) => <Input {...a} autoComplete="username" {...register('login')} />}
      </Field>
      <Field label="Password" error={formState.errors.password?.message}>
        {(a) => <Input {...a} type="password" autoComplete="current-password" {...register('password')} />}
      </Field>
      <div className="flex justify-end">
        <Link to="/forgot-password" className="text-xs text-muted hover:text-text">Forgot password?</Link>
      </div>
      <Button type="submit" className="w-full" loading={login.isPending}>Log in</Button>
    </form>
  );
}
