#!/usr/bin/env python3
"""
Jolt dev server — Python replacement for Vite HMR dev mode.

Serves static files, proxies API requests to Javalin,
and pushes instant live-reload over WebSocket when files change.

Usage:
  python dev_server.py [--port 5173] [--javalin-port 9000] [--static-dir PATH]

Requires:  pip install aiohttp
"""

import argparse
import asyncio
import mimetypes
from pathlib import Path

from aiohttp import web, ClientSession


def parse_args():
    p = argparse.ArgumentParser(description="Jolt dev server (Python HMR)")
    p.add_argument("--port", type=int, default=5173, help="Dev server port")
    p.add_argument("--javalin-port", type=int, default=9000, help="Javalin port")
    p.add_argument("--static-dir", default="server/src/main/resources/static", help="Static files directory")
    p.add_argument("--poll-interval", type=float, default=1.0, help="File-watch poll interval (s)")
    return p.parse_args()


class DevServer:

    def __init__(self, args):
        self.static_dir = Path(args.static_dir).resolve()
        self.javalin_port = args.javalin_port
        self.poll_interval = args.poll_interval
        self.ws_clients: set[web.WebSocketResponse] = set()
        self.last_mtime: float = 0.0

    # ── File watching ──────────────────────────────────

    def scan_mtime(self) -> float:
        max_mt = 0.0
        for f in self.static_dir.rglob("*"):
            if f.is_file():
                mt = f.stat().st_mtime
                if mt > max_mt:
                    max_mt = mt
        return max_mt

    async def watch_loop(self):
        self.last_mtime = self.scan_mtime()
        while True:
            await asyncio.sleep(self.poll_interval)
            current = self.scan_mtime()
            if current != self.last_mtime:
                self.last_mtime = current
                n = len(self.ws_clients)
                print(f"[dev] File change detected — reload → {n} client(s)")
                dead = []
                for ws in list(self.ws_clients):
                    try:
                        await ws.send_json({"type": "reload"})
                    except Exception:
                        dead.append(ws)
                for ws in dead:
                    self.ws_clients.discard(ws)

    # ── Static files ───────────────────────────────────

    async def handle_index(self, request: web.Request) -> web.Response:
        idx = self.static_dir / "index.html"
        if idx.is_file():
            return web.Response(body=idx.read_bytes(), content_type="text/html")
        return web.Response(status=404, text="Not found")

    async def handle_static(self, request: web.Request) -> web.Response:
        rel = request.match_info.get("path", "")
        fp = (self.static_dir / rel).resolve()

        if not str(fp).startswith(str(self.static_dir)):
            return web.Response(status=403, text="Forbidden")
        if not fp.is_file():
            return web.Response(status=404, text="Not found")

        mime, _ = mimetypes.guess_type(str(fp))
        return web.Response(body=fp.read_bytes(), content_type=mime or "application/octet-stream")

    # ── WebSocket HMR ──────────────────────────────────

    async def handle_ws(self, request: web.Request) -> web.WebSocketResponse:
        ws = web.WebSocketResponse(heartbeat=15)
        await ws.prepare(request)
        self.ws_clients.add(ws)
        print(f"[dev] WS connected ({len(self.ws_clients)} total)")
        try:
            async for _ in ws:
                pass  # client sends nothing; we only push
        finally:
            self.ws_clients.discard(ws)
            print(f"[dev] WS disconnected ({len(self.ws_clients)} remaining)")
        return ws

    # ── API proxy → Javalin ────────────────────────────

    async def handle_proxy(self, request: web.Request) -> web.StreamingResponse:
        path = request.rel_url.path
        qs = request.rel_url.query_string
        url = f"http://localhost:{self.javalin_port}{path}" + (f"?{qs}" if qs else "")

        headers = {k: v for k, v in request.headers.items()
                   if k.lower() not in ("host", "connection", "upgrade",
                                        "sec-websocket-key", "sec-websocket-version")}

        async with ClientSession() as session:
            async with session.request(
                request.method, url, headers=headers,
                data=await request.read(), allow_redirects=False,
            ) as resp:
                body = await resp.read()
                return web.Response(
                    status=resp.status, body=body,
                    content_type=resp.content_type,
                    headers={k: v for k, v in resp.headers.items()
                             if k.lower() not in ("transfer-encoding", "connection")},
                )

    # ── Compat: /api/dev/mtime (old polling endpoint) ──

    async def handle_dev_mtime(self, request: web.Request) -> web.Response:
        return web.json_response({"mtime": int(self.last_mtime * 1000)})

    # ── App wiring ─────────────────────────────────────

    def create_app(self) -> web.Application:
        app = web.Application()

        # HMR WebSocket (must be before /ws/ proxy)
        app.router.add_get("/ws/hmr", self.handle_ws)

        # Compat polling endpoint
        app.router.add_get("/api/dev/mtime", self.handle_dev_mtime)

        # API + WS proxy to Javalin (before static catch-all)
        app.router.add_route("*", "/api/{path:.*}", self.handle_proxy)
        app.router.add_route("*", "/ws/{path:.*}", self.handle_proxy)

        # Static files
        app.router.add_get("/", self.handle_index)
        app.router.add_get("/{path:.*}", self.handle_static)

        return app


async def main():
    args = parse_args()
    srv = DevServer(args)

    if not srv.static_dir.is_dir():
        print(f"ERROR: static dir not found: {srv.static_dir}")
        return

    print(f"[dev] Static:   {srv.static_dir}")
    print(f"[dev] Javalin:  localhost:{args.javalin_port}")
    print(f"[dev] Dev:      http://localhost:{args.port}")
    print(f"[dev] HMR WS:   ws://localhost:{args.port}/ws/hmr")

    app = srv.create_app()
    watcher = asyncio.create_task(srv.watch_loop())

    runner = web.AppRunner(app)
    await runner.setup()
    await web.TCPSite(runner, "localhost", args.port).start()

    print(f"[dev] Ready → http://localhost:{args.port}?dev")

    try:
        await asyncio.Future()  # run forever
    except KeyboardInterrupt:
        pass
    finally:
        watcher.cancel()
        await runner.cleanup()


if __name__ == "__main__":
    asyncio.run(main())