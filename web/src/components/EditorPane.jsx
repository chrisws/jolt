import React from 'react';  
import Editor from '@monaco-editor/react';  
import SearchResults from './SearchResults';  

export default function EditorPane({ tabs, activeTabId, onActivateTab, onCloseTab, searchResults, onResultClick }) {  
  const activeTab = tabs.find(t => t.id === activeTabId);  

  return (  
    <div style={styles.container}>  
      {/* Tab bar */}  
      <div style={styles.tabBar}>  
        {tabs.map(tab => (  
          <div 
            key={tab.id} 
            style={{ ...styles.tab, ...(tab.id === activeTabId ? styles.activeTab : {}) }}
            onClick={() => onActivateTab(tab.id)}
          >
            <span>{tab.name}</span>
            <span style={styles.closeBtn} onClick={(e) => { e.stopPropagation(); onCloseTab(tab.id); }}>×</span>
          </div>
        ))}
      </div>  

      {/* Editor or empty state */}  
      {activeTab ? (  
        <Editor 
          path={activeTab.path} 
          language={activeTab.language} 
          value={activeTab.content} 
          theme="vs-dark" 
          options={{ minimap: { enabled: true }, fontSize: 14, lineNumbers: 'on', scrollBeyondLastLine: false }}
        />
      ) : (  
        <div style={styles.emptyState}>Open a file from the explorer to begin</div>
      )}  

      {/* Search results panel (shown when results exist) */}  
      {searchResults.length > 0 && (  
        <SearchResults results={searchResults} onSelect={onResultClick} />
      )}
    </div>
  );  
}  

const styles = {  
  container: { flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' },  
  tabBar: { display: 'flex', background: '#181825', borderBottom: '1px solid #313244', overflowX: 'auto' },  
  tab: { display: 'flex', alignItems: 'center', gap: '6px', padding: '6px 12px', cursor: 'pointer', color: '#a6adc8', borderRight: '1px solid #313244', whiteSpace: 'nowrap' },  
  activeTab: { background: '#1e1e2e', color: '#cdd6f4' },  
  closeBtn: { marginLeft: '4px', opacity: 0.6, fontSize: '0.9rem' },  
  emptyState: { flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#585b70', fontSize: '1rem' } 
};