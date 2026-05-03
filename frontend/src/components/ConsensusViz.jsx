const ROLE_COLORS = {
  LEADER: '#00d4ff',
  FOLLOWER: '#6366f1',
  CANDIDATE: '#ffaa00',
  DEAD: '#ff3366',
}

const NODE_POSITIONS = {
  1: [100, 100],
  2: [300, 50],
  3: [500, 100],
}

export default function ConsensusViz({ nodes }) {
  if (!nodes || nodes.length === 0) {
    return (
      <div style={{
        background: 'var(--surface)',
        border: '1px solid var(--border)',
        borderRadius: '12px',
        padding: '1rem',
        marginBottom: '1.5rem',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        height: '200px',
        color: 'var(--muted)',
        fontSize: '0.85rem',
      }}>
        Connecting...
      </div>
    )
  }

  const leader = nodes.find(n => n.role === 'LEADER')
  const followers = nodes.filter(n => n.role === 'FOLLOWER')
  const maxTerm = Math.max(...nodes.map(n => n.currentTerm ?? 0))

  return (
    <div style={{
      background: 'var(--surface)',
      border: '1px solid var(--border)',
      borderRadius: '12px',
      padding: '1rem',
      marginBottom: '1.5rem',
    }}>
      <style>{`
        .flow-line {
          stroke-dasharray: 8 4;
          animation: dash 0.8s linear infinite;
        }
      `}</style>
      <svg viewBox="0 0 600 170" style={{ width: '100%', maxWidth: '600px', display: 'block', margin: '0 auto' }}>
        <defs>
          <marker id="arrow-leader" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
            <path d="M0,0 L0,6 L6,3 z" fill="#00d4ff" />
          </marker>
        </defs>

        {leader && followers.map(follower => {
          const [lx, ly] = NODE_POSITIONS[leader.nodeId] || [300, 100]
          const [fx, fy] = NODE_POSITIONS[follower.nodeId] || [300, 100]
          const dx = fx - lx
          const dy = fy - ly
          const len = Math.sqrt(dx * dx + dy * dy)
          const r = 35
          const sx = lx + (dx / len) * r
          const sy = ly + (dy / len) * r
          const ex = fx - (dx / len) * r
          const ey = fy - (dy / len) * r
          return (
            <line
              key={`${leader.nodeId}-${follower.nodeId}`}
              x1={sx} y1={sy} x2={ex} y2={ey}
              stroke="#00d4ff"
              strokeWidth="1.5"
              strokeOpacity="0.6"
              markerEnd="url(#arrow-leader)"
              className="flow-line"
            />
          )
        })}

        {nodes.map(node => {
          const [cx, cy] = NODE_POSITIONS[node.nodeId] || [300, 100]
          const color = ROLE_COLORS[node.role] || '#64748b'
          return (
            <g key={node.nodeId}>
              {node.role === 'LEADER' && (
                <text
                  x={cx} y={cy - 42}
                  textAnchor="middle"
                  fill="#00d4ff"
                  fontSize="18"
                  fontFamily="var(--font-mono)"
                >★</text>
              )}
              <circle
                cx={cx} cy={cy} r={35}
                fill={color + '22'}
                stroke={color}
                strokeWidth="2"
              />
              <text
                x={cx} y={cy + 5}
                textAnchor="middle"
                fill="white"
                fontSize="13"
                fontFamily="var(--font-mono)"
                fontWeight="700"
              >
                N{node.nodeId}
              </text>
              <text
                x={cx} y={cy + 52}
                textAnchor="middle"
                fill={color}
                fontSize="9"
                fontFamily="var(--font-mono)"
                letterSpacing="1"
              >
                {node.role}
              </text>
            </g>
          )
        })}

        <text
          x="300" y="155"
          textAnchor="middle"
          fill="var(--muted)"
          fontSize="11"
          fontFamily="var(--font-mono)"
        >
          TERM {maxTerm}
        </text>
      </svg>
    </div>
  )
}
