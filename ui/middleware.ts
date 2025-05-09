import createMiddleware from 'next-intl/middleware';
import { locales, defaultLocale } from './i18n';

export default createMiddleware({
  // A list of all locales that are supported
  locales,

  // The default locale to use when a non-locale-prefixed
  // path is visited
  defaultLocale,

  // Whether to add a locale prefix to the URL
  localePrefix: 'always',
});

export const config = {
  // Match all pathnames except for
  // - files with extensions (e.g. static files)
  // - API routes
  // - _next paths (Next.js internals)
  // - favicon.ico
  matcher: ['/((?!api|_next|.*\\..*|favicon.ico).*)']
};
