import React from 'react';  

export default function SearchResults({ results, onSelect }) {  
  return (  
    <div style={styles.panel}>  
      <div style={styles.header}>Search Results ({results.length})</div>  
      <div style={styles.list}>  
        {results.map((hit, i) => (  
          <div key={i} style={styles.item} onClick={() => onSelect(hit)}>  
            <span style={{ ...styles.kindBadge, background: kindColor(hit.kind) }}>{hit.kind}</span>  
            <span style={styles.name}>{hit.name || hit.id}</span>  
            <span style={styles.path}>{hit.filePath}:{hit.lineStart}</span>  
          </div>
        ))}
      </div>
    </div>
  );  
}  

function kindColor(kind) {  
  switch (kind) {    
    case 'CLASS': return '#7c3aed';     
    case 'METHOD': return '#2563eb';     
    case 'FIELD': return '#059669';     
    case 'FILE': return '#6b7280';     
    default: return '#4b5563';   
  } 
}  

const styles = {  
  panel: { height: '180px', borderTop: '1px solid #313244', background: '#1e1e2e', display: 'flex', flexDirection: 'column' },  
  header: { padding: '6px 12px', fontSize: '0.75rem', color: '#a6adc8', fontWeight: 600 },  
  list: { flex: 1, overflowY: 'auto' },  
  item: { display: 'flex', alignItems: 'center', gap: '8px', padding: '4px 12px', cursor: 'pointer', fontSize: '0.85rem' },  
  kindBadge: { padding: '1px 6px', borderRadius: '3px', fontSize: '0.7rem', color: '#fff' },  
  name: { color: '#cdd6f4' },  
  path: { marginLeft: 'auto', color: '#585b70', fontSize: '0.8rem' }
};