import { Inter } from 'next/font/google'
import { NextIntlClientProvider } from 'next-intl'
import { locales, defaultLocale } from '@/i18n'
import { ThemeProvider } from '@/components/theme-provider'
import '@/app/globals.css'

const inter = Inter({ subsets: ['latin'] })

export function generateStaticParams() {
  return locales.map((locale) => ({ locale }))
}

export default async function RootLayout({
  children,
  params: { locale }
}: {
  children: React.ReactNode
  params: { locale: string }
}) {
  // Ensure locale is valid or use default
  const resolvedLocale = locale || defaultLocale;

  // Validate that the locale is supported
  const validLocale = locales.includes(resolvedLocale as any) ? resolvedLocale : defaultLocale;

  // Load the messages for the requested locale
  let messages
  try {
    messages = (await import(`@/messages/${validLocale}.json`)).default
  } catch (error) {
    console.error(`Failed to load messages for locale: ${validLocale}`, error);
    // Fallback to default locale if messages can't be loaded
    if (validLocale !== defaultLocale) {
      try {
        messages = (await import(`@/messages/${defaultLocale}.json`)).default;
      } catch (fallbackError) {
        console.error(`Failed to load fallback messages for default locale: ${defaultLocale}`, fallbackError);
        messages = {};
      }
    } else {
      messages = {};
    }
  }

  return (
    <html lang={validLocale} suppressHydrationWarning>
      <body className={inter.className}>
        <NextIntlClientProvider locale={validLocale} messages={messages}>
          <ThemeProvider
            attribute="class"
            defaultTheme="system"
            enableSystem
            disableTransitionOnChange
          >
            {children}
          </ThemeProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  )
}
