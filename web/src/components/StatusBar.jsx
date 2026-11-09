import React from 'react';  

export default function StatusBar({ classesIndexed, activeTab }) {  
  return (  
    <div style={styles.bar}>  
      <span>{classesIndexed.toLocaleString()} classes indexed</span>  
      {activeTab && (<>
        <span style={styles.sep}>│</span>
        <span>{activeTab.path}</span>
        <span style={styles.sep}>│</span>
        <span>{activeTab.totalLines} lines</span>
      </>)} 
    </div>
  );  
}  

const styles = {  
  bar: { display: 'flex', alignItems: 'center', gap: '12px', padding: '4px 16px', background: '#181825', borderTop: '1px solid #313244', fontSize: '0.75rem', color: '#a6adc8' },  
  sep: { opacity: 0.4 } 
};