"use client"

import { useState, useEffect } from 'react'

interface UseApiDataOptions<T> {
  initialData?: T
  onSuccess?: (data: T) => void
  onError?: (error: Error) => void
  enabled?: boolean
}

/**
 * Hook for fetching data from an API with loading and error states
 */
export function useApiData<T>(
  fetchFn: () => Promise<T>,
  options: UseApiDataOptions<T> = {}
) {
  const { initialData, onSuccess, onError, enabled = true } = options
  
  const [data, setData] = useState<T | undefined>(initialData)
  const [isLoading, setIsLoading] = useState(enabled)
  const [error, setError] = useState<Error | null>(null)
  const [isRefetching, setIsRefetching] = useState(false)

  const fetchData = async (showLoading = true) => {
    try {
      if (showLoading) {
        setIsLoading(true)
      } else {
        setIsRefetching(true)
      }
      
      setError(null)
      const result = await fetchFn()
      setData(result)
      
      if (onSuccess) {
        onSuccess(result)
      }
      
      return result
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err))
      setError(error)
      
      if (onError) {
        onError(error)
      }
      
      return undefined
    } finally {
      if (showLoading) {
        setIsLoading(false)
      } else {
        setIsRefetching(false)
      }
    }
  }

  const refetch = () => fetchData(false)

  useEffect(() => {
    if (enabled) {
      fetchData()
    }
  }, [enabled])

  return {
    data,
    isLoading,
    error,
    refetch,
    isRefetching
  }
}

/**
 * Hook for mutating data with an API
 */
export function useApiMutation<T, R = T>(
  mutationFn: (data: T) => Promise<R>,
  options: {
    onSuccess?: (data: R, variables: T) => void
    onError?: (error: Error, variables: T) => void
  } = {}
) {
  const { onSuccess, onError } = options
  
  const [data, setData] = useState<R | undefined>(undefined)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<Error | null>(null)

  const mutate = async (variables: T) => {
    try {
      setIsLoading(true)
      setError(null)
      
      const result = await mutationFn(variables)
      setData(result)
      
      if (onSuccess) {
        onSuccess(result, variables)
      }
      
      return result
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err))
      setError(error)
      
      if (onError) {
        onError(error, variables)
      }
      
      throw error
    } finally {
      setIsLoading(false)
    }
  }

  return {
    mutate,
    data,
    isLoading,
    error,
    reset: () => {
      setData(undefined)
      setError(null)
    }
  }
}
