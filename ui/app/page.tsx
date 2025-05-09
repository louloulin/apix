import { redirect } from 'next/navigation'
import { defaultLocale } from '@/i18n'

export const dynamic = 'force-dynamic';

export default function Home() {
  // Redirect to the default locale dashboard
  redirect(`/${defaultLocale}/dashboard`)
}
