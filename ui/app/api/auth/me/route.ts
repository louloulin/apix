import { NextResponse } from 'next/server'

export async function GET(request: Request) {
  try {
    // Get the token from the request headers
    const authHeader = request.headers.get('Authorization')
    
    if (!authHeader) {
      return NextResponse.json(
        { success: false, error: 'No authorization header' },
        { status: 401 }
      )
    }
    
    const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/auth/me`, {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': authHeader,
      },
    })

    if (!response.ok) {
      throw new Error(`API error: ${response.status}`)
    }

    const data = await response.json()
    return NextResponse.json(data)
  } catch (error) {
    console.error('Error fetching current user:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to fetch current user' },
      { status: 500 }
    )
  }
}
