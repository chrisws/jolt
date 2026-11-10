import React, { useState, useRef, useEffect } from 'react';

export default function FeedbackButton({ currentPage = '', currentFile = '' }) {
  const [open, setOpen] = useState(false);
  const [message, setMessage] = useState('');
  const [sent, setSent] = useState(false);
  const ref = useRef(null);

  // Close on outside click  
  useEffect(() => {    
    if (!open) return;    
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };    
    document.addEventListener('mousedown', handler);   
    return () => document.removeEventListener('mousedown', handler);  
  }, [open]);  

  const submit = async () => {    
    if (!message.trim()) return;    
    try {      
      await fetch('/api/feedback', {        
        method: 'POST',        
        headers: { 'Content-Type': 'application/json' },        
        body: JSON.stringify({ page: currentPage, file: currentFile || undefined, message })      
      });      
      setMessage('');      
      setSent(true);      
      setTimeout(() => { setSent(false); setOpen(false); }, 1500);    
   } catch (e) { console.error('Feedback failed:', e); } 
  };  

  return (  
    <div style={styles.wrap} ref={ref}>  
      <button style={styles.btn} onClick={() => setOpen(v => !v)} title="Give feedback">     
        💬   
      </button>   

      {open && (
        <div style={styles.popover}>
          <p style={styles.label}>What needs improving?</p>
          <textarea
            value={message}
            onChange={e => setMessage(e.target.value)}
            placeholder="Describe the issue or suggestion…"
            rows={4}
            style={styles.textarea}
          />
          <div style={styles.actions}>
            <button style={{ ...styles.submitBtn, opacity: sent ? 0.6 : 1 }} onClick={submit} disabled={!message.trim()}>  
              {sent ? '✓ Sent!' : 'Submit'}  
            </button>  
            <button style={styles.cancelBtn} onClick={() => setOpen(false)}>Cancel</button>  
          </div>  
        </div>  
      )}  
    </div>  
  );  
}  

const styles = {  
  wrap: { position: 'relative' },  
  btn: { background: 'none', border: 'none', cursor: 'pointer', fontSize: '1rem', padding: '4px 8px', borderRadius: '4px' },  
  popover: { position: 'absolute', bottom: 'calc(100% + 8px)', right: 0, width: '280px', background: '#1e1e2e', border: '1px solid #313244', borderRadius: '8px', padding: '12px', boxShadow: '0 -4px 16px rgba(0,0,0,0.3)' },  
  label: { margin: '0 0 8px', fontSize: '0.85rem', color: '#a6adc8' },  
  textarea: { width: '100%', boxSizing: 'border-box', background: '#181825', border: '1px solid #313244', borderRadius: '6px', color: '#cdd6f4', padding: '8px', resize: 'vertical', fontFamily: 'inherit' },  
  actions: { display: 'flex', gap: '8px', marginTop: '8px' },  
  submitBtn: { flex: 1, background: '#89b4fa', color: '#1e1e2e', border: 'none', borderRadius: '6px', padding: '6px 12px', cursor: 'pointer', fontWeight: 500 },  
  cancelBtn: { background: '#313244', color: '#cdd6f4', border: 'none', borderRadius: '6px', padding: '6px 12px', cursor: 'pointer' }  
};