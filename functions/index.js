const {onCall,onRequest,HttpsError}=require("firebase-functions/v2/https");
const {onSchedule}=require("firebase-functions/v2/scheduler");
const {defineSecret}=require("firebase-functions/params");
const {initializeApp}=require("firebase-admin/app");
const {getFirestore,FieldValue}=require("firebase-admin/firestore");
const {getMessaging}=require("firebase-admin/messaging");
const logger=require("firebase-functions/logger");

initializeApp();
const db=getFirestore();
const PESAPAL_KEY=defineSecret("PESAPAL_CONSUMER_KEY");
const PESAPAL_SECRET=defineSecret("PESAPAL_CONSUMER_SECRET");
const PESAPAL_BASE=defineSecret("PESAPAL_BASE_URL");
const AT_KEY=defineSecret("AT_API_KEY");
const AT_USERNAME=defineSecret("AT_USERNAME");
const PESAPAL_IPN_URL=defineSecret("PESAPAL_IPN_URL");
const PESAPAL_CALLBACK_URL=defineSecret("PESAPAL_CALLBACK_URL");
const PESAPAL_IPN_ID=defineSecret("PESAPAL_IPN_ID");

function baseUrl(){
 const configured=PESAPAL_BASE.value();
 if(!configured) throw new Error("PESAPAL_BASE_URL is required; sandbox/live mode must be explicit");
 let parsed; try{parsed=new URL(configured);}catch(_){throw new Error("PESAPAL_BASE_URL is invalid");}
 if(parsed.protocol!=="https:"||!["pay.pesapal.com","cybqa.pesapal.com"].includes(parsed.hostname)) throw new Error("PESAPAL_BASE_URL must use an official Pesapal HTTPS API host");
 return configured.replace(/\\/$/,"");
}
async function token(){
 const r=await fetch(baseUrl()+"/api/Auth/RequestToken",{method:"POST",headers:{"Content-Type":"application/json","Accept":"application/json"},body:JSON.stringify({consumer_key:PESAPAL_KEY.value(),consumer_secret:PESAPAL_SECRET.value()})});
 if(!r.ok) throw new Error("Pesapal authentication failed ("+r.status+")");
 const j=await r.json(); if(!j.token) throw new Error("Pesapal token missing"); return j.token;
}
async function registerIpn(t){
 const r=await fetch(baseUrl()+"/api/URLSetup/RegisterIPN",{method:"POST",headers:{Authorization:"Bearer "+t,"Content-Type":"application/json","Accept":"application/json"},body:JSON.stringify({url:PESAPAL_IPN_URL.value(), ipn_notification_type:"GET"})});
 if(!r.ok) throw new Error("Pesapal IPN registration failed ("+r.status+")");
 const j=await r.json(); if(!j.ipn_id) throw new Error("Pesapal did not return IPN ID"); return j.ipn_id;
}
async function requireRole(req,roles){
 if(!req.auth) throw new HttpsError("unauthenticated","Sign in required");
 const snap=await db.doc("users/"+req.auth.uid).get();
 const role=snap.exists?snap.get("role"):null;
 if(!roles.includes(role) && req.auth.token.role!=="SUPER_ADMIN") throw new HttpsError("permission-denied","Insufficient role");
 return {uid:req.auth.uid,user:snap.data()||{}};
}
async function sendUser(uid,title,body,data={}){
 const u=await db.doc("users/"+uid).get(); const tokenValue=u.get("fcmToken");
 if(tokenValue) await getMessaging().send({token:tokenValue,notification:{title,body},data:Object.fromEntries(Object.entries(data).map(([k,v])=>[k,String(v)]))}).catch(e=>logger.warn("FCM delivery failed",e));
}
exports.testPesapalConnection=onCall({secrets:[PESAPAL_KEY,PESAPAL_SECRET,PESAPAL_BASE]},async req=>{
 await requireRole(req,["SUPER_ADMIN"]); const t=await token(); return {success:true,message:"Pesapal authentication successful",environment:baseUrl().includes("cybqa")?"sandbox":"live",tokenReceived:!!t};
});
exports.updateAppConfig=onCall(async req=>{
 await requireRole(req,["SUPER_ADMIN"]);
 const allowed=["appName","googleLoginEnabled","facebookLoginEnabled","phoneLoginEnabled","emailLoginEnabled","googleWebClientId","googleAndroidClientId","googleIosClientId","facebookAppId","facebookClientToken","facebookDisplayName","pesapalEnv","pesapalConsumerKey","pesapalConsumerSecret","pesapalIpnId","pesapalFeePercent","atEnv","atApiKey","atUsername","atSenderId","smsTemplates","lateFeePercent","lateFeeAfterDays","caretakerCommission","withdrawalDailyLimit","minTopUp","maxTopUp","maintenanceMode","maintenanceMessage","supportPhone"];
 const data=req.data||{}; const clean={};
 for(const [k,v] of Object.entries(data)){if(!allowed.includes(k)) throw new HttpsError("invalid-argument","Unsupported config key: "+k); if(["pesapalConsumerKey","pesapalConsumerSecret","facebookClientToken","atApiKey"].includes(k)) throw new HttpsError("failed-precondition","Provider secrets must be stored in Firebase Secret Manager, not Firestore."); clean[k]=v;}
 if(clean.pesapalConsumerKey||clean.pesapalConsumerSecret||clean.facebookClientToken||clean.atApiKey) logger.info("Admin updated a secret config field");
 clean.updatedAt=FieldValue.serverTimestamp(); clean.updatedBy=req.auth.uid;
 await db.doc("app_config/global").set(clean,{merge:true}); return {success:true};
});
exports.createPesapalOrder=onCall({secrets:[PESAPAL_KEY,PESAPAL_SECRET,PESAPAL_BASE,PESAPAL_IPN_URL,PESAPAL_IPN_ID,PESAPAL_CALLBACK_URL]},async req=>{
 const {uid,user}=await requireRole(req,["LANDLORD","TENANT","CARETAKER"]);
 const amount=Number(req.data?.amount); if(!Number.isSafeInteger(amount)||amount<1000||amount>50000000) throw new HttpsError("invalid-argument","Top-up must be UGX 1,000 to 50,000,000");
 const email=String(req.data?.email||user.email||""); const phone=String(req.data?.phone||user.phone||"");
 if(!email||!phone||!user.name) throw new HttpsError("failed-precondition","Complete your name, email and phone first");
 const t=await token(); const ipnId=PESAPAL_IPN_ID.value();
 if(!ipnId) throw new HttpsError("failed-precondition","Register your Pesapal IPN and configure PESAPAL_IPN_ID in Secret Manager");
 const ref="RNT-"+uid.slice(0,8)+"-"+Date.now();
 const origin=PESAPAL_CALLBACK_URL.value();
 if(!origin) throw new HttpsError("failed-precondition","PESAPAL_CALLBACK_URL is not configured");
 const r=await fetch(baseUrl()+"/api/Transactions/SubmitOrderRequest",{method:"POST",headers:{Authorization:"Bearer "+t,"Content-Type":"application/json","Accept":"application/json"},body:JSON.stringify({id:ref,currency:"UGX",amount,description:"Rento wallet top-up",callback_url:origin,notification_id:ipnId,billing_address:{email_address:email,phone_number:phone,first_name:user.name}})});
 if(!r.ok) throw new HttpsError("unavailable","Pesapal order request failed");
 const j=await r.json(); if(!j.order_tracking_id||!j.redirect_url) throw new HttpsError("internal","Pesapal returned an invalid order");
 await db.doc("wallet_transactions/"+j.order_tracking_id).create({userId:uid,type:"TOPUP",amount,currency:"UGX",pesapalTrackingId:j.order_tracking_id,merchantReference:ref,method:"PESAPAL",status:"PENDING",createdAt:FieldValue.serverTimestamp()});
 return {trackingId:j.order_tracking_id,redirectUrl:j.redirect_url,status:"PENDING"};
});
exports.pesapalIPNListener=onRequest({secrets:[PESAPAL_KEY,PESAPAL_SECRET,PESAPAL_BASE]},async(req,res)=>{
 try{
  const tracking=String(req.query.OrderTrackingId||req.body?.OrderTrackingId||""); if(!tracking){res.status(400).send("Missing tracking ID");return;}
  const t=await token(); const r=await fetch(baseUrl()+"/api/Transactions/GetTransactionStatus?orderTrackingId="+encodeURIComponent(tracking),{headers:{Authorization:"Bearer "+t,Accept:"application/json"}});
  if(!r.ok){res.status(502).send("Unable to verify transaction");return;}
  const p=await r.json(); const txRef=db.doc("wallet_transactions/"+tracking);
  await db.runTransaction(async tr=>{
   const s=await tr.get(txRef); if(!s.exists)return; const row=s.data(); if(row.status==="COMPLETED"||row.status==="FAILED")return;
   const status=String(p.payment_status_description||"").toUpperCase();
   if(status==="COMPLETED"&&Number(p.amount)===Number(row.amount)&&String(p.currency)==="UGX"){
    const wRef=db.doc("wallets/"+row.userId); tr.set(wRef,{userId:row.userId,balanceUgx:FieldValue.increment(row.amount),updatedAt:FieldValue.serverTimestamp()},{merge:true});
    tr.update(txRef,{status:"COMPLETED",verifiedAt:FieldValue.serverTimestamp(),paymentMethod:p.payment_method||"PESAPAL"});
   }else if(["FAILED","INVALID"].includes(status))tr.update(txRef,{status:"FAILED",verifiedAt:FieldValue.serverTimestamp()});
  });
  const latest=await txRef.get(); if(latest.exists&&latest.get("status")==="COMPLETED") await sendUser(latest.get("userId"),"Wallet top-up successful","UGX "+latest.get("amount")+" has been added to your Rento wallet.",{trackingId:tracking});
  res.status(200).send("OK");
 }catch(e){logger.error("Pesapal IPN processing error",e);res.status(500).send("Processing error");}
});
exports.payRentFromWallet=onCall(async req=>{
 const {uid}=await requireRole(req,["TENANT"]); const invoiceId=String(req.data?.invoiceId||""); if(!invoiceId)throw new HttpsError("invalid-argument","Invoice required");
 const invoiceRef=db.doc("rent_invoices/"+invoiceId);
 let paidAmount=0;
 await db.runTransaction(async tr=>{
  const inv=await tr.get(invoiceRef); if(!inv.exists)throw new HttpsError("not-found","Invoice not found");
  const row=inv.data(); if(row.tenantId!==uid)throw new HttpsError("permission-denied","Invoice does not belong to tenant");
  if(!["PENDING","OVERDUE"].includes(row.status))throw new HttpsError("failed-precondition","Invoice is not payable");
  paidAmount=Number(row.amountUgx)+Number(row.lateFeeUgx||0); if(!Number.isSafeInteger(paidAmount)||paidAmount<=0)throw new HttpsError("failed-precondition","Invalid invoice amount");
  const tenantWallet=db.doc("wallets/"+uid), landlordWallet=db.doc("wallets/"+row.landlordId);
  const tw=await tr.get(tenantWallet); const balance=Number(tw.get("balanceUgx")||0);
  if(balance<paidAmount)throw new HttpsError("failed-precondition","Insufficient wallet balance");
  const config=await tr.get(db.doc("app_config/global")); const pct=Math.max(0,Math.min(100,Number(config.get("caretakerCommission")||0)));
  const commission=Math.floor(paidAmount*pct/100); const landlordAmount=paidAmount-commission;
  tr.set(tenantWallet,{balanceUgx:FieldValue.increment(-paidAmount),updatedAt:FieldValue.serverTimestamp()},{merge:true});
  tr.set(landlordWallet,{balanceUgx:FieldValue.increment(landlordAmount),updatedAt:FieldValue.serverTimestamp()},{merge:true});
  if(commission>0&&row.caretakerId)tr.set(db.doc("wallets/"+row.caretakerId),{balanceUgx:FieldValue.increment(commission),updatedAt:FieldValue.serverTimestamp()},{merge:true});
  tr.update(invoiceRef,{status:"PAID",paidAt:FieldValue.serverTimestamp(),paidAmountUgx:paidAmount});
  tr.create(db.doc("wallet_transactions/rent_"+invoiceId),{userId:uid,type:"RENT_PAYMENT",amount:paidAmount,status:"COMPLETED",createdAt:FieldValue.serverTimestamp(),invoiceId,landlordId:row.landlordId});
  tr.create(db.doc("wallet_transactions/rent_credit_"+invoiceId),{userId:row.landlordId,type:"RENT_CREDIT",amount:landlordAmount,status:"COMPLETED",createdAt:FieldValue.serverTimestamp(),invoiceId});
  if(commission>0&&row.caretakerId)tr.create(db.doc("wallet_transactions/rent_commission_"+invoiceId),{userId:row.caretakerId,type:"COMMISSION",amount:commission,status:"COMPLETED",createdAt:FieldValue.serverTimestamp(),invoiceId});
 });
 return {success:true,amount:paidAmount};
});
exports.initiateWithdrawal=onCall(async req=>{
 const {uid}=await requireRole(req,["LANDLORD","TENANT","CARETAKER"]);
 const amount=Number(req.data?.amount); if(!Number.isSafeInteger(amount)||amount<1000)throw new HttpsError("invalid-argument","Invalid withdrawal amount");
 const dest=req.data?.destination||{}; if(!["MTN","AIRTEL","BANK"].includes(dest.type)||!dest.account)throw new HttpsError("invalid-argument","Valid withdrawal destination required");
 const day=new Date().toISOString().slice(0,10); const config=(await db.doc("app_config/global").get()).data()||{};
 const limit=Number(config.withdrawalDailyLimit||5000000);
 const daily=await db.collection("wallet_transactions").where("userId","==",uid).where("type","==","WITHDRAWAL").where("dayKey","==",day).get();
 if(daily.docs.reduce((n,d)=>n+Number(d.get("amount")||0),0)+amount>limit)throw new HttpsError("resource-exhausted","Daily withdrawal limit exceeded");
 // Pesapal disbursement is not enabled by standard checkout credentials. Never debit without an approved payout API.
 throw new HttpsError("failed-precondition","Payouts require Pesapal merchant disbursement approval and payout credentials. No funds were debited.");
});
exports.generateMonthlyRent=onSchedule("0 0 1 * *",async()=>{
 const units=await db.collection("units").where("status","==","OCCUPIED").get(); const batch=db.batch(); const period=new Date().toISOString().slice(0,7);
 for(const u of units.docs){const x=u.data(); if(!x.tenantId||!x.landlordId)continue; const id=x.tenantId+"_"+u.id+"_"+period; const ref=db.doc("rent_invoices/"+id); batch.set(ref,{tenantId:x.tenantId,landlordId:x.landlordId,unitId:u.id,period,amountUgx:Number(x.monthlyRentUgx||0),lateFeeUgx:0,status:"PENDING",createdAt:FieldValue.serverTimestamp(),dueAt:Date.now()+5*86400000},{merge:true});}
 await batch.commit();
});
exports.sendRentReminders=onSchedule("every day 08:00",async()=>{
 const now=Date.now(); const bills=await db.collection("rent_invoices").where("status","in",["PENDING","OVERDUE"]).get();
 for(const d of bills.docs){const b=d.data(),days=Math.floor((now-Number(b.dueAt||now))/86400000); if([ -3,0,3,7].includes(days)){await sendUser(b.tenantId,"Rent payment reminder","Your rent for "+b.period+" is due. Please open Rento to pay.",{invoiceId:d.id});}}
});

