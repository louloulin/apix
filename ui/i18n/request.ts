import { getRequestConfig } from 'next-intl/server';
import { locales, defaultLocale } from '../i18n';

// This is a simplified version of the getRequestConfig function from i18n.ts
// It's needed by next-intl for proper functioning
export default getRequestConfig(async ({ locale }) => {
  // If locale is undefined, use the default locale
  const resolvedLocale = locale || defaultLocale;

  // Validate that the locale is supported
  const validLocale = locales.includes(resolvedLocale as any) ? resolvedLocale : defaultLocale;

  // Load the messages for the requested locale
  let messages;
  try {
    messages = (await import(`../messages/${validLocale}.json`)).default;
  } catch (error) {
    console.error(`Failed to load messages for locale: ${validLocale}`, error);
    // Fallback to default locale if messages can't be loaded
    if (validLocale !== defaultLocale) {
      try {
        messages = (await import(`../messages/${defaultLocale}.json`)).default;
      } catch (fallbackError) {
        console.error(`Failed to load fallback messages for default locale: ${defaultLocale}`, fallbackError);
        messages = {};
      }
    } else {
      messages = {};
    }
  }

  return {
    locale: validLocale,
    messages,
    timeZone: 'UTC',
  };
});
