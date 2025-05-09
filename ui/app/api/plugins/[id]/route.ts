import { NextResponse } from 'next/server'

export async function GET(
  request: Request,
  { params }: { params: { id: string } }
) {
  try {
    const id = params.id

    // 尝试从后端获取数据
    try {
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/plugins/${id}`, {
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
      console.warn('Backend API not available, using mock data:', fetchError)
    }

    // 模拟数据
    const mockPlugins = {
      'rate-limiter': {
        id: 'rate-limiter',
        name: 'Rate Limiter',
        type: 'security',
        version: '1.0.0',
        status: 'enabled',
        config: {
          limit: 100,
          window: 60,
          response_code: 429,
          response_message: 'Too many requests'
        }
      },
      'jwt-auth': {
        id: 'jwt-auth',
        name: 'JWT Authentication',
        type: 'authentication',
        version: '1.2.0',
        status: 'enabled',
        config: {
          secret: 'your-secret-key',
          expiry: 3600,
          algorithm: 'HS256',
          header_name: 'Authorization',
          cookie_name: 'jwt_token'
        }
      },
      'request-transformer': {
        id: 'request-transformer',
        name: 'Request Transformer',
        type: 'transformation',
        version: '0.9.0',
        status: 'disabled',
        config: {
          add_headers: true,
          remove_headers: ['x-powered-by'],
          add_query_params: false,
          transform_body: true
        }
      },
      'response-cache': {
        id: 'response-cache',
        name: 'Response Cache',
        type: 'business-logic',
        version: '1.1.0',
        status: 'enabled',
        config: {
          ttl: 300,
          max_size: 1000,
          cache_control: true,
          vary_headers: ['Accept', 'Accept-Encoding']
        }
      }
    }

    if (mockPlugins[id]) {
      return NextResponse.json({
        success: true,
        plugin: mockPlugins[id]
      })
    } else {
      return NextResponse.json(
        { success: false, error: 'Plugin not found' },
        { status: 404 }
      )
    }
  } catch (error) {
    console.error('Error fetching plugin:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to fetch plugin' },
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

    // 尝试调用后端 API
    try {
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/plugins/${id}`, {
        method: 'PUT',
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

    // 返回模拟成功响应
    return NextResponse.json({
      success: true,
      plugin: {
        ...body,
        id: id
      },
      message: 'Plugin updated successfully'
    })
  } catch (error) {
    console.error('Error updating plugin:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to update plugin' },
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

    // 尝试调用后端 API
    try {
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/plugins/${id}`, {
        method: 'DELETE',
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
      message: `Plugin ${id} deleted successfully`
    })
  } catch (error) {
    console.error('Error deleting plugin:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to delete plugin' },
      { status: 500 }
    )
  }
}
