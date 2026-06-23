import api from './api'

export interface Appointment {
  id: string
  patientId: string
  physioId: string
  scheduledAt: string
  durationMinutes: number
  status: 'SCHEDULED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED'
  notes?: string
  videoRoomId?: string
}

export const appointmentService = {
  getMyAppointments: () => api.get<Appointment[]>('/appointments/my').then(r => r.data),

  getOne: (id: string) => api.get<Appointment>(`/appointments/${id}`).then(r => r.data),

  book: (data: { physioId: string; patientId?: string; scheduledAt: string; durationMinutes?: number; notes?: string }) =>
    api.post<Appointment>('/appointments', data).then(r => r.data),

  updateStatus: (id: string, status: Appointment['status']) =>
    api.put<Appointment>(`/appointments/${id}/status`, { status }).then(r => r.data),
}
