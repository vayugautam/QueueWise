import { ArrowRight, CalendarPlus, Clock3, ShieldCheck } from 'lucide-react'
import { Link } from 'react-router-dom'

export function LandingPage() {
  return (
    <main className="landing-page">
      <nav className="landing-nav"><Link className="brand landing-brand" to="/"><span className="brand-mark">Q</span><span><strong>QueueWise</strong><small>service operations</small></span></Link><div><Link className="text-button" to="/login">Sign in</Link><Link className="primary-button landing-nav-button" to="/register">Create account</Link></div></nav>
      <section className="landing-hero"><div><p className="eyebrow">Appointments and queues</p><h1>Your day, without the waiting.</h1><p>Book ahead, follow your place, and arrive when it matters.</p><div className="landing-actions"><Link className="primary-button" to="/register">Get started <ArrowRight size={17} /></Link><Link className="landing-secondary-link" to="/login">I already have an account</Link></div></div><div className="landing-signal"><div className="landing-signal-top"><span className="live-dot" /> Live service overview</div><strong>QueueWise</strong><span>Less waiting. More certainty.</span></div></section>
      <section className="landing-principles"><article><CalendarPlus size={20} /><h2>Book with clarity</h2><p>Choose a service and time without guessing what happens next.</p></article><article><Clock3 size={20} /><h2>Keep your place</h2><p>See the queue move and make your time useful again.</p></article><article><ShieldCheck size={20} /><h2>Stay in control</h2><p>Your organization keeps every update scoped and accountable.</p></article></section>
    </main>
  )
}
