import { useEffect, useState } from 'react'
import { Footer, Header } from '../components/layout'
import { ConsolePage, LandingPage } from '../pages'
import ConsoleGate from './ConsoleGate'

export default function AppRoutes() {
  const [route, setRoute] = useState(location.hash)
  useEffect(() => {
    const on = () => { setRoute(location.hash); if (location.hash.startsWith('#/console')) window.scrollTo(0, 0) }
    window.addEventListener('hashchange', on)
    return () => window.removeEventListener('hashchange', on)
  }, [])
  return (
    <>
      <Header onHome={() => setRoute('')} />
      {route.startsWith('#/console') ? (
        <ConsoleGate>{({ session, logout }) => <ConsolePage session={session} route={route} onLogout={logout} />}</ConsoleGate>
      ) : <LandingPage />}
      <Footer />
    </>
  )
}
