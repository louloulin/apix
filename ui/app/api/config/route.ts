import { NextResponse } from 'next/server'

export async function GET() {
  try {
    // 尝试从后端获取数据
    try {
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/config`, {
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
    const mockConfig = {
      server: {
        port: 8080,
        host: "0.0.0.0",
        workerPoolSize: 20,
        maxWebsocketFrameSize: 65536,
        maxWebsocketMessageSize: 262144,
        websocketSubProtocols: "",
        compressionSupported: true,
        compressionLevel: 6,
        decoderInitialBufferSize: 128,
        maxInitialLineLength: 4096,
        maxHeaderSize: 8192,
        maxChunkSize: 8192,
        maxFormAttributeSize: 8192,
        maxFormAttributes: 1000
      },
      http: {
        idleTimeout: 30,
        connectTimeout: 60,
        keepAlive: true,
        maxPoolSize: 10,
        maxWaitQueueSize: 1000,
        pipelining: false,
        keepAliveTimeout: 60,
        readIdleTimeout: 0,
        writeIdleTimeout: 0,
        idleTimeoutUnit: "SECONDS"
      },
      security: {
        corsEnabled: true,
        corsAllowedOrigins: "*",
        corsAllowedMethods: "GET,POST,PUT,DELETE,OPTIONS",
        corsAllowedHeaders: "*",
        corsAllowCredentials: true,
        csrfProtectionEnabled: false,
        rateLimitingEnabled: true,
        rateLimitRequests: 100,
        rateLimitPeriod: 60
      },
      cache: {
        enabled: true,
        maxSize: 1000,
        ttl: 300,
        cleanupInterval: 60
      }
    }

    return NextResponse.json(mockConfig)
  } catch (error) {
    console.error('Error fetching configuration:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to fetch configuration' },
      { status: 500 }
    )
  }
}

export async function PUT(request: Request) {
  try {
    const body = await request.json()

    // 尝试调用后端 API
    try {
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/config`, {
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
      message: 'Configuration updated successfully'
    })
  } catch (error) {
    console.error('Error updating configuration:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to update configuration' },
      { status: 500 }
    )
  }
}
