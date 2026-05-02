import { useState } from 'react'
import { submitTask } from '../api/bridge'

const inputStyle = {
  background: 'var(--bg)',
  border: '1px solid var(--border)',
  color: 'var(--text)',
  padding: '0.6rem 0.8rem',
  borderRadius: '6px',
  fontFamily: 'var(--font-mono)',
  fontSize: '0.85rem',
  width: '100%',
  outline: 'none',
}

const labelStyle = {
  display: 'block',
  color: 'var(--muted)',
  fontSize: '0.7rem',
  letterSpacing: '0.1em',
  marginBottom: '0.4rem',
}

export default function TaskSubmitter({ onResult }) {
  const [taskType, setTaskType] = useState('compute')
  const [payload, setPayload] = useState('')
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const placeholder = taskType === 'compute'
    ? 'e.g. fibonacci:10'
    : 'e.g. Hello from client'

  async function handleDispatch() {
    if (loading) return
    setLoading(true)
    setError(null)
    try {
      const res = await submitTask(taskType, payload)
      setResult(res)
      onResult({ ...res, timestamp: new Date().toISOString(), taskType, payload })
      setTimeout(() => setResult(null), 8000)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      background: 'var(--surface)',
      border: '1px solid var(--border)',
      borderRadius: '12px',
      padding: '1.5rem',
      marginTop: '1.5rem',
    }}>
      <div style={{
        fontFamily: 'var(--font-display)',
        fontSize: '1rem',
        color: 'var(--leader)',
        letterSpacing: '0.1em',
        marginBottom: '1.25rem',
      }}>
        TASK SUBMITTER
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '180px 1fr auto', gap: '1rem', alignItems: 'end' }}>
        <div>
          <label style={labelStyle}>TASK TYPE</label>
          <select
            value={taskType}
            onChange={e => setTaskType(e.target.value)}
            style={{ ...inputStyle, cursor: 'pointer' }}
          >
            <option value="compute">compute</option>
            <option value="message">message</option>
          </select>
        </div>

        <div>
          <label style={labelStyle}>PAYLOAD</label>
          <input
            type="text"
            value={payload}
            onChange={e => setPayload(e.target.value)}
            placeholder={placeholder}
            style={inputStyle}
            onKeyDown={e => e.key === 'Enter' && handleDispatch()}
          />
        </div>

        <button
          onClick={handleDispatch}
          disabled={loading}
          style={{
            background: loading ? 'rgba(0,212,255,0.1)' : 'rgba(0,212,255,0.15)',
            border: '1px solid var(--leader)',
            color: 'var(--leader)',
            padding: '0.6rem 1.25rem',
            borderRadius: '6px',
            cursor: loading ? 'not-allowed' : 'pointer',
            fontFamily: 'var(--font-mono)',
            fontSize: '0.8rem',
            fontWeight: 700,
            letterSpacing: '0.05em',
            whiteSpace: 'nowrap',
            opacity: loading ? 0.7 : 1,
          }}
        >
          {loading ? 'DISPATCHING...' : '▶ DISPATCH TASK'}
        </button>
      </div>

      {error && (
        <div style={{
          marginTop: '1rem',
          padding: '0.75rem',
          background: 'rgba(255,51,102,0.1)',
          border: '1px solid rgba(255,51,102,0.3)',
          borderRadius: '6px',
          color: 'var(--dead)',
          fontSize: '0.8rem',
        }}>
          {error}
        </div>
      )}

      {result && (
        <div style={{
          marginTop: '1rem',
          padding: '1rem',
          background: result.error ? 'rgba(255,51,102,0.1)' : 'rgba(0,212,255,0.1)',
          border: `1px solid ${result.error ? 'rgba(255,51,102,0.3)' : 'rgba(0,212,255,0.3)'}`,
          borderRadius: '6px',
          fontSize: '0.8rem',
          animation: 'fadeIn 0.3s ease',
        }}>
          {result.selectedNode && (
            <div style={{ color: 'var(--muted)', marginBottom: '0.4rem' }}>
              → Node {result.selectedNode} selected
            </div>
          )}
          {result.redirected && (
            <div style={{ color: 'var(--candidate)', marginBottom: '0.4rem' }}>
              ↪ Redirected to Node {result.executedBy}
            </div>
          )}
          <div style={{
            fontSize: '1.1rem',
            fontWeight: 700,
            color: result.error ? 'var(--dead)' : 'var(--leader)',
            marginBottom: '0.4rem',
          }}>
            RESULT: {result.result ?? result.error ?? '—'}
          </div>
          {result.taskId && (
            <div style={{ color: 'var(--muted)' }}>
              TASK ID: {result.taskId.slice(0, 8)}...
            </div>
          )}
        </div>
      )}
    </div>
  )
}
