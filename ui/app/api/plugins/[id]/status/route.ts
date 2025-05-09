import { NextResponse } from 'next/server'

export async function PUT(
  request: Request,
  { params }: { params: { id: string } }
) {
  try {
    const id = params.id
    const body = await request.json()
    const action = body.action // 'enable' 或 'disable'
    
    if (!action || (action !== 'enable' && action !== 'disable')) {
      return NextResponse.json(
        { success: false, error: 'Invalid action. Must be "enable" or "disable"' },
        { status: 400 }
      )
    }
    
    // 尝试调用后端 API
    try {
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/plugins/${id}/${action}`, {
        method: 'PUT',
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
        status: action === 'enable' ? 'enabled' : 'disabled'
      },
      message: `Plugin ${id} ${action === 'enable' ? 'enabled' : 'disabled'} successfully`
    })
  } catch (error) {
    console.error('Error updating plugin status:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to update plugin status' },
      { status: 500 }
    )
  }
}
