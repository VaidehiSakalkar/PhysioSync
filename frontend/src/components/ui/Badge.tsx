import clsx from 'clsx'

type BadgeColor = 'good' | 'warning' | 'danger' | 'info' | 'neutral'

interface BadgeProps {
  color?: BadgeColor
  children: React.ReactNode
  className?: string
}

const colorMap: Record<BadgeColor, string> = {
  good:    'badge-good',
  warning: 'badge-warning',
  danger:  'badge-danger',
  info:    'text-sky-400 bg-sky-400/10 border border-sky-400/20 rounded-lg px-2 py-0.5 text-xs font-semibold',
  neutral: 'text-slate-400 bg-slate-400/10 border border-slate-400/20 rounded-lg px-2 py-0.5 text-xs font-semibold',
}

export function Badge({ color = 'neutral', children, className }: BadgeProps) {
  return (
    <span className={clsx(colorMap[color], className)}>
      {children}
    </span>
  )
}
