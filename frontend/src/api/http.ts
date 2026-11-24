import type { ApiResult } from '../types/world'

const JSON_HEADERS = { 'Content-Type': 'application/json' }

export class HttpError extends Error {
  constructor(
    message: string,
    public status?: number,
  ) {
    super(message)
  }
}

async function parseResult<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new HttpError(`HTTP ${res.status}`, res.status)
  }
  const body = (await res.json()) as ApiResult<T>
  if (body.code !== 0) {
    throw new HttpError(body.message || '请求失败')
  }
  return body.data
}

/** 带超时的 GET */
export async function getJson<T>(url: string, timeoutMs = 8000): Promise<T> {
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), timeoutMs)
  try {
    const res = await fetch(url, { signal: ctrl.signal })
    return await parseResult<T>(res)
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      throw new HttpError(`请求超时（>${timeoutMs}ms），请确认后端已启动`)
    }
    throw e
  } finally {
    clearTimeout(timer)
  }
}

/** 带超时的 POST */
export async function postJson<T>(url: string, timeoutMs = 15000): Promise<T> {
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), timeoutMs)
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: JSON_HEADERS,
      signal: ctrl.signal,
    })
    return await parseResult<T>(res)
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      throw new HttpError(`请求超时（>${timeoutMs}ms）`)
    }
    throw e
  } finally {
    clearTimeout(timer)
  }
}
