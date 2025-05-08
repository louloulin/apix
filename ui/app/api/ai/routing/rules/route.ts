import { NextResponse } from 'next/server'

export async function GET() {
  try {
    const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/ai/routing/rules`, {
      headers: {
        'Content-Type': 'application/json',
      },
    })

    if (!response.ok) {
      // This endpoint might not be implemented yet, so return empty rules
      if (response.status === 404) {
        return NextResponse.json({ rules: [] })
      }
      throw new Error(`API error: ${response.status}`)
    }

    const data = await response.json()
    return NextResponse.json(data)
  } catch (error) {
    console.error('Error fetching routing rules:', error)
    // Return empty rules array instead of error to handle gracefully
    return NextResponse.json({ rules: [] })
  }
}
