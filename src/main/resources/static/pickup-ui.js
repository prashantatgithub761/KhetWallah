"use strict";
(() => {
 const t=(key)=>window.KhetI18n?.t(key)||key;
 function node(tag,key,cls){const el=document.createElement(tag);if(cls)el.className=cls;if(key){el.dataset.i18n=key;el.textContent=t(key);}return el;}
 async function post(path,body){const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),20000);try{
  const tokenResponse=await fetch('/api/csrf',{redirect:'error',signal:controller.signal});if(!tokenResponse.ok)throw new Error(t('Please sign in again.'));
  const csrf=await tokenResponse.json();const response=await fetch(path,{method:'POST',redirect:'manual',credentials:'same-origin',signal:controller.signal,
   headers:{Accept:'application/json',[csrf.headerName]:csrf.token,...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{})});
  if(response.type==='opaqueredirect'||response.status===401)throw new Error(t('Please sign in again.'));
  const data=response.status===204?null:await response.json().catch(()=>({}));if(!response.ok)throw new Error(t(data?.message||'Could not confirm pickup. Please try again.'));return data;
 }catch(error){if(error.name==='AbortError'||error instanceof TypeError)throw new Error(t('Connection interrupted. Refresh the order to check its latest status before retrying.'));throw error;}finally{clearTimeout(timer);}}
 window.addOrderPickupCode=(card,order,{farmerView,onCompleted})=>{
  const panel=node('section',null,'pickup-code-panel');
  panel.append(node('h3',farmerView?'Confirm the handover':'Your pickup code'));
  panel.append(node('p',farmerView?'Ask the buyer for their current six-digit code after handing over the produce.':'Generate your code at pickup. Share it with the farmer only after receiving your produce.','form-help'));
  const feedback=node('p',null,'form-feedback');feedback.setAttribute('role','status');feedback.tabIndex=-1;
  if(farmerView){
   const form=document.createElement('form'),label=node('label','Buyer pickup code'),input=document.createElement('input');
   input.id=`pickup-code-${order.id}`;label.htmlFor=input.id;input.type='text';input.inputMode='numeric';input.autocomplete='off';input.pattern='[0-9]{6}';input.maxLength=6;input.required=true;
   const button=node('button','Confirm handover');button.type='submit';form.className='pickup-code-form';form.append(label,input,button,feedback);
   let busy=false;form.addEventListener('submit',async event=>{event.preventDefault();if(busy||!form.reportValidity())return;busy=true;button.disabled=true;feedback.textContent=t('Checking pickup code…');
    try{await post(`/api/orders/${order.id}/confirm-pickup`,{code:input.value});input.value='';await onCompleted();}
    catch(error){feedback.textContent=error.message;feedback.focus();}finally{busy=false;button.disabled=false;}});panel.append(form);
  }else{
   const button=node('button','Generate pickup code'),output=document.createElement('div');button.type='button';output.className='pickup-code-output';output.hidden=true;output.setAttribute('role','status');
   let busy=false,resendAt=0,expiresAt=0,timer=null;
   function tick(){if(!card.isConnected){clearInterval(timer);return;}const seconds=Math.max(0,Math.ceil((resendAt-Date.now())/1000));button.disabled=busy||seconds>0;button.textContent=seconds?`${t('Generate another code in')} ${seconds}s`:t('Generate pickup code');
    if(expiresAt&&Date.now()>=expiresAt){output.replaceChildren();output.hidden=true;feedback.textContent=t('The pickup code expired. Generate a new one.');clearInterval(timer);}}
   button.addEventListener('click',async()=>{if(busy||Date.now()<resendAt)return;busy=true;button.disabled=true;feedback.textContent=t('Preparing pickup code…');try{
    const data=await post(`/api/orders/${order.id}/pickup-code`);output.replaceChildren();const code=node('strong');code.textContent=data.code;code.className='pickup-code-digits';code.setAttribute('aria-label',t('Pickup code')+' '+data.code.split('').join(' '));
    const expiry=node('p');expiry.textContent=t('Expires at')+' '+new Date(data.expiresAt).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'});output.append(code,expiry);output.hidden=false;
    feedback.textContent=t('Keep this code private until handover.');resendAt=Date.now()+data.resendAfterSeconds*1000;expiresAt=Date.parse(data.expiresAt);clearInterval(timer);timer=setInterval(tick,1000);
   }catch(error){feedback.textContent=error.message;}finally{busy=false;tick();}});
   window.addEventListener('pagehide',()=>{output.replaceChildren();output.hidden=true;clearInterval(timer);});
   window.addEventListener('pageshow',event=>{if(event.persisted){tick();if(resendAt){clearInterval(timer);timer=setInterval(tick,1000);}}});panel.append(button,output,feedback);
  }
  card.append(panel);
 };
})();
