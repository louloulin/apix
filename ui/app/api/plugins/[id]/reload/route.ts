import { NextResponse } from 'next/server'

export async function POST(
  request: Request,
  { params }: { params: { id: string } }
) {
  try {
    const id = params.id
    const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/plugins/${id}/reload`, {
      method: 'POST',
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
    console.error('Error reloading plugin:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to reload plugin' },
      { status: 500 }
    )
  }
}
