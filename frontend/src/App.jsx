import { useEffect, useState } from 'react'
import { fetchStatus, killNode, reviveNode } from './api/bridge'
import ConsensusViz from './components/ConsensusViz'
import NodeCard from './components/NodeCard'
import TaskSubmitter from './components/TaskSubmitter'
import TaskLog from './components/TaskLog'
import ErrorBanner from './components/ErrorBanner'

export default function App() {
  const [nodes, setNodes] = useState([])
  const [logs, setLogs] = useState([])
  const [error, setError] = useState(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    async function load() {
      try {
        const data = await fetchStatus()
        setNodes(data)
        setError(null)
      } catch {
        setError('Bridge offline')
      } finally {
        setIsLoading(false)
      }
    }

    load()
    const poll = setInterval(async () => {
      try {
        const data = await fetchStatus()
        setNodes(data)
        setError(null)
      } catch {
        setError('Bridge offline')
      }
    }, 2000)

    return () => clearInterval(poll)
  }, [])

  async function handleKill(nodeId) {
    try {
      await killNode(nodeId)
    } catch (e) {
      setError(e.message)
    }
  }

  async function handleRevive(nodeId) {
    try {
      await reviveNode(nodeId)
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', padding: '2rem' }}>
      <header style={{ marginBottom: '2rem' }}>
        <h1 style={{
          fontFamily: 'var(--font-display)',
          fontSize: 'clamp(1.4rem, 4vw, 2.2rem)',
          color: 'var(--leader)',
          fontWeight: 800,
          letterSpacing: '0.08em',
          margin: 0,
        }}>
          DISTRIBUTED TASK ORCHESTRATION
        </h1>
        <div style={{ color: 'var(--muted)', fontSize: '0.75rem', marginTop: '0.4rem', letterSpacing: '0.1em' }}>
          RAFT-LIKE CONSENSUS · LAMPORT CLOCKS · BRIDGE API
        </div>
      </header>

      {error && <ErrorBanner message={error} />}

      <ConsensusViz nodes={nodes} />

      {isLoading && nodes.length === 0 ? (
        <div style={{ color: 'var(--muted)', fontSize: '0.85rem', textAlign: 'center', padding: '2rem' }}>
          Connecting to nodes...
        </div>
      ) : (
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(3, 1fr)',
          gap: '1rem',
        }}>
          {nodes.map(n => (
            <NodeCard
              key={n.nodeId}
              node={n}
              onKill={handleKill}
              onRevive={handleRevive}
            />
          ))}
        </div>
      )}

      <TaskSubmitter onResult={log => setLogs(prev => [log, ...prev].slice(0, 50))} />
      <TaskLog logs={logs} />
    </div>
  )
}
