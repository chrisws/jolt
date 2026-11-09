import React from 'react';

export default function Breadcrumbs({ path }) {
  if (!path) return null;

  const segments = path.split('/').filter(Boolean);

  return (
    <nav style={styles.breadcrumb} aria-label="File path">
      {segments.map((seg, i) => {
        const isLast = i === segments.length - 1;
        const subPath = segments.slice(0, i + 1).join('/');
        return (
          <span key={subPath} style={styles.segment}>
            {!isLast && (
              <button style={styles.link} title={`Go to ${subPath}`}>
                {seg}
              </button>
            )}
            {isLast && <span style={styles.current}>{seg}</span>}
            {!isLast && <span style={styles.sep}>›</span>}
          </span>
        );
      })}
    </nav>
  );
}

const styles = {
  breadcrumb: { display: 'flex', alignItems: 'center', gap: '2px', padding: '4px 16px', fontSize: '0.8rem', color: '#a6adc8' },  
  segment: { display: 'inline-flex', alignItems: 'center' },  
  link: { background: 'none', border: 'none', cursor: 'pointer', color: '#89b4fa', padding: '2px 4px', borderRadius: '3px', font: 'inherit' },  
  current: { color: '#cdd6f4', fontWeight: 500, padding: '2px 4px' },  
  sep: { margin: '0 2px', opacity: 0.5 }  
};