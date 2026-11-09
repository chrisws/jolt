import React, { useState, useRef } from 'react';  
import * as api from '../api';  

export default function SearchBar({ onResults }) {  
  const [query, setQuery] = useState('');  
  const inputRef = useRef(null);  

  async function handleSearch(e) {  
    e.preventDefault();   
    if (!query.trim()) return;   
    try {     
      const results = await api.search(query.trim());     
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
      <input 
        ref={inputRef} 
        type="text" 
        placeholder="Search symbols, files... (Ctrl+Shift+F)"
        value={query} 
        onChange={(e) => setQuery(e.target.value)} 
        style={styles.input}
      />
      <button type="submit" style={styles.btn}>🔍</button>
    </form>
  );  
}  

const styles = {  
  form: { display: 'flex', gap: '8px', flex: 1, maxWidth: '500px' },  
  input: { flex: 1, padding: '6px 12px', background: '#313244', border: 'none', borderRadius: '4px', color: '#cdd6f4', fontSize: '0.9rem' },  
  btn: { padding: '6px 12px', background: '#585b70', border: 'none', borderRadius: '4px', cursor: 'pointer', color: '#cdd6f4' } 
};