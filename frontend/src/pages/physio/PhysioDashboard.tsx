import { useEffect, useState } from 'react'
import { useAuth } from '../../hooks/useAuth'
import { patientService, PatientProfile } from '../../services/patientService'
import { exerciseService, Exercise, Routine, SessionLog, RecoveryPlan } from '../../services/exerciseService'
import { Header } from '../../components/layout/Header'
import { Card } from '../../components/ui/Card'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { Input } from '../../components/ui/Input'
import { Spinner } from '../../components/ui/Spinner'
import { useToast } from '../../components/ui/Toast'
import { Users, Search, Activity, FileText, Plus } from 'lucide-react'

export function PhysioDashboard() {
  const { name } = useAuth()
  const { toast } = useToast()

  const [patients, setPatients] = useState<PatientProfile[]>([])
  const [exercises, setExercises] = useState<Exercise[]>([])
  const [selectedPatient, setSelectedPatient] = useState<PatientProfile | null>(null)
  const [patientSessions, setPatientSessions] = useState<SessionLog[]>([])
  const [patientRoutines, setPatientRoutines] = useState<Routine[]>([])
  const [patientPlans, setPatientPlans] = useState<RecoveryPlan[]>([])
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [showRoutineModal, setShowRoutineModal] = useState(false)
  const [routineName, setRoutineName] = useState('')
  const [selectedExercises, setSelectedExercises] = useState<string[]>([])

  useEffect(() => {
    Promise.all([
      patientService.getPhysioPatients(),
      exerciseService.listAll(),
    ]).then(([p, e]) => {
      setPatients(p)
      setExercises(e)
    }).finally(() => setLoading(false))
  }, [])

  const selectPatient = async (patient: PatientProfile) => {
    setSelectedPatient(patient)
    const [sessions, routines, plans] = await Promise.all([
      exerciseService.getPatientSessions(patient.id),
      exerciseService.getPatientRoutines(patient.id),
      exerciseService.getPatientPlans(patient.id),
    ])
    setPatientSessions((sessions as any).content ?? [])
    setPatientRoutines(routines)
    setPatientPlans(plans)
  }

  const createRoutine = async () => {
    if (!selectedPatient || !routineName || selectedExercises.length === 0) return
    try {
      await exerciseService.createRoutine({
        patientId: selectedPatient.id,
        name: routineName,
        exerciseIds: selectedExercises,
      })
      toast('Routine created!', 'success')
      setShowRoutineModal(false)
      setRoutineName('')
      setSelectedExercises([])
      // Refresh routines
      const updated = await exerciseService.getPatientRoutines(selectedPatient.id)
      setPatientRoutines(updated)
    } catch {
      toast('Failed to create routine', 'error')
    }
  }

  const filtered = patients.filter(p =>
    p.name.toLowerCase().includes(search.toLowerCase()) ||
    p.email.toLowerCase().includes(search.toLowerCase()))

  if (loading) return (
    <div className="min-h-screen bg-surface-900 flex items-center justify-center">
      <Spinner className="h-12 w-12" />
    </div>
  )

  return (
    <div className="min-h-screen bg-surface-900">
      <Header />
      <main className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 py-8">

        <div className="mb-6">
          <h1 className="text-2xl font-bold text-white">Physio Dashboard</h1>
          <p className="text-slate-400 mt-1">Welcome, {name} — manage your patients</p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

          {/* Patient List */}
          <div className="lg:col-span-1">
            <div className="flex items-center gap-2 mb-3">
              <Users className="h-4 w-4 text-slate-400" />
              <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wider">
                My Patients ({patients.length})
              </h2>
            </div>
            <div className="mb-3">
              <Input id="patient-search" placeholder="Search patients…"
                value={search} onChange={e => setSearch(e.target.value)}
                icon={<Search className="h-4 w-4" />} />
            </div>
            <div className="space-y-2">
              {filtered.map(p => (
                <button key={p.id} onClick={() => selectPatient(p)}
                  className={`w-full text-left glass-card p-4 hover:bg-white/10 transition-all duration-200
                    ${selectedPatient?.id === p.id ? 'border-primary-500/50 bg-primary-500/10' : ''}`}>
                  <p className="font-medium text-white">{p.name}</p>
                  <p className="text-xs text-slate-400 mt-0.5">{p.email}</p>
                  {p.medicalHistory && (
                    <p className="text-xs text-slate-500 mt-1 line-clamp-1">{p.medicalHistory}</p>
                  )}
                </button>
              ))}
              {filtered.length === 0 && (
                <p className="text-slate-500 text-sm p-4">No patients found</p>
              )}
            </div>
          </div>

          {/* Patient Detail */}
          <div className="lg:col-span-2">
            {!selectedPatient ? (
              <div className="flex flex-col items-center justify-center h-64 text-slate-500">
                <Users className="h-16 w-16 mb-4 opacity-30" />
                <p>Select a patient to view details</p>
              </div>
            ) : (
              <div className="space-y-6 animate-fade-in">
                {/* Patient header */}
                <Card>
                  <div className="flex items-start justify-between">
                    <div>
                      <h2 className="text-xl font-bold text-white">{selectedPatient.name}</h2>
                      <p className="text-sm text-slate-400">{selectedPatient.email}</p>
                      {selectedPatient.medicalHistory && (
                        <p className="text-sm text-slate-300 mt-2">
                          <span className="text-slate-500">History: </span>
                          {selectedPatient.medicalHistory}
                        </p>
                      )}
                    </div>
                    <Button onClick={() => setShowRoutineModal(true)}
                      icon={<Plus className="h-4 w-4" />} size="sm">
                      Assign Routine
                    </Button>
                  </div>
                </Card>

                {/* Routines */}
                <div>
                  <h3 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-3">Routines</h3>
                  <div className="space-y-2">
                    {patientRoutines.length === 0 && <Card><p className="text-slate-500 text-sm">No routines assigned</p></Card>}
                    {patientRoutines.map(r => (
                      <Card key={r.id} padding="sm">
                        <div className="flex items-center justify-between">
                          <p className="font-medium text-white">{r.name}</p>
                          <div className="flex items-center gap-2">
                            <Badge color={r.active ? 'good' : 'neutral'}>{r.active ? 'Active' : 'Inactive'}</Badge>
                            <span className="text-xs text-slate-400">{r.exercises.length} exercises</span>
                          </div>
                        </div>
                      </Card>
                    ))}
                  </div>
                </div>

                {/* Recent sessions */}
                <div>
                  <h3 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-3">
                    <Activity className="inline h-4 w-4 mr-1" />Recent Sessions
                  </h3>
                  <div className="space-y-2">
                    {patientSessions.length === 0 && <Card><p className="text-slate-500 text-sm">No sessions yet</p></Card>}
                    {patientSessions.slice(0, 5).map(s => (
                      <Card key={s.id} padding="sm">
                        <div className="flex items-center justify-between">
                          <div>
                            <p className="text-sm text-white">{new Date(s.sessionTime).toLocaleDateString()}</p>
                            <p className="text-xs text-slate-400">{s.repsCompleted} reps · {s.durationSeconds ? `${s.durationSeconds}s` : '—'}</p>
                          </div>
                          <Badge color={s.avgAngleAccuracy >= 0.8 ? 'good' : s.avgAngleAccuracy >= 0.5 ? 'warning' : 'danger'}>
                            {Math.round(s.avgAngleAccuracy * 100)}% accuracy
                          </Badge>
                        </div>
                      </Card>
                    ))}
                  </div>
                </div>

                {/* Recovery Plans */}
                <div>
                  <h3 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-3">
                    <FileText className="inline h-4 w-4 mr-1" />Recovery Plans
                  </h3>
                  <div className="space-y-2">
                    {patientPlans.length === 0 && <Card><p className="text-slate-500 text-sm">No AI plans generated yet</p></Card>}
                    {patientPlans.slice(0, 2).map(p => (
                      <Card key={p.id} padding="sm">
                        <p className="text-xs text-slate-400 mb-1">{new Date(p.generatedAt).toLocaleDateString()}</p>
                        <p className="text-sm text-slate-300">{p.progressionNotes}</p>
                      </Card>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </main>

      {/* Assign Routine Modal */}
      <Modal isOpen={showRoutineModal} onClose={() => setShowRoutineModal(false)} title="Assign Routine">
        <div className="space-y-4">
          <Input id="routine-name" label="Routine Name" placeholder="e.g. Week 1 Recovery"
            value={routineName} onChange={e => setRoutineName(e.target.value)} />

          <div>
            <label className="text-sm font-medium text-slate-300 mb-2 block">Select Exercises</label>
            <div className="space-y-2 max-h-48 overflow-y-auto">
              {exercises.map(ex => (
                <label key={ex.id} className="flex items-center gap-3 p-3 rounded-xl bg-white/5 cursor-pointer hover:bg-white/10">
                  <input type="checkbox"
                    checked={selectedExercises.includes(ex.id)}
                    onChange={e => {
                      if (e.target.checked) setSelectedExercises(prev => [...prev, ex.id])
                      else setSelectedExercises(prev => prev.filter(id => id !== ex.id))
                    }}
                    className="h-4 w-4 accent-primary-600"
                  />
                  <div>
                    <p className="text-sm font-medium text-white">{ex.name}</p>
                    <p className="text-xs text-slate-400">{ex.targetJoint.replace('_', ' ')} · {ex.targetReps} reps</p>
                  </div>
                </label>
              ))}
            </div>
          </div>

          <Button className="w-full justify-center" onClick={createRoutine}
            disabled={!routineName || selectedExercises.length === 0}>
            Create Routine
          </Button>
        </div>
      </Modal>
    </div>
  )
}
