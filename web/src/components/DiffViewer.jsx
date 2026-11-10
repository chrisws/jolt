import { useState, useEffect } from 'react';
import { getGitFileDiff } from '../api';

export default function DiffViewer({ commitHash, filePath, onClose }) {
  const [diff, setDiff] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (commitHash && filePath) loadDiff();
  }, [commitHash, filePath]);

  async function loadDiff() {
    setLoading(true);
    try {
      const data = await getGitFileDiff(null, commitHash, filePath);
      setDiff(data.diff || '');
    } catch (e) { console.error(e); setDiff(''); }
    setLoading(false);
  }

  if (!commitHash || !filePath) return null;

  const lines = diff.split('\n');

  return (
    <div className="diff-viewer flex flex-col h-full text-sm bg-gray-900">
      <div className="flex items-center justify-between px-3 py-2 border-b border-gray-700">
        <span className="font-mono text-xs text-gray-300 truncate">{filePath}</span>
        <button onClick={onClose} className="text-gray-400 hover:text-white text-lg leading-none">×</button>
      </div>

      <div className="flex-1 overflow-auto font-mono text-xs">
        {loading ? <div className="p-3 text-gray-500">Loading diff...</div> : (
          <table className="w-full">
            <tbody>
              {lines.map((line, i) => {
                let cls = 'text-gray-400';
                if (line.startsWith('+')) cls = 'bg-green-900/30 text-green-300';
                else if (line.startsWith('-')) cls = 'bg-red-900/30 text-red-300';
                else if (line.startsWith('---') || line.startsWith('+++')) cls = 'text-blue-400';

                return (
                  <tr key={i} className={cls}>
                    <td className="select-none px-2 text-right text-gray-600 w-8">{i + 1}</td>
                    <td className="px-2 whitespace-pre">{line}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      <div className="px-3 py-1.5 border-t border-gray-700 text-xs text-gray-500">
        {lines.filter(l => l.startsWith('+')).length} additions, {lines.filter(l => l.startsWith('-')).length} deletions
      </div>
    </div>
  );
}
