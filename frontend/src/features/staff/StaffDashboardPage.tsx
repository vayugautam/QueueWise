import { useQuery } from '@tanstack/react-query'
import { Activity, CheckCircle2, Clock3, LoaderCircle, Monitor, PauseCircle, RefreshCw } from 'lucide-react'
import { Link } from 'react-router-dom'
import { apiClient } from '../../api/client'
import { ErrorState } from '../../components/AsyncState'

interface CounterSnapshot {
  id: string
  name: string
  status: string
  pauseReason: string | null
  activeTokenCount: number
}

interface StaffDashboardResponse {
  organizationId: string
  staffId: string
  waitingTokens: number
  counters: CounterSnapshot[]
}

export function StaffDashboardPage() {
  const dashboard = useQuery({
    queryKey: ['staff-dashboard'],
    queryFn: async () => (await apiClient.get<StaffDashboardResponse>('/staff/dashboard')).data,
    refetchInterval: 15_000,
  })

  return (
    <section className="staff-dashboard-view">
      <div className="dashboard-intro">
        <div><p className="eyebrow">Operations / Staff</p><h1>Ready when you are.</h1><p className="dashboard-copy">A calm overview of your counters and the work waiting behind them.</p></div>
        <button className="icon-button" aria-label="Refresh staff dashboard" onClick={() => dashboard.refetch()}><RefreshCw size={17} /></button>
      </div>
      {dashboard.isLoading && <div className="async-state"><LoaderCircle className="spin" size={19} /><span>Loading your counters...</span></div>}
      {dashboard.error && <ErrorState error={dashboard.error} />}
      {dashboard.data && <StaffSnapshot data={dashboard.data} />}
    </section>
  )
}

function StaffSnapshot({ data }: { data: StaffDashboardResponse }) {
  return (
    <>
      <div className="staff-summary-grid">
        <article className="metric-card metric-card-accent"><div className="metric-card-top"><span>Waiting in queue</span><Clock3 size={17} /></div><strong>{data.waitingTokens}</strong><small>Across your organization</small></article>
        <article className="metric-card"><div className="metric-card-top"><span>Assigned counters</span><Monitor size={17} /></div><strong>{data.counters.length}</strong><small>Available in your workspace</small></article>
        <article className="metric-card"><div className="metric-card-top"><span>Active service</span><Activity size={17} /></div><strong>{data.counters.filter((counter) => counter.activeTokenCount > 0).length}</strong><small>Counters currently serving</small></article>
      </div>
      <article className="panel staff-counter-panel"><div className="panel-heading"><div><p className="eyebrow">Your workspace</p><h2>Assigned counters</h2></div><span className="soft-status">Live</span></div><div className="staff-counter-list">{data.counters.length === 0 ? <div className="staff-empty"><PauseCircle size={20} /><span>No counters are assigned to you yet.</span></div> : data.counters.map((counter) => <div className="staff-counter-row" key={counter.id}><span className={`staff-counter-icon staff-counter-icon-${counter.status.toLowerCase()}`}><Monitor size={18} /></span><div><strong>{counter.name}</strong><small>{counter.pauseReason || `${counter.activeTokenCount} active token${counter.activeTokenCount === 1 ? '' : 's'}`}</small></div><span className={`queue-token-status queue-token-status-${counter.status.toLowerCase()}`}>{counter.status}</span><Link className="text-button" to={`/staff/counter?counterId=${counter.id}`}>Open console</Link></div>)}</div></article>
      <div className="staff-note"><CheckCircle2 size={16} /><span>Dashboard data refreshes automatically. Queue actions remain in the counter console.</span></div>
    </>
  )
}
