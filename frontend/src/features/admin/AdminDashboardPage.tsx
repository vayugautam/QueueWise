import { useQuery } from '@tanstack/react-query'
import { Activity, AlertCircle, ArrowRight, BarChart3, CheckCircle2, Clock3, FileSearch, LoaderCircle, ShieldCheck, Ticket, Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import { apiClient } from '../../api/client'
import { ErrorState } from '../../components/AsyncState'

interface AnalyticsResponse {
  totalTokens: number
  completedTokens: number
  cancelledTokens: number
  noShows: number
  averageWaitingTimeMinutes: number | null
  averageServiceTimeMinutes: number | null
  serviceDemand: { serviceName: string; tokenCount: number }[]
  counterPerformance: { counterName: string; tokenCount: number; averageWaitingTimeMinutes: number | null; averageServiceTimeMinutes: number | null }[]
}

interface AuditLog {
  id: number
  actorName: string | null
  action: string
  entityType: string
  outcome: string
  createdAt: string
}

interface AuditPage {
  content: AuditLog[]
}

const range = () => {
  const end = new Date()
  const start = new Date(end)
  start.setDate(start.getDate() - 30)
  return { start: start.toISOString(), end: end.toISOString() }
}

const minutes = (value: number | null) => value == null ? '—' : `${value.toFixed(1)} min`

export function AdminDashboardPage() {
  const dates = range()
  const analytics = useQuery({
    queryKey: ['admin-dashboard-analytics', dates.start.slice(0, 10), dates.end.slice(0, 10)],
    queryFn: async () => (await apiClient.get<AnalyticsResponse>('/v1/analytics/dashboard', { params: { startDate: dates.start, endDate: dates.end } })).data,
    refetchInterval: 30_000,
  })
  const audits = useQuery({
    queryKey: ['admin-dashboard-audit'],
    queryFn: async () => (await apiClient.get<AuditPage>('/audit-logs', { params: { size: 5 } })).data,
    refetchInterval: 30_000,
  })

  if (analytics.isLoading || audits.isLoading) return <div className="analytics-state"><LoaderCircle className="spin" size={22} /><p>Loading administration overview...</p></div>
  if (analytics.error || audits.error) return <ErrorState error={analytics.error || audits.error} />
  if (!analytics.data || !audits.data) return <ErrorState error={new Error('Administration data is unavailable.')} />

  const data = analytics.data
  const completionRate = data.totalTokens === 0 ? 0 : Math.round((data.completedTokens / data.totalTokens) * 100)
  const topService = data.serviceDemand[0]
  const busiestCounter = data.counterPerformance[0]

  return (
    <section className="admin-dashboard-view">
      <div className="dashboard-intro">
        <div><p className="eyebrow">Administration / Last 30 days</p><h1>Keep the whole clinic in view.</h1><p className="dashboard-copy">A concise pulse on flow, demand, and recent administrative activity.</p></div>
        <Link className="primary-button" to="/admin/analytics"><BarChart3 size={17} /> Open analytics</Link>
      </div>

      <div className="admin-summary-grid">
        <article className="metric-card metric-card-accent"><div className="metric-card-top"><span>Total tokens</span><Ticket size={17} /></div><strong>{data.totalTokens}</strong><small>{completionRate}% completed</small></article>
        <article className="metric-card"><div className="metric-card-top"><span>Waiting time</span><Clock3 size={17} /></div><strong>{minutes(data.averageWaitingTimeMinutes)}</strong><small>Arrival to called</small></article>
        <article className="metric-card"><div className="metric-card-top"><span>Service time</span><Activity size={17} /></div><strong>{minutes(data.averageServiceTimeMinutes)}</strong><small>Started to completed</small></article>
        <article className="metric-card"><div className="metric-card-top"><span>No-shows</span><Users size={17} /></div><strong>{data.noShows}</strong><small>Requires attention</small></article>
      </div>

      <div className="admin-overview-grid">
        <article className="panel admin-pulse-panel"><div className="panel-heading"><div><p className="eyebrow">Operational pulse</p><h2>What stands out</h2></div><ShieldCheck size={19} /></div><div className="admin-pulse-list"><div><span className="admin-pulse-icon"><BarChart3 size={16} /></span><p><strong>Top demand</strong><small>{topService ? `${topService.serviceName} · ${topService.tokenCount} tokens` : 'No service demand recorded'}</small></p></div><div><span className="admin-pulse-icon admin-pulse-icon-coral"><CheckCircle2 size={16} /></span><p><strong>Busiest counter</strong><small>{busiestCounter ? `${busiestCounter.counterName} · ${busiestCounter.tokenCount} completed` : 'No completed service recorded'}</small></p></div><div><span className="admin-pulse-icon admin-pulse-icon-gold"><AlertCircle size={16} /></span><p><strong>Follow-up</strong><small>{data.cancelledTokens} cancellations and {data.noShows} no-shows in range</small></p></div></div></article>

        <article className="panel admin-audit-panel"><div className="panel-heading"><div><p className="eyebrow">Recent activity</p><h2>Audit trail</h2></div><FileSearch size={19} /></div><div className="admin-audit-list">{audits.data.content.length === 0 ? <p className="admin-empty">No recent audit activity.</p> : audits.data.content.map((item) => <div className="admin-audit-row" key={item.id}><span className="admin-audit-dot" /><div><strong>{item.action.replaceAll('_', ' ')}</strong><small>{item.actorName || 'System'} · {item.entityType}</small></div><time>{new Date(item.createdAt).toLocaleDateString()}</time></div>)}</div><Link className="admin-audit-link" to="/admin/analytics">Review operational data <ArrowRight size={14} /></Link></article>
      </div>
    </section>
  )
}
