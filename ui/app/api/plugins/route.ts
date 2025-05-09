import { NextResponse } from 'next/server'

export async function GET() {
  try {
    // 尝试从后端获取数据
    try {
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/plugins`, {
        headers: {
          'Content-Type': 'application/json',
        },
        // 设置较短的超时时间，避免长时间等待
        signal: AbortSignal.timeout(2000)
      })

      if (response.ok) {
        const data = await response.json()
        return NextResponse.json(data)
      }
    } catch (fetchError) {
      console.warn('Backend API not available, using mock data:', fetchError)
    }

    // 如果后端API不可用，返回模拟数据
    const mockPlugins = [
      {
        id: 'rate-limiter',
        name: 'Rate Limiter',
        type: 'security',
        version: '1.0.0',
        status: 'enabled',
        config: {
          limit: 100,
          window: 60
        }
      },
      {
        id: 'jwt-auth',
        name: 'JWT Authentication',
        type: 'authentication',
        version: '1.2.0',
        status: 'enabled',
        config: {
          secret: '****',
          expiry: 3600
        }
      },
      {
        id: 'request-transformer',
        name: 'Request Transformer',
        type: 'transformation',
        version: '0.9.0',
        status: 'disabled',
        config: {
          add_headers: true,
          remove_headers: ['x-powered-by']
        }
      },
      {
        id: 'response-cache',
        name: 'Response Cache',
        type: 'business-logic',
        version: '1.1.0',
        status: 'enabled',
        config: {
          ttl: 300,
          max_size: 1000
        }
      }
    ]

    return NextResponse.json({
      success: true,
      plugins: mockPlugins
    })
  } catch (error) {
    console.error('Error in plugins API route:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to fetch plugins' },
      { status: 500 }
    )
  }
}
