import React, { useState, useEffect } from 'react';

export default function Dashboard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetch('/api/dashboard')
      .then(r => r.json())
      .then(setData)
      .catch(e => setError(e.message));
  }, []);

  if (error) {
    return <div style={styles.center}><span style={styles.errorText}>Failed to load dashboard: {error}</span></div>;
  }

  if (!data) {
    return <div style={styles.center}><span style={styles.loading}>Loading…</span></div>;
  }

  const { stats, git, recentChanges, indexAgeMinutes } = data;

  return (
    <div style={styles.container}>
      <h1 style={styles.title}>⚡ Jolt — Project Dashboard</h1>

      <div style={styles.grid}>
        {/* Project Stats */}
        <div style={styles.card}>
          <h2 style={styles.cardTitle}>📊 Project Stats</h2>
          <div style={styles.statRow}>
            <Stat label="Classes" value={stats?.classes ?? '—'} />
            <Stat label="Methods" value={stats?.methods ?? '—'} />
            <Stat label="Files Indexed" value={stats?.filesIndexed ?? '—'} />
          </div>
          {indexAgeMinutes != null && (
            <p style={styles.subtle}>Index age: {indexAgeMinutes} min</p>
          )}
        </div>

        {/* Git Status */}
        <div style={styles.card}>
          <h2 style={styles.cardTitle}>🌿 Git Status</h2>
          {git ? (
            <>
              <div style={styles.branchBadge}>{git.branch}</div>
              {git.lastCommit && (
                <div style={styles.commitInfo}>
                  <span style={styles.hash}>{git.lastCommit.hash}</span>{' '}
                  {git.lastCommit.message}
                  <br />
                  <span style={styles.subtle}>{git.lastCommit.author} · {git.lastCommit.date}</span>
                </div>
              )}
              {git.aheadBy > 0 && (
                <p style={styles.ahead}>↑ {git.aheadBy} ahead of remote</p>
              )}
              {git.totalCommits != null && (
                <p style={styles.subtle}>{git.totalCommits} total commits</p>
              )}
            </>
          ) : (
            <p style={styles.subtle}>Not a git repository</p>
          )}
        </div>

        {/* Recent Changes */}
        <div style={styles.card}>
          <h2 style={styles.cardTitle}>📝 Recent Changes</h2>
          {recentChanges?.length > 0 ? (
            <ul style={styles.changeList}>
              {recentChanges.slice(0, 5).map((c, i) => (
                <li key={i} style={styles.changeItem}>
                  <ActionBadge action={c.action} /> {c.file}
                </li>
              ))}
            </ul>
          ) : (
            <p style={styles.subtle}>No recent changes</p>
          )}
        </div>
      </div>
    </div>
  );
}

function Stat({ label, value }) {
  return (
    <div style={styles.statBox}>
      <span style={styles.statValue}>{value}</span>
      <span style={styles.statLabel}>{label}</span>
    </div>
  );
}

function ActionBadge({ action }) {
  const colors = { modified: '#f9e2af', added: '#a6e3a1', deleted: '#f38ba8' };
  return (
    <span style={{ ...styles.badge, color: colors[action] || '#a6adc8' }}>
      {action?.toUpperCase() || '?'}
    </span>
  );
}

const styles = {
  container: { flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '40px', overflow: 'auto' },
  center: { flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' },
  title: { color: '#cdd6f4', fontSize: '1.5rem', marginBottom: '24px' },
  grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px', width: '100%', maxWidth: '900px' },
  card: { background: '#181825', border: '1px solid #313244', borderRadius: '10px', padding: '20px' },
  cardTitle: { color: '#a6adc8', fontSize: '0.9rem', margin: '0 0 12px' },
  statRow: { display: 'flex', gap: '16px' },
  statBox: { display: 'flex', flexDirection: 'column', alignItems: 'center' },
  statValue: { color: '#cdd6f4', fontSize: '1.4rem', fontWeight: 700 },
  statLabel: { color: '#a6adc8', fontSize: '0.75rem' },
  branchBadge: { display: 'inline-block', background: '#313244', color: '#a6e3a1', borderRadius: '4px', padding: '2px 8px', fontSize: '0.85rem', fontWeight: 600, marginBottom: '8px' },
  commitInfo: { color: '#cdd6f4', fontSize: '0.85rem', lineHeight: 1.5 },
  hash: { fontFamily: "'JetBrains Mono', monospace", color: '#89b4fa' },
  ahead: { color: '#f9e2af', fontSize: '0.8rem' },
  subtle: { color: '#6c7086', fontSize: '0.8rem' },
  changeList: { listStyle: 'none', padding: 0, margin: 0 },
  changeItem: { color: '#cdd6f4', fontSize: '0.85rem', padding: '4px 0', borderBottom: '1px solid #313244' },
  badge: { fontFamily: "'JetBrains Mono', monospace", fontSize: '0.7rem', fontWeight: 700, marginRight: '6px' },
  loading: { color: '#a6adc8' },
  errorText: { color: '#f38ba8' }
};