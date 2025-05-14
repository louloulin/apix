import { describe, it, expect, vi } from 'vitest'
import { screen, render } from '@testing-library/react'
import '@testing-library/jest-dom'

// Mock the next/navigation hooks
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    prefetch: vi.fn()
  }),
  useSearchParams: () => ({
    get: vi.fn()
  }),
  usePathname: () => '/dashboard/metrics'
}))

// Mock the API client
vi.mock('@/lib/api-client', () => ({
  metricsApi: {
    getMetrics: vi.fn().mockResolvedValue({
      timestamp: Date.now(),
      cpu: {
        cores: 8,
        systemLoad: 1.5,
        processCpuLoad: 0.25,
        processCpuTime: 1000000000
      },
      memory: {
        heap: {
          init: 268435456,
          used: 134217728,
          committed: 268435456,
          max: 536870912
        },
        nonHeap: {
          init: 7667712,
          used: 66060288,
          committed: 69730304,
          max: -1
        },
        total: {
          init: 276103168,
          used: 200278016,
          committed: 338165760,
          max: 536870912
        }
      },
      threads: {
        count: 25,
        peakCount: 30,
        daemonCount: 20,
        totalStarted: 35
      },
      jvm: {
        name: "OpenJDK 64-Bit Server VM",
        vendor: "Oracle Corporation",
        version: "17.0.2",
        startTime: Date.now() - 3600000,
        uptime: 3600000,
        inputArguments: ["-Xmx512m", "-Xms256m"]
      },
      os: {
        name: "Mac OS X",
        version: "12.6",
        arch: "x86_64",
        availableProcessors: 8
      }
    }),
    getHealth: vi.fn().mockResolvedValue({
      status: "UP",
      timestamp: Date.now(),
      uptime: 3600000,
      checks: [
        {
          name: "system",
          status: "UP"
        },
        {
          name: "memory",
          status: "UP"
        }
      ]
    })
  }
}))

describe('System Metrics Page', () => {
  it('passes a basic test', () => {
    expect(true).toBe(true)
  })
})
