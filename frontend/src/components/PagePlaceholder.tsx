import type { LucideIcon } from 'lucide-react'

interface PagePlaceholderProps {
  eyebrow: string
  title: string
  description: string
  icon: LucideIcon
}

export function PagePlaceholder({ eyebrow, title, description, icon: Icon }: PagePlaceholderProps) {
  return (
    <section className="page-placeholder" aria-labelledby="page-placeholder-title">
      <div className="placeholder-icon">
        <Icon size={22} strokeWidth={1.8} />
      </div>
      <p className="eyebrow">{eyebrow}</p>
      <h1 id="page-placeholder-title">{title}</h1>
      <p className="placeholder-copy">{description}</p>
      <div className="placeholder-rule" aria-hidden="true" />
      <p className="placeholder-status">The route is ready for its feature slice.</p>
    </section>
  )
}
