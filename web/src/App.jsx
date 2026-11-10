import React, { useState, useEffect } from 'react';  
import FileTree from './components/FileTree';  
import EditorPane from './components/EditorPane';  
import SearchBar from './components/SearchBar';  
import StatusBar from './components/StatusBar';  
import Breadcrumbs from './components/Breadcrumbs';  
import QuickOpen from './components/QuickOpen';  
import FeedbackButton from './components/FeedbackButton';  
import * as api from './api';  

export default function App() {  
  const [tabs, setTabs] = useState([]);  
  const [activeTabId, setActiveTabId] = useState(null);  
  const [searchResults, setSearchResults] = useState([]);  
  const [statusInfo, setStatusInfo] = useState({ classesIndexed: 0 });  
  const [quickOpenVisible, setQuickOpenVisible] = useState(false);  

  // Load status on mount  
  useEffect(() => {    
    api.getStatus().then(setStatusInfo).catch(console.error);   
  }, []);  

  // ─── Keyboard Shortcuts ──────────────────────────────────────
  useEffect(() => {
    const handler = (e) => {
      // Ctrl+P → Quick Open palette
      if ((e.ctrlKey || e.metaKey) && e.key === 'p') {
        e.preventDefault();
        setQuickOpenVisible(v => !v);
      }
      // Ctrl+W → Close active tab
      else if ((e.ctrlKey || e.metaKey) && e.key === 'w') {
        e.preventDefault();
        closeActiveTab();
      }
      // Ctrl+Tab / Ctrl+Shift+Tab → Next/Prev tab
      else if ((e.ctrlKey || e.metaKey) && e.key === 'Tab') {
        e.preventDefault();
        cycleTabs(e.shiftKey ? -1 : 1);
      }
    };

    window.addEventListener('keydown', handler);  
    return () => window.removeEventListener('keydown', handler);  
  });  

  // ─── Tab Management ──────────────────────────────────────────  
  const openFile = async (path) => {    
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

  const closeActiveTab = () => {  
    if (!activeTabId) return;   
    setTabs(prev => prev.filter(t => t.id !== activeTabId));   
    if (activeTabId) {     
      const remaining = tabs.filter(t => t.id !== activeTabId);     
      setActiveTabId(remaining.length > 0 ? remaining[remaining.length - 1].id : null);   
    }  
  };  

  const closeTab = (id) => {  
    setTabs(prev => prev.filter(t => t.id !== id));   
    if (activeTabId === id) {     
      const remaining = tabs.filter(t => t.id !== id);     
      setActiveTabId(remaining.length > 0 ? remaining[remaining.length - 1].id : null);   
    }  
  };  

  const cycleTabs = (direction) => {    
    if (tabs.length <= 1) return;    
    const idx = tabs.findIndex(t => t.id === activeTabId);    
    const nextIdx = (idx + direction + tabs.length) % tabs.length;    
    setActiveTabId(tabs[nextIdx].id);  
  };  

  // ─── Copy Link ───────────────────────────────────────────────
  const copyLink = () => {    
    const tab = tabs.find(t => t.id === activeTabId);    
    if (!tab) return;    
    const url = `${window.location.origin}?file=${encodeURIComponent(tab.path)}`;    
    navigator.clipboard.writeText(url).then(() => {      
      setStatusInfo(prev => ({ ...prev, copied: true }));      
      setTimeout(() => setStatusInfo(prev => ({ ...prev, copied: false })), 2000);    
   }).catch(console.error);  
  };  

  // ─── Search Result Click ─────────────────────────────────────
  const onSearchResultClick = async (hit) => {  
    await openFile(hit.filePath);  
  };  

  const activePath = tabs.find(t => t.id === activeTabId)?.path || '';

  return (  
    <div style={styles.app}>  
      {/* Top toolbar */}  
      <div style={styles.toolbar}>  
        <span style={styles.logo}>⚡ Jolt</span>  
        <SearchBar onResults={setSearchResults} />   
        <button style={styles.copyBtn} onClick={copyLink} title="Copy link to this file">     
          {statusInfo.copied ? '✓ Copied!' : '🔗 Copy Link'}   
        </button> 
      </div> 

      {/* Breadcrumbs for active file */}
      <Breadcrumbs path={activePath} />

      {/* Main area: tree + editor */}
      <div style={styles.mainArea}>
        <FileTree onSelectFile={openFile} activePath={activePath} />
        
        <EditorPane 
          tabs={tabs} 
          activeTabId={activeTabId} 
          onActivateTab={setActiveTabId} 
          onCloseTab={closeTab} 
          searchResults={searchResults} 
          onResultClick={onSearchResultClick}
        />
      </div>  

      {/* Bottom bar */}  
      <div style={{ display: 'flex', alignItems: 'center' }}>   
        <StatusBar classesIndexed={statusInfo.classesIndexed || 0} activeTab={tabs.find(t => t.id === activeTabId)} />    
        <FeedbackButton currentPage="editor" currentFile={activePath} />  
      </div>

      {/* Quick Open palette (Ctrl+P) */}
      <QuickOpen open={quickOpenVisible} onClose={() => setQuickOpenVisible(false)} onSelectFile={openFile} />
    </div>
  );
}

const styles = {  
  app: { display: 'flex', flexDirection: 'column', height: '100vh', fontFamily: "'Segoe UI', system-ui, sans-serif", background: '#1e1e2e' },  
  toolbar: { display: 'flex', alignItems: 'center', gap: '16px', padding: '8px 16px', background: '#181825', borderBottom: '1px solid #313244' },  
  logo: { fontWeight: 700, fontSize: '1.2rem', color: '#cdd6f4' },  
  copyBtn: { marginLeft: 'auto', background: '#313244', border: 'none', color: '#cdd6f4', padding: '6px 12px', borderRadius: '6px', cursor: 'pointer', fontSize: '0.85rem' },  
  mainArea: { flex: 1, display: 'flex', overflow: 'hidden' }  
};