import React, { useState, useRef, useEffect } from 'react';
import * as api from '../api';
import { sendWs } from '../ws';

const DEBOUNCE_MS = 300;
const MIN_QUERY_LEN = 2;

export default function SearchBar({ onResults }) {
  const [query, setQuery] = useState('');
  const [mode, setMode] = useState('text'); // 'text' | 'semantic'
  const inputRef = useRef(null);
  const debounceRef = useRef(null);

  // Debounced live search via WebSocket
  function handleInputChange(e) {
    const val = e.target.value;
    setQuery(val);

    if (debounceRef.current) clearTimeout(debounceRef.current);

    const trimmed = val.trim();
    if (trimmed.length >= MIN_QUERY_LEN) {
      debounceRef.current = setTimeout(() => {
        sendWs({ type: 'search', q: trimmed, mode, limit: 50 });
      }, DEBOUNCE_MS);
    } else {
      onResults([]); // clear results for short/empty queries
    }
  }

  // Enter key still works as explicit search (HTTP fallback)
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

  // Ctrl+Shift+F to focus search + cleanup debounce on unmount
  useEffect(() => {
    const handler = (e) => {
      if (e.ctrlKey && e.shiftKey && e.key === 'F') {
        e.preventDefault();
        inputRef.current?.focus();
      }
    };
    window.addEventListener('keydown', handler);
    return () => {
      window.removeEventListener('keydown', handler);
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
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
        onChange={handleInputChange} 
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
