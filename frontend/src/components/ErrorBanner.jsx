export default function ErrorBanner({ message }) {
  return (
    <div style={{
      background: 'rgba(255,51,102,0.1)',
      border: '1px solid rgba(255,51,102,0.4)',
      borderRadius: '8px',
      padding: '0.75rem 1rem',
      color: 'var(--dead)',
      fontSize: '0.8rem',
      marginBottom: '1.5rem',
      display: 'flex',
      alignItems: 'center',
      gap: '0.5rem',
    }}>
      <span>⚠</span>
      <span>{message}</span>
    </div>
  )
}
