import React, { useState, useEffect, useRef } from 'react';
import * as api from '../api';

// Assign a consistent color to each author  
const AUTHOR_COLORS = [  
  '#89b4fa', '#a6e3a1', '#f9e2af', '#f38ba8', '#cba6f7',  
  '#94e2d5', '#fab387', '#f5c2e7', '#74c7ec', '#bae6fd'  
];  

function authorColor(author) {  
  if (!author) return '#585b70';  
  let hash = 0;  
  for (let i = 0; i < author.length; i++) {    
    hash = ((hash << 5) - hash + author.charCodeAt(i)) | 0;   
  }   
  return AUTHOR_COLORS[Math.abs(hash) % AUTHOR_COLORS.length]; 
}  

export default function BlameGutter({ filePath, totalLines, activeLine }) {  
  const [blameData, setBlameData] = useState(null);  
  const [loading, setLoading] = useState(false);  
  const [hoveredLine, setHoveredLine] = useState(null);  
  const containerRef = useRef(null);  

  // Fetch blame when file changes or on mount  
  useEffect(() => {    
    if (!filePath) { setBlameData(null); return; }    
    setLoading(true);    
    api.getBlame(filePath, 1, totalLines || null)      
      .then(setBlameData)      
      .catch(() => setBlameData([]))      
      .finally(() => setLoading(false));   
  }, [filePath, totalLines]);  

  // Scroll gutter to follow active line  
  useEffect(() => {    
    if (!containerRef.current || activeLine == null) return;    
    const el = containerRef.current.querySelector(`[data-line="${activeLine}"]`);    
    if (el) el.scrollIntoView({ block: 'nearest' });   
  }, [activeLine]);  

  if (!filePath) return null;  

  if (loading) {    
    return <div style={styles.wrap}><span style={styles.loading}>…</span></div>;   
  }  

  if (!blameData || blameData.length === 0) return null;  

  // Group consecutive lines by author for visual bands  
  const lines = blameData.map(b => ({ ...b, color: authorColor(b.author) }));  

  return (  
    <div style={styles.wrap} ref={containerRef}>  
      {lines.map(line => (
        <div
          key={line.line}
          data-line={line.line}
          style={{ ...styles.line, borderLeftColor: line.color }}
          onMouseEnter={() => setHoveredLine(line)}
          onMouseLeave={() => setHoveredLine(null)}
        >
          <span style={{ ...styles.lineNum, color: line.color }}>{line.line}</span>
        </div>
      ))}  

      {/* Tooltip on hover */}  
      {hoveredLine && (
        <div style={{ ...styles.tooltip, left: '100%', top: `${(hoveredLine.line / lines.length) * 100}%` }}>  
          <strong>{hoveredLine.author}</strong>{' '}  
          <span style={styles.tooltipDate}>{hoveredLine.date}</span>{' '}  
          <span style={styles.tooltipHash}>{hoveredLine.hash}</span>{' '}  
          {hoveredLine.summary}  
        </div>  
      )}  
    </div>  
  );  
}  

const styles = {  
  wrap: { width: '42px', background: '#11111b', borderRight: '1px solid #313244', overflowY: 'auto', position: 'relative', flexShrink: 0 },  
  line: { height: '22px', display: 'flex', alignItems: 'center', paddingLeft: '4px', borderLeft: '3px solid transparent' },  
  lineNum: { fontSize: '0.65rem', fontFamily: "'JetBrains Mono', monospace", opacity: 0.8 },  
  tooltip: { position: 'absolute', background: '#1e1e2e', border: '1px solid #313244', borderRadius: '6px', padding: '6px 10px', color: '#cdd6f4', fontSize: '0.75rem', whiteSpace: 'nowrap', zIndex: 10, pointerEvents: 'none' },  
  tooltipDate: { color: '#a6adc8' },  
  tooltipHash: { color: '#89b4fa', fontFamily: "'JetBrains Mono', monospace" },  
  loading: { color: '#585b70', fontSize: '0.8rem' } 
};