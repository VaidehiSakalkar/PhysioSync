import api from './api'
import { SessionLog } from './exerciseService'

export interface SessionSummary {
  totalSessions: number
  avgAccuracy: number
  totalReps: number
  totalDurationSeconds: number
}

export const sessionService = {
  /** Save a completed session log (patient) */
  logSession: (log: Partial<SessionLog>) =>
    api.post<SessionLog>('/exercises/sessions', log).then(r => r.data),

  /** Get all recent sessions for the logged-in patient */
  getMyRecentSessions: (limit = 20) =>
    api.get<SessionLog[]>('/exercises/sessions/my/recent', { params: { limit } }).then(r => r.data),

  /** Get paginated sessions for a specific patient (physio view) */
  getPatientSessions: (patientId: string, page = 0, size = 20) =>
    api
      .get<{ content: SessionLog[]; totalElements: number; totalPages: number }>(
        `/exercises/sessions/patient/${patientId}`,
        { params: { page, size } }
      )
      .then(r => r.data),

  /** Get a single session by ID */
  getSessionById: (sessionId: string) =>
    api.get<SessionLog>(`/exercises/sessions/${sessionId}`).then(r => r.data),

  /** Compute a client-side summary from a list of session logs */
  computeSummary: (sessions: SessionLog[]): SessionSummary => {
    if (sessions.length === 0) {
      return { totalSessions: 0, avgAccuracy: 0, totalReps: 0, totalDurationSeconds: 0 }
    }
    return {
      totalSessions: sessions.length,
      avgAccuracy:
        sessions.reduce((sum, s) => sum + s.avgAngleAccuracy, 0) / sessions.length,
      totalReps: sessions.reduce((sum, s) => sum + s.repsCompleted, 0),
      totalDurationSeconds: sessions.reduce((sum, s) => sum + (s.durationSeconds ?? 0), 0),
    }
  },
}
