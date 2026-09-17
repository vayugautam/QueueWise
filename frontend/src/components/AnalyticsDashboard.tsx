import { useEffect, useState } from 'react'
import {
  BarChart3,
  CheckCircle2,
  Clock3,
  Gauge,
  LoaderCircle,
  Ticket,
  UserRoundX,
  XCircle,
} from 'lucide-react'
import { apiClient } from '../api/client'

interface AnalyticsDashboardResponse {
  totalTokens: number
  completedTokens: number
  cancelledTokens: number
  noShows: number
  averageWaitingTimeMinutes: number | null
  averageServiceTimeMinutes: number | null
  peakHours: { hourOfDay: number; tokenCount: number }[]
  serviceDemand: { serviceName: string; tokenCount: number }[]
  counterPerformance: {
    counterName: string
    tokenCount: number
    averageWaitingTimeMinutes: number | null
    averageServiceTimeMinutes: number | null
  }[]
}

const dateRange = () => {
  const end = new Date()
  const start = new Date(end)
  start.setDate(start.getDate() - 30)
  return { start: start.toISOString(), end: end.toISOString() }
}

const formatMinutes = (minutes: number | null) => minutes == null ? '—' : `${minutes.toFixed(1)} min`
const formatHour = (hour: number) => `${String(hour).padStart(2, '0')}:00`

export function AnalyticsDashboard() {
  const [data, setData] = useState<AnalyticsDashboardResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const range = dateRange()
    apiClient.get<AnalyticsDashboardResponse>('/v1/analytics/dashboard', {
      params: { startDate: range.start, endDate: range.end },
    })
      .then((response) => setData(response.data))
      .catch(() => setError('Analytics could not be loaded right now.'))
  }, [])

  if (error) return <section className="analytics-state"><XCircle size={22} /><p>{error}</p></section>
  if (!data) return <section className="analytics-state"><LoaderCircle className="spin" size={22} /><p>Loading analytics...</p></section>

  const summary = [
    ['Total tokens', data.totalTokens, Ticket, 'All tokens created in range'],
    ['Completed', data.completedTokens, CheckCircle2, 'Successfully served'],
    ['Cancelled', data.cancelledTokens, XCircle, 'Cancelled before service'],
    ['No-shows', data.noShows, UserRoundX, 'Customers not served'],
    ['Average wait', formatMinutes(data.averageWaitingTimeMinutes), Clock3, 'Arrival to called'],
    ['Average service', formatMinutes(data.averageServiceTimeMinutes), Gauge, 'Started to completed'],
  ] as const

  const largestDemand = Math.max(...data.serviceDemand.map((item) => item.tokenCount), 1)
  const largestPeak = Math.max(...data.peakHours.map((item) => item.tokenCount), 1)

  return (
    <section className="analytics-view">
      <div className="dashboard-intro">
        <div>
          <p className="eyebrow">Administration / Last 30 days</p>
          <h1>Know where time goes.</h1>
          <p className="dashboard-copy">A database-backed view of demand, flow, and counter performance.</p>
        </div>
      </div>

      <div className="analytics-summary-grid">
        {summary.map(([label, value, Icon, detail]) => (
          <article className="metric-card" key={label}>
            <div className="metric-card-top"><span>{label}</span><Icon size={17} /></div>
            <strong>{value}</strong>
            <small>{detail}</small>
          </article>
        ))}
      </div>

      <div className="analytics-columns">
        <article className="panel analytics-panel">
          <div className="panel-heading"><div><p className="eyebrow">Demand by hour</p><h2>Peak hours</h2></div><BarChart3 size={19} /></div>
          <div className="bar-list">
            {data.peakHours.map((item) => (
              <div className="bar-row" key={item.hourOfDay}>
                <span>{formatHour(item.hourOfDay)}</span>
                <div className="bar-track"><i style={{ width: `${(item.tokenCount / largestPeak) * 100}%` }} /></div>
                <strong>{item.tokenCount}</strong>
              </div>
            ))}
          </div>
        </article>

        <article className="panel analytics-panel">
          <div className="panel-heading"><div><p className="eyebrow">Volume by service</p><h2>Service demand</h2></div><Ticket size={19} /></div>
          <div className="bar-list">
            {data.serviceDemand.map((item) => (
              <div className="bar-row" key={item.serviceName}>
                <span className="bar-label">{item.serviceName}</span>
                <div className="bar-track"><i className="bar-fill-coral" style={{ width: `${(item.tokenCount / largestDemand) * 100}%` }} /></div>
                <strong>{item.tokenCount}</strong>
              </div>
            ))}
          </div>
        </article>
      </div>

      <article className="panel analytics-panel counter-panel">
        <div className="panel-heading"><div><p className="eyebrow">Completed service</p><h2>Counter performance</h2></div><Gauge size={19} /></div>
        <div className="analytics-table-wrap">
          <table className="analytics-table">
            <thead><tr><th>Counter</th><th>Completed tokens</th><th>Avg. wait</th><th>Avg. service</th></tr></thead>
            <tbody>{data.counterPerformance.map((item) => <tr key={item.counterName}><th>{item.counterName}</th><td>{item.tokenCount}</td><td>{formatMinutes(item.averageWaitingTimeMinutes)}</td><td>{formatMinutes(item.averageServiceTimeMinutes)}</td></tr>)}</tbody>
          </table>
        </div>
      </article>
    </section>
  )
}
