import { motion } from 'framer-motion';
import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { buttonClasses } from '@/components/ui/Button';
import { brand } from '@/design-system/brand';
import { useAuth } from '@/features/auth/hooks';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { HeroIllustration } from './HeroIllustration';
import { LiveCommunities } from './LiveCommunities';
import { FeatureSections } from './FeatureSections';

export default function LandingPage() {
  useDocumentTitle(null);
  const { isAuthed } = useAuth();
  return (
    <>
      <section className="relative overflow-hidden border-b border-border">
        <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 py-16 md:px-6 lg:grid-cols-[1.05fr_1fr] lg:py-24">
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.35 }}>
            <p className="mb-5 inline-flex items-center gap-2 rounded-full border border-border px-3 py-1 text-xs text-muted">
              <span className="h-1.5 w-1.5 rounded-full bg-accent" aria-hidden="true" />
              Early access · actively in development
            </p>
            <h1 className="font-serif text-[40px] font-semibold leading-[1.05] tracking-tight sm:text-5xl lg:text-[56px]">
              Conversations that
              <br />
              <span className="text-accent">settle into knowledge.</span>
            </h1>
            <p className="mt-6 max-w-lg text-lg leading-relaxed text-muted">
              {brand.name} is a home for communities that think out loud. Discuss in threads, rank by quality instead of
              noise, and keep what you learn — with the people who maintain each community recognised for it.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              {isAuthed ? (
                <Link to="/home" className={buttonClasses('primary', 'md')}>
                  Open your feed <ArrowRight size={16} />
                </Link>
              ) : (
                <Link to="/register" className={buttonClasses('primary', 'md')}>
                  Create your account <ArrowRight size={16} />
                </Link>
              )}
              <Link to="/explore" className={buttonClasses('outline', 'md')}>Browse without an account</Link>
            </div>
          </motion.div>
          <HeroIllustration />
        </div>
      </section>

      <section aria-labelledby="pillars" className="border-b border-border">
        <div className="mx-auto max-w-6xl px-4 py-16 md:px-6">
          <h2 id="pillars" className="max-w-2xl font-serif text-3xl font-semibold tracking-tight">
            Familiar where it matters. Different where it counts.
          </h2>
          <div className="mt-10 grid gap-10 md:grid-cols-3">
            {[
              ['01', 'Communities first', 'Every discussion lives in a community with its own rules, flairs and moderators. Public, restricted or private — enforced on the server, not just hidden in the UI.'],
              ['02', 'Quality over volume', 'Feeds blend vote ratio, discussion depth and freshness. A post with ten thoughtful replies can outrank one with a hundred drive-by votes.'],
              ['03', 'Threads you can follow', 'Nested comments load lazily, collapse cleanly and stay readable on a phone. Sort by best, top, new, controversial or oldest.'],
            ].map(([n, t, d]) => (
              <div key={n} className="border-t border-border pt-5">
                <span className="font-mono text-xs text-accent">{n}</span>
                <h3 className="mt-2 text-lg font-semibold">{t}</h3>
                <p className="mt-2 text-[15px] leading-relaxed text-muted">{d}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <LiveCommunities />
      <FeatureSections />

      <section className="border-t border-border">
        <div className="mx-auto flex max-w-6xl flex-col items-start gap-6 px-4 py-16 md:flex-row md:items-center md:justify-between md:px-6">
          <div>
            <h2 className="font-serif text-3xl font-semibold tracking-tight">Start a community worth reading.</h2>
            <p className="mt-2 text-muted">Free to join. Your feed is shaped by the communities and people you choose.</p>
          </div>
          <Link to={isAuthed ? '/communities/new' : '/register'} className={buttonClasses('primary', 'md')}>
            {isAuthed ? 'Create a community' : 'Join ' + brand.name} <ArrowRight size={16} />
          </Link>
        </div>
      </section>
    </>
  );
}
