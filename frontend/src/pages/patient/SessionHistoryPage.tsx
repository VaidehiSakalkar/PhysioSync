import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { sessionService } from '../../services/sessionService'
import { SessionLog } from '../../services/exerciseService'
import { Header } from '../../components/layout/Header'
import { Card } from '../../components/ui/Card'
import { Badge } from '../../components/ui/Badge'
import { Spinner } from '../../components/ui/Spinner'
import { useToast } from '../../components/ui/Toast'
import {
  ChevronLeft, Activity, Target, Clock, TrendingUp,
  BarChart2, CheckCircle2, XCircle, ChevronDown, ChevronUp
} from 'lucide-react'
import {
  Chart as ChartJS,
  CategoryScale, LinearScale, PointElement, LineElement,
  BarElement, Title, Tooltip, Legend, Filler
} from 'chart.js'
import { Line, Bar } from 'react-chartjs-2'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement,
  BarElement, Title, Tooltip, Legend, Filler)

function AccuracyBadge({ value }: { value: number }) {
  const pct = Math.round(value * 100)
  if (pct >= 80) return <Badge color="good">{pct}%</Badge>
  if (pct >= 50) return <Badge color="warning">{pct}%</Badge>
  return <Badge color="danger">{pct}%</Badge>
}

function formatDuration(seconds?: number): string {
  if (!seconds) return '—'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return m > 0 ? `${m}m ${s}s` : `${s}s`
}

function SessionDetail({ session, onClose }: { session: SessionLog; onClose: () => void }) {
  const repAngles = session.repAngles ?? []

  const chartData = {
    labels: repAngles.map((_, i) => `Rep ${i + 1}`),
    datasets: [
      {
        label: 'Measured Angle (°)',
        data: repAngles,
        borderColor: '#0d9488',
        backgroundColor: 'rgba(13, 148, 136, 0.15)',
        fill: true,
        tension: 0.3,
        pointRadius: 4,
        pointBackgroundColor: '#0d9488',
      },
    ],
  }

  const chartOptions = {
    responsive: true,
    plugins: {
      legend: { display: false },
      tooltip: { callbacks: { label: (ctx: any) => `${ctx.raw}°` } },
    },
    scales: {
      y: {
        ticks: { color: '#94a3b8', callback: (v: any) => `${v}°` },
        grid: { color: 'rgba(255,255,255,0.05)' },
      },
      x: {
        ticks: { color: '#94a3b8' },
        grid: { color: 'rgba(255,255,255,0.05)' },
      },
    },
  }

  return (
    <div className="animate-fade-in">
      <Card className="mb-4">
        <div className="flex items-center justify-between mb-4">
          <h3 className="font-semibold text-white">Rep-by-Rep Angle Analysis</h3>
          <button onClick={onClose} className="text-slate-500 hover:text-white transition-colors">
            <ChevronUp className="h-5 w-5" />
          </button>
        </div>

        {repAngles.length === 0 ? (
          <p className="text-slate-500 text-sm text-center py-6">No per-rep data recorded for this session.</p>
        ) : (
          <>
            <Line data={chartData} options={chartOptions} />
            <div className="mt-4 grid grid-cols-3 gap-3">
              <div className="rounded-xl bg-white/5 p-3 text-center">
                <p className="text-xs text-slate-500 mb-0.5">Min Angle</p>
                <p className="text-lg font-bold text-white">{Math.min(...repAngles).toFixed(1)}°</p>
              </div>
              <div className="rounded-xl bg-white/5 p-3 text-center">
                <p className="text-xs text-slate-500 mb-0.5">Max Angle</p>
                <p className="text-lg font-bold text-white">{Math.max(...repAngles).toFixed(1)}°</p>
              </div>
              <div className="rounded-xl bg-white/5 p-3 text-center">
                <p className="text-xs text-slate-500 mb-0.5">Avg Angle</p>
                <p className="text-lg font-bold text-white">
                  {(repAngles.reduce((a, b) => a + b, 0) / repAngles.length).toFixed(1)}°
                </p>
              </div>
            </div>
          </>
        )}
      </Card>
    </div>
  )
}

