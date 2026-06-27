import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ToastProvider } from './components/ui/Toast'
import { LoginPage }                from './pages/auth/LoginPage'
import { RegisterPage }             from './pages/auth/RegisterPage'
import { PatientDashboard }         from './pages/patient/PatientDashboard'
import { SessionPage }              from './pages/patient/SessionPage'
import { AppointmentBookingPage }   from './pages/patient/AppointmentBookingPage'
import { SessionHistoryPage }       from './pages/patient/SessionHistoryPage'
import { PoseSandboxPage }          from './pages/patient/PoseSandboxPage'
import { PhysioDashboard }          from './pages/physio/PhysioDashboard'
import { VideoConsultationPage }    from './pages/video/VideoConsultationPage'

/** Simple auth guard — reads JWT from localStorage */
function PrivateRoute({ children, role }: { children: JSX.Element; role?: 'PATIENT' | 'PHYSIO' }) {
  const token      = localStorage.getItem('physiolink_token')
  const storedRole = localStorage.getItem('physiolink_role')
  if (!token) return <Navigate to="/login" replace />
  if (role && storedRole !== role) return <Navigate to="/login" replace />
  return children
}

export default function App() {
  return (
    <ToastProvider>
      <BrowserRouter>
        <Routes>
          {/* Public */}
          <Route path="/login"    element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* Patient routes */}
          <Route path="/patient" element={
            <PrivateRoute role="PATIENT"><PatientDashboard /></PrivateRoute>
          } />
          <Route path="/patient/session/:exerciseId" element={
            <PrivateRoute role="PATIENT"><SessionPage /></PrivateRoute>
          } />
          <Route path="/patient/sandbox" element={
            <PrivateRoute role="PATIENT"><PoseSandboxPage /></PrivateRoute>
          } />
          <Route path="/patient/appointments" element={
            <PrivateRoute role="PATIENT"><AppointmentBookingPage /></PrivateRoute>
          } />
          <Route path="/patient/history" element={
            <PrivateRoute role="PATIENT"><SessionHistoryPage /></PrivateRoute>
          } />

          {/* Physio routes */}
          <Route path="/physio" element={
            <PrivateRoute role="PHYSIO"><PhysioDashboard /></PrivateRoute>
          } />

          {/* Shared video route */}
          <Route path="/video/:roomId" element={
            <PrivateRoute><VideoConsultationPage /></PrivateRoute>
          } />

          {/* Default redirect */}
          <Route path="/"  element={<Navigate to="/login" replace />} />
          <Route path="*"  element={<Navigate to="/login" replace />} />
        </Routes>
      </BrowserRouter>
    </ToastProvider>
  )
}
