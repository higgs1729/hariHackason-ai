import { useEffect, useState } from 'react'
import './App.css'

type HelloResponse = {
  message: string
  serverTime: string
}

function App() {
  const [hello, setHello] = useState<HelloResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/hello')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json() as Promise<HelloResponse>
      })
      .then(setHello)
      .catch((e: Error) => setError(e.message))
  }, [])

  return (
    <main className="app">
      <h1>Hanamizuki AI</h1>
      <p className="status">
        backend:{' '}
        {hello ? (
          <span className="ok">
            {hello.message} ({hello.serverTime})
          </span>
        ) : error ? (
          <span className="ng">not connected ({error}) — start the backend</span>
        ) : (
          <span>connecting...</span>
        )}
      </p>
    </main>
  )
}

export default App
