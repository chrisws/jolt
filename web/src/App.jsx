import React, { useState, useEffect } from 'react';  
import FileTree from './components/FileTree';  
import EditorPane from './components/EditorPane';  
import SearchBar from './components/SearchBar';  
import StatusBar from './components/StatusBar';  
import * as api from './api';  

export default function App() {  
  // Tabs: [{ id, path, name, content, language, lineStart }]  
  const [tabs, setTabs] = useState([]);  
  const [activeTabId, setActiveTabId] = useState(null);  
  const [searchResults, setSearchResults] = useState([]);  
  const [statusInfo, setStatusInfo] = useState({ classesIndexed: 0 });  

  // Load status on mount  
  useEffect(() => {  
    api.getStatus().then(setStatusInfo).catch(console.error);  
  }, []);  

  // Open a file (from tree click or search result)  
  const openFile = async (path) => {  
    // If already open, just activate the tab  
    const existing = tabs.find(t => t.path === path);   
    if (existing) { setActiveTabId(existing.id); return; }   

    try {
      const data = await api.getFile(path);
      const newTab = {
        id: Date.now(),
        path: data.path,
        name: data.path.split('/').pop(),
        content: data.content,
        language: data.language || 'plaintext',
        totalLines: data.totalLines
      };
      setTabs(prev => [...prev, newTab]);
      setActiveTabId(newTab.id);
    } catch (e) { console.error('Failed to open file:', e); } 
  };

  // Close a tab  
  const closeTab = (id) => {  
    setTabs(prev => prev.filter(t => t.id !== id));   
    if (activeTabId === id) {     
      const remaining = tabs.filter(t => t.id !== id);     
      setActiveTabId(remaining.length > 0 ? remaining[remaining.length - 1].id : null);   
    }  
  };  

  // Handle search results click → open file at line  
  const onSearchResultClick = async (hit) => {  
    await openFile(hit.filePath);    
    // We could also scroll to the line — Monaco handles that via options  
  };  

  return (  
    <div style={styles.app}>  
      {/* Top toolbar */}  
      <div style={styles.toolbar}>  
        <span style={styles.logo}>⚡ Jolt</span>  
        <SearchBar onResults={setSearchResults} />  
      </div>  

      {/* Main area: tree + editor */}  
      <div style={styles.mainArea}>  
        <FileTree onSelectFile={openFile} activePath={tabs.find(t => t.id === activeTabId)?.path} />
        
        <EditorPane 
          tabs={tabs} 
          activeTabId={activeTabId} 
          onActivateTab={setActiveTabId} 
          onCloseTab={closeTab} 
          searchResults={searchResults} 
          onResultClick={onSearchResultClick}
        />
      </div>  

      {/* Bottom status bar */}  
      <StatusBar classesIndexed={statusInfo.classesIndexed || 0} activeTab={tabs.find(t => t.id === activeTabId)} />
    </div>
  );
}

const styles = {
  app: { display: 'flex', flexDirection: 'column', height: '100vh', fontFamily: "'Segoe UI', system-ui, sans-serif", background: '#1e1e2e' },
  toolbar: { display: 'flex', alignItems: 'center', gap: '16px', padding: '8px 16px', background: '#181825', borderBottom: '1px solid #313244' },
  logo: { fontWeight: 700, fontSize: '1.2rem', color: '#cdd6f4' },
  mainArea: { flex: 1, display: 'flex', overflow: 'hidden' }
};