"use strict";
(() => {
 if(window.KhetMarketplace)return;
 const t=(key,params={})=>window.KhetI18n?.t(key,params)||String(key).replace(/\{(\w+)\}/g,(_,n)=>String(params[n]??""));
 const node=(tag,cls,text)=>{const el=document.createElement(tag);if(cls)el.className=cls;if(text!==undefined){el.textContent=text;el.dataset.i18nSkip="";}return el;};
 const copy=(el,key,params={})=>{delete el.dataset.i18nSkip;el.dataset.i18n=key;el.dataset.i18nParams=JSON.stringify(params);el.textContent=t(key,params);return el;};
 const label=(tag,cls,key,params={})=>copy(node(tag,cls),key,params);
 async function request(url,options={}){
  const timerController=new AbortController(),timer=setTimeout(()=>timerController.abort(),18000);
  try{const response=await fetch(url,{credentials:"same-origin",cache:"no-store",...options,headers:{Accept:"application/json",...options.headers},signal:timerController.signal});
   if(response.redirected||response.status===401){const e=new Error(t("Please sign in to continue."));e.status=401;throw e;}
   const json=response.status===204?null:await response.json().catch(()=>null);
   if(!response.ok){const e=new Error(t(json?.message||"Could not complete the request. Try again."));e.status=response.status;throw e;}
   if(response.status!==204&&json===null)throw new Error(t("Unexpected response. Refresh and try again."));return json;
  }catch(e){if(e.name==="AbortError")throw new Error(t(options.method?"The request took too long. Refresh to check whether your change was saved before trying again.":"The request took too long. Check your connection and try again."));throw e;}finally{clearTimeout(timer);}
 }
 async function mutate(url,method,body){const csrf=await request("/api/csrf");return request(url,{method,headers:{[csrf.headerName]:csrf.token,...(body===undefined?{}:{"Content-Type":"application/json"})},...(body===undefined?{}:{body:JSON.stringify(body)})});}
 const money=value=>new Intl.NumberFormat(window.KhetI18n?.locale||"en-IN",{style:"currency",currency:"INR",maximumFractionDigits:2}).format(Number(value||0));
 function saveButton(type,id){const button=label("button","secondary save-button",type==="farmers"?"Save farmer":"Save crop");button.type="button";button.disabled=true;button.setAttribute("aria-pressed","false");let saved=false,busy=false;
  const key=()=>saved?(type==="farmers"?"Farmer saved":"Crop saved"):(type==="farmers"?"Save farmer":"Save crop");
  request(`/api/me/saved-${type}/${id}`).then(state=>{saved=state.saved;copy(button,key());button.setAttribute("aria-pressed",String(saved));}).catch(()=>{}).finally(()=>{if(!busy)button.disabled=false;});
  button.addEventListener("click",async()=>{if(busy)return;busy=true;button.disabled=true;const old=button.parentElement?.querySelector(".save-feedback");old?.remove();
   try{await mutate(`/api/me/saved-${type}/${id}`,saved?"DELETE":"PUT");saved=!saved;copy(button,key());button.setAttribute("aria-pressed",String(saved));}
   catch(error){const feedback=node("span","save-feedback field-help",error.message);feedback.setAttribute("role","status");button.after(feedback);if(error.status===401){const login=label("a","","Sign in");login.href="/login?returnTo="+encodeURIComponent(location.pathname+location.search);feedback.append(document.createTextNode(" "),login);}}
   finally{busy=false;button.disabled=false;}});return button;
 }
 window.enhanceListingActions=(container,listing)=>{if(!container||!Number.isSafeInteger(Number(listing?.id))||container.querySelector("[data-saved-actions]"))return;const actions=node("div","actions saved-actions");actions.dataset.savedActions="";actions.append(saveButton("listings",listing.id));if(Number(listing.farmerId)>0){const farmer=label("a","button-link secondary","Meet the farmer");farmer.href=`/farmer.html?id=${Number(listing.farmerId)}`;actions.append(farmer);}container.append(actions);};
 window.KhetMarketplace={t,node,label,copy,request,mutate,money,saveButton};
})();
