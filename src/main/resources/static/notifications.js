"use strict";
(() => {
 const list=document.getElementById("notification-list"), message=document.getElementById("notifications-message"), unread=document.getElementById("unread-count");
 const previous=document.getElementById("previous-notifications"), next=document.getElementById("next-notifications"), refresh=document.getElementById("refresh-notifications"), all=document.getElementById("read-all-notifications");
 const labels={CREATED:"Order requested",ACCEPTED:"Order accepted",REJECTED:"Order declined",CANCELLED:"Order cancelled",PICKUP_UPDATED:"Pickup details updated",COMPLETED:"Collection confirmed"};
 let page=0,busy=false;
 const node=(tag,cls,text)=>{const el=document.createElement(tag);if(cls)el.className=cls;if(text!==undefined){el.textContent=text;el.dataset.i18nSkip="";}return el;};
 const t=(key,params={})=>window.KhetI18n?.t(key,params)||String(key).replace(/\{(\w+)\}/g,(_,name)=>String(params[name]??""));
 const copy=(element,key,params={})=>{element.dataset.i18n=key;element.dataset.i18nParams=JSON.stringify(params);element.textContent=t(key,params);return element;};
 const label=(tag,cls,key,params={})=>copy(node(tag,cls),key,params);
 const aria=(element,key,params={})=>{element.dataset.i18nAriaLabel=key;element.dataset.i18nParams=JSON.stringify(params);element.setAttribute("aria-label",t(key,params));};
 const dateText=element=>{const date=new Date(element.dateTime);element.textContent=element.dataset.communityTime==="date"?date.toLocaleDateString(window.KhetI18n?.locale):date.toLocaleString(window.KhetI18n?.locale);};
 document.addEventListener("khet:languagechange",()=>document.querySelectorAll("time[data-community-time]").forEach(dateText));

 async function request(url,options={}){const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);try{const response=await fetch(url,{credentials:"same-origin",cache:"no-store",...options,headers:{Accept:"application/json",...options.headers},signal:controller.signal});if(response.redirected||response.status===401||(!response.headers.get("Content-Type")?.includes("application/json")&&response.status!==204))throw new Error("Please sign in again to see your notifications.");if(!response.ok){const body=await response.json().catch(()=>({}));throw new Error(body.message||"Could not update notifications. Try again.");}return response.status===204?null:await response.json();}catch(error){if(error.name==="AbortError")throw new Error("The request took too long. Check your connection and try again.");throw error;}finally{clearTimeout(timer);}}
 async function post(path){const csrf=await request("/api/csrf");return request(path,{method:"POST",headers:{[csrf.headerName]:csrf.token}});}
 function card(item){const article=node("article",`dashboard-panel notification-card${item.readAt?"":" is-unread"}`);article.dataset.notificationId=item.id;
  const heading=node("h2");heading.append(label("span","",item.labelKey||labels[item.eventType]||"Order updated"),document.createTextNode(" · "),label("span","","orderReference",{id:item.orderId}));
  const time=node("time","section-description",new Date(item.createdAt).toLocaleString(window.KhetI18n?.locale));time.dateTime=item.createdAt;time.dataset.communityTime="datetime";
  article.append(heading,node("p","",item.cropName),time);const actions=node("div","actions");const link=label("a","button-link secondary","View order");
  // The API generates these links; enforce the two local destinations in the browser too.
  const url=new URL(item.orderUrl,location.origin);if(url.origin===location.origin&&["/my-orders.html","/received-orders.html"].includes(url.pathname))link.href=url.pathname+url.search+url.hash;
  actions.append(link);
  if(!item.readAt){const read=label("button","secondary","Mark as read");read.type="button";aria(read,"Mark update for order #{id} as read",{id:item.orderId});read.addEventListener("click",async()=>{read.disabled=true;try{await post(`/api/me/notifications/${item.id}/read`);await load();}catch(error){copy(message,error.message);read.disabled=false;}});actions.append(read);}
  else actions.append(label("span","section-description","Read"));article.append(actions);return article;
 }
 async function load(){if(busy)return;busy=true;refresh.disabled=true;all.disabled=true;previous.disabled=true;next.disabled=true;list.replaceChildren();copy(message,"Loading notifications…");document.getElementById("notifications-login").hidden=true;
  try{const data=await request(`/api/me/notifications?page=${page}&size=20`);copy(unread,"unreadCount",{count:data.unreadCount});all.disabled=data.unreadCount===0;list.replaceChildren(...data.content.map(card));copy(message,data.content.length?"":"You’re all caught up. New order updates will appear here.");previous.disabled=page===0;next.disabled=page+1>=data.totalPages;copy(document.getElementById("notifications-page"),data.totalPages?"pageOf":"",{page:page+1,total:data.totalPages});}
  catch(error){copy(message,error.message);document.getElementById("notifications-login").hidden=!error.message.includes("sign in");copy(unread,"");copy(document.getElementById("notifications-page"),"");}
  finally{busy=false;refresh.disabled=false;}}
 refresh.addEventListener("click",load);previous.addEventListener("click",()=>{page--;load();});next.addEventListener("click",()=>{page++;load();});all.addEventListener("click",async()=>{all.disabled=true;try{await post("/api/me/notifications/read-all");await load();}catch(error){copy(message,error.message);all.disabled=false;}});load();
})();