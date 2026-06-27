import { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { patientService, PatientProfile } from '../../services/patientService'
import { appointmentService, Appointment } from '../../services/appointmentService'
import { Header } from '../../components/layout/Header'
import { Card } from '../../components/ui/Card'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { useToast } from '../../components/ui/Toast'
import { ChevronLeft, Video, CheckCircle2, XCircle, User } from 'lucide-react'

const STATUS_COLOR: Record<Appointment['status'], 'good' | 'info' | 'neutral' | 'danger'> = {
  SCHEDULED:  'info',
  CONFIRMED:  'good',
  COMPLETED:  'neutral',
  CANCELLED:  'danger',
}

export function PhysioAppointmentsPage() {
  const { toast } = useToast()
  const navigate = useNavigate()

  const [appointments, setAppointments] = useState<Appointment[]>([])
  const [patientsMap, setPatientsMap] = useState<Record<string, PatientProfile>>({})
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      appointmentService.getMyAppointments(),
      patientService.getPhysioPatients(),
    ]).then(([appts, patients]) => {
      setAppointments(appts.sort(
        (a, b) => new Date(b.scheduledAt).getTime() - new Date(a.scheduledAt).getTime()
      ))
      const pMap: Record<string, PatientProfile> = {}
      patients.forEach(p => pMap[p.id] = p)
      setPatientsMap(pMap)
    }).catch(() => {
      toast('Failed to load appointments', 'error')
    }).finally(() => setLoading(false))
  }, [])

  const handleUpdateStatus = async (id: string, newStatus: Appointment['status']) => {
    try {
      const updated = await appointmentService.updateStatus(id, newStatus)
      setAppointments(prev => prev.map(a => a.id === id ? updated : a))
      toast(`Appointment marked as ${newStatus}`, 'success')
    } catch {
      toast('Failed to update status', 'error')
    }
  }

  if (loading) return (
    <div className="min-h-screen bg-surface-900 flex items-center justify-center">
      <Spinner className="h-12 w-12" />
    </div>
  )

  const upcoming = appointments.filter(
    a => a.status === 'SCHEDULED' || a.status === 'CONFIRMED'
  )
  const past = appointments.filter(
    a => a.status === 'COMPLETED' || a.status === 'CANCELLED'
  )

  return (
    <div className="min-h-screen bg-surface-900">
      <Header />
      <main className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8 py-8">
        {/* Page heading */}
        <div className="flex items-center gap-3 mb-8">
          <button
            onClick={() => navigate('/physio')}
            className="p-2 rounded-xl bg-white/5 hover:bg-white/10 transition-colors text-slate-400 hover:text-white"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-white">Appointments</h1>
            <p className="text-slate-400 text-sm mt-0.5">Manage your patient schedule</p>
          </div>
        </div>

        {/* Upcoming Appointments */}
        <section className="mb-8">
          <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-3">
            Upcoming ({upcoming.length})
          </h2>
          {upcoming.length === 0 ? (
            <Card>
              <p className="text-slate-500 text-sm text-center py-4">No upcoming appointments.</p>
            </Card>
          ) : (
            <div className="space-y-4">
              {upcoming.map(a => (
                <AppointmentCard 
                  key={a.id} 
                  appt={a} 
                  patient={patientsMap[a.patientId]}
                  onUpdateStatus={handleUpdateStatus} 
                />
              ))}
            </div>
          )}
        </section>

        {/* Past Appointments */}
        {past.length > 0 && (
          <section>
            <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-3">
              Past
            </h2>
            <div className="space-y-4">
              {past.slice(0, 20).map(a => (
                <AppointmentCard 
                  key={a.id} 
                  appt={a} 
                  patient={patientsMap[a.patientId]}
                  faded 
                />
              ))}
            </div>
          </section>
        )}
      </main>
    </div>
  )
}

function AppointmentCard({ 
  appt, 
  patient,
  faded = false,
  onUpdateStatus
}: { 
  appt: Appointment; 
  patient?: PatientProfile;
  faded?: boolean;
  onUpdateStatus?: (id: string, s: Appointment['status']) => void;
}) {
  const date = new Date(appt.scheduledAt)
  const isUpcoming = appt.status === 'SCHEDULED' || appt.status === 'CONFIRMED'

  return (
    <Card padding="md" className={faded ? 'opacity-60' : ''}>
      <div className="flex flex-col sm:flex-row gap-4 sm:items-center justify-between">
        <div className="flex items-center gap-4">
          {/* Date block */}
          <div className="flex flex-col items-center justify-center min-w-[64px] h-16 rounded-xl bg-white/5 border border-white/10 text-center">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              {date.toLocaleDateString('en-US', { month: 'short' })}
            </span>
            <span className="text-2xl font-bold text-white leading-none mt-1">
              {date.getDate()}
            </span>
          </div>

          {/* Details */}
          <div>
            <div className="flex items-center gap-2 mb-1">
              <User className="h-4 w-4 text-primary-400" />
              <span className="font-medium text-white">{patient?.name || 'Unknown Patient'}</span>
            </div>
            <p className="text-sm text-slate-300">
              {date.toLocaleDateString('en-US', { weekday: 'long' })},{' '}
              {date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })}
            </p>
            <p className="text-xs text-slate-400 mt-0.5">
              {appt.durationMinutes} min session
              {appt.notes ? ` · Note: ${appt.notes}` : ''}
            </p>
          </div>
        </div>

        {/* Status + Actions */}
        <div className="flex flex-col sm:items-end gap-3 mt-4 sm:mt-0">
          <Badge color={STATUS_COLOR[appt.status]}>
            {appt.status}
          </Badge>
          
          <div className="flex items-center gap-2">
            {appt.videoRoomId && isUpcoming && (
              <Link to={`/video/${appt.videoRoomId}`}>
                <Button size="sm" variant="secondary" icon={<Video className="h-3.5 w-3.5" />}>
                  Join Video
                </Button>
              </Link>
            )}

            {isUpcoming && onUpdateStatus && (
              <>
                {appt.status === 'SCHEDULED' && (
                  <Button size="sm" onClick={() => onUpdateStatus(appt.id, 'CONFIRMED')}
                    icon={<CheckCircle2 className="h-3.5 w-3.5" />}>
                    Confirm
                  </Button>
                )}
                {appt.status === 'CONFIRMED' && (
                  <Button size="sm" onClick={() => onUpdateStatus(appt.id, 'COMPLETED')}
                    icon={<CheckCircle2 className="h-3.5 w-3.5" />}>
                    Mark Completed
                  </Button>
                )}
                <Button size="sm" variant="secondary" onClick={() => onUpdateStatus(appt.id, 'CANCELLED')}
                  icon={<XCircle className="h-3.5 w-3.5" />}>
                  Cancel
                </Button>
              </>
            )}
          </div>
        </div>
      </div>
    </Card>
  )
}
