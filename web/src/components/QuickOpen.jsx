import React, { useState, useEffect, useRef } from 'react';
import * as api from '../api';

export default function QuickOpen({ open, onClose, onSelectFile }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [highlighted, setHighlighted] = useState(0);
  const inputRef = useRef(null);

  // Focus input when opened
  useEffect(() => {
    if (open) {
      setQuery('');
      setResults([]);
      setHighlighted(0);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [open]);

  // Fuzzy filter as user types (debounced via effect)  
  useEffect(() => {  
    if (!query.trim()) { setResults([]); return; }   
    let cancelled = false;   

    // Fetch all files from root tree then filter client-side  
    api.getDirectoryTree('').then(tree => {     
      if (cancelled) return;      
      const paths = flattenPaths(tree || []);      
      const q = query.toLowerCase();      
      const filtered = paths.filter(p => p.toLowerCase().includes(q)).slice(0, 20);      
      setResults(filtered);      
      setHighlighted(0);    
   }).catch(console.error);  

    return () => { cancelled = true; };  
  }, [query]);

  // Keyboard navigation within the palette  
  const handleKeyDown = (e) => {    
    if (e.key === 'ArrowDown') { e.preventDefault(); setHighlighted(h => Math.min(h + 1, results.length - 1)); }    
    else if (e.key === 'ArrowUp') { e.preventDefault(); setHighlighted(h => Math.max(h - 1, 0)); }    
    else if (e.key === 'Enter' && results[highlighted]) { onSelectFile(results[highlighted]); onClose(); }    
    else if (e.key === 'Escape') { onClose(); }  
  };

  if (!open) return null;

  return (
    <div style={styles.overlay} onClick={onClose}>
      <div style={styles.panel} onClick={e => e.stopPropagation()}>
        <input
          ref={inputRef}
          type="text"
          placeholder="Search files…"
          value={query}
          onChange={e => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          style={styles.input}
        />
        <ul style={styles.list}>
          {results.map((path, i) => (
            <li key={path} 
                style={{ ...styles.item, ...(i === highlighted ? styles.itemActive : {}) }}  
                onMouseEnter={() => setHighlighted(i)}  
                onClick={() => { onSelectFile(path); onClose(); }}>   
              {renderPathSegments(path)}  
            </li>  
          ))}  
        </ul>  
      </div>  
    </div>  
  );  
}  

function flattenPaths(nodes, prefix = '') {  
  const out = [];  
  for (const node of nodes) {    
    const p = prefix ? `${prefix}/${node.name}` : node.name;    
    if (node.type === 'file') out.push(p);    
    else if (node.children) out.push(...flattenPaths(node.children, p));  
  }  
  return out;  
}  

function renderPathSegments(path) {  
  const parts = path.split('/');  
  const fileName = parts.pop();   
  const dirParts = parts.join('/') + '/';   
  return (<><span style={styles.dir}>{dirParts}</span><span style={styles.file}>{fileName}</span></>);  
}  

const styles = {
  overlay: { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', paddingTop: '15vh', zIndex: 1000 },
  panel: { width: '480px', maxHeight: '320px', background: '#1e1e2e', borderRadius: '8px', boxShadow: '0 8px 32px rgba(0,0,0,0.4)', overflow: 'hidden' },
  input: { width: '100%', padding: '12px 16px', border: 'none', borderBottom: '1px solid #313244', background: '#181825', color: '#cdd6f4', fontSize: '1rem', outline: 'none' },
  list: { listStyle: 'none', margin: 0, padding: '4px 0', overflowY: 'auto' },
  item: { padding: '8px 16px', cursor: 'pointer', fontSize: '0.9rem' },
  itemActive: { background: '#313244' },
  dir: { color: '#a6adc8' },
  file: { color: '#cdd6f4', fontWeight: 500 }  
};