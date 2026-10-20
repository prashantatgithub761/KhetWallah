/* Cache only an explicit list of public, account-free assets. Never cache APIs or private HTML. */
'use strict';
const CACHE='khetwallah-public-v20261020-2';
const ASSETS=['/offline.html','/theme.css','/enhancements.css','/app-shell.js','/i18n.js','/manifest.webmanifest','/icons/icon-192.png','/icons/icon-512.png'];
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(ASSETS))));
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith('khetwallah-public-')&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener('message',event=>{if(event.data?.type==='SKIP_WAITING')self.skipWaiting();});
self.addEventListener('fetch',event=>{
 const request=event.request,url=new URL(request.url);
 if(request.method!=='GET'||url.origin!==self.location.origin)return;
 if(url.pathname.startsWith('/api/')||url.pathname==='/login'||url.pathname==='/logout')return;
 if(request.mode==='navigate'){
  event.respondWith(fetch(request).catch(()=>caches.match('/offline.html').then(r=>r||new Response('You are offline. Reconnect to continue.',{status:503,headers:{'Content-Type':'text/plain; charset=utf-8'}}))));return;
 }
 if(!ASSETS.includes(url.pathname))return;
 const refreshed=caches.open(CACHE).then(cache=>fetch(request).then(async response=>{
  if(response.ok&&response.type==='basic'&&!response.redirected)await cache.put(url.pathname,response.clone());
  return response;
 }));
 event.waitUntil(refreshed.then(()=>{},()=>{}));
 event.respondWith(caches.open(CACHE).then(cache=>cache.match(url.pathname)).then(cached=>cached||refreshed));
});
