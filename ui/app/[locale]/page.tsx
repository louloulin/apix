import { redirect } from 'next/navigation'
import { locales, defaultLocale } from '@/i18n'

export const dynamic = 'force-dynamic';

export default function Home({
  params: { locale }
}: {
  params: { locale: string }
}) {
  // Ensure locale is valid or use default
  const resolvedLocale = locale || defaultLocale;

  // Validate that the locale is supported
  const validLocale = locales.includes(resolvedLocale as any) ? resolvedLocale : defaultLocale;

  // Redirect to the dashboard page for the current locale
  redirect(`/${validLocale}/dashboard`)
}
