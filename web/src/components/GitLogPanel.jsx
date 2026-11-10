import { useState, useEffect } from 'react';
import { getGitLog, getCommitDetail, getGitBranches, getCurrentBranch } from '../api';

export default function GitLogPanel({ onSelectCommit }) {
  const [commits, setCommits] = useState([]);
  const [branches, setBranches] = useState([]);
  const [currentBranch, setCurrentBranch] = useState('');
  const [selectedCommit, setSelectedCommit] = useState(null);
  const [filterAuthor, setFilterAuthor] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadCommits();
    loadBranches();
  }, [filterAuthor]);

  async function loadCommits() {
    setLoading(true);
    try {
      const data = await getGitLog(30, filterAuthor || null);
      setCommits(data);
    } catch (e) { console.error(e); }
    setLoading(false);
  }

  async function loadBranches() {
    try {
      const b = await getCurrentBranch();
      setCurrentBranch(b.branch || '');
      const all = await getGitBranches();
      setBranches(all);
    } catch (e) { console.error(e); }
  }

  async function handleCommitClick(commit) {
    setSelectedCommit(commit);
    if (onSelectCommit) onSelectCommit(commit.hash);
  }

  return (
    <div className="git-log-panel flex flex-col h-full text-sm">
      {/* Branch selector */}
      <div className="flex items-center gap-2 px-3 py-2 border-b border-gray-700">
        <span className="text-gray-400 text-xs">Branch:</span>
        <select
          className="bg-gray-800 text-gray-200 rounded px-2 py-1 text-xs flex-1"
          value={currentBranch}
          onChange={() => {}} // Phase 5: branch switching
        >
          {branches.map(b => (
            <option key={b.name} value={b.name}>{b.name}</option>
          ))}
        </select>
      </div>

      {/* Author filter */}
      <div className="px-3 py-1.5 border-b border-gray-700">
        <input
          type="text"
          placeholder="Filter by author..."
          className="w-full bg-gray-800 text-gray-200 rounded px-2 py-1 text-xs"
          value={filterAuthor}
          onChange={e => setFilterAuthor(e.target.value)}
        />
      </div>

      {/* Commit list */}
      <div className="flex-1 overflow-y-auto">
        {loading && <div className="p-3 text-gray-500">Loading...</div>}
        {!loading && commits.map(c => (
          <div
            key={c.hash}
            className={`px-3 py-2 border-b border-gray-800 cursor-pointer hover:bg-gray-800 ${selectedCommit?.hash === c.hash ? 'bg-blue-900/30' : ''}`}
            onClick={() => handleCommitClick(c)}
          >
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs text-blue-400">{c.abbrevHash}</span>
              <span className="text-gray-300 truncate flex-1">{c.message.split('\n')[0]}</span>
            </div>
            <div className="flex items-center gap-2 mt-1 text-xs text-gray-500">
              <span>{c.author}</span>
              <span>·</span>
              <span>{c.date?.substring(0, 16) || ''}</span>
            </div>
          </div>
        ))}
      </div>

      {/* Agent indicator */}
      {commits.some(c => c.author?.toLowerCase().includes('agent')) && (
        <div className="px-3 py-1.5 bg-purple-900/20 text-purple-300 text-xs border-t border-gray-700">
          ⚡ Agent commits detected in this log
        </div>
      )}
    </div>
  );
}
