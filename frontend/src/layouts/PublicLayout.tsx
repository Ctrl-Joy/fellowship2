import { Link, Outlet, ScrollRestoration } from 'react-router-dom';
import { buttonClasses } from '@/components/ui/Button';
import { BrandMark } from '@/design-system/BrandMark';
import { brand } from '@/design-system/brand';
import { useAuth } from '@/features/auth/hooks';
import { ThemeToggle } from './ThemeToggle';

export function PublicHeader() {
  const { isAuthed } = useAuth();
  return (
    <header className="sticky top-0 z-30 border-b border-border/70 bg-bg/90 backdrop-blur supports-[backdrop-filter]:bg-bg/75">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4 md:px-6">
        <Link to="/" aria-label={`${brand.name} home`}>
          <BrandMark withName />
        </Link>
        <nav aria-label="Primary" className="flex items-center gap-1.5">
          <Link to="/explore" className="hidden px-3 py-2 text-sm text-muted hover:text-text sm:block">
            Explore
          </Link>
          <Link to="/communities" className="hidden px-3 py-2 text-sm text-muted hover:text-text sm:block">
            Communities
          </Link>
          <ThemeToggle />
          {isAuthed ? (
            <Link to="/home" className={buttonClasses('primary', 'sm')}>
              Open {brand.name}
            </Link>
          ) : (
            <>
              <Link to="/login" className={buttonClasses('ghost', 'sm')}>
                Log in
              </Link>
              <Link to="/register" className={buttonClasses('primary', 'sm')}>
                Join
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}

export function PublicFooter() {
  return (
    <footer className="border-t border-border">
      <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-10 text-sm text-muted md:flex-row md:items-start md:justify-between md:px-6">
        <div className="max-w-xs">
          <BrandMark withName />
          <p className="mt-3 text-faint">{brand.tagline}</p>
        </div>
        <nav aria-label="Footer" className="grid grid-cols-2 gap-x-12 gap-y-2 sm:grid-cols-3">
          <Link to="/about" className="hover:text-text">About</Link>
          <Link to="/explore" className="hover:text-text">Explore</Link>
          <Link to="/communities" className="hover:text-text">Communities</Link>
          <Link to="/terms" className="hover:text-text">Terms</Link>
          <Link to="/privacy" className="hover:text-text">Privacy</Link>
          <a href="/swagger-ui.html" className="hover:text-text">API docs</a>
        </nav>
      </div>
      <div className="border-t border-border py-4 text-center text-xs text-faint">
        © {new Date().getFullYear()} {brand.name}. A working product name — subject to change.
      </div>
    </footer>
  );
}

export function PublicLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-3 focus:z-50 focus:rounded-md focus:bg-surface focus:px-3 focus:py-2">
        Skip to content
      </a>
      <PublicHeader />
      <main id="main" tabIndex={-1} className="flex-1 focus:outline-none">
        <Outlet />
      </main>
      <PublicFooter />
      <ScrollRestoration />
    </div>
  );
}
