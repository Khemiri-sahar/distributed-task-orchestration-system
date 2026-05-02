function formatTime(ts) {
  if (!ts) return '—'
  try {
    return new Date(ts).toTimeString().slice(0, 8)
  } catch {
    return '—'
  }
}

function rowBg(log) {
  if (log.error) return 'rgba(255,51,102,0.08)'
  if (log.taskType === 'message') return 'rgba(0,212,255,0.08)'
  return 'rgba(99,102,241,0.08)'
}

const thStyle = {
  padding: '0.6rem 0.8rem',
  textAlign: 'left',
  color: 'var(--muted)',
  fontSize: '0.65rem',
  letterSpacing: '0.1em',
  fontWeight: 700,
  borderBottom: '1px solid var(--border)',
  position: 'sticky',
  top: 0,
  background: '#12121a',
  whiteSpace: 'nowrap',
}

const tdStyle = {
  padding: '0.5rem 0.8rem',
  fontSize: '0.75rem',
  borderBottom: '1px solid rgba(255,255,255,0.03)',
}

export default function TaskLog({ logs }) {
  return (
    <div style={{
      background: 'var(--surface)',
      border: '1px solid var(--border)',
      borderRadius: '12px',
      marginTop: '1.5rem',
      overflow: 'hidden',
    }}>
      <div style={{
        fontFamily: 'var(--font-display)',
        fontSize: '1rem',
        color: 'var(--leader)',
        letterSpacing: '0.1em',
        padding: '1rem 1.5rem',
        borderBottom: '1px solid var(--border)',
      }}>
        TASK LOG
      </div>

      {logs.length === 0 ? (
        <div style={{
          textAlign: 'center',
          color: 'var(--muted)',
          padding: '2rem',
          fontSize: '0.85rem',
        }}>
          No tasks dispatched yet
        </div>
      ) : (
        <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                {['TIME', 'TASK ID', 'TYPE', 'PAYLOAD', 'RESULT', 'NODE', 'REDIRECT'].map(h => (
                  <th key={h} style={thStyle}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {logs.map((log, i) => (
                <tr
                  key={log.taskId || i}
                  style={{
                    background: rowBg(log),
                    animation: i === 0 ? 'fadeIn 0.3s ease' : undefined,
                  }}
                >
                  <td style={tdStyle}>{formatTime(log.timestamp)}</td>
                  <td style={{ ...tdStyle, fontFamily: 'var(--font-mono)', color: 'var(--muted)' }}>
                    {log.taskId ? `${log.taskId.slice(0, 8)}...` : '—'}
                  </td>
                  <td style={{ ...tdStyle, color: log.taskType === 'message' ? 'var(--leader)' : 'var(--follower)' }}>
                    {log.taskType ?? '—'}
                  </td>
                  <td style={{ ...tdStyle, color: 'var(--muted)' }}>
                    {log.payload ? String(log.payload).slice(0, 20) : '—'}
                  </td>
                  <td style={{
                    ...tdStyle,
                    color: log.error ? 'var(--dead)' : '#22c55e',
                    maxWidth: '180px',
                  }}>
                    {log.error
                      ? String(log.error).slice(0, 25)
                      : log.result
                        ? String(log.result).slice(0, 25)
                        : '—'}
                  </td>
                  <td style={tdStyle}>
                    {log.executedBy ? `Node ${log.executedBy}` : '—'}
                  </td>
                  <td style={{ ...tdStyle, color: log.redirected ? 'var(--candidate)' : 'var(--muted)' }}>
                    {log.redirected ? '↪' : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
