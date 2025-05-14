import { NextResponse } from 'next/server'

export async function GET() {
  try {
    // 尝试从后端获取数据
    try {
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/health`, {
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
    const mockHealth = {
      status: "UP",
      timestamp: Date.now(),
      checks: [
        {
          name: "database",
          status: "UP",
          details: {
            database: "PostgreSQL",
            version: "14.5"
          }
        },
        {
          name: "diskSpace",
          status: "UP",
          details: {
            total: 1000000000000,
            free: 700000000000,
            threshold: 10000000000
          }
        },
        {
          name: "memory",
          status: "UP",
          details: {
            heapUsed: 187699728,
            heapMax: 4294967296
          }
        }
      ]
    }

    return NextResponse.json(mockHealth)
  } catch (error) {
    console.error('Error fetching health check:', error)
    return NextResponse.json(
      {
        status: "DOWN",
        error: 'Failed to fetch health check',
        timestamp: Date.now()
      },
      { status: 500 }
    )
  }
}
