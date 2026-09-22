import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../state/auth'
import { routes } from '../routes'
import { Loading } from './Notice'

/** Route group that needs a session. Sends anonymous users to Home with the intended path in state. */
export function RequireAuth() {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <Loading label="ログイン確認中" />
  if (!user) return <Navigate to={routes.home()} replace state={{ from: location.pathname }} />
  return <Outlet />
}
