import React from 'react'
import clsx from 'clsx'

interface CardProps {
  children: React.ReactNode
  className?: string
  hover?: boolean
  padding?: 'sm' | 'md' | 'lg'
}

const paddingMap = { sm: 'p-4', md: 'p-6', lg: 'p-8' }

export function Card({ children, className, hover = false, padding = 'md' }: CardProps) {
  return (
    <div className={clsx(
      'glass-card',
      paddingMap[padding],
      hover && 'hover:bg-white/10 transition-colors duration-200 cursor-pointer',
      className
    )}>
      {children}
    </div>
  )
}
