import { getRequestConfig } from 'next-intl/server';

// Define the list of supported locales
export const locales = ['en', 'zh'];
export const defaultLocale = 'en';

// Get the request configuration for next-intl
export default getRequestConfig(async ({ locale }) => {
  // If locale is undefined, use the default locale
  const resolvedLocale = locale || defaultLocale;

  // Validate that the locale is supported
  const validLocale = locales.includes(resolvedLocale as any) ? resolvedLocale : defaultLocale;

  // Load the messages for the requested locale
  let messages;
  try {
    messages = (await import(`./messages/${validLocale}.json`)).default;
  } catch (error) {
    console.error(`Failed to load messages for locale: ${validLocale}`, error);
    // Fallback to default locale if messages can't be loaded
    if (validLocale !== defaultLocale) {
      try {
        messages = (await import(`./messages/${defaultLocale}.json`)).default;
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
