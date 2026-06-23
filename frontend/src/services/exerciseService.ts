import api from './api'

export interface Exercise {
  id: string
  name: string
  description?: string
  targetJoint: string
  targetAngleMin: number
  targetAngleMax: number
  targetReps: number
  targetSets: number
  videoUrl?: string
}

export interface Routine {
  id: string
  patientId: string
  physioId: string
  name: string
  notes?: string
  active: boolean
  exercises: Exercise[]
}

export interface SessionLog {
  id: string
  patientId: string
  exerciseId: string
  repsCompleted: number
  avgAngleAccuracy: number
  sessionTime: string
  durationSeconds?: number
  repAngles: number[]
}

export interface RecoveryPlan {
  id: string
  patientId: string
  generatedAt: string
  planJson: Record<string, unknown>
  progressionNotes?: string
  redFlags: string[]
}

export const exerciseService = {
  listAll: (joint?: string) =>
    api.get<Exercise[]>('/exercises', { params: joint ? { joint } : {} }).then(r => r.data),

  getOne: (id: string) => api.get<Exercise>(`/exercises/${id}`).then(r => r.data),

  createExercise: (data: Partial<Exercise>) =>
    api.post<Exercise>('/exercises', data).then(r => r.data),

  getMyRoutines: () => api.get<Routine[]>('/exercises/routines/my').then(r => r.data),

  getPatientRoutines: (patientId: string) =>
    api.get<Routine[]>(`/exercises/routines/patient/${patientId}`).then(r => r.data),

  createRoutine: (data: { patientId: string; name: string; notes?: string; exerciseIds: string[] }) =>
    api.post<Routine>('/exercises/routines', data).then(r => r.data),

  logSession: (log: Partial<SessionLog>) =>
    api.post<SessionLog>('/exercises/sessions', log).then(r => r.data),

  getMyRecentSessions: () =>
    api.get<SessionLog[]>('/exercises/sessions/my/recent').then(r => r.data),

  getPatientSessions: (patientId: string, page = 0, size = 20) =>
    api.get(`/exercises/sessions/patient/${patientId}`, { params: { page, size } }).then(r => r.data),

  getMyPlans: () => api.get<RecoveryPlan[]>('/exercises/plans/my').then(r => r.data),

  getPatientPlans: (patientId: string) =>
    api.get<RecoveryPlan[]>(`/exercises/plans/patient/${patientId}`).then(r => r.data),
}
