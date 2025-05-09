import { NextResponse } from 'next/server';

// This is a workaround for the favicon.ico issue
// It returns a 204 No Content response for favicon.ico requests
export async function GET() {
  return new NextResponse(null, { status: 204 });
}
