import React, { useState, useRef } from 'react';
import * as api from '../api';

export default function SearchBar({ onResults }) {
  const [query, setQuery] = useState('');
  const [mode, setMode] = useState('text'); // 'text' | 'semantic'
  const inputRef = useRef(null);

  async function handleSearch(e) {
    e.preventDefault();
    if (!query.trim()) return;
    try {
      const results = mode === 'semantic'
        ? await api.searchSemantic(query.trim())
        : await api.search(query.trim());
      onResults(results);
    } catch (err) { console.error('Search failed:', err); }
  }

  // Ctrl+Shift+F to focus search
  React.useEffect(() => {
    const handler = (e) => {
      if (e.ctrlKey && e.shiftKey && e.key === 'F') {
        e.preventDefault();
        inputRef.current?.focus();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  return (
    <form onSubmit={handleSearch} style={styles.form}>
      <div style={styles.modeToggle}>
        <button type="button" onClick={() => setMode('text')} style={{ ...styles.modeBtn, ...(mode === 'text' ? styles.modeBtnActive : {}) }}>Text</button>
        <button type="button" onClick={() => setMode('semantic')} style={{ ...styles.modeBtn, ...(mode === 'semantic' ? styles.modeBtnActive : {}) }}>Semantic</button>
      </div>
      <input 
        ref={inputRef} 
        type="text" 
        placeholder={mode === 'semantic' ? 'Semantic search… (Ctrl+Shift+F)' : 'Search symbols, files… (Ctrl+Shift+F)'}
        value={query} 
        onChange={(e) => setQuery(e.target.value)} 
        style={styles.input}
      />
      <button type="submit" style={styles.btn}>🔍</button>
    </form>
  );
}

const styles = {
  form: { display: 'flex', gap: '8px', flex: 1, maxWidth: '560px', alignItems: 'center' },
  modeToggle: { display: 'flex', borderRadius: '4px', overflow: 'hidden', border: '1px solid #45475a' },
  modeBtn: { padding: '4px 10px', background: '#313244', border: 'none', cursor: 'pointer', color: '#a6adc8', fontSize: '0.8rem' },
  modeBtnActive: { background: '#585b70', color: '#cdd6f4' },
  input: { flex: 1, padding: '6px 12px', background: '#313244', border: 'none', borderRadius: '4px', color: '#cdd6f4', fontSize: '0.9rem' },
  btn: { padding: '6px 12px', background: '#585b70', border: 'none', borderRadius: '4px', cursor: 'pointer', color: '#cdd6f4' }
};