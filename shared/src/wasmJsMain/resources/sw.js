const CACHE_NAME = 'lojinha-offline-v1';
const OFFLINE_URL = '/offline.html';
const PRECACHE_ASSETS = [
    OFFLINE_URL,
    '/manifest.webmanifest',
    '/icon-192.png',
    '/icon-512.png',
    '/favicon.png'
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => cache.addAll(PRECACHE_ASSETS))
    );
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((keys) =>
            Promise.all(
                keys
                    .filter((key) => key !== CACHE_NAME)
                    .map((key) => caches.delete(key))
            )
        ).then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', (event) => {
    const request = event.request;
    const url = new URL(request.url);

    // Never intercept API or cross-origin requests
    if (url.origin !== self.location.origin || url.pathname.startsWith('/api/')) {
        return;
    }

    if (request.mode === 'navigate') {
        event.respondWith(
            fetch(request).catch(async () => {
                const cache = await caches.open(CACHE_NAME);
                const cached = await cache.match(OFFLINE_URL);
                return cached || new Response(
                    'Lojinha Kiosk is offline. Please start the Lojinha Desktop application.',
                    { status: 503, headers: { 'Content-Type': 'text/plain; charset=utf-8' } }
                );
            })
        );
    }
});
