import { useRef, useState, useCallback, useEffect } from 'react'
import { useWebSocket, PoseFrame } from '../../hooks/useWebSocket'
import { Header } from '../../components/layout/Header'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Camera, CameraOff } from 'lucide-react'
import { useToast } from '../../components/ui/Toast'

const CAPTURE_INTERVAL_MS = 100

export function PoseSandboxPage() {
  const { toast } = useToast()
  
  const [sessionActive, setSessionActive] = useState(false)
  const [poseData, setPoseData] = useState<PoseFrame | null>(null)
  
  const videoRef = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const captureIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const onFrame = useCallback((frame: PoseFrame) => {
    setPoseData(frame)
    drawLandmarks(frame)
  }, [])

  const { sendFrame } = useWebSocket('', onFrame, sessionActive)

  const startSession = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' } })
      streamRef.current = stream
      if (videoRef.current) {
        videoRef.current.srcObject = stream
        await videoRef.current.play()
      }
      setSessionActive(true)

      captureIntervalRef.current = setInterval(() => {
        if (!videoRef.current || !canvasRef.current) return
        const ctx = canvasRef.current.getContext('2d')
        if (!ctx) return
        canvasRef.current.width  = videoRef.current.videoWidth
        canvasRef.current.height = videoRef.current.videoHeight
        ctx.drawImage(videoRef.current, 0, 0)
        const base64 = canvasRef.current.toDataURL('image/jpeg', 0.6).split(',')[1]
        sendFrame(base64)
      }, CAPTURE_INTERVAL_MS)
    } catch {
      toast('Camera access denied. Please allow camera permissions.', 'error')
    }
  }

  const stopSession = () => {
    if (captureIntervalRef.current) clearInterval(captureIntervalRef.current)
    streamRef.current?.getTracks().forEach(t => t.stop())
    setSessionActive(false)
    setPoseData(null)
  }

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (captureIntervalRef.current) clearInterval(captureIntervalRef.current)
      streamRef.current?.getTracks().forEach(t => t.stop())
    }
  }, [])

  const drawLandmarks = (frame: PoseFrame) => {
    const canvas = canvasRef.current
    const video  = videoRef.current
    if (!canvas || !video || !frame.landmarks?.length) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const { width: w, height: h } = canvas

    frame.landmarks.forEach(lm => {
      if (lm.visibility < 0.5) return
      ctx.beginPath()
      ctx.arc(lm.x * w, lm.y * h, 4, 0, 2 * Math.PI)
      ctx.fillStyle = '#14b8a6' // primary-500
      ctx.fill()
    })
  }

  return (
    <div className="min-h-screen bg-surface-900">
      <Header />
      <main className="mx-auto max-w-5xl px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-white">Pose Detection Sandbox</h1>
          <p className="text-slate-400 mt-1">Test your camera and real-time pose estimation</p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2">
            <div className="relative rounded-2xl overflow-hidden bg-black aspect-video shadow-lg">
              <video ref={videoRef} className="absolute inset-0 w-full h-full object-cover" muted playsInline />
              <canvas ref={canvasRef} className="absolute inset-0 w-full h-full object-cover" />

              {!sessionActive && (
                <div className="absolute inset-0 flex items-center justify-center bg-black/70">
                  <div className="text-center">
                    <Camera className="h-16 w-16 text-slate-600 mx-auto mb-4" />
                    <p className="text-slate-400 mb-6">Camera off — start to test pose detection</p>
                    <Button onClick={startSession} size="lg" icon={<Camera className="h-5 w-5" />}>
                      Start Camera
                    </Button>
                  </div>
                </div>
              )}
            </div>

            {sessionActive && (
              <div className="mt-4 flex gap-3">
                <Button onClick={stopSession} variant="danger" icon={<CameraOff className="h-4 w-4" />}>
                  Stop Camera
                </Button>
              </div>
            )}
          </div>

          <div className="space-y-4">
            <Card>
              <p className="text-sm font-medium text-slate-400 uppercase tracking-wider mb-2">Detection Status</p>
              {sessionActive ? (
                poseData?.detected ? (
                  <p className="text-emerald-400 font-semibold flex items-center gap-2">
                    <span className="relative flex h-3 w-3">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                      <span className="relative inline-flex rounded-full h-3 w-3 bg-emerald-500"></span>
                    </span>
                    Pose Detected
                  </p>
                ) : (
                  <p className="text-amber-400 font-semibold">No Pose Detected</p>
                )
              ) : (
                <p className="text-slate-500">Inactive</p>
              )}
            </Card>

            <Card>
              <p className="text-sm font-medium text-slate-400 uppercase tracking-wider mb-2">Landmarks Tracked</p>
              <p className="text-3xl font-bold text-white">
                {poseData?.landmarks ? poseData.landmarks.length : 0}
              </p>
              <p className="text-xs text-slate-500 mt-1">out of 33 Keypoints</p>
            </Card>
          </div>
        </div>
      </main>
    </div>
  )
}
