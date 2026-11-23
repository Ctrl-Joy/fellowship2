import { MapPinOff } from 'lucide-react';
import { Link } from 'react-router-dom';
import { buttonClasses } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/States';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { PageColumns } from '@/layouts/PageColumns';

export default function NotFoundPage() {
  useDocumentTitle('Not found');
  return (
    <PageColumns>
      <EmptyState
        icon={MapPinOff}
        title="This page doesn’t exist"
        description="The link may be broken, or the page may have moved."
        action={<Link to="/home" className={buttonClasses('primary', 'sm')}>Go home</Link>}
      />
    </PageColumns>
  );
}