export function SessionHistoryPage() {
  const navigate = useNavigate()
  const { toast } = useToast()

  const [sessions, setSessions] = useState<SessionLog[]>([])
  const [loading, setLoading] = useState(true)
  const [expandedId, setExpandedId] = useState<string | null>(null)

  useEffect(() => {
    sessionService.getMyRecentSessions(50)
      .then(data => {
        setSessions(data.sort(
          (a, b) => new Date(b.sessionTime).getTime() - new Date(a.sessionTime).getTime()
        ))
      })
      .catch(() => toast('Failed to load session history', 'error'))
      .finally(() => setLoading(false))
  }, [])

  const summary = sessionService.computeSummary(sessions)

  // Accuracy trend chart (last 10 sessions, chronological)
  const recent10 = [...sessions].reverse().slice(-10)
  const trendData = {
    labels: recent10.map((_, i) => `S${i + 1}`),
    datasets: [{
      label: 'Accuracy %',
      data: recent10.map(s => Math.round(s.avgAngleAccuracy * 100)),
      borderColor: '#0d9488',
      backgroundColor: 'rgba(13,148,136,0.15)',
      fill: true,
      tension: 0.4,
      pointBackgroundColor: '#0d9488',
    }],
  }

  const repData = {
    labels: recent10.map((_, i) => `S${i + 1}`),
    datasets: [{
      label: 'Reps',
      data: recent10.map(s => s.repsCompleted),
      backgroundColor: 'rgba(99, 102, 241, 0.6)',
      borderRadius: 8,
      borderSkipped: false,
    }],
  }

  const chartOptions = {
    responsive: true,
    plugins: { legend: { display: false } },
    scales: {
      y: { ticks: { color: '#94a3b8' }, grid: { color: 'rgba(255,255,255,0.05)' } },
      x: { ticks: { color: '#94a3b8' }, grid: { color: 'rgba(255,255,255,0.05)' } },
    },
  }

  if (loading) return (
    <div className="min-h-screen bg-surface-900 flex items-center justify-center">
      <Spinner className="h-12 w-12" />
    </div>
  )

  return (
    <div className="min-h-screen bg-surface-900">
      <Header />
      <main className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8 py-8">

        {/* Heading */}
        <div className="flex items-center gap-3 mb-8">
          <button
            onClick={() => navigate('/patient')}
            className="p-2 rounded-xl bg-white/5 hover:bg-white/10 transition-colors text-slate-400 hover:text-white"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-white">Session History</h1>
            <p className="text-slate-400 text-sm mt-0.5">All your exercise sessions in one place</p>
          </div>
        </div>

        {/* Summary stats */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
          {[
            { icon: <Activity className="h-5 w-5 text-primary-400" />, label: 'Total Sessions', value: summary.totalSessions.toString() },
            { icon: <Target className="h-5 w-5 text-emerald-400" />,   label: 'Avg Accuracy',  value: `${Math.round(summary.avgAccuracy * 100)}%` },
            { icon: <BarChart2 className="h-5 w-5 text-indigo-400" />, label: 'Total Reps',    value: summary.totalReps.toString() },
            { icon: <Clock className="h-5 w-5 text-amber-400" />,      label: 'Time Trained',  value: formatDuration(summary.totalDurationSeconds) },
          ].map(({ icon, label, value }) => (
            <Card key={label} padding="sm">
              <div className="flex items-center gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/10 shrink-0">
                  {icon}
                </div>
                <div>
                  <p className="text-xs text-slate-400">{label}</p>
                  <p className="text-xl font-bold text-white">{value}</p>
                </div>
              </div>
            </Card>
          ))}
        </div>

        {/* Charts row */}
        {sessions.length >= 2 && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
            <Card>
              <div className="flex items-center gap-2 mb-4">
                <TrendingUp className="h-4 w-4 text-primary-400" />
                <h2 className="text-sm font-semibold text-slate-300">Accuracy Trend</h2>
              </div>
              <Line data={trendData} options={{
                ...chartOptions,
                scales: {
                  ...chartOptions.scales,
                  y: { ...chartOptions.scales.y, min: 0, max: 100,
                    ticks: { ...chartOptions.scales.y.ticks, callback: (v: any) => `${v}%` } },
                },
              }} />
            </Card>
            <Card>
              <div className="flex items-center gap-2 mb-4">
                <BarChart2 className="h-4 w-4 text-indigo-400" />
                <h2 className="text-sm font-semibold text-slate-300">Reps per Session</h2>
              </div>
              <Bar data={repData} options={chartOptions} />
            </Card>
          </div>
        )}

        {/* Session log table */}
        <section>
          <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-3">
            All Sessions ({sessions.length})
          </h2>
          {sessions.length === 0 ? (
            <Card>
              <p className="text-slate-500 text-sm text-center py-8">
                No sessions recorded yet. Start an exercise to begin tracking!
              </p>
            </Card>
          ) : (
            <div className="space-y-2">
              {sessions.map(s => {
                const isExpanded = expandedId === s.id
                const pct = Math.round(s.avgAngleAccuracy * 100)
                return (
                  <div key={s.id}>
                    <button
                      onClick={() => setExpandedId(isExpanded ? null : s.id)}
                      className={`w-full text-left glass-card p-4 hover:bg-white/10 transition-all duration-200
                        ${isExpanded ? 'border-primary-500/40 bg-primary-500/5' : ''}`}
                    >
                      <div className="flex items-center gap-4">
                        {/* Status icon */}
                        <div className="shrink-0">
                          {pct >= 70
                            ? <CheckCircle2 className="h-5 w-5 text-emerald-400" />
                            : <XCircle className="h-5 w-5 text-rose-400" />
                          }
                        </div>

                        {/* Date */}
                        <div className="min-w-[120px]">
                          <p className="text-sm font-medium text-white">
                            {new Date(s.sessionTime).toLocaleDateString('en-US', {
                              weekday: 'short', month: 'short', day: 'numeric'
                            })}
                          </p>
                          <p className="text-xs text-slate-500">
                            {new Date(s.sessionTime).toLocaleTimeString('en-US', {
                              hour: '2-digit', minute: '2-digit'
                            })}
                          </p>
                        </div>

                        {/* Stats */}
                        <div className="flex-1 flex items-center gap-4 flex-wrap">
                          <div>
                            <p className="text-xs text-slate-500">Reps</p>
                            <p className="text-sm font-semibold text-white">{s.repsCompleted}</p>
                          </div>
                          <div>
                            <p className="text-xs text-slate-500">Duration</p>
                            <p className="text-sm font-semibold text-white">{formatDuration(s.durationSeconds)}</p>
                          </div>
                        </div>

                        {/* Accuracy badge */}
                        <div className="shrink-0 flex items-center gap-2">
                          <AccuracyBadge value={s.avgAngleAccuracy} />
                          {isExpanded
                            ? <ChevronUp className="h-4 w-4 text-slate-500" />
                            : <ChevronDown className="h-4 w-4 text-slate-500" />
                          }
                        </div>
                      </div>

                      {/* Inline accuracy bar */}
                      <div className="mt-3 h-1.5 w-full rounded-full bg-white/10">
                        <div
                          className={`h-1.5 rounded-full transition-all duration-500
                            ${pct >= 80 ? 'bg-emerald-500' : pct >= 50 ? 'bg-amber-500' : 'bg-rose-500'}`}
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </button>

                    {/* Expanded detail */}
                    {isExpanded && (
                      <SessionDetail
                        session={s}
                        onClose={() => setExpandedId(null)}
                      />
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </section>
      </main>
    </div>
  )
}
