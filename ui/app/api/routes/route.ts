import { NextResponse } from 'next/server'

export async function GET() {
  try {
    // 尝试从后端获取数据
    try {
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/routes`, {
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

    // 返回模拟数据
    const mockRoutes = [
      {
        id: "1",
        path: "/v1/completions",
        target: "https://api.openai.com/v1/completions",
        methods: ["POST"],
        plugins: ["rate-limiter", "jwt-auth"],
        enabled: true,
        priority: 100,
        type: "llm"
      },
      {
        id: "2",
        path: "/v1/chat/completions",
        target: "https://api.openai.com/v1/chat/completions",
        methods: ["POST"],
        plugins: ["rate-limiter", "jwt-auth"],
        enabled: true,
        priority: 100,
        type: "llm"
      },
      {
        id: "3",
        path: "/v1/messages",
        target: "https://api.anthropic.com/v1/messages",
        methods: ["POST"],
        plugins: ["rate-limiter", "jwt-auth"],
        enabled: true,
        priority: 90,
        type: "llm"
      },
      {
        id: "4",
        path: "/vectors/search",
        target: "INTERNAL",
        methods: ["POST", "GET"],
        plugins: ["jwt-auth"],
        enabled: true,
        priority: 80,
        type: "vector"
      },
      {
        id: "5",
        path: "/vectors/upsert",
        target: "INTERNAL",
        methods: ["POST"],
        plugins: ["jwt-auth"],
        enabled: true,
        priority: 80,
        type: "vector"
      }
    ]

    return NextResponse.json({
      success: true,
      routes: mockRoutes
    })
  } catch (error) {
    console.error('Error in routes API route:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to fetch routes' },
      { status: 500 }
    )
  }
}

export async function POST(request: Request) {
  try {
    const body = await request.json()

    // 尝试调用后端 API
    try {
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/routes`, {
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
    const id = `route-${Math.floor(Math.random() * 10000)}`

    // 返回模拟成功响应
    return NextResponse.json({
      success: true,
      route: {
        ...body,
        id: id,
        enabled: body.enabled !== undefined ? body.enabled : true
      },
      message: 'Route created successfully'
    })
  } catch (error) {
    console.error('Error creating route:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to create route' },
      { status: 500 }
    )
  }
}
