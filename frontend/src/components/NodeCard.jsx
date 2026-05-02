const ROLE_STYLES = {
  LEADER: {
    border: '1px solid var(--leader)',
    boxShadow: '0 0 20px rgba(0,212,255,0.3)',
  },
  FOLLOWER: {
    border: '1px solid var(--follower)',
  },
  CANDIDATE: {
    border: '1px solid var(--candidate)',
    animation: 'pulse 1s infinite',
  },
  DEAD: {
    border: '1px solid var(--dead)',
    opacity: 0.4,
  },
}

const ROLE_COLORS = {
  LEADER: 'var(--leader)',
  FOLLOWER: 'var(--follower)',
  CANDIDATE: 'var(--candidate)',
  DEAD: 'var(--dead)',
}

export default function NodeCard({ node, onKill, onRevive }) {
  const { nodeId, role, term, tasksExecuted, lamportClock, leaderId, lockedTask, alive } = node
  const roleStyle = ROLE_STYLES[role] || ROLE_STYLES.FOLLOWER
  const roleColor = ROLE_COLORS[role] || 'var(--muted)'

  return (
    <div style={{
      background: 'var(--surface)',
      borderRadius: '12px',
      padding: '1.5rem',
      ...roleStyle,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
        <span style={{
          fontSize: '4rem',
          fontFamily: 'var(--font-display)',
          lineHeight: 1,
          color: roleColor,
        }}>
          {nodeId}
        </span>
        <span style={{
          background: roleColor,
          color: '#000',
          padding: '0.25rem 0.75rem',
          borderRadius: '999px',
          fontSize: '0.7rem',
          fontWeight: 700,
          letterSpacing: '0.1em',
        }}>
          {role}
        </span>
      </div>

      <div style={{ color: 'var(--muted)', fontSize: '0.75rem', marginBottom: '1rem' }}>
        PORT: 5005{nodeId}
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gap: '0.5rem',
        marginBottom: '1rem',
      }}>
        {[
          ['TERM', term ?? '—'],
          ['TASKS EXECUTED', tasksExecuted ?? 0],
          ['LAMPORT CLK', lamportClock ?? 0],
          ['LEADER', leaderId ?? '—'],
        ].map(([label, value]) => (
          <div key={label} style={{
            background: 'rgba(255,255,255,0.03)',
            borderRadius: '6px',
            padding: '0.5rem',
          }}>
            <div style={{ color: 'var(--muted)', fontSize: '0.6rem', marginBottom: '0.2rem', letterSpacing: '0.08em' }}>
              {label}
            </div>
            <div style={{ fontSize: '0.85rem', fontWeight: 700 }}>{value}</div>
          </div>
        ))}
      </div>

      {lockedTask && (
        <div style={{
          background: 'rgba(255,170,0,0.1)',
          border: '1px solid rgba(255,170,0,0.3)',
          borderRadius: '6px',
          padding: '0.4rem 0.6rem',
          fontSize: '0.75rem',
          color: '#ffaa00',
          marginBottom: '1rem',
        }}>
          🔒 {lockedTask.slice(0, 8)}...
        </div>
      )}

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.75rem' }}>
          <span style={{
            width: '8px', height: '8px', borderRadius: '50%',
            background: alive ? '#22c55e' : 'var(--dead)',
            display: 'inline-block',
            boxShadow: alive ? '0 0 6px #22c55e' : 'none',
          }} />
          <span style={{ color: alive ? '#22c55e' : 'var(--dead)' }}>
            {alive ? 'ALIVE' : 'DEAD'}
          </span>
        </div>

        {alive ? (
          <button
            onClick={() => onKill(nodeId)}
            style={{
              background: 'transparent',
              border: '1px solid var(--dead)',
              color: 'var(--dead)',
              padding: '0.3rem 0.8rem',
              borderRadius: '6px',
              cursor: 'pointer',
              fontSize: '0.75rem',
              fontFamily: 'var(--font-mono)',
            }}
          >
            ⚡ KILL
          </button>
        ) : (
          <button
            onClick={() => onRevive(nodeId)}
            style={{
              background: 'transparent',
              border: '1px solid #22c55e',
              color: '#22c55e',
              padding: '0.3rem 0.8rem',
              borderRadius: '6px',
              cursor: 'pointer',
              fontSize: '0.75rem',
              fontFamily: 'var(--font-mono)',
            }}
          >
            ↺ REVIVE
          </button>
        )}
      </div>
    </div>
  )
}
