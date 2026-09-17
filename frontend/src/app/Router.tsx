import { Navigate, Route, Routes } from 'react-router-dom'
import type { ReactNode } from 'react'
import { BarChart3, CalendarPlus, ClipboardList, Clock3, FileSearch as FileSearchIcon, Monitor, ShieldCheck, Ticket, Users as UsersIcon } from 'lucide-react'
import { AppShell } from '../components/AppShell'
import { PagePlaceholder } from '../components/PagePlaceholder'
import { LoginPage } from '../auth/LoginPage'
import { useAuth } from '../auth/AuthProvider'
import { CustomerDashboard } from '../features/customer/CustomerDashboard'
import { AppointmentBookingPage } from '../features/appointments/AppointmentBookingPage'
import { LiveQueuePage } from '../features/queue/LiveQueuePage'
import { StaffDashboardPage } from '../features/staff/StaffDashboardPage'
import { AnalyticsDashboard } from '../components/AnalyticsDashboard'
import { AdminDashboardPage } from '../features/admin/AdminDashboardPage'
import { LandingPage } from '../features/landing/LandingPage'
import { RegisterPage } from '../auth/RegisterPage'

function ShellRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return <AppShell>{children}</AppShell>
}

function AnalyticsRoute() {
  const { isAuthenticated, user } = useAuth()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (!user?.roles.some((role) => role === 'ADMIN' || role === 'SUPERVISOR')) {
    return <Navigate to="/customer/dashboard" replace />
  }
  return <AppShell><AnalyticsDashboard /></AppShell>
}

function AdminRoute() {
  const { isAuthenticated, user } = useAuth()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (!user?.roles.some((role) => role === 'ADMIN' || role === 'SUPERVISOR')) {
    return <Navigate to="/customer/dashboard" replace />
  }
  return <AppShell><AdminDashboardPage /></AppShell>
}

export function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/customer/dashboard" element={<ShellRoute><CustomerDashboard /></ShellRoute>} />
      <Route path="/customer/services" element={<ShellRoute><PagePlaceholder eyebrow="Customer space" title="Service browser" description="Service discovery will be added here." icon={CalendarPlus} /></ShellRoute>} />
      <Route path="/customer/book" element={<ShellRoute><AppointmentBookingPage /></ShellRoute>} />
      <Route path="/customer/token/:id" element={<ShellRoute><PagePlaceholder eyebrow="Queue" title="Token details" description="Token status and history will be added here." icon={Ticket} /></ShellRoute>} />
      <Route path="/customer/history" element={<ShellRoute><PagePlaceholder eyebrow="Customer space" title="Appointment history" description="Appointment history will be added here." icon={ClipboardList} /></ShellRoute>} />
      <Route path="/staff/dashboard" element={<ShellRoute><StaffDashboardPage /></ShellRoute>} />
      <Route path="/staff/queue" element={<ShellRoute><LiveQueuePage /></ShellRoute>} />
      <Route path="/staff/counter" element={<ShellRoute><PagePlaceholder eyebrow="Operations" title="Counter console" description="Counter workflows will be added here." icon={Monitor} /></ShellRoute>} />
      <Route path="/admin/dashboard" element={<AdminRoute />} />
      <Route path="/admin/services" element={<ShellRoute><PagePlaceholder eyebrow="Administration" title="Services" description="Service management will be added here." icon={CalendarPlus} /></ShellRoute>} />
      <Route path="/admin/counters" element={<ShellRoute><PagePlaceholder eyebrow="Administration" title="Counters" description="Counter management will be added here." icon={Monitor} /></ShellRoute>} />
      <Route path="/admin/users" element={<ShellRoute><PagePlaceholder eyebrow="Administration" title="Users" description="User management will be added here." icon={UsersIcon} /></ShellRoute>} />
      <Route path="/admin/analytics" element={<AnalyticsRoute />} />
      <Route path="/admin/audit-logs" element={<ShellRoute><PagePlaceholder eyebrow="Administration" title="Audit logs" description="Audit log browsing will be added here." icon={FileSearchIcon} /></ShellRoute>} />
      <Route path="/customer" element={<Navigate to="/customer/dashboard" replace />} />
      <Route path="/services" element={<Navigate to="/customer/services" replace />} />
      <Route path="/appointments/book" element={<Navigate to="/customer/book" replace />} />
      <Route path="/queue" element={<Navigate to="/staff/queue" replace />} />
      <Route path="/history" element={<Navigate to="/customer/history" replace />} />
      <Route path="/staff" element={<Navigate to="/staff/dashboard" replace />} />
      <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
