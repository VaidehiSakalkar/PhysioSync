import { useEffect, useRef, useCallback } from 'react'

const ML_WS_URL = import.meta.env.VITE_ML_WS_URL ?? 'ws://localhost:8000/ws/pose'

export type PoseFrame = {
  detected: boolean
  angle: number
  accuracy: number
  feedback: string
  repCount: number
  landmarks: Array<{ x: number; y: number; z: number; visibility: number }>
}

export function useWebSocket(
  exerciseId: string,
  onFrame: (frame: PoseFrame) => void,
  enabled: boolean
) {
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const connect = useCallback(() => {
    if (!enabled) return
    const ws = new WebSocket(`${ML_WS_URL}?exercise_id=${exerciseId}`)
    wsRef.current = ws

    ws.onmessage = (ev) => {
      try {
        const data: PoseFrame = JSON.parse(ev.data)
        onFrame(data)
      } catch {
        // ignore malformed frames
      }
    }

    ws.onclose = () => {
      if (enabled) {
        reconnectTimer.current = setTimeout(connect, 2000)
      }
    }

    ws.onerror = (e) => {
      console.error('Pose WebSocket error', e)
      ws.close()
    }
  }, [exerciseId, onFrame, enabled])

  useEffect(() => {
    if (enabled) connect()
    return () => {
      if (reconnectTimer.current) clearTimeout(reconnectTimer.current)
      wsRef.current?.close()
    }
  }, [enabled, connect])

  const sendFrame = useCallback((base64Frame: string) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ frame: base64Frame }))
    }
  }, [])

  return { sendFrame }
}
