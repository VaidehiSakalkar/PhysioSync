import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { exerciseService, Routine, SessionLog, RecoveryPlan } from '../../services/exerciseService'
import { appointmentService, Appointment } from '../../services/appointmentService'
import { Header } from '../../components/layout/Header'
import { Card } from '../../components/ui/Card'
import { Badge } from '../../components/ui/Badge'
import { Spinner } from '../../components/ui/Spinner'
import { Button } from '../../components/ui/Button'
import { Activity, Calendar, Dumbbell, TrendingUp, ChevronRight, Target, FileText } from 'lucide-react'
import {
  Chart as ChartJS,
  CategoryScale, LinearScale, PointElement, LineElement,
  Title, Tooltip, Legend, Filler
} from 'chart.js'
import { Line } from 'react-chartjs-2'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler)

export function PatientDashboard() {
  const { name } = useAuth()
  const [routines, setRoutines] = useState<Routine[]>([])
  const [sessions, setSessions] = useState<SessionLog[]>([])
  const [plans, setPlans] = useState<RecoveryPlan[]>([])
  const [appointments, setAppointments] = useState<Appointment[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      exerciseService.getMyRoutines(),
      exerciseService.getMyRecentSessions(),
      exerciseService.getMyPlans(),
      appointmentService.getMyAppointments(),
    ]).then(([r, s, p, a]) => {
      setRoutines(r)
      setSessions(s)
      setPlans(p)
      setAppointments(a.filter(ap => ap.status === 'SCHEDULED' || ap.status === 'CONFIRMED'))
    }).finally(() => setLoading(false))
  }, [])

  const avgAccuracy = sessions.length
    ? Math.round((sessions.reduce((sum, s) => sum + s.avgAngleAccuracy, 0) / sessions.length) * 100)
    : 0

  const chartData = {
    labels: sessions.slice().reverse().map((_, i) => `Session ${i + 1}`),
    datasets: [{
      label: 'Angle Accuracy %',
      data: sessions.slice().reverse().map(s => Math.round(s.avgAngleAccuracy * 100)),
      borderColor: '#0d9488',
      backgroundColor: 'rgba(13, 148, 136, 0.1)',
      fill: true,
      tension: 0.4,
      pointBackgroundColor: '#0d9488',
    }],
  }

  const chartOptions = {
    responsive: true,
    plugins: { legend: { display: false } },
    scales: {
      y: { min: 0, max: 100, ticks: { color: '#94a3b8' }, grid: { color: 'rgba(255,255,255,0.05)' } },
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
      <main className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 py-8">

        {/* Welcome */}
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-white">
            Good morning, {name?.split(' ')[0]} 👋
          </h1>
          <p className="mt-1 text-slate-400">Here's your rehabilitation overview</p>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          <StatCard icon={<Dumbbell className="h-5 w-5 text-primary-400" />}
            label="Active Routines" value={routines.length.toString()} />
          <StatCard icon={<Activity className="h-5 w-5 text-emerald-400" />}
            label="Sessions Done" value={sessions.length.toString()} />
          <StatCard icon={<Target className="h-5 w-5 text-amber-400" />}
            label="Avg Accuracy" value={`${avgAccuracy}%`}
            color={avgAccuracy >= 80 ? 'text-emerald-400' : avgAccuracy >= 60 ? 'text-amber-400' : 'text-rose-400'} />
          <StatCard icon={<Calendar className="h-5 w-5 text-sky-400" />}
            label="Upcoming Appts" value={appointments.length.toString()} />
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

          {/* Active Routines */}
          <div className="lg:col-span-1">
            <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-3">
              Active Routines
            </h2>
            <div className="space-y-3">
              {routines.length === 0 && (
                <Card><p className="text-slate-500 text-sm">No active routines yet</p></Card>
              )}
              {routines.map(r => (
                <Card key={r.id} hover padding="sm">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-white">{r.name}</p>
                      <p className="text-xs text-slate-400 mt-0.5">{r.exercises.length} exercises</p>
                    </div>
                    <div className="flex flex-col items-end gap-2">
                      {r.exercises.slice(0, 3).map(ex => (
                        <Link key={ex.id} to={`/patient/session/${ex.id}`}>
                          <Button size="sm" variant="secondary" icon={<ChevronRight className="h-3.5 w-3.5" />}>
                            Start
                          </Button>
                        </Link>
                      ))}
                    </div>
                  </div>
                </Card>
              ))}
            </div>
          </div>

          {/* Progress Chart */}
          <div className="lg:col-span-2">
            <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-3">
              Accuracy Progress
            </h2>
            <Card>
              {sessions.length < 2 ? (
                <p className="text-slate-500 text-sm">Complete more sessions to see your progress</p>
              ) : (
                <Line data={chartData} options={chartOptions} />
              )}
            </Card>
          </div>

          {/* Upcoming Appointments */}
          <div className="lg:col-span-1">
            <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-3">
              Upcoming Appointments
            </h2>
            <div className="space-y-3">
              {appointments.length === 0 && (
                <Card><p className="text-slate-500 text-sm">No upcoming appointments</p></Card>
              )}
              {appointments.slice(0, 3).map(a => (
                <Card key={a.id} padding="sm">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-white text-sm">
                        {new Date(a.scheduledAt).toLocaleDateString('en-US', {weekday: 'short', month: 'short', day: 'numeric'})}
                      </p>
                      <p className="text-xs text-slate-400">
                        {new Date(a.scheduledAt).toLocaleTimeString('en-US', {hour: '2-digit', minute: '2-digit'})} · {a.durationMinutes} min
                      </p>
                    </div>
                    <Badge color={a.status === 'CONFIRMED' ? 'good' : 'info'}>
                      {a.status}
                    </Badge>
                  </div>
                </Card>
              ))}
            </div>
          </div>

          {/* Recovery Plans */}
          <div className="lg:col-span-2">
            <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-3">
              Latest Recovery Plan
            </h2>
            {plans.length === 0 ? (
              <Card><p className="text-slate-500 text-sm">No recovery plans generated yet. Complete a session to get your AI plan.</p></Card>
            ) : (
              <Card>
                <div className="flex items-center gap-2 mb-3">
                  <FileText className="h-4 w-4 text-primary-400" />
                  <p className="text-sm text-slate-300">
                    Generated {new Date(plans[0].generatedAt).toLocaleDateString()}
                  </p>
                </div>
                {plans[0].progressionNotes && (
                  <p className="text-slate-300 text-sm mb-3">{plans[0].progressionNotes}</p>
                )}
                {plans[0].redFlags && plans[0].redFlags.length > 0 && (
                  <div className="space-y-1">
                    <p className="text-xs font-medium text-rose-400">⚠ Red Flags</p>
                    {plans[0].redFlags.map((flag, i) => (
                      <p key={i} className="text-xs text-rose-300">{flag}</p>
                    ))}
                  </div>
                )}
              </Card>
            )}
          </div>

        </div>
      </main>
    </div>
  )
}

function StatCard({ icon, label, value, color = 'text-white' }: {
  icon: React.ReactNode; label: string; value: string; color?: string
}) {
  return (
    <Card padding="sm">
      <div className="flex items-center gap-3">
        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/10">
          {icon}
        </div>
        <div>
          <p className="text-xs text-slate-400">{label}</p>
          <p className={`text-xl font-bold ${color}`}>{value}</p>
        </div>
      </div>
    </Card>
  )
}
