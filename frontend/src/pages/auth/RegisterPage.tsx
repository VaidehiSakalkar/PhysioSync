import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Activity, Mail, Lock, User, Phone } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import { useToast } from '../../components/ui/Toast'
import { Input } from '../../components/ui/Input'
import { Button } from '../../components/ui/Button'

export function RegisterPage() {
  const [form, setForm] = useState({ email: '', password: '', name: '', phone: '', role: 'PATIENT' })
  const [loading, setLoading] = useState(false)
  const { register } = useAuth()
  const { toast } = useToast()
  const navigate = useNavigate()

  const set = (field: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [field]: e.target.value }))

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    try {
      const res = await register(form.email, form.password, form.name, form.phone, form.role)
      toast(`Account created! Welcome, ${res.name}`, 'success')
      navigate(res.role === 'PHYSIO' ? '/physio' : '/patient')
    } catch {
      toast('Registration failed. Try a different email.', 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-surface-900">
      <div className="absolute inset-0 bg-gradient-to-br from-primary-950/60 via-surface-900 to-surface-950 pointer-events-none" />
      <div className="absolute top-1/3 right-1/3 w-80 h-80 bg-primary-600/10 rounded-full blur-3xl pointer-events-none" />

      <div className="relative w-full max-w-md animate-fade-in">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center h-16 w-16 rounded-2xl bg-primary-600 shadow-xl shadow-primary-900/60 mb-4">
            <Activity className="h-8 w-8 text-white" />
          </div>
          <h1 className="text-3xl font-bold text-white">PhysioLink</h1>
          <p className="mt-1 text-slate-400">Create your account</p>
        </div>

        <div className="glass-card p-8">
          <form onSubmit={handleSubmit} className="space-y-4" id="register-form">
            <Input id="reg-name" label="Full Name" placeholder="John Smith"
              value={form.name} onChange={set('name')} icon={<User className="h-4 w-4" />} required />
            <Input id="reg-email" label="Email" type="email" placeholder="you@example.com"
              value={form.email} onChange={set('email')} icon={<Mail className="h-4 w-4" />} required />
            <Input id="reg-password" label="Password" type="password" placeholder="Min 6 characters"
              value={form.password} onChange={set('password')} icon={<Lock className="h-4 w-4" />} required minLength={6} />
            <Input id="reg-phone" label="Phone (optional)" placeholder="+1 555 0100"
              value={form.phone} onChange={set('phone')} icon={<Phone className="h-4 w-4" />} />

            <div className="flex flex-col gap-1.5">
              <label htmlFor="reg-role" className="text-sm font-medium text-slate-300">I am a</label>
              <select id="reg-role" value={form.role} onChange={set('role')}
                className="input bg-surface-800">
                <option value="PATIENT">Patient</option>
                <option value="PHYSIO">Physiotherapist</option>
              </select>
            </div>

            <Button id="reg-submit" type="submit" loading={loading} className="w-full justify-center mt-2">
              Create Account
            </Button>
          </form>

          <p className="mt-5 text-center text-sm text-slate-400">
            Already have an account?{' '}
            <Link to="/login" className="text-primary-400 hover:text-primary-300 font-medium">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  )
}
