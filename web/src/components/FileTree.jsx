import React, { useState, useEffect } from 'react';  
import * as api from '../api';  

export default function FileTree({ onSelectFile, activePath }) {  
  const [rootEntries, setRootEntries] = useState([]);  
  const [expandedDirs, setExpandedDirs] = useState({}); // path → entries[]  
  const [loading, setLoading] = useState(false);  

  // Load root on mount  
  useEffect(() => { loadDir(''); }, []);  

  async function loadDir(path) {  
    try {
      const entries = await api.getDirectoryTree(path);
      if (path === '') setRootEntries(entries); 
      else setExpandedDirs(prev => ({ ...prev, [path]: entries })); 
    } catch (e) { console.error('Failed to load dir:', e); } 
  }  

  function toggleDir(entry) {  
    if (entry.type !== 'directory') return;   
    if (expandedDirs[entry.path]) {     
      // Collapse     
      setExpandedDirs(prev => {       const next = { ...prev };       
      delete next[entry.path];       
      return next;     
     });   
    } else {     
      loadDir(entry.path);   
    }  
  }  

  function renderEntries(entries, depth = 0) {  
    return entries.map(entry => (  
      <div key={entry.path}>  
        <div 
          style={{ ...styles.item, paddingLeft: `${depth * 16 + 8}px`, background: entry.path === activePath ? '#313244' : undefined }}
          onClick={() => entry.type === 'file' ? onSelectFile(entry.path) : toggleDir(entry)}
        >
          <span style={styles.icon}>{entry.type === 'directory' ? '📁' : fileIcon(entry.name)}</span>
          <span>{entry.name}</span>
        </div>
        {/* Render children of expanded dirs */}  
        {entry.type === 'directory' && expandedDirs[entry.path] && renderEntries(expandedDirs[entry.path], depth + 1)}
      </div>
    ));  
  }  

  return (  
    <div style={styles.tree}>  
      <div style={styles.header}>Explorer</div>  
      {renderEntries(rootEntries)}  
    </div>
  );  
}  

function fileIcon(name) {  
  if (name.endsWith('.java')) return '☕';   
  if (name.endsWith('.xml')) return '📄';   
  if (name.endsWith('.jsp')) return '🟢';   
  if (name.endsWith('.sql')) return '🗃️';   
  if (name.endsWith('.md')) return '📝';   
  return '·'; 
}  

const styles = {  
  tree: { width: '240px', overflowY: 'auto', background: '#1e1e2e', borderRight: '1px solid #313244', fontSize: '0.85rem' },  
  header: { padding: '8px 12px', fontWeight: 600, color: '#a6adc8', textTransform: 'uppercase', fontSize: '0.75rem' },  
  item: { display: 'flex', alignItems: 'center', gap: '6px', padding: '3px 8px', cursor: 'pointer', color: '#cdd6f4' },  
  icon: { width: '16px', textAlign: 'center' }
};