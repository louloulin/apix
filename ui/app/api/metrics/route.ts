import { NextResponse } from 'next/server'

export async function GET() {
  try {
    // 尝试从后端获取数据
    try {
      const response = await fetch(`${process.env.API_BASE_URL || 'http://localhost:8080'}/admin/metrics`, {
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
    const mockMetrics = {
      timestamp: Date.now(),
      cpu: {
        cores: 8,
        systemLoad: 2.15,
        processCpuLoad: 0.23,
        processCpuTime: 25600000000
      },
      memory: {
        heap: {
          init: 268435456,
          used: 187699728,
          committed: 536870912,
          max: 4294967296
        },
        nonHeap: {
          init: 7667712,
          used: 75231232,
          committed: 79691776,
          max: -1
        }
      },
      threads: {
        count: 32,
        peakCount: 36,
        daemonCount: 22,
        totalStarted: 42,
        threadDetails: {
          RUNNABLE: 12,
          WAITING: 8,
          TIMED_WAITING: 10,
          BLOCKED: 2
        }
      },
      jvm: {
        name: "OpenJDK 64-Bit Server VM",
        vendor: "GraalVM Community",
        version: "17.0.7+7-jvmci-23.0-b12",
        uptime: 3600000,
        startTime: Date.now() - 3600000,
        systemProperties: {
          "java.vm.name": "OpenJDK 64-Bit Server VM",
          "java.vm.version": "17.0.7+7-jvmci-23.0-b12",
          "java.runtime.version": "17.0.7+7-jvmci-23.0-b12",
          "os.name": "Mac OS X",
          "os.arch": "x86_64",
          "file.encoding": "UTF-8"
        }
      },
      os: {
        name: "Mac OS X",
        version: "12.6",
        arch: "x86_64",
        availableProcessors: 8
      },
      gc: {
        collectors: [
          {
            name: "G1 Young Generation",
            collectionCount: 12,
            collectionTime: 235
          },
          {
            name: "G1 Old Generation",
            collectionCount: 2,
            collectionTime: 120
          }
        ]
      }
    }

    return NextResponse.json(mockMetrics)
  } catch (error) {
    console.error('Error fetching metrics:', error)
    return NextResponse.json(
      { success: false, error: 'Failed to fetch metrics' },
      { status: 500 }
    )
  }
}
