import { createServer as createHttpServer, request as httpRequest } from "node:http";
import { createServer as createHttpsServer } from "node:https";
import { existsSync } from "node:fs";
import { readFile, stat } from "node:fs/promises";
import { join, extname } from "node:path";

const root = process.env.DIST_DIR ?? "./dist";
const port = Number(process.env.PORT ?? 5173);
const BACKEND_URL = process.env.BACKEND_URL;

const TLS_CERT = process.env.TLS_CERT;
const TLS_KEY = process.env.TLS_KEY;
const isHttps = Boolean(TLS_CERT && TLS_KEY && existsSync(TLS_CERT) && existsSync(TLS_KEY));

// CSP origins derived from the built frontend (dist/index.html):
// - same-origin scripts/styles
// - Google Fonts CSS + font files
// - API backend (VITE_API_BASE_URL, defaults to http://localhost:8080)
let apiOrigin = "http://localhost:8080";
if (process.env.VITE_API_BASE_URL) {
  try {
    apiOrigin = new URL(process.env.VITE_API_BASE_URL).origin;
  } catch {
    apiOrigin = ""; // relative URL, same origin
  }
}

const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' https://fonts.googleapis.com",
  "font-src 'self' https://fonts.gstatic.com",
  "img-src 'self' data:",
  apiOrigin ? `connect-src 'self' ${apiOrigin}` : "connect-src 'self'",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join("; ");

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".ico": "image/x-icon",
};

async function handleRequest(req, res) {
  // Reverse-proxy /api requests to backend if configured
  if (BACKEND_URL && req.url?.startsWith("/api")) {
    try {
      const targetUrl = new URL(req.url, BACKEND_URL);
      const proxyHeaders = {
        ...req.headers,
        host: targetUrl.host,
        "x-forwarded-host": req.headers.host || "",
        "x-forwarded-proto": isHttps ? "https" : (req.headers["x-forwarded-proto"] || "http"),
        "x-forwarded-for": (req.headers["x-forwarded-for"] ? req.headers["x-forwarded-for"] + ", " : "") + (req.socket.remoteAddress || ""),
      };
      const proxyReq = httpRequest(
        targetUrl,
        {
          method: req.method,
          headers: proxyHeaders,
        },
        (proxyRes) => {
          res.writeHead(proxyRes.statusCode ?? 500, proxyRes.headers);
          proxyRes.pipe(res);
        }
      );
      proxyReq.on("error", (err) => {
        console.error("API proxy error:", err.message);
        res.writeHead(502, { "Content-Type": "text/plain" });
        res.end("Bad Gateway: unable to reach backend API");
      });
      req.pipe(proxyReq);
      return;
    } catch (err) {
      console.error("Proxy dispatch error:", err);
      res.writeHead(500, { "Content-Type": "text/plain" });
      res.end("Internal Proxy Error");
      return;
    }
  }

  const url = decodeURIComponent(req.url ?? "/").split("?")[0];
  let filePath = join(root, url === "/" ? "/index.html" : url);
  try {
    const stats = await stat(filePath);
    if (stats.isFile()) {
      res.setHeader("Content-Type", MIME[extname(filePath)] ?? "application/octet-stream");
      res.setHeader("Cache-Control", "no-cache");
      res.setHeader("Content-Security-Policy", CSP);
      res.writeHead(200);
      res.end(await readFile(filePath));
      return;
    }
  } catch {
    // fall through to index.html for SPA routes
  }
  try {
    const index = await stat(join(root, "index.html"));
    if (index.isFile()) {
      res.setHeader("Content-Type", MIME[".html"]);
      res.setHeader("Content-Security-Policy", CSP);
      res.writeHead(200);
      res.end(await readFile(join(root, "index.html")));
      return;
    }
  } catch {
    // ignore
  }
  res.writeHead(404);
  res.end("Not Found");
}

let server;
if (isHttps) {
  const [cert, key] = await Promise.all([
    readFile(TLS_CERT),
    readFile(TLS_KEY),
  ]);
  server = createHttpsServer({ cert, key }, handleRequest);
} else {
  server = createHttpServer(handleRequest);
}

server.listen(port, "0.0.0.0", () => {
  const proto = isHttps ? "https" : "http";
  console.log(`FlowerConnect frontend serving on ${proto}://0.0.0.0:${port}`);
  if (BACKEND_URL) {
    console.log(`Proxying /api to ${BACKEND_URL}`);
  }
});
