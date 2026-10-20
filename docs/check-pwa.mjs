// Privacy contract: offline support must not store account data or replay mutations.
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
const root=new URL('../src/main/resources/static/',import.meta.url);
const listeners=new Map(),added=[],put=[],deleted=[],store=new Map();
let networkFails=false;
const response={ok:true,type:'basic',redirected:false,clone(){return this;}};
const cache={async addAll(urls){added.push(...urls);urls.forEach(u=>store.set(u,{offline:u==='/offline.html'}));},async match(key){return store.get(key);},async put(key,value){put.push(key);store.set(key,value);}};
const scope={location:{origin:'https://khetwallah.example'},clients:{async claim(){}},addEventListener(name,fn){listeners.set(name,fn);},skipWaiting(){}};
const context={self:scope,URL,Response,caches:{async open(){return cache;},async keys(){return ['khetwallah-public-old','other-app'];},async delete(key){deleted.push(key);},async match(key){return store.get(key);}},async fetch(){if(networkFails)throw Error('offline');return response;}};
vm.runInNewContext(await readFile(new URL('sw.js',root),'utf8'),context);
let tasks=[];listeners.get('install')({waitUntil:p=>tasks.push(p)});await Promise.all(tasks);
assert(added.includes('/offline.html'));assert(added.every(p=>!p.startsWith('/api/')&&!['/account.html','/my-orders.html','/saved.html','/messages.html','/login'].includes(p)));
for(const path of ['/api/me/orders','/api/csrf','/api/me/messages/unread','/login','/logout']){let intercepted=false;listeners.get('fetch')({request:{url:'https://khetwallah.example'+path,method:'GET',mode:'cors'},respondWith(){intercepted=true;}});assert.equal(intercepted,false,path+' must bypass offline caching');}
let intercepted=false;listeners.get('fetch')({request:{url:'https://khetwallah.example/api/orders',method:'POST',mode:'cors'},respondWith(){intercepted=true;}});assert.equal(intercepted,false);
networkFails=true;let returned;listeners.get('fetch')({request:{url:'https://khetwallah.example/account.html',method:'GET',mode:'navigate'},respondWith(p){returned=p;}});assert.equal((await returned).offline,true);assert.equal(put.length,0);
tasks=[];listeners.get('activate')({waitUntil:p=>tasks.push(p)});await Promise.all(tasks);assert.deepEqual(deleted,['khetwallah-public-old']);
const manifest=JSON.parse(await readFile(new URL('manifest.webmanifest',root),'utf8'));assert.equal(manifest.display,'standalone');for(const size of [192,512]){const icon=manifest.icons.find(i=>i.sizes===`${size}x${size}`);assert(icon);const bytes=await readFile(new URL(icon.src.slice(1),root));assert.equal(bytes.readUInt32BE(16),size);assert.equal(bytes.readUInt32BE(20),size);}
console.log('PWA privacy, offline fallback, cache isolation and icon checks passed.');
