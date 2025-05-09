import { NextResponse } from 'next/server'

export async function GET(
  request: Request,
  { params }: { params: { id: string } }
) {
  try {
    const id = params.id
    const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/ai/routing/rules/${id}`, {
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
    console.error('Error fetching AI routing rule:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to fetch AI routing rule' },
      { status: 500 }
    )
  }
}

export async function PUT(
  request: Request,
  { params }: { params: { id: string } }
) {
  try {
    const id = params.id
    const body = await request.json()
    
    const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/ai/routing/rules/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
    })

    if (!response.ok) {
      throw new Error(`API error: ${response.status}`)
    }

    const data = await response.json()
    return NextResponse.json(data)
  } catch (error) {
    console.error('Error updating AI routing rule:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to update AI routing rule' },
      { status: 500 }
    )
  }
}

export async function DELETE(
  request: Request,
  { params }: { params: { id: string } }
) {
  try {
    const id = params.id
    const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/ai/routing/rules/${id}`, {
      method: 'DELETE',
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
    console.error('Error deleting AI routing rule:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to delete AI routing rule' },
      { status: 500 }
    )
  }
}
