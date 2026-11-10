let ws = null;
let listeners = [];
let reconnectTimer = null;

const WS_URL = `ws://${window.location.host}/ws`;

export function connectWs() {
  if (ws && ws.readyState <= WebSocket.OPEN) return;

  ws = new WebSocket(WS_URL);

  ws.onopen = () => {
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
  };

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      listeners.forEach(fn => fn(data));
    } catch (e) {
      console.warn('WS: non-JSON message:', event.data);
    }
  };

  ws.onclose = () => scheduleReconnect();
  ws.onerror = () => { if (ws) ws.close(); };
}

export function sendWs(obj) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(obj));
  }
}

export function onWsMessage(fn) {
  listeners.push(fn);
  return () => { listeners = listeners.filter(f => f !== fn); };
}

export function disconnectWs() {
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
  listeners = [];
  if (ws) { ws.onclose = null; ws.close(); ws = null; }
}

function scheduleReconnect() {
  if (reconnectTimer) return;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connectWs();
  }, 3000);
}
