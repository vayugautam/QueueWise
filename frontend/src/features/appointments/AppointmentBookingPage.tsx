import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ArrowLeft, CalendarPlus, CheckCircle2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { apiClient } from '../../api/client'
import { ApiRequestError } from '../../api/errors'
import { ErrorState } from '../../components/AsyncState'

interface AppointmentResponse {
  id: string
  scheduledStart: string
  scheduledEnd: string
  status: string
  confirmationCode: string
}

interface BookingInput {
  serviceId: string
  scheduledStart: string
}

export function AppointmentBookingPage() {
  const [serviceId, setServiceId] = useState('')
  const [scheduledStart, setScheduledStart] = useState('')
  const booking = useMutation({
    mutationFn: async (input: BookingInput) => {
      const response = await apiClient.post<AppointmentResponse>('/appointments', {
        serviceId: input.serviceId,
        scheduledStart: new Date(input.scheduledStart).toISOString(),
      })
      return response.data
    },
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    booking.mutate({ serviceId, scheduledStart })
  }

  if (booking.data) {
    return (
      <section className="booking-view">
        <div className="booking-success panel">
          <span className="booking-success-icon"><CheckCircle2 size={25} /></span>
          <p className="eyebrow">Appointment confirmed</p>
          <h1>You are booked.</h1>
          <p>Your confirmation code is <strong>{booking.data.confirmationCode}</strong>.</p>
          <div className="booking-success-actions"><Link className="primary-button" to="/customer/dashboard">Back to dashboard</Link><button className="text-button" onClick={() => booking.reset()}>Book another appointment</button></div>
        </div>
      </section>
    )
  }

  return (
    <section className="booking-view">
      <Link className="back-link" to="/customer/dashboard"><ArrowLeft size={15} /> Customer dashboard</Link>
      <div className="dashboard-intro booking-intro">
        <div>
          <p className="eyebrow">Appointments</p>
          <h1>Choose your time.</h1>
          <p className="dashboard-copy">Book a future visit with one of your organization’s services.</p>
        </div>
        <span className="booking-mark"><CalendarPlus size={22} /></span>
      </div>

      <form className="booking-form panel" onSubmit={submit}>
        {booking.error && <ErrorState error={booking.error as ApiRequestError} />}
        <label>Service ID<input value={serviceId} onChange={(event) => setServiceId(event.target.value)} placeholder="Service UUID" required /></label>
        <label>Date and time<input type="datetime-local" value={scheduledStart} onChange={(event) => setScheduledStart(event.target.value)} required /></label>
        <p className="booking-hint">The service duration and end time are determined by QueueWise.</p>
        <button className="primary-button" type="submit" disabled={booking.isPending}>{booking.isPending ? 'Booking...' : 'Confirm appointment'} <CalendarPlus size={17} /></button>
      </form>
    </section>
  )
}
