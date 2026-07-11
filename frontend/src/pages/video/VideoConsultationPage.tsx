import { useEffect, useRef, useState, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Header } from '../../components/layout/Header'
import { Button } from '../../components/ui/Button'
import { useToast } from '../../components/ui/Toast'
import { Video, VideoOff, Mic, MicOff, PhoneOff, Monitor } from 'lucide-react'

const _videoWsBase = import.meta.env.VITE_VIDEO_WS_URL ?? 'ws://localhost:8084'
const SIGNAL_URL = `${_videoWsBase.replace(/\/+$/, '')}/ws/video`

export function VideoConsultationPage() {
  const { roomId } = useParams<{ roomId: string }>()
  const { toast } = useToast()
  const navigate = useNavigate()

  const localVideoRef  = useRef<HTMLVideoElement>(null)
  const remoteVideoRef = useRef<HTMLVideoElement>(null)
  const peerRef        = useRef<RTCPeerConnection | null>(null)
  const wsRef          = useRef<WebSocket | null>(null)
  const localStreamRef = useRef<MediaStream | null>(null)

  const [videoEnabled, setVideoEnabled] = useState(true)
  const [audioEnabled, setAudioEnabled] = useState(true)
  const [connected, setConnected]       = useState(false)
  const [callStarted, setCallStarted]   = useState(false)

  const send = useCallback((msg: object) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ ...msg, roomId }))
    }
  }, [roomId])

  const createPeer = useCallback(async () => {
    // Fetch ICE config from video-service
    let iceServers = [{ urls: 'stun:stun.l.google.com:19302' }]
    try {
      const res = await fetch('/api/video/ice-config')
      const cfg = await res.json()
      iceServers = cfg.iceServers
    } catch { /* use default STUN */ }

    const pc = new RTCPeerConnection({ iceServers })
    peerRef.current = pc

    // Send ICE candidates as they are gathered
    pc.onicecandidate = e => {
      if (e.candidate) send({ type: 'candidate', payload: e.candidate })
    }

    // Show remote stream
    pc.ontrack = e => {
      if (remoteVideoRef.current) {
        remoteVideoRef.current.srcObject = e.streams[0]
        setConnected(true)
      }
    }

    // Add local tracks
    localStreamRef.current?.getTracks().forEach(t => pc.addTrack(t, localStreamRef.current!))

    return pc
  }, [send])

  useEffect(() => {
    const ws = new WebSocket(SIGNAL_URL)
    wsRef.current = ws

    ws.onopen = () => send({ type: 'join' })

    ws.onmessage = async (ev) => {
      const msg = JSON.parse(ev.data)
      const pc = peerRef.current

      if (msg.type === 'offer') {
        const peer = await createPeer()
        await peer.setRemoteDescription(new RTCSessionDescription(msg.payload))
        const answer = await peer.createAnswer()
        await peer.setLocalDescription(answer)
        send({ type: 'answer', payload: answer })
      } else if (msg.type === 'answer' && pc) {
        await pc.setRemoteDescription(new RTCSessionDescription(msg.payload))
      } else if (msg.type === 'candidate' && pc) {
        await pc.addIceCandidate(new RTCIceCandidate(msg.payload))
      }
    }

    return () => ws.close()
  }, [createPeer, send])

  const startCall = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
      localStreamRef.current = stream
      if (localVideoRef.current) {
        localVideoRef.current.srcObject = stream
        localVideoRef.current.muted = true
      }

      const pc = await createPeer()
      const offer = await pc.createOffer()
      await pc.setLocalDescription(offer)
      send({ type: 'offer', payload: offer })
      setCallStarted(true)
    } catch {
      toast('Could not access camera/microphone', 'error')
    }
  }

  const endCall = () => {
    localStreamRef.current?.getTracks().forEach(t => t.stop())
    peerRef.current?.close()
    wsRef.current?.close()
    navigate(-1)
  }

  const toggleVideo = () => {
    localStreamRef.current?.getVideoTracks().forEach(t => { t.enabled = !t.enabled })
    setVideoEnabled(v => !v)
  }

  const toggleAudio = () => {
    localStreamRef.current?.getAudioTracks().forEach(t => { t.enabled = !t.enabled })
    setAudioEnabled(a => !a)
  }

  return (
    <div className="min-h-screen bg-surface-950 flex flex-col">
      <Header />
      <main className="flex-1 flex flex-col p-4 max-w-6xl mx-auto w-full">
        <div className="mb-4 flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold text-white">Video Consultation</h1>
            <p className="text-sm text-slate-400">Room: <code className="text-primary-400">{roomId}</code></p>
          </div>
          {connected && <span className="flex items-center gap-2 text-emerald-400 text-sm">
            <span className="h-2 w-2 bg-emerald-400 rounded-full animate-pulse" />
            Connected
          </span>}
        </div>

        {/* Video grid */}
        <div className="flex-1 grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
          {/* Remote */}
          <div className="relative rounded-2xl overflow-hidden bg-black min-h-[300px]">
            <video ref={remoteVideoRef} autoPlay playsInline className="w-full h-full object-cover" />
            {!connected && (
              <div className="absolute inset-0 flex items-center justify-center text-slate-500">
                <div className="text-center">
                  <Monitor className="h-16 w-16 mx-auto mb-3 opacity-30" />
                  <p>Waiting for other participant…</p>
                </div>
              </div>
            )}
            <div className="absolute bottom-3 left-3 text-xs bg-black/60 px-2 py-1 rounded-lg text-slate-300">
              Remote
            </div>
          </div>

          {/* Local */}
          <div className="relative rounded-2xl overflow-hidden bg-black min-h-[300px]">
            <video ref={localVideoRef} autoPlay playsInline muted className="w-full h-full object-cover" />
            {!callStarted && (
              <div className="absolute inset-0 flex items-center justify-center">
                <Button onClick={startCall} size="lg" icon={<Video className="h-5 w-5" />}>
                  Join Call
                </Button>
              </div>
            )}
            <div className="absolute bottom-3 left-3 text-xs bg-black/60 px-2 py-1 rounded-lg text-slate-300">
              You
            </div>
          </div>
        </div>

        {/* Controls */}
        {callStarted && (
          <div className="flex items-center justify-center gap-4">
            <button onClick={toggleAudio}
              className={`h-12 w-12 rounded-full flex items-center justify-center transition-colors ${audioEnabled ? 'bg-white/20 hover:bg-white/30' : 'bg-rose-600 hover:bg-rose-500'}`}>
              {audioEnabled ? <Mic className="h-5 w-5 text-white" /> : <MicOff className="h-5 w-5 text-white" />}
            </button>
            <button onClick={toggleVideo}
              className={`h-12 w-12 rounded-full flex items-center justify-center transition-colors ${videoEnabled ? 'bg-white/20 hover:bg-white/30' : 'bg-rose-600 hover:bg-rose-500'}`}>
              {videoEnabled ? <Video className="h-5 w-5 text-white" /> : <VideoOff className="h-5 w-5 text-white" />}
            </button>
            <button onClick={endCall}
              className="h-12 w-12 rounded-full bg-rose-600 hover:bg-rose-500 flex items-center justify-center transition-colors">
              <PhoneOff className="h-5 w-5 text-white" />
            </button>
          </div>
        )}
      </main>
    </div>
  )
}
