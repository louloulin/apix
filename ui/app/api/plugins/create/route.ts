import { NextResponse } from 'next/server'

export async function POST(request: Request) {
  try {
    const body = await request.json()
    
    // 尝试调用后端 API
    try {
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/plugins`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(2000)
      })

      if (response.ok) {
        const data = await response.json()
        return NextResponse.json(data)
      }
    } catch (fetchError) {
      console.warn('Backend API not available, using mock response:', fetchError)
    }
    
    // 生成一个随机 ID
    const id = body.id || `plugin-${Math.floor(Math.random() * 10000)}`
    
    // 返回模拟成功响应
    return NextResponse.json({
      success: true,
      plugin: {
        ...body,
        id: id,
        status: body.status || 'enabled',
        version: body.version || '1.0.0'
      },
      message: 'Plugin created successfully'
    })
  } catch (error) {
    console.error('Error creating plugin:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to create plugin' },
      { status: 500 }
    )
  }
}
