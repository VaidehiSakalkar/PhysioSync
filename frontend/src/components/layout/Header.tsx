import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { Activity, LogOut, User } from 'lucide-react'
import { Button } from '../ui/Button'

export function Header() {
  const { name, role, isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  if (!isAuthenticated) return null

  const dashboardPath = role === 'PHYSIO' ? '/physio' : '/patient'

  return (
    <header className="sticky top-0 z-40 border-b border-white/10 bg-surface-900/80 backdrop-blur-md">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="flex h-16 items-center justify-between">
          {/* Logo */}
          <Link to={dashboardPath} className="flex items-center gap-2.5 group">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary-600 shadow-lg shadow-primary-900/50 group-hover:bg-primary-500 transition-colors">
              <Activity className="h-5 w-5 text-white" />
            </div>
            <span className="text-lg font-bold text-white">PhysioLink</span>
          </Link>

          {/* Nav links */}
          <nav className="hidden md:flex items-center gap-1">
            {role === 'PATIENT' && (
              <>
                <NavLink to="/patient">Dashboard</NavLink>
                <NavLink to="/patient/appointments">Appointments</NavLink>
                <NavLink to="/patient/sandbox">Pose Sandbox</NavLink>
              </>
            )}
            {role === 'PHYSIO' && (
              <>
                <NavLink to="/physio">Dashboard</NavLink>
                <NavLink to="/physio/appointments">Appointments</NavLink>
              </>
            )}
          </nav>

          {/* User info + logout */}
          <div className="flex items-center gap-3">
            <div className="hidden sm:flex items-center gap-2 text-sm text-slate-400">
              <User className="h-4 w-4" />
              <span>{name}</span>
              <span className="px-1.5 py-0.5 rounded-md bg-primary-600/20 text-primary-400 text-xs font-medium">
                {role}
              </span>
            </div>
            <Button variant="secondary" size="sm" onClick={handleLogout} icon={<LogOut className="h-3.5 w-3.5" />}>
              Logout
            </Button>
          </div>
        </div>
      </div>
    </header>
  )
}

function NavLink({ to, children }: { to: string; children: React.ReactNode }) {
  return (
    <Link to={to}
      className="px-3 py-2 rounded-lg text-sm font-medium text-slate-400 hover:text-white hover:bg-white/10 transition-all duration-200">
      {children}
    </Link>
  )
}
