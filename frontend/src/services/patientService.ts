import api from './api'

export interface PatientProfile {
  id: string
  email: string
  name: string
  phone?: string
  medicalHistory?: string
  assignedPhysioId?: string
  emergencyContact?: string
}

export const patientService = {
  getMyProfile: () => api.get<PatientProfile>('/patients/me').then(r => r.data),

  updateMyProfile: (data: Partial<Pick<PatientProfile, 'medicalHistory' | 'emergencyContact'>>) =>
    api.put<PatientProfile>('/patients/me', data).then(r => r.data),

  getPatient: (id: string) => api.get<PatientProfile>(`/patients/${id}`).then(r => r.data),

  getPhysioPatients: () => api.get<PatientProfile[]>('/physios/patients').then(r => r.data),

  getPhysioProfile: () => api.get('/physios/me').then(r => r.data),

  getAllPhysios: () => api.get<{id: string, name: string}[]>('/physios').then(r => r.data),
}
