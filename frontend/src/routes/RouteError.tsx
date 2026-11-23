import { isRouteErrorResponse, Link, useRouteError } from 'react-router-dom';
import { buttonClasses } from '@/components/ui/Button';
import { BrandMark } from '@/design-system/BrandMark';

export function RouteError() {
  const err = useRouteError();
  const message = isRouteErrorResponse(err)
    ? `${err.status} — ${err.statusText}`
    : err instanceof Error
      ? err.message
      : 'Unexpected error';
  const isChunk = /dynamically imported module|Failed to fetch/i.test(message);
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
      <BrandMark size={36} />
      <h1 className="font-serif text-2xl font-semibold">Something broke on this page</h1>
      <p className="max-w-md text-sm text-muted">
        {isChunk ? 'A new version is available. Reload to continue.' : message}
      </p>
      <div className="flex gap-2">
        <button className={buttonClasses('primary', 'md')} onClick={() => window.location.reload()}>
          Reload
        </button>
        <Link to="/home" className={buttonClasses('secondary', 'md')}>
          Go home
        </Link>
      </div>
    </main>
  );
}
