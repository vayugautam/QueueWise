import { CalendarPlus, Clock3, History, Search, Ticket } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/AuthProvider'

export function CustomerDashboard() {
  const { user } = useAuth()
  const firstName = user?.firstName || 'there'

  return (
    <section className="customer-dashboard">
      <div className="dashboard-intro">
        <div>
          <p className="eyebrow">Customer space</p>
          <h1>Good morning, {firstName}.</h1>
          <p className="dashboard-copy">Your queue and appointment home base.</p>
        </div>
        <Link className="primary-button" to="/customer/book"><CalendarPlus size={17} /> Book an appointment</Link>
      </div>

      <div className="customer-status-panel panel">
        <div className="customer-status-icon"><Ticket size={22} /></div>
        <div>
          <p className="eyebrow">Your queue</p>
          <h2>No active token</h2>
          <p>When you check in, your live position and estimated wait will appear here.</p>
        </div>
        <Link className="text-button customer-status-link" to="/staff/queue">View live queue <Clock3 size={14} /></Link>
      </div>

      <div className="section-heading"><div><p className="eyebrow">Start here</p><h2>What would you like to do?</h2></div></div>
      <div className="quick-grid customer-actions">
        <Link to="/customer/services" className="quick-card"><span className="quick-icon"><Search size={19} /></span><strong>Find a service</strong><small>Browse available care</small></Link>
        <Link to="/customer/book" className="quick-card"><span className="quick-icon quick-icon-coral"><CalendarPlus size={19} /></span><strong>Book an appointment</strong><small>Choose a time that works</small></Link>
        <Link to="/customer/history" className="quick-card"><span className="quick-icon quick-icon-gold"><History size={19} /></span><strong>View history</strong><small>Review previous visits</small></Link>
      </div>

      <div className="customer-note"><Clock3 size={16} /><span>QueueWise keeps your place visible without asking you to keep watching the clock.</span></div>
    </section>
  )
}
