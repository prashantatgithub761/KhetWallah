/* Shared mobile navigation and an opt-in install/update experience. */
(() => {
 'use strict';
 const t=key=>window.KhetI18n?.t(key)||key;
 const links=[['/','Browse','🌾'],['/saved.html','Saved','♡'],['/my-orders.html','Orders','▣'],['/account.html','Account','♙']];
 const more=[['/saved.html','Saved crops and farmers'],['/messages.html','Messages'],['/farmer-profile.html','My farmer profile'],['/insights.html','Farmer insights'],['/received-orders.html','Received orders'],['/notifications.html','Notifications']];
 const path=location.pathname;
 function copy(tag,key){const el=document.createElement(tag);el.dataset.i18n=key;el.textContent=t(key);return el;}
 function routeLink(url,key){const a=copy('a',key);a.href=url;if(path===url)a.setAttribute('aria-current','page');return a;}
 function init(){
  const header=document.querySelector('.header-content');
  if(header){const menu=document.createElement('details');menu.className='market-tools';const summary=copy('summary','More');summary.setAttribute('aria-label',t('More marketplace tools'));const nav=document.createElement('nav');nav.setAttribute('aria-label',t('Marketplace tools'));more.forEach(([url,key])=>nav.append(routeLink(url,key)));menu.append(summary,nav);header.append(menu);document.addEventListener('keydown',e=>{if(e.key==='Escape')menu.open=false;});document.addEventListener('click',e=>{if(!menu.contains(e.target))menu.open=false;});}
  const bottom=document.createElement('nav');bottom.className='mobile-bottom-nav';bottom.setAttribute('aria-label',t('Mobile navigation'));
  links.forEach(([url,key,icon])=>{const a=document.createElement('a');a.href=url;const art=document.createElement('span');art.textContent=icon;art.className='mobile-nav-icon';art.setAttribute('aria-hidden','true');a.append(art,copy('span',key));if(path===url||(key==='Orders'&&path==='/received-orders.html'))a.setAttribute('aria-current','page');bottom.append(a);});document.body.append(bottom);
  const status=copy('p','You are offline. Reconnect to check current prices, stock and orders.');status.className='connection-banner';status.setAttribute('role','status');status.hidden=navigator.onLine;document.body.prepend(status);
  window.addEventListener('offline',()=>status.hidden=false);window.addEventListener('online',()=>status.hidden=true);
  if(path==='/account.html'){
   const section=document.createElement('section');section.className='marketplace-shortcuts';section.setAttribute('aria-label',t('More from KhetWallah'));section.append(copy('h2','Make KhetWallah your own'));const grid=document.createElement('div');grid.className='market-shortcut-grid';more.slice(0,4).forEach(([url,key])=>grid.append(routeLink(url,key)));section.append(grid);document.querySelector('main')?.append(section);
  }
  window.KhetI18n?.apply(bottom);
  const languageChange=()=>{status.textContent=t('You are offline. Reconnect to check current prices, stock and orders.');bottom.setAttribute('aria-label',t('Mobile navigation'));};document.addEventListener('khet:languagechange',languageChange);
  updateUnread();document.addEventListener('khet:messages-updated',updateUnread);
  installSupport();
 }
 async function updateUnread(){
  if(document.hidden)return;
  try{const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),6000);let r;try{r=await fetch('/api/me/messages/unread',{credentials:'same-origin',redirect:'manual',cache:'no-store',signal:controller.signal,headers:{Accept:'application/json'}});}finally{clearTimeout(timer);}if(!r.ok||!r.headers.get('Content-Type')?.includes('application/json'))return;const data=await r.json();const n=Number(data.unreadCount);if(!Number.isSafeInteger(n)||n<0)return;document.querySelectorAll('a[href="/messages.html"]').forEach(a=>{a.querySelector('.unread-pill')?.remove();if(n){const badge=document.createElement('span');badge.className='unread-pill';badge.textContent=n>99?'99+':String(n);badge.setAttribute('aria-label',`${n} ${t('unread messages')}`);a.append(badge);}});}catch(_){/* A badge must not block navigation when signed out or offline. */}
 }
 async function installSupport(){
  let installEvent;
  const install=copy('button','Install KhetWallah');install.type='button';install.className='install-app secondary';install.hidden=true;document.querySelector('.site-footer')?.append(install);
  window.addEventListener('beforeinstallprompt',e=>{e.preventDefault();installEvent=e;install.hidden=false;});
  install.addEventListener('click',async()=>{if(!installEvent)return;install.disabled=true;try{await installEvent.prompt();await installEvent.userChoice;}finally{installEvent=null;install.hidden=true;install.disabled=false;}});
  window.addEventListener('appinstalled',()=>install.hidden=true);
  if(!('serviceWorker' in navigator)||!window.isSecureContext)return;
  try{const registration=await navigator.serviceWorker.register('/sw.js',{scope:'/',updateViaCache:'none'});let requestedReload=false;let shown=false;
   const offer=()=>{if(shown||!registration.waiting)return;shown=true;const bar=document.createElement('div');bar.className='update-banner';bar.setAttribute('role','status');bar.append(copy('span','A new version is ready. Save your work before updating.'));const update=copy('button','Update now'),later=copy('button','Later');update.type=later.type='button';later.className='secondary';update.addEventListener('click',()=>{requestedReload=true;registration.waiting?.postMessage({type:'SKIP_WAITING'});});later.addEventListener('click',()=>bar.remove());bar.append(update,later);document.body.append(bar);};
   offer();registration.addEventListener('updatefound',()=>{const worker=registration.installing;worker?.addEventListener('statechange',()=>{if(worker.state==='installed'&&navigator.serviceWorker.controller)offer();});});navigator.serviceWorker.addEventListener('controllerchange',()=>{if(requestedReload)location.reload();});
  }catch(_){/* Browsing continues normally where offline/install support is unavailable. */}
 }
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();
