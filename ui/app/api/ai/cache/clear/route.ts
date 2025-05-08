import { NextResponse } from 'next/server'

export async function POST(request: Request) {
  try {
    const { searchParams } = new URL(request.url)
    const modelId = searchParams.get('modelId')

    let url = `${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/ai/cache/clear`
    if (modelId) {
      url += `?modelId=${modelId}`
    }

    const response = await fetch(url, {
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
    console.error('Error clearing AI cache:', error)
    return NextResponse.json(
      { error: 'Failed to clear AI cache' },
      { status: 500 }
    )
  }
}
