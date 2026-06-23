import { useEffect, useRef, useState, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useWebSocket, PoseFrame } from '../../hooks/useWebSocket'
import { exerciseService, Exercise } from '../../services/exerciseService'
import { useAuth } from '../../hooks/useAuth'
import { useToast } from '../../components/ui/Toast'
import { Header } from '../../components/layout/Header'
import { Card } from '../../components/ui/Card'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Camera, CameraOff, RotateCcw, CheckCircle } from 'lucide-react'

const CAPTURE_INTERVAL_MS = 100  // send a frame every 100ms

export function SessionPage() {
  const { exerciseId } = useParams<{ exerciseId: string }>()
  const { userId } = useAuth()
  const { toast } = useToast()
  const navigate = useNavigate()

  const [exercise, setExercise] = useState<Exercise | null>(null)
  const [sessionActive, setSessionActive] = useState(false)
  const [poseData, setPoseData] = useState<PoseFrame | null>(null)
  const [repCount, setRepCount] = useState(0)
  const [accuracyHistory, setAccuracyHistory] = useState<number[]>([])
  const [sessionStartTime, setSessionStartTime] = useState<number | null>(null)

  const videoRef = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const captureIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (exerciseId) exerciseService.getOne(exerciseId).then(setExercise)
  }, [exerciseId])

  const onFrame = useCallback((frame: PoseFrame) => {
    setPoseData(frame)
    setRepCount(frame.repCount)
    if (frame.detected) {
      setAccuracyHistory(prev => [...prev.slice(-50), frame.accuracy])
    }
    drawLandmarks(frame)
  }, [])

  const { sendFrame } = useWebSocket(exerciseId ?? '', onFrame, sessionActive)

  const startSession = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' } })
      streamRef.current = stream
      if (videoRef.current) {
        videoRef.current.srcObject = stream
        await videoRef.current.play()
      }
      setSessionActive(true)
      setSessionStartTime(Date.now())

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

  const stopSession = async () => {
    if (captureIntervalRef.current) clearInterval(captureIntervalRef.current)
    streamRef.current?.getTracks().forEach(t => t.stop())
    setSessionActive(false)

    // Save session log
    if (accuracyHistory.length > 0 && userId && exerciseId) {
      const avgAccuracy = accuracyHistory.reduce((a, b) => a + b, 0) / accuracyHistory.length
      const durationSeconds = sessionStartTime ? Math.round((Date.now() - sessionStartTime) / 1000) : undefined
      try {
        await exerciseService.logSession({
          exerciseId,
          repsCompleted: repCount,
          avgAngleAccuracy: avgAccuracy,
          durationSeconds,
          repAngles: accuracyHistory,
        })
        toast(`Session saved! ${repCount} reps — ${Math.round(avgAccuracy * 100)}% accuracy`, 'success')
        setTimeout(() => navigate('/patient'), 2000)
      } catch {
        toast('Session completed but could not save. Please try again.', 'error')
      }
    }
  }

  const drawLandmarks = (frame: PoseFrame) => {
    const canvas = canvasRef.current
    const video  = videoRef.current
    if (!canvas || !video || !frame.landmarks?.length) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const { width: w, height: h } = canvas

    // Draw landmark dots
    frame.landmarks.forEach(lm => {
      if (lm.visibility < 0.5) return
      ctx.beginPath()
      ctx.arc(lm.x * w, lm.y * h, 4, 0, 2 * Math.PI)
      ctx.fillStyle = frame.accuracy > 0.8 ? '#10b981' : frame.accuracy > 0.5 ? '#f59e0b' : '#ef4444'
      ctx.fill()
    })
  }

  const avgAccuracy = accuracyHistory.length
    ? accuracyHistory.reduce((a, b) => a + b, 0) / accuracyHistory.length
    : 0

  const accuracyColor = avgAccuracy >= 0.8 ? 'good' : avgAccuracy >= 0.5 ? 'warning' : 'danger'

  return (
    <div className="min-h-screen bg-surface-900">
      <Header />
      <main className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 py-6">
        <div className="mb-4">
          <h1 className="text-2xl font-bold text-white">{exercise?.name ?? 'Loading…'}</h1>
          {exercise && (
            <p className="text-slate-400 mt-1">
              Target: {exercise.targetJoint.replace('_', ' ')} · {exercise.targetAngleMin}°–{exercise.targetAngleMax}° · {exercise.targetReps} reps × {exercise.targetSets} sets
            </p>
          )}
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Camera feed */}
          <div className="lg:col-span-2">
            <div className="relative rounded-2xl overflow-hidden bg-black aspect-video">
              <video ref={videoRef} className="absolute inset-0 w-full h-full object-cover" muted playsInline />
              <canvas ref={canvasRef} className="absolute inset-0 w-full h-full object-cover" />

              {!sessionActive && (
                <div className="absolute inset-0 flex items-center justify-center bg-black/70">
                  <div className="text-center">
                    <Camera className="h-16 w-16 text-slate-600 mx-auto mb-4" />
                    <p className="text-slate-400 mb-6">Camera off — start session to begin</p>
                    <Button id="start-session-btn" onClick={startSession} size="lg"
                      icon={<Camera className="h-5 w-5" />}>
                      Start Session
                    </Button>
                  </div>
                </div>
              )}

              {/* Live feedback overlay */}
              {sessionActive && poseData && (
                <div className="absolute top-4 left-4 right-4">
                  <div className="glass-card p-3 text-sm">
                    <p className={`font-medium ${poseData.accuracy >= 0.8 ? 'text-emerald-400' : poseData.accuracy >= 0.5 ? 'text-amber-400' : 'text-rose-400'}`}>
                      {poseData.detected ? poseData.feedback : '⚠ Pose not detected — ensure full body is visible'}
                    </p>
                  </div>
                </div>
              )}
            </div>

            {sessionActive && (
              <div className="flex gap-3 mt-4">
                <Button id="stop-session-btn" onClick={stopSession} variant="danger" icon={<CameraOff className="h-4 w-4" />}>
                  End Session
                </Button>
                <Button variant="ghost" onClick={() => { setRepCount(0); setAccuracyHistory([]) }}
                  icon={<RotateCcw className="h-4 w-4" />}>
                  Reset
                </Button>
              </div>
            )}
          </div>

          {/* HUD */}
          <div className="space-y-4">
            <Card>
              <p className="text-xs text-slate-400 uppercase tracking-wider mb-1">Rep Count</p>
              <p className="text-5xl font-bold text-white">{repCount}</p>
              <p className="text-xs text-slate-500 mt-1">of {exercise?.targetReps ?? '?'} target</p>
            </Card>

            <Card>
              <p className="text-xs text-slate-400 uppercase tracking-wider mb-1">Avg Accuracy</p>
              <div className="flex items-end gap-2">
                <p className="text-4xl font-bold text-white">{Math.round(avgAccuracy * 100)}%</p>
                <Badge color={accuracyColor} className="mb-1">
                  {avgAccuracy >= 0.8 ? 'Excellent' : avgAccuracy >= 0.5 ? 'Fair' : 'Needs Work'}
                </Badge>
              </div>
              {/* Mini accuracy bar */}
              <div className="mt-3 h-2 bg-white/10 rounded-full overflow-hidden">
                <div className={`h-full rounded-full transition-all duration-300 ${avgAccuracy >= 0.8 ? 'bg-emerald-500' : avgAccuracy >= 0.5 ? 'bg-amber-500' : 'bg-rose-500'}`}
                  style={{ width: `${Math.round(avgAccuracy * 100)}%` }} />
              </div>
            </Card>

            {poseData && (
              <Card>
                <p className="text-xs text-slate-400 uppercase tracking-wider mb-1">Joint Angle</p>
                <p className="text-3xl font-bold text-white">{poseData.angle?.toFixed(1)}°</p>
                <p className="text-xs text-slate-500 mt-1">
                  Target: {exercise?.targetAngleMin}°–{exercise?.targetAngleMax}°
                </p>
              </Card>
            )}

            {repCount >= (exercise?.targetReps ?? 999) && (
              <Card className="border-emerald-500/30 bg-emerald-500/10">
                <div className="flex items-center gap-2 text-emerald-400">
                  <CheckCircle className="h-5 w-5" />
                  <p className="font-semibold">Target reached!</p>
                </div>
                <Button size="sm" className="mt-3" onClick={stopSession}>
                  Save & Finish
                </Button>
              </Card>
            )}
          </div>
        </div>
      </main>
    </div>
  )
}
