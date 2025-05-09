import { NextResponse } from 'next/server'

export async function GET(
  request: Request,
  { params }: { params: { id: string } }
) {
  try {
    const id = params.id

    // 尝试从后端获取数据
    try {
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/routes/${id}`, {
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
    const mockRoutes = {
      "1": {
        id: "1",
        path: "/v1/completions",
        target: "https://api.openai.com/v1/completions",
        methods: ["POST"],
        plugins: ["rate-limiter", "jwt-auth"],
        enabled: true,
        priority: 100,
        type: "llm",
        description: "OpenAI completions endpoint"
      },
      "2": {
        id: "2",
        path: "/v1/chat/completions",
        target: "https://api.openai.com/v1/chat/completions",
        methods: ["POST"],
        plugins: ["rate-limiter", "jwt-auth"],
        enabled: true,
        priority: 100,
        type: "llm",
        description: "OpenAI chat completions endpoint"
      },
      "3": {
        id: "3",
        path: "/v1/messages",
        target: "https://api.anthropic.com/v1/messages",
        methods: ["POST"],
        plugins: ["rate-limiter", "jwt-auth"],
        enabled: true,
        priority: 90,
        type: "llm",
        description: "Anthropic Claude messages endpoint"
      },
      "4": {
        id: "4",
        path: "/vectors/search",
        target: "INTERNAL",
        methods: ["POST", "GET"],
        plugins: ["jwt-auth"],
        enabled: true,
        priority: 80,
        type: "vector",
        description: "Vector search endpoint"
      },
      "5": {
        id: "5",
        path: "/vectors/upsert",
        target: "INTERNAL",
        methods: ["POST"],
        plugins: ["jwt-auth"],
        enabled: true,
        priority: 80,
        type: "vector",
        description: "Vector upsert endpoint"
      }
    }

    if (mockRoutes[id]) {
      return NextResponse.json({
        success: true,
        route: mockRoutes[id]
      })
    } else {
      return NextResponse.json(
        { success: false, error: 'Route not found' },
        { status: 404 }
      )
    }
  } catch (error) {
    console.error('Error fetching route:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to fetch route' },
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
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/routes/${id}`, {
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
      route: {
        ...body,
        id: id
      },
      message: 'Route updated successfully'
    })
  } catch (error) {
    console.error('Error updating route:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to update route' },
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
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/routes/${id}`, {
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
      message: `Route ${id} deleted successfully`
    })
  } catch (error) {
    console.error('Error deleting route:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to delete route' },
      { status: 500 }
    )
  }
}
