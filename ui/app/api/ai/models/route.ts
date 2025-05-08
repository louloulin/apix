import { NextResponse } from 'next/server'

export async function GET() {
  try {
    const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/ai/models`, {
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
    console.error('Error fetching AI models:', error)
    return NextResponse.json(
      { error: 'Failed to fetch AI models' },
      { status: 500 }
    )
  }
}
