import { useState, type ReactNode } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import {
  BarChart3,
  CalendarDays,
  ClipboardList,
  Clock3,
  LayoutDashboard,
  LifeBuoy,
  Menu,
  Monitor,
  PanelLeftClose,
  Search,
  Settings2,
  ShieldCheck,
  Ticket,
  Users,
  X,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

interface AppShellProps {
  children: ReactNode
}

interface NavigationItem {
  label: string
  path: string
  icon: LucideIcon
}

const navigationGroups: { label: string; items: NavigationItem[] }[] = [
  {
    label: 'Customer space',
    items: [
      { label: 'Dashboard', path: '/customer/dashboard', icon: LayoutDashboard },
      { label: 'Find a service', path: '/customer/services', icon: Search },
      { label: 'My appointments', path: '/customer/history', icon: CalendarDays },
      { label: 'Live queue', path: '/staff/queue', icon: Clock3 },
    ],
  },
  {
    label: 'Operations',
    items: [
      { label: 'Staff dashboard', path: '/staff/dashboard', icon: ClipboardList },
      { label: 'Counter console', path: '/staff/counter', icon: Monitor },
    ],
  },
  {
    label: 'Administration',
    items: [
      { label: 'Admin dashboard', path: '/admin/dashboard', icon: ShieldCheck },
      { label: 'Analytics', path: '/admin/analytics', icon: BarChart3 },
    ],
  },
]

const pageTitles: Record<string, string> = {
  '/customer/dashboard': 'Customer dashboard',
  '/customer/services': 'Find a service',
  '/customer/history': 'My appointments',
  '/staff/queue': 'Live queue',
  '/staff/dashboard': 'Staff dashboard',
  '/staff/counter': 'Counter console',
  '/admin/dashboard': 'Admin dashboard',
  '/admin/analytics': 'Analytics',
}
import { useAuth } from '../auth/AuthProvider'

export function AppShell({ children }: AppShellProps) {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false)
  const location = useLocation()
  const { user, logout } = useAuth()
  const pageTitle = pageTitles[location.pathname] ?? 'QueueWise'
  const displayName = user ? `${user.firstName ?? ''} ${user.lastName ?? ''}`.trim() || user.email || user.phone || 'Signed-in user' : 'Signed-out user'
  const initials = displayName.split(' ').map((part) => part[0]).join('').slice(0, 2).toUpperCase()

  return (
    <div className="app-shell min-h-screen">
      <aside className={`sidebar ${isSidebarOpen ? 'sidebar-open' : ''}`}>
        <div className="sidebar-topline">
          <NavLink to="/customer/dashboard" className="brand" onClick={() => setIsSidebarOpen(false)}>
            <span className="brand-mark">Q</span>
            <span>
              <strong>QueueWise</strong>
              <small>service operations</small>
            </span>
          </NavLink>
          <button className="icon-button sidebar-close" onClick={() => setIsSidebarOpen(false)} aria-label="Close navigation">
            <X size={18} />
          </button>
        </div>

        <div className="workspace-switcher">
          <span className="workspace-dot" />
          <span>
            <small>Current workspace</small>
            <strong>Northside Clinic</strong>
          </span>
          <Settings2 size={16} />
        </div>

        <nav className="sidebar-nav" aria-label="Primary navigation">
          {navigationGroups.map((group) => (
            <div className="nav-group" key={group.label}>
              <p>{group.label}</p>
              {group.items.map(({ label, path, icon: Icon }) => (
                <NavLink
                  key={path}
                  to={path}
                  onClick={() => setIsSidebarOpen(false)}
                  className={({ isActive }) => `nav-link ${isActive ? 'nav-link-active' : ''}`}
                >
                  <Icon size={17} strokeWidth={1.8} />
                  <span>{label}</span>
                </NavLink>
              ))}
            </div>
          ))}
        </nav>

        <div className="sidebar-footer">
          <button className="help-link">
            <LifeBuoy size={17} />
            <span>Support centre</span>
          </button>
          <div className="profile-chip">
            <span className="avatar">{initials}</span>
            <span>
              <strong>{displayName}</strong>
              <small>{user?.roles?.join(' / ') || 'Account'}</small>
            </span>
            <button className="icon-button" aria-label="Sign out" onClick={logout}><Users size={15} /></button>
          </div>
        </div>
      </aside>

      {isSidebarOpen && <button className="sidebar-scrim" onClick={() => setIsSidebarOpen(false)} aria-label="Close navigation" />}

      <div className="shell-content">
        <header className="topbar">
          <button className="icon-button menu-button" onClick={() => setIsSidebarOpen(true)} aria-label="Open navigation">
            <Menu size={20} />
          </button>
          <div>
            <p className="topbar-kicker">Northside Clinic / Today</p>
            <h2>{pageTitle}</h2>
          </div>
          <div className="topbar-actions">
            <span className="status-pill"><span /> Systems operational</span>
            <button className="icon-button" aria-label="Open notifications"><Ticket size={19} /></button>
            <button className="icon-button" aria-label="Open settings"><Settings2 size={19} /></button>
          </div>
        </header>
        <main className="main-content">{children}</main>
      </div>
    </div>
  )
}