exports.createProperty=onCall(async req=>{
 const {uid}=await requireRole(req,["LANDLORD"]);
 const d=req.data||{}; const name=String(d.name||"").trim(); const type=String(d.type||"");
 if(name.length<2||name.length>100||!["APARTMENT","HOUSE","HOSTEL","COMMERCIAL","SINGLE_ROOM"].includes(type))throw new HttpsError("invalid-argument","Provide a property name and valid type");
 const ref=db.collection("properties").doc();
 const row={id:ref.id,landlordId:uid,name,type,description:String(d.description||"").slice(0,2000),address:String(d.address||"").slice(0,300),latitude:Number.isFinite(Number(d.latitude))?Number(d.latitude):null,longitude:Number.isFinite(Number(d.longitude))?Number(d.longitude):null,photoUrls:Array.isArray(d.photoUrls)?d.photoUrls.slice(0,12):[],createdAt:FieldValue.serverTimestamp(),updatedAt:FieldValue.serverTimestamp(),archived:false};
 await ref.create(row); return {propertyId:ref.id};
});
exports.createUnit=onCall(async req=>{
 const {uid}=await requireRole(req,["LANDLORD"]); const d=req.data||{};
 const propertyId=String(d.propertyId||""); const number=String(d.number||"").trim();
 const rent=Number(d.monthlyRentUgx),deposit=Number(d.depositUgx||0);
 if(!propertyId||!number||!Number.isSafeInteger(rent)||rent<0||!Number.isSafeInteger(deposit)||deposit<0)throw new HttpsError("invalid-argument","Invalid unit details");
 const p=await db.doc("properties/"+propertyId).get(); if(!p.exists||p.get("landlordId")!==uid)throw new HttpsError("permission-denied","Property not owned by landlord");
 const ref=db.collection("units").doc();
 await ref.create({id:ref.id,propertyId,landlordId:uid,number,bedrooms:Math.max(0,Number(d.bedrooms)||0),bathrooms:Math.max(0,Number(d.bathrooms)||0),monthlyRentUgx:rent,depositUgx:deposit,status:"VACANT",createdAt:FieldValue.serverTimestamp(),updatedAt:FieldValue.serverTimestamp()});
 return {unitId:ref.id};
});
exports.createMaintenanceRequest=onCall(async req=>{
 const {uid}=await requireRole(req,["TENANT"]); const d=req.data||{};
 const unitId=String(d.unitId||""),description=String(d.description||"").trim(),priority=String(d.priority||"LOW");
 if(!unitId||description.length<5||description.length>3000||!["LOW","MEDIUM","URGENT"].includes(priority))throw new HttpsError("invalid-argument","Invalid maintenance request");
 const unit=await db.doc("units/"+unitId).get(); if(!unit.exists||unit.get("tenantId")!==uid)throw new HttpsError("permission-denied","Unit is not assigned to this tenant");
 const ref=db.collection("maintenance").doc();
 await ref.create({id:ref.id,tenantId:uid,landlordId:unit.get("landlordId"),propertyId:unit.get("propertyId"),unitId,description,priority,status:"OPEN",mediaUrls:Array.isArray(d.mediaUrls)?d.mediaUrls.slice(0,5):[],createdAt:FieldValue.serverTimestamp()});
 await sendUser(unit.get("landlordId"),"New maintenance request",description.slice(0,120),{maintenanceId:ref.id});
 return {requestId:ref.id};
});
exports.createRentInvoice=onCall(async req=>{
 const {uid}=await requireRole(req,["LANDLORD"]); const d=req.data||{};
 const unitId=String(d.unitId||""),period=String(d.period||"");
 if(!/^\\d{4}-\\d{2}$/.test(period))throw new HttpsError("invalid-argument","Period must be YYYY-MM");
 const unit=await db.doc("units/"+unitId).get(); if(!unit.exists||unit.get("landlordId")!==uid)throw new HttpsError("permission-denied","Unit not owned by landlord");
 const tenantId=String(unit.get("tenantId")||""); if(!tenantId)throw new HttpsError("failed-precondition","Unit has no assigned tenant");
 const amount=Number(unit.get("monthlyRentUgx")||0); if(!Number.isSafeInteger(amount)||amount<=0)throw new HttpsError("failed-precondition","Unit rent is invalid");
 const id=tenantId+"_"+unitId+"_"+period; const ref=db.doc("rent_invoices/"+id);
 await ref.create({tenantId,landlordId:uid,unitId,period,amountUgx:amount,lateFeeUgx:0,status:"PENDING",createdAt:FieldValue.serverTimestamp(),dueAt:Date.now()+5*86400000});
 return {invoiceId:id};
});
exports.getAdminDashboardStats=onCall(async req=>{
 await requireRole(req,["SUPER_ADMIN"]);
 const [users,properties,txs]=await Promise.all([db.collection("users").get(),db.collection("properties").get(),db.collection("wallet_transactions").get()]);
 const today=new Date(); today.setHours(0,0,0,0); const month=new Date(today.getFullYear(),today.getMonth(),1);
 let volumeToday=0,volumeMonth=0,failed=0,walletBalance=0;
 for(const t of txs.docs){const x=t.data(),date=x.createdAt?.toDate?.()||new Date(0);if(x.status==="FAILED")failed++;if(x.type==="TOPUP"&&x.status==="COMPLETED"){if(date>=today)volumeToday+=Number(x.amount||0);if(date>=month)volumeMonth+=Number(x.amount||0);}}
 const wallets=await db.collection("wallets").get(); for(const w of wallets.docs)walletBalance+=Number(w.get("balanceUgx")||0);
 const roles=users.docs.reduce((a,d)=>{const k=d.get("role")||"UNSELECTED";a[k]=(a[k]||0)+1;return a;},{});
 return {totalUsers:users.size,landlords:roles.LANDLORD||0,tenants:roles.TENANT||0,caretakers:roles.CARETAKER||0,totalWalletBalance:walletBalance,pesapalVolumeToday:volumeToday,pesapalVolumeMonth:volumeMonth,failedTransactions:failed,activeProperties:properties.docs.filter(d=>d.get("archived")!==true).length};
});


exports.selectUserRole=onCall(async req=>{
 if(!req.auth) throw new HttpsError("unauthenticated","Sign in required");
 const role=String(req.data?.role||"").toUpperCase();
 if(!["LANDLORD","TENANT"].includes(role)) throw new HttpsError("invalid-argument","Choose Landlord or Tenant. Caretaker access requires administrator approval.");
 const ref=db.doc("users/"+req.auth.uid);
 await db.runTransaction(async tr=>{
  const snap=await tr.get(ref);
  if(!snap.exists) throw new HttpsError("failed-precondition","Complete account registration first");
  const current=String(snap.get("role")||"UNSELECTED");
  if(current!=="UNSELECTED" && current!==role) throw new HttpsError("failed-precondition","Role can only be selected once. Contact support to change it.");
  tr.set(ref,{role,roleSelectedAt:FieldValue.serverTimestamp()},{merge:true});
 });
 return {success:true,role};
});
