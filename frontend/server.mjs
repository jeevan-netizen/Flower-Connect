import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { join, extname } from "node:path";

const root = process.env.DIST_DIR ?? "./dist";
const port = Number(process.env.PORT ?? 5173);

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

const server = createServer(async (req, res) => {
  const url = decodeURIComponent(req.url ?? "/").split("?")[0];
  let filePath = join(root, url === "/" ? "/index.html" : url);
  try {
    const stats = await stat(filePath);
    if (stats.isFile()) {
      res.setHeader("Content-Type", MIME[extname(filePath)] ?? "application/octet-stream");
      res.setHeader("Cache-Control", "no-cache");
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
      res.writeHead(200);
      res.end(await readFile(join(root, "index.html")));
      return;
    }
  } catch {
    // ignore
  }
  res.writeHead(404);
  res.end("Not Found");
});

server.listen(port, "0.0.0.0", () => {
  console.log(`FlowerConnect frontend serving on http://0.0.0.0:${port}`);
});