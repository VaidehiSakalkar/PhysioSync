import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { patientService } from '../../services/patientService'
import { appointmentService, Appointment } from '../../services/appointmentService'
import { Header } from '../../components/layout/Header'
import { Card } from '../../components/ui/Card'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { useToast } from '../../components/ui/Toast'
import {
  Calendar, Clock, Plus, X, CheckCircle2, AlertCircle,
  ChevronLeft, Video, Stethoscope
} from 'lucide-react'

const DURATION_OPTIONS = [30, 45, 60]

const STATUS_COLOR: Record<Appointment['status'], 'good' | 'info' | 'neutral' | 'danger'> = {
  SCHEDULED:  'info',
  CONFIRMED:  'good',
  COMPLETED:  'neutral',
  CANCELLED:  'danger',
}

function toLocalDatetimeInput(iso: string) {
  // ISO → 'YYYY-MM-DDTHH:mm'
  return iso.slice(0, 16)
}

function getMinDatetime() {
  const d = new Date()
  d.setMinutes(d.getMinutes() + 30) // at least 30 min from now
  return d.toISOString().slice(0, 16)
}

export function AppointmentBookingPage() {
  const { toast } = useToast()
  const navigate = useNavigate()

  const [appointments, setAppointments] = useState<Appointment[]>([])
  const [physioId, setPhysioId] = useState<string | null>(null)
  const [loading, setLoading]   = useState(true)

  // Booking form state
  const [showForm, setShowForm]     = useState(false)
  const [scheduledAt, setScheduledAt] = useState(getMinDatetime())
  const [duration, setDuration]     = useState(30)
  const [notes, setNotes]           = useState('')
  const [booking, setBooking]       = useState(false)

  useEffect(() => {
    Promise.all([
      patientService.getMyProfile(),
      appointmentService.getMyAppointments(),
    ]).then(([profile, appts]) => {
      setPhysioId(profile.assignedPhysioId ?? null)
      setAppointments(appts.sort(
        (a, b) => new Date(b.scheduledAt).getTime() - new Date(a.scheduledAt).getTime()
      ))
    }).catch(() => {
      toast('Failed to load appointments', 'error')
    }).finally(() => setLoading(false))
  }, [])

  const upcoming = appointments.filter(
    a => a.status === 'SCHEDULED' || a.status === 'CONFIRMED'
  )
  const past = appointments.filter(
    a => a.status === 'COMPLETED' || a.status === 'CANCELLED'
  )

  const handleBook = async () => {
    if (!physioId) {
      toast('You are not assigned to a physiotherapist yet.', 'error')
      return
    }
    if (!scheduledAt) {
      toast('Please select a date and time.', 'error')
      return
    }
    setBooking(true)
    try {
      const appt = await appointmentService.book({
        physioId,
        scheduledAt: new Date(scheduledAt).toISOString(),
        durationMinutes: duration,
        notes: notes.trim() || undefined,
      })
      setAppointments(prev => [appt, ...prev])
      setShowForm(false)
      setNotes('')
      setScheduledAt(getMinDatetime())
      toast('Appointment booked!', 'success')
    } catch {
      toast('Failed to book appointment. Please try again.', 'error')
    } finally {
      setBooking(false)
    }
  }

  if (loading) return (
    <div className="min-h-screen bg-surface-900 flex items-center justify-center">
      <Spinner className="h-12 w-12" />
    </div>
  )

  return (
    <div className="min-h-screen bg-surface-900">
      <Header />
      <main className="mx-auto max-w-3xl px-4 sm:px-6 lg:px-8 py-8">

        {/* Page heading */}
        <div className="flex items-center gap-3 mb-8">
          <button
            onClick={() => navigate('/patient')}
            className="p-2 rounded-xl bg-white/5 hover:bg-white/10 transition-colors text-slate-400 hover:text-white"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-white">Appointments</h1>
            <p className="text-slate-400 text-sm mt-0.5">Manage your physiotherapy sessions</p>
          </div>
          <div className="ml-auto">
            {!showForm && (
              <Button
                id="book-appointment-btn"
                onClick={() => setShowForm(true)}
                icon={<Plus className="h-4 w-4" />}
                disabled={!physioId}
              >
                Book Appointment
              </Button>
            )}
          </div>
        </div>

        {/* No physio assigned warning */}
        {!physioId && (
          <div className="mb-6 flex items-start gap-3 rounded-2xl border border-amber-500/30 bg-amber-500/10 p-4">
            <AlertCircle className="h-5 w-5 text-amber-400 mt-0.5 shrink-0" />
            <p className="text-sm text-amber-300">
              You are not yet assigned to a physiotherapist. Please contact your clinic to get assigned before booking.
            </p>
          </div>
        )}

        {/* Booking form */}
        {showForm && (
          <div className="mb-8 animate-fade-in">
            <Card>
              <div className="flex items-center justify-between mb-5">
                <div className="flex items-center gap-2">
                  <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary-600/20">
                    <Calendar className="h-4 w-4 text-primary-400" />
                  </div>
                  <h2 className="font-semibold text-white">New Appointment</h2>
                </div>
                <button
                  onClick={() => setShowForm(false)}
                  className="p-1.5 rounded-lg hover:bg-white/10 text-slate-400 hover:text-white transition-colors"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>

              <div className="space-y-5">
                {/* Date & Time */}
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-1.5" htmlFor="appt-datetime">
                    Date &amp; Time
                  </label>
                  <div className="relative">
                    <Clock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-500 pointer-events-none" />
                    <input
                      id="appt-datetime"
                      type="datetime-local"
                      value={scheduledAt}
                      min={getMinDatetime()}
                      onChange={e => setScheduledAt(e.target.value)}
                      className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-white text-sm
                        focus:outline-none focus:border-primary-500/60 focus:ring-1 focus:ring-primary-500/30
                        [color-scheme:dark] transition-colors"
                    />
                  </div>
                </div>

                {/* Duration */}
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-1.5">
                    Duration
                  </label>
                  <div className="flex gap-2">
                    {DURATION_OPTIONS.map(d => (
                      <button
                        key={d}
                        onClick={() => setDuration(d)}
                        className={`flex-1 py-2 rounded-xl text-sm font-medium border transition-all duration-150
                          ${duration === d
                            ? 'bg-primary-600 border-primary-500 text-white shadow-lg shadow-primary-900/30'
                            : 'bg-white/5 border-white/10 text-slate-400 hover:bg-white/10 hover:text-white'
                          }`}
                      >
                        {d} min
                      </button>
                    ))}
                  </div>
                </div>

                {/* Notes */}
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-1.5" htmlFor="appt-notes">
                    Notes <span className="text-slate-500">(optional)</span>
                  </label>
                  <textarea
                    id="appt-notes"
                    value={notes}
                    onChange={e => setNotes(e.target.value)}
                    placeholder="e.g. Focus on knee flexion, bringing my MRI scans…"
                    rows={3}
                    className="w-full px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-white text-sm
                      placeholder:text-slate-600 resize-none
                      focus:outline-none focus:border-primary-500/60 focus:ring-1 focus:ring-primary-500/30
                      transition-colors"
                  />
                </div>

                {/* Actions */}
                <div className="flex gap-3 pt-1">
                  <Button
                    id="confirm-booking-btn"
                    className="flex-1 justify-center"
                    onClick={handleBook}
                    loading={booking}
                    disabled={!scheduledAt}
                  >
                    <CheckCircle2 className="h-4 w-4" />
                    Confirm Booking
                  </Button>
                  <Button
                    variant="secondary"
                    onClick={() => setShowForm(false)}
                    disabled={booking}
                  >
                    Cancel
                  </Button>
                </div>
              </div>
            </Card>
          </div>
        )}

        {/* Upcoming Appointments */}
        <section className="mb-8">
          <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-3">
            Upcoming ({upcoming.length})
          </h2>
          {upcoming.length === 0 ? (
            <Card>
              <p className="text-slate-500 text-sm text-center py-4">
                No upcoming appointments.{' '}
                {physioId && (
                  <button onClick={() => setShowForm(true)} className="text-primary-400 hover:underline">
                    Book one now →
                  </button>
                )}
              </p>
            </Card>
          ) : (
            <div className="space-y-3">
              {upcoming.map(a => (
                <AppointmentCard key={a.id} appt={a} />
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
            <div className="space-y-3">
              {past.slice(0, 10).map(a => (
                <AppointmentCard key={a.id} appt={a} faded />
              ))}
            </div>
          </section>
        )}
      </main>
    </div>
  )
}

function AppointmentCard({ appt, faded = false }: { appt: Appointment; faded?: boolean }) {
  const date = new Date(appt.scheduledAt)
  return (
    <Card padding="sm" className={faded ? 'opacity-60' : ''}>
      <div className="flex items-center gap-4">
        {/* Date block */}
        <div className="flex flex-col items-center justify-center min-w-[52px] h-14 rounded-xl bg-white/5 border border-white/10 text-center">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
            {date.toLocaleDateString('en-US', { month: 'short' })}
          </span>
          <span className="text-2xl font-bold text-white leading-none">
            {date.getDate()}
          </span>
        </div>

        {/* Details */}
        <div className="flex-1 min-w-0">
          <p className="font-medium text-white text-sm">
            {date.toLocaleDateString('en-US', { weekday: 'long' })},{' '}
            {date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })}
          </p>
          <p className="text-xs text-slate-400 mt-0.5">
            {appt.durationMinutes} min session
            {appt.notes ? ` · ${appt.notes}` : ''}
          </p>
        </div>

        {/* Status + Video button */}
        <div className="flex items-center gap-2 shrink-0">
          {appt.videoRoomId && (appt.status === 'SCHEDULED' || appt.status === 'CONFIRMED') && (
            <a href={`/video/${appt.videoRoomId}`}>
              <Button size="sm" variant="secondary" icon={<Video className="h-3.5 w-3.5" />}>
                Join
              </Button>
            </a>
          )}
          <Badge color={STATUS_COLOR[appt.status]}>
            {appt.status}
          </Badge>
        </div>
      </div>
    </Card>
  )
}
