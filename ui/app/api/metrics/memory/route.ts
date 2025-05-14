import { NextResponse } from 'next/server'

export async function GET() {
  try {
    const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/metrics/memory`, {
      headers: {
        'Content-Type': 'application/json',
      },
    })

    if (!response.ok) {
      throw new Error(`API error: ${response.status}`)
    }

    const data = await response.json()
    return NextResponse.json(data)
  } catch (error) {
    console.error('Error fetching memory metrics:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to fetch memory metrics' },
      { status: 500 }
    )
  }
}
