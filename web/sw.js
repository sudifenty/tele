/* Tele parent dashboard: service worker.
 * Purpose: make the dashboard installable and let it open its app shell offline.
 * It does NOT cache Firebase data, and it never intercepts cross-origin requests
 * (Firebase Auth, Firestore, and the Google CDN always go straight to the network).
 * Bump CACHE when the app shell changes so old copies are replaced. */
const CACHE = "tele-shell-v1";
const SHELL = ["/", "/index.html", "/manifest.webmanifest", "/icons/icon-192.png", "/icons/icon-512.png"];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return; // leave Firebase and CDN traffic alone

  // Network first, so a new deployment is picked up right away; the cache is the offline fallback.
  event.respondWith(
    fetch(req)
      .then((res) => {
        if (res.ok && (req.mode === "navigate" || SHELL.includes(url.pathname))) {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(req, copy));
        }
        return res;
      })
      .catch(() =>
        caches.match(req).then((hit) => hit || (req.mode === "navigate" ? caches.match("/index.html") : Response.error()))
      )
  );
});
