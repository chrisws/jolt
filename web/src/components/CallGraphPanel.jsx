import { useState, useEffect } from 'react';
import { getCallGraph, getCallGraphCallers } from '../api';

export default function CallGraphPanel({ method, onClose }) {
  const [edges, setEdges] = useState([]);
  const [callers, setCallers] = useState([]);
  const [depth, setDepth] = useState(3);
  const [tab, setTab] = useState('callees'); // 'callees' | 'callers'
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (method) loadCallees();
  }, [method, depth]);

  async function loadCallees() {
    setLoading(true);
    try {
      const data = await getCallGraph(method, depth);
      setEdges(data.edges || []);
    } catch (e) { console.error(e); }
    setLoading(false);
  }

  async function loadCallers() {
    setLoading(true);
    try {
      const data = await getCallGraphCallers(method);
      setCallers(data.callers || []);
    } catch (e) { console.error(e); }
    setLoading(false);
  }

  if (!method) return null;

  return (
    <div className="call-graph-panel flex flex-col h-full text-sm bg-gray-900">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 border-b border-gray-700">
        <span className="font-mono text-xs text-blue-300 truncate max-w-[200px]">{method}</span>
        <button onClick={onClose} className="text-gray-400 hover:text-white text-lg leading-none">×</button>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-gray-700">
        <button className={`flex-1 px-3 py-1.5 text-xs ${tab === 'callees' ? 'bg-gray-800 text-white' : 'text-gray-400'}`} onClick={() => setTab('callees')}>Callees</button>
        <button className={`flex-1 px-3 py-1.5 text-xs ${tab === 'callers' ? 'bg-gray-800 text-white' : 'text-gray-400'}`} onClick={() => { setTab('callers'); loadCallers(); }}>Callers</button>
      </div>

      {/* Depth control */}
      {tab === 'callees' && (
        <div className="px-3 py-1.5 flex items-center gap-2 text-xs text-gray-400">
          <span>Depth:</span>
          <input type="range" min="1" max="6" value={depth} onChange={e => setDepth(Number(e.target.value))} className="w-20" />
          <span>{depth}</span>
        </div>
      )}

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-2">
        {loading && <div className="text-gray-500 p-2">Loading...</div>}

        {tab === 'callees' && !loading && (
          edges.length === 0 ? <div className="text-gray-500 p-2">No outgoing calls found.</div> :
          edges.map((e, i) => (
            <div key={i} className="flex items-center gap-2 px-2 py-1.5 border-b border-gray-800 hover:bg-gray-800 cursor-pointer">
              <span className="text-green-400 text-xs">→</span>
              <span className="font-mono text-xs text-gray-200">{e.callee}</span>
              {e.receiver && <span className="text-gray-500 text-xs">({e.receiver})</span>}
              <span className="ml-auto text-gray-600 text-xs">L{e.line}</span>
            </div>
          ))
        )}

        {tab === 'callers' && !loading && (
          callers.length === 0 ? <div className="text-gray-500 p-2">No incoming calls found.</div> :
          callers.map((c, i) => (
            <div key={i} className="flex items-center gap-2 px-2 py-1.5 border-b border-gray-800 hover:bg-gray-800 cursor-pointer">
              <span className="text-yellow-400 text-xs">←</span>
              <span className="font-mono text-xs text-gray-200">{c}</span>
            </div>
          ))
        )}
      </div>

      {/* Footer */}
      {tab === 'callees' && !loading && (
        <div className="px-3 py-1.5 border-t border-gray-700 text-xs text-gray-500">
          {edges.length} call edge{edges.length !== 1 ? 's' : ''}
        </div>
      )}
    </div>
  );
}
