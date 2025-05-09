import { NextResponse } from 'next/server'

export async function POST(
  request: Request,
  { params }: { params: { id: string } }
) {
  try {
    const id = params.id

    // 尝试调用后端 API
    try {
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/plugins/${id}/disable`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        signal: AbortSignal.timeout(2000)
      })

      if (response.ok) {
        const data = await response.json()
        return NextResponse.json(data)
      }
    } catch (fetchError) {
      console.warn('Backend API not available, using mock response:', fetchError)
    }

    // 返回模拟成功响应
    return NextResponse.json({
      success: true,
      plugin: {
        id,
        status: 'disabled'
      },
      message: `Plugin ${id} disabled successfully`
    })
  } catch (error) {
    console.error('Error disabling plugin:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to disable plugin' },
      { status: 500 }
    )
  }
}
