import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'

// Pages (stubs — implemented in Phase 4)
const LoginPage         = () => <div className="p-8 text-center text-primary-400">LoginPage — Phase 4</div>
const PatientDashboard  = () => <div className="p-8 text-center text-primary-400">PatientDashboard — Phase 4</div>
const PhysioDashboard   = () => <div className="p-8 text-center text-primary-400">PhysioDashboard — Phase 4</div>
const SessionPage       = () => <div className="p-8 text-center text-primary-400">SessionPage — Phase 4</div>

/** Simple auth guard — reads JWT from localStorage */
function PrivateRoute({ children, role }: { children: JSX.Element; role?: string }) {
  const token = localStorage.getItem('physiolink_token')
  const storedRole = localStorage.getItem('physiolink_role')
  if (!token) return <Navigate to="/login" replace />
  if (role && storedRole !== role) return <Navigate to="/login" replace />
  return children
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route
          path="/patient/*"
          element={
            <PrivateRoute role="PATIENT">
              <PatientDashboard />
            </PrivateRoute>
          }
        />

        <Route
          path="/patient/session/:exerciseId"
          element={
            <PrivateRoute role="PATIENT">
              <SessionPage />
            </PrivateRoute>
          }
        />

        <Route
          path="/physio/*"
          element={
            <PrivateRoute role="PHYSIO">
              <PhysioDashboard />
            </PrivateRoute>
          }
        />

        {/* Default redirect */}
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
