import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Clock3, LoaderCircle, RefreshCw, Ticket } from 'lucide-react'
import { Link } from 'react-router-dom'
import { apiClient } from '../../api/client'
import { ErrorState } from '../../components/AsyncState'

interface LiveToken {
  id: string
  displayCode: string
  tokenNumber: number
  priority: string
  status: string
  position: number | null
  estimatedWaitMinutes: number | null
  arrivalAt: string | null
}

interface LiveQueueResponse {
  queueId: string
  serviceId: string
  businessDate: string
  queueStatus: string
  waitingCount: number
  estimatedWaitMinutes: number
  observedAt: string
  tokens: LiveToken[]
}

export function LiveQueuePage() {
  const [queueId, setQueueId] = useState('')
  const [requestedQueueId, setRequestedQueueId] = useState('')
  const queue = useQuery({
    queryKey: ['live-queue', requestedQueueId],
    queryFn: async () => (await apiClient.get<LiveQueueResponse>(`/queues/${requestedQueueId}/live`)).data,
    enabled: Boolean(requestedQueueId),
    refetchInterval: 15_000,
  })

  function loadQueue(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setRequestedQueueId(queueId.trim())
  }

  return (
    <section className="live-queue-view">
      <Link className="back-link" to="/customer/dashboard"><ArrowLeft size={15} /> Customer dashboard</Link>
      <div className="dashboard-intro live-queue-intro">
        <div><p className="eyebrow">Operations / Live</p><h1>See the line move.</h1><p className="dashboard-copy">Enter a queue ID to view its privacy-safe live snapshot.</p></div>
        <span className="booking-mark"><Clock3 size={22} /></span>
      </div>

      <form className="queue-lookup panel" onSubmit={loadQueue}>
        <label>Queue ID<input value={queueId} onChange={(event) => setQueueId(event.target.value)} placeholder="Queue UUID" required /></label>
        <button className="primary-button" type="submit"><RefreshCw size={16} /> Load queue</button>
      </form>

      {queue.isFetching && <div className="async-state"><LoaderCircle className="spin" size={19} /><span>Refreshing queue...</span></div>}
      {queue.error && <ErrorState error={queue.error} />}
      {queue.data && <QueueSnapshot data={queue.data} />}
    </section>
  )
}

function QueueSnapshot({ data }: { data: LiveQueueResponse }) {
  return (
    <div className="queue-snapshot">
      <div className="queue-summary-grid">
        <article className="metric-card metric-card-accent"><div className="metric-card-top"><span>Queue status</span><Clock3 size={17} /></div><strong>{data.queueStatus}</strong><small>Observed {new Date(data.observedAt).toLocaleTimeString()}</small></article>
        <article className="metric-card"><div className="metric-card-top"><span>Waiting</span><Ticket size={17} /></div><strong>{data.waitingCount}</strong><small>People ahead in this queue</small></article>
        <article className="metric-card"><div className="metric-card-top"><span>Estimated wait</span><Clock3 size={17} /></div><strong>{data.estimatedWaitMinutes} <em>min</em></strong><small>Based on service duration</small></article>
      </div>
      <article className="panel queue-token-panel"><div className="panel-heading"><div><p className="eyebrow">Queue composition</p><h2>Current tokens</h2></div><span className="soft-status">Live</span></div><div className="queue-token-list">{data.tokens.map((token) => <div className="queue-token-row" key={token.id}><span className="token-code">{token.displayCode}</span><span className={`queue-token-status queue-token-status-${token.status.toLowerCase()}`}>{token.status}</span><span>{token.position ? `Position ${token.position}` : 'In service'}</span><strong>{token.estimatedWaitMinutes == null ? '—' : `${token.estimatedWaitMinutes} min`}</strong></div>)}</div></article>
    </div>
  )
}
