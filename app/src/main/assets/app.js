'use strict';
const $=id=>document.getElementById(id);
const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let st=null,category='Gentle',playing='',dirty=false,busy=false,toastTimer,confirmFn,presetPending=false,historyExpanded=false,wakePreviewing=false;
const catalog=window.SOUNDS||[];
const appearanceCatalog=window.APPEARANCE;
appearanceCatalog.themes=appearanceCatalog.themes.map(t=>({...t,...customColorMath(t.background,t.primary,t.secondary,t.preferredText),custom:false}));
let currentTheme='rose',currentIcon='petal';
let customThemes=[];
function iconPath(theme,icon){const custom=[...appearanceCatalog.themes,...customThemes].find(t=>t.id===theme);return custom?customIconUrl(custom,icon):`icons/${theme}_${icon}.svg`;}
function renderAppearanceOptions(){
 $('themeOptions').innerHTML=appearanceCatalog.themes.map(theme=>`<button type="button" class="theme-option" id="profile${theme.id==='navy'?'Navy':theme.id==='rose'?'Rose':theme.id}" data-profile="${theme.id}" aria-pressed="false"><img src="${iconPath(theme.id,currentIcon)}" alt=""><strong>${esc(theme.name)}</strong><small>${theme.dark?'Night palette':'Day palette'}</small><span class="profile-check" aria-hidden="true">✓</span></button>`).join('');
 $('iconOptions').innerHTML=appearanceCatalog.icons.map(icon=>`<button type="button" class="icon-option" data-icon="${icon.id}" aria-pressed="false"><img src="${iconPath(currentTheme,icon.id)}" alt=""><strong>${esc(icon.name)}</strong><span class="profile-check" aria-hidden="true">✓</span></button>`).join('');
}
function applyAppearance(profile,design){
 const theme=[...appearanceCatalog.themes,...customThemes].find(t=>t.id===profile)||appearanceCatalog.themes[1];
 const icon=appearanceCatalog.icons.find(i=>i.id===design)||appearanceCatalog.icons[0];
 currentTheme=theme.id;currentIcon=icon.id;document.documentElement.dataset.profile=theme.id;
 Object.keys(customColorMath('#28262D','#D89AAA','#A783A8','#F4EADC').tokens).forEach(key=>document.documentElement.style.removeProperty('--'+key));document.documentElement.style.removeProperty('color-scheme');
 if(theme.tokens){Object.entries(theme.tokens).forEach(([key,value])=>document.documentElement.style.setProperty('--'+key,value));document.documentElement.style.colorScheme=theme.dark?'dark':'light';}
 document.documentElement.dataset.icon=icon.id;$('brandMark').src=iconPath(theme.id,icon.id);
 $('appearancePreview').src=iconPath(theme.id,icon.id);$('appearanceName').textContent=theme.name;$('appearanceIconName').textContent=icon.name+' icon';
 document.querySelectorAll('.theme-option').forEach(button=>{button.setAttribute('aria-pressed',String(button.dataset.profile===theme.id));button.querySelector('img').src=iconPath(button.dataset.profile,icon.id);});
 document.querySelectorAll('.icon-option').forEach(button=>{button.setAttribute('aria-pressed',String(button.dataset.icon===icon.id));button.querySelector('img').src=iconPath(theme.id,button.dataset.icon);});
 $('appearanceStatus').textContent=theme.name+' selected · saves automatically.';$('iconStatus').textContent=icon.name+' selected · the app icon uses the closest bundled palette.';
 $('themeSelect').innerHTML=[...appearanceCatalog.themes,...customThemes].map(t=>`<option value="${esc(t.id)}">${esc(t.name)}</option>`).join('');$('themeSelect').value=theme.id;
 renderCustomThemes();
}
renderAppearanceOptions();applyAppearance(...location.hash.slice(1).split('/'));
$('themeSelect').onchange=()=>send('appearance',{profile:$('themeSelect').value});
$('themeOptions').onclick=event=>{const button=event.target.closest('[data-profile]');if(button)send('appearance',{profile:button.dataset.profile});};
$('iconOptions').onclick=event=>{const button=event.target.closest('[data-icon]');if(button)send('iconDesign',{design:button.dataset.icon});};
$('openAppearance').onclick=()=>page('appearance');$('appearanceBack').onclick=()=>{page('settings');showSettingsGroup('appearance');};
 setupCustomThemes();
function send(action,data={}){if(window.Native?.postMessage)window.Native.postMessage(JSON.stringify({action,...data}));else toast('This preview needs the Android app for calendar connections and alarms.');}
function showSettingsGroup(group){document.querySelectorAll('[data-group]').forEach(el=>el.hidden=el.dataset.group!==group);document.querySelectorAll('[data-settings-group]').forEach(el=>el.setAttribute('aria-pressed',String(el.dataset.settingsGroup===group)));}
showSettingsGroup('wake');document.querySelector('.settings-menu').onclick=e=>{const b=e.target.closest('[data-settings-group]');if(b)showSettingsGroup(b.dataset.settingsGroup);};
function page(name){stopPreview();if(name==='settings')showSettingsGroup('wake');document.querySelectorAll('.page').forEach(el=>el.hidden=el.id!==name);document.querySelectorAll('nav button').forEach(el=>{if(el.dataset.page===(['appearance','themeEditor'].includes(name)?'settings':name))el.setAttribute('aria-current','page');else el.removeAttribute('aria-current');});window.scrollTo(0,0);}
function relativeDay(ms){const today=new Date(),day=new Date(ms);today.setHours(0,0,0,0);day.setHours(0,0,0,0);const tomorrow=new Date(today);tomorrow.setDate(tomorrow.getDate()+1);return day.getTime()===today.getTime()?'Today':day.getTime()===tomorrow.getTime()?'Tomorrow':new Intl.DateTimeFormat(undefined,{weekday:'long'}).format(ms);}
function time(ms){return new Intl.DateTimeFormat(undefined,{hour:'numeric',minute:'2-digit'}).format(ms);}
function shortDate(ms){return new Intl.DateTimeFormat(undefined,{weekday:'short',month:'short',day:'numeric'}).format(ms);}
function timeMarkup(ms){const parts=new Intl.DateTimeFormat(undefined,{hour:'numeric',minute:'2-digit'}).formatToParts(ms);return parts.map(p=>p.type==='dayPeriod'?`<small>${esc(p.value)}</small>`:esc(p.value)).join('');}
function setBusy(value){busy=value;document.body.classList.toggle('busy',value);document.querySelectorAll('form button[type=submit],#importFile,#refreshCalendar,#disconnect,#enabled,#testAlarm,#addManual,#heroAction,#useQuietPreset,#restorePlayback,#soundPackAction,#previewWake,#exportSettings,#importSettings,#clearHistory').forEach(el=>el.disabled=value||!st||!!st.authorizing);$('connectButton').textContent=value?'Please wait…':$('addCalendar').checked?'Add calendar':st?.source?'Replace first calendar':'Connect work calendar';renderCalendarFeedback();}
function toast(message){$('toast').textContent=message;$('toast').classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').classList.remove('visible'),5000);}
function receive(state){if(presetPending&&((presetPending==='quiet'&&state.settings.quietPreset)||(presetPending==='restore'&&!state.settings.canRestorePlayback))){$('volume').value=state.settings.volume;$('fade').value=state.settings.fade;$('volumeValue').textContent=state.settings.volume+'%';presetPending=false;}st=state;customThemes=state.customThemes||[];applyAppearance(state.settings.appearance,state.settings.iconDesign);render();}
function calendarExplanation(){
 const i=st?.calendarInfo||{};
 if(!st?.source)return '';
 if(i.events===undefined)return 'Refresh your calendar to check what the link contains.';
 if(!st.shifts.length&&i.filtered>0)return `${i.filtered} upcoming ${i.filtered===1?'entry was':'entries were'} excluded by your title filter. Clear the filter to show them. An availability-only link hides titles.`;
 if(i.events===0&&i.availability>0&&st.shifts.length)return 'Availability-only link connected. Every busy block can create a work alarm, so use a calendar containing only your work shifts. Review the next wake-up time.';
 if(i.events===0&&i.availability>0)return 'This availability-only link has no upcoming busy blocks in the next 60 days. Check the shared calendar and refresh it. All-day blocks also need a chosen shift start time.';
 if(i.events===0)return 'The link downloaded successfully, but the file contains no events. Check that the sharing link belongs to the calendar with your work shifts. Your calendar service may take time to publish new events.';
 if(!st.shifts.length)return `The download contains ${i.events} calendar ${i.events===1?'entry':'entries'}, but no matching shifts in the next 60 days. Check that your upcoming work shifts are on the calendar being shared.`;
 return `${st.shifts.length} upcoming ${st.shifts.length===1?'entry':'entries'} found. Review the times before enabling work alarms.`;
}
function renderCalendarFeedback(){
 const el=$('calendarFeedback');if(!el||!st)return;
 const healthy=st.shifts.length>0&&!st.calendarInfo?.filtered&&!(!st.calendarInfo?.events&&st.calendarInfo?.availability);
 const message=busy?'Checking the calendar. This may take a few seconds…':st.error||(healthy?'':calendarExplanation());
 el.hidden=!message;el.className=st.error&&!busy?'note error':'note';
 el.replaceChildren();if(message)el.textContent=message;
 if(!busy&&st.calendarInfo?.filtered>0){const b=document.createElement('button');b.className='text-button full';b.textContent='Clear title filter';b.onclick=()=>{dirty=false;send('clearFilter');};el.append(b);}
}
function renderAllDayAlert(){
 const alert=$('allDayAlert'),rows=(st?.shifts||[]).filter(a=>a.needsTime);
 alert.hidden=!rows.length;alert.replaceChildren();if(!rows.length)return;
 const heading=document.createElement('strong');heading.textContent='All-day blocks need a start time';alert.append(heading);
 const detail=document.createElement('p');detail.textContent=`${rows.length} upcoming ${rows.length===1?'entry has':'entries have'} no wake-up alarm. An all-day calendar block does not say when work begins. Enter the actual shift start time before relying on it.`;alert.append(detail);
 const ids=[...new Set(rows.map(a=>a.calendarId||'primary'))];
 for(const id of ids){const source=(st.calendars||[]).find(c=>c.id===id);const button=document.createElement('button');button.type='button';button.className='primary full';button.textContent=`Set start time${ids.length>1?' · '+(source?.name||'Work calendar'):''} →`;button.onclick=()=>openCalendarRules(id,true);alert.append(button);}
}
function connectionSucceeded(){$('calendarUrl').value='';$('addCalendar').checked=false;}
const calendarGuides={
 proton:{steps:['Create a calendar containing only your own work shifts, then open calendar.proton.me in a browser.','Open Settings → All settings → Calendars, select that work calendar, and choose Share with anyone → Create link.','Choose Limited view to share busy times without event details, then copy the link. Leave Petal Wake’s title filter blank.'],note:'Limited view treats every busy block as work, so keep that calendar work-only. Full view also works if you need event titles. Treat either link as private.'},
 google:{steps:['On a computer, open Google Calendar and choose Settings.','Under Settings for my calendars, select your work calendar, then Integrate calendar.','Copy the Secret address in iCal format and paste it below.'],note:'Keep the Secret address private. A work or school administrator may disable this option.'},
 outlook:{steps:['Open Outlook Calendar on the web and go to Settings → Calendar → Shared calendars.','Under Publish a calendar, choose your work calendar and viewing permission, then select Publish.','Copy the ICS link, not the HTML link, and paste it below.'],note:'Anyone with the published link can view the calendar at the permission level you chose.'},
 icloud:{steps:['Open Calendar on an iPhone or iCloud.com and find your work calendar.','Open that calendar’s information or sharing settings and turn on Public Calendar.','Copy or share the calendar link and paste it below.'],note:'A public iCloud calendar is read-only to subscribers, but anyone with its link can view it.'},
 fastmail:{steps:['In Fastmail, open Settings → Calendars and edit your work calendar.','Choose Publish, enable Full event details, and save.','Copy the public calendar URL and paste it below.'],note:'Full event details lets Petal Wake use shift titles. Anyone with the published link may view those details.'},
 zoho:{steps:['In Zoho Calendar on the web, open your work calendar’s settings.','Under Sharing and permissions, find Private URL and copy its iCal link.','Paste the iCal link below, not the calendar webpage link.'],note:'Keep the private URL secret. A public iCal link with event details also works if you choose to publish the calendar.'},
 nextcloud:{steps:['In Nextcloud Calendar, open the sharing menu for your work calendar.','Create a Share link, then open its public calendar page.','Copy the subscription link from that page and paste it below.'],note:'Use the subscription link, not the public webpage or a one-time exported .ics file.'},
 teamup:{steps:['In Teamup, open the top-right menu → Preferences → iCalendar Feeds.','Choose a feed for your own work subcalendar, or a combined feed if needed.','Copy the iCalendar feed URL and paste it below.'],note:'A combined feed may include other people’s events. Choose only the calendars you need.'},
 wheniwork:{steps:['Open When I Work in a web browser and go to My Schedule.','Choose Calendar Sync and copy the My Schedule link.','Paste that personal calendar link below.'],note:'Choose My Schedule, not Entire Schedule. Calendar Sync is available on the web rather than in the Android app.'},
 deputy:{steps:['Open Deputy on the web and go to your Profile.','Choose Add your Shifts to your personal calendar app, then select the ongoing WebCal option.','Tap Copy Link and paste it below.'],note:'Use the ongoing link, not a one-time .ics export. If your workplace requires sign-in to read the feed, Petal Wake cannot download it.'},
 '7shifts':{steps:['Open 7shifts on the web and go to Schedule → Tools → Calendar Sync.','Choose your location and department for your personal schedule.','Copy the calendar URL and paste it below.'],note:'Keep your personal schedule link private. 7shifts may limit how far ahead it publishes shifts.'},
 planday:{steps:['In the Planday Android app, open Profile → Sync with calendar.','Choose Copy Link.','Paste the copied subscription URL below.'],note:'Schedule changes can take time to appear in the feed. Check your next alarm after a shift changes.'},
 other:{steps:['In your calendar or work scheduling app, look for Share, Publish, Subscribe, or iCal/ICS settings.','Choose a live, read-only iCalendar subscription link for the calendar with your work shifts.','Copy the https:// or webcal:// link and paste it below. If the service only exports a file, use Import calendar file instead.'],note:'A calendar webpage or one-time file download URL may not refresh. Check the app’s sharing help if you cannot find a subscription link.'}
};
function renderCalendarGuide(){
 const guide=calendarGuides[$('calendarService').value],box=$('calendarDirections');box.replaceChildren();
 const title=document.createElement('h3');title.textContent='How to get your link';box.append(title);
 const steps=document.createElement('ol');for(const step of guide.steps){const item=document.createElement('li');item.textContent=step;steps.append(item);}box.append(steps);
 const note=document.createElement('p');note.className='fine';note.textContent=guide.note;box.append(note);
}
$('calendarService').onchange=renderCalendarGuide;renderCalendarGuide();

function render(){
 if(!st)return;
 $('dateLabel').textContent=new Intl.DateTimeFormat(undefined,{weekday:'long',month:'long',day:'numeric'}).format(new Date());
 $('enabled').checked=st.settings.enabled;$('enabled').setAttribute('aria-checked',String(st.settings.enabled));
 $('leadSummary').textContent=`Wake up ${st.settings.lead} minutes before each shift`;
 const future=Object.values(st.scheduled).filter(a=>a.at>Date.now()).sort((a,b)=>a.at-b.at);
 const next=future[0];
 const permitted=st.permissions.exact&&st.permissions.notifications;
 const allDayMissing=st.calendarInfo?.needsTime||0;
 $('heroLabel').textContent=next?'Next alarm · '+relativeDay(next.at):'Next alarm';
 $('heroStatus').textContent=!permitted?'Setup needed':next?'Alarm set':allDayMissing?'Start time needed':st.settings.enabled?'Ready':'Paused';
 $('nextAlarm').innerHTML=next?`<div class="alarm-time">${timeMarkup(next.at)}</div><p class="hero-date">${esc(new Intl.DateTimeFormat(undefined,{weekday:'long',month:'long',day:'numeric'}).format(next.at))}</p><div class="hero-work"><div><strong>${esc(next.title)}</strong><small>${next.kind==='shift'?`Wake ${next.leadMinutes??st.settings.lead} minutes before work`:next.kind==='snooze'?'Snoozed alarm':'Personal alarm'}</small></div>${next.kind==='shift'?`<span>${esc(time(next.start))}<small>Shift starts</small></span>`:''}</div>`:`<div class="hero-title">No alarm scheduled</div><p class="hero-detail">${allDayMissing?'All-day work blocks need a real shift start time before they can set an alarm.':st.source?(st.settings.enabled?'Review your upcoming shifts and alarm permissions.':'Turn on work alarms, or add a personal alarm below.'):'Connect your work calendar to schedule wake-up alarms automatically.'}</p>`;
 $('heroAction').textContent=!permitted?'Finish alarm setup':!st.source&&!next?'Connect work calendar':next?.kind==='shift'?'Skip once':next?'Manage personal alarms':allDayMissing?'Set all-day start time':'Review calendar & shifts';
 $('heroAction').onclick=()=>{if(!permitted)page('settings');else if(next?.kind==='shift')send('skip',{id:next.id});else if(next){$('manualList').scrollIntoView({behavior:'smooth',block:'center'});}else page('calendar');};
 $('workConnection').textContent=st.source?(st.linked?'Shared calendar':'Imported calendar')+' · '+(st.settings.enabled?'Enabled':'Paused'):'No calendar connected';
 let attention='';
 if(st.readinessIssue)attention+=`<div class="note error" role="alert"><strong>Alarm readiness</strong><br>${esc(st.readinessIssue)}</div>`;
 if(allDayMissing)attention+=`<div class="note error" role="alert"><strong>All-day work blocks need a start time</strong><br>${allDayMissing} upcoming ${allDayMissing===1?'entry has':'entries have'} no alarm scheduled. <button class="text-button full" id="attentionSetAllDay" type="button">Set actual shift start time →</button></div>`;
 if(st.error)attention+=`<div class="note error" role="alert">${esc(st.error)}${st.source?' Previously downloaded shifts stay available offline.':''}</div>`;
 if(st.source&&st.linked&&Date.now()-st.lastSync>8*3600000)attention+='<div class="note">Your calendar has not refreshed in over 8 hours. Open Calendar and refresh to check your shifts.</div>';
 if(st.late>0)attention+=`<div class="note">${st.late} upcoming shift${st.late>1?'s have':' has'} a wake-up time that already passed. Set a personal alarm if you still need one.</div>`;
 if(next&&st.permissions.systemVolume===0)attention+='<div class="note error">Your phone’s alarm volume is muted. Increase it in Settings before relying on this alarm.</div>';
 $('attention').innerHTML=attention;
 if($('attentionSetAllDay'))$('attentionSetAllDay').onclick=()=>{page('calendar');$('allDayAlert').scrollIntoView({block:'start'});};
 const remaining=st.shifts.filter(a=>a.id!==next?.id);const visible=remaining.slice(0,3);
 $('shiftList').innerHTML=visible.length?visible.map(a=>alarmRow(a,true)).join(''):`<div class="empty">${st.source?(next?.kind==='shift'?'Your next work alarm is shown above.':esc(calendarExplanation())):'Connect a calendar to see upcoming work shifts.'}</div>`;
 $('viewAllShifts').hidden=remaining.length<=3;$('viewAllShifts').textContent=`View all ${st.shifts.length} shifts`; 
 const personal=[...Object.values(st.manual),...Object.values(st.snoozes)].filter(a=>a.at>Date.now()).sort((a,b)=>a.at-b.at);
 $('manualList').innerHTML=personal.length?personal.map(a=>alarmRow(a,false)).join(''):'<div class="empty">Need a one-time wake-up? Add a personal alarm.</div>';renderHistory();
 const sound=catalog.find(a=>a.file===st.settings.sound);$('selectedSound').textContent=sound?.title||'Choose a sound';
 $('connectionStatus').textContent=st.source?st.source:'Add your work calendar’s sharing link.';
 renderAllDayAlert();
 $('connectedDetails').hidden=!st.source;
 $('connectedDetails').innerHTML=st.source?`<div class="panel"><h2>${(st.calendars||[]).length} connected calendar${st.calendars?.length===1?'':'s'}</h2><div id="calendarHealthDetail"></div><p>${st.shifts.length} upcoming entries · ${st.settings.enabled?'Work alarms enabled':'Work alarms paused'}</p><button class="secondary full" id="refreshCalendar">${st.linked?'Refresh now':'Re-import calendar file'}</button><details class="calendar-check"><summary>Calendar check</summary><p class="fine">${st.calendarInfo?.events??0} event records received · ${st.calendarInfo?.timed??0} timed · ${st.calendarInfo?.allDay??0} all-day · ${st.calendarInfo?.availability??0} availability records<br>${st.calendarInfo?.upcomingTimed??0} upcoming timed · ${st.calendarInfo?.upcomingAllDay??0} upcoming all-day · ${st.calendarInfo?.filtered??0} filtered out</p><button class="text-button" id="copyCalendarCheck">Copy calendar check</button><p class="fine">Copies counts only. Your link and event details are excluded.</p></details><button class="text-button full" id="disconnect">Disconnect all calendars</button></div>`:'';
 $('calendarSources').innerHTML=(st.calendars||[]).map(c=>`<div class="panel calendar-source"><div><strong>${esc(c.name)}</strong><small>${esc(c.kind)} · ${c.lastSync?'Downloaded '+esc(downloadAge(c.lastSync)):'No download yet'}</small>${c.error?`<p class="fine error-text">${esc(c.error)} · Saved alarms remain in place.</p>`:''}<small>${c.lead} min before shifts${c.filter?' · titles containing '+esc(c.filter):''}</small></div><div class="calendar-source-actions"><button type="button" class="text-button" data-edit-calendar="${esc(c.id)}">Edit rules</button>${c.kind==='Shared link'?`<button type="button" class="text-button" data-copy-calendar="${esc(c.id)}">Copy link securely</button>`:''}${c.id==='primary'?'':`<button type="button" class="text-button" data-remove-calendar="${esc(c.id)}">Remove</button>`}</div></div>`).join('');
 $('addCalendarLine').hidden=!st.source;$('replaceLinkNote').hidden=!st.source||$('addCalendar').checked;renderSetupChecklist(next,permitted);
 $('calendarShiftsSection').hidden=!st.source;
 $('calendarShiftList').innerHTML=st.shifts.length?st.shifts.slice(0,8).map(a=>alarmRow(a,true)).join(''):`<div class="empty">${esc(calendarExplanation())}</div>`;
 if($('copyCalendarCheck'))$('copyCalendarCheck').onclick=()=>send('copyCalendarCheck');
 if($('refreshCalendar'))$('refreshCalendar').onclick=()=>send(st.linked?'refresh':'import');
 if($('disconnect'))$('disconnect').onclick=()=>confirmAction('Disconnect all calendars?','All work alarms from these calendars will be removed. Personal alarms stay scheduled.',()=>send('disconnect'),'Disconnect');
 $('connectButton').textContent=$('addCalendar').checked?'Add calendar':st.source?'Replace first calendar':'Connect work calendar';
 $('permissions').innerHTML=[['Exact alarm scheduling',st.permissions.exact,'exact'],['Alarm notifications',st.permissions.notifications,'notifications'],['Show alarm on lock screen',st.permissions.fullscreen,'fullscreen'],['Phone alarm volume',st.permissions.systemVolume>0,'volumeSettings']].map(([name,ready,action])=>`<div class="permission"><span>${esc(name)}</span>${ready?'<span class="ready">✓ Ready</span>':`<button class="text-button" data-permission="${action}">${action==='volumeSettings'?'Increase':'Allow'} →</button>`}</div>`).join('');
 if(!dirty){for(const key of ['lead','filter','snooze','volume','fade','earlyLead','lateLead'])$(key).value=st.settings[key];$('allDayEnabled').checked=st.settings.allDayEnabled||false;$('allDayTime').value=st.settings.allDayEnabled?minuteTime(st.settings.allDayMinutes??480):'';$('allDayTime').disabled=!$('allDayEnabled').checked;$('allDayTime').required=$('allDayEnabled').checked;$('vibrate').checked=st.settings.vibrate;$('fallbackMinutes').value=st.settings.fallbackMinutes||0;$('volumeValue').textContent=st.settings.volume+'%';$('shiftRulesEnabled').checked=st.settings.shiftRulesEnabled;$('earlyBefore').value=minuteTime(st.settings.earlyBefore);$('lateAfter').value=minuteTime(st.settings.lateAfter);$('eveningCheck').checked=st.settings.eveningCheck;$('eveningTime').value=minuteTime(st.settings.eveningMinutes);}
 $('soundPackStatus').textContent=st.settings.soundPackInstalled?'28 additional orchestra recordings installed for offline use.':st.settings.requestedSound!==st.settings.sound?'Your selected orchestra recording needs the pack. A gentle bundled recording will play until you download it.':'28 additional orchestra recordings are available as an optional offline download.';
 $('soundPackAction').textContent=st.settings.soundPackInstalled?'Remove orchestra pack':'Download orchestra pack';
 renderConfidence();renderSounds();setBusy(busy);
}
function minuteTime(minutes){return String(Math.floor((minutes??0)/60)).padStart(2,'0')+':'+String((minutes??0)%60).padStart(2,'0');}
function timeMinutes(value){const [hour,minute]=value.split(':').map(Number);return hour*60+minute;}
function alarmRow(a,shift){
 const ms=shift?a.start:a.at,d=new Date(ms),set=!!st.scheduled[a.id];
 let status=shift?(a.needsTime?'Start time needed':a.skipped?'Skipped':a.at<=Date.now()?'Wake time passed':!st.settings.enabled?'Paused':set?'Alarm set':'Setup needed'):set?(a.kind==='snooze'?'Snoozed':'Alarm set'):'Setup needed';
 return `<div class="alarm-row${a.needsTime?' needs-time':''}"><div class="day-block"><span>${esc(new Intl.DateTimeFormat(undefined,{weekday:'short'}).format(d))}</span><strong>${d.getDate()}</strong></div><div class="row-main"><strong>${esc(a.title)}</strong><p>${shift?(a.allDay?(a.needsTime?'All-day block · start time unknown':`All-day entry · using ${esc(time(a.start))}`):`Shift ${esc(time(a.start))}${a.end>a.start?' – '+esc(time(a.end)):''}`):esc(shortDate(a.at))}<br>${a.needsTime?'<strong class="needs-time-label">No alarm scheduled</strong>':esc(status)}${shift&&st.calendars?.length>1?' · '+esc(a.calendarName||'Work calendar'):''}</p></div><div class="row-right"><div class="wake">${a.needsTime?'No time':timeMarkup(a.at)}</div>${a.needsTime?`<button class="row-action set-start-action" data-set-all-day="${esc(a.calendarId||'primary')}">Set start time</button>`:`<button class="row-action" data-alarm-action="${shift?(a.skipped?'unskip':'skip'):'delete'}" data-id="${esc(a.id)}">${shift?(a.skipped?'Restore':'Skip'):'Remove'}</button>`}</div></div>`;
}
function renderSetupChecklist(next,permitted){const checks=[[!!st.source,'Connect a work-only calendar'],[!!st.shifts.length,'Review upcoming shift entries'],[!st.calendarInfo?.needsTime,'Choose a time for all-day shifts'],[permitted,'Allow exact alarms and notifications'],[st.settings.enabled,'Enable work alarms'],[!!next,'Confirm the next wake-up time']];$('setupSteps').innerHTML=checks.map(([ok,label])=>`<div class="setup-step"><span aria-hidden="true">${ok?'✓':'○'}</span><span>${esc(label)}</span></div>`).join('');$('setupChecklist').hidden=checks.every(([ok])=>ok);}
function renderHistory(){
 const history=st?.history||[],rows=historyExpanded?history:history.slice(0,6);
 const labels={scheduled:'Scheduled',removed:'Removed',rang:'Rang',snoozed:'Snoozed',dismissed:'Dismissed','timed out':'Timed out','backup alert':'Backup alert started','playback failed':'Playback failed',moved:'Wake-up moved',canceled:'Wake-up removed',added:'New wake-up'};
 $('historyList').innerHTML=rows.length?rows.map(row=>{const kind=row.kind==='shift'?'Work alarm':row.kind==='manual'?'Personal alarm':row.kind==='snooze'?'Snoozed alarm':'Alarm';const changed=row.to?(' → '+shortDate(row.to)+' '+time(row.to)):'';const detail=row.at?shortDate(row.at)+' '+time(row.at)+changed:row.to?shortDate(row.to)+' '+time(row.to):'';return `<div class="activity-row"><strong>${esc(labels[row.event]||row.event)} · ${esc(kind)}</strong><small>${esc(detail)}</small><small>Recorded ${esc(new Date(row.when).toLocaleString())}</small></div>`;}).join(''):'<div class="empty">No alarm activity recorded yet.</div>';
 $('historyMore').hidden=history.length<=6;$('historyMore').textContent=historyExpanded?'Show recent activity':'Show all '+history.length+' entries';
}
function renderSounds(){
 const query=$('soundSearch').value.trim().toLocaleLowerCase();
 const list=catalog.filter(a=>(category==='Gentle'?a.gentle:a.category===category)&&(a.title+' '+a.composer).toLocaleLowerCase().includes(query));
 $('soundList').innerHTML=list.length?list.map(a=>{const locked=/^orchestra_(0[5-9]|[12][0-9]|3[0-2])\.m4a$/.test(a.file)&&!st?.settings.soundPackInstalled;return `<div class="sound-row"><button class="play-button" data-preview="${a.file}" aria-label="${locked?'Download pack to preview':'Preview'} ${esc(a.title)}" ${locked?'disabled':''}>${uiIcon(playing===a.file?'stop':'play')}</button><div class="row-main"><strong>${esc(a.title)}</strong><p>${esc(a.composer)} · ${locked?'Orchestra pack':a.category!=='Standard'?Math.floor(a.duration/60)+':'+String(a.duration%60).padStart(2,'0'):'Looping tone'}</p></div><button class="select-sound ${st?.settings.sound===a.file?'selected':''}" data-sound="${a.file}" aria-pressed="${st?.settings.sound===a.file}" aria-label="${locked?'Download pack for':'Use'} ${esc(a.title)}" ${locked?'disabled':''}>${locked?'Get pack':st?.settings.sound===a.file?'Selected':'Use'}</button></div>`;}).join(''):'<div class="empty">No sounds match your search.</div>';
 $('soundNote').textContent=({Gentle:'16 softer choices · orchestra and solo piano · balanced levels and smooth loop joins. All offline.',Classical:'48 recorded music choices · 28 orchestra recordings need the optional pack.',Nature:'Ocean waves, soft rain and distant thunder. All included offline.',Standard:'From familiar beeps to gentle chimes. All included offline.'})[category];
 for(const [id,cat] of [['gentleTab','Gentle'],['classicalTab','Classical'],['natureTab','Nature'],['standardTab','Standard']]){$(id).classList.toggle('active',category===cat);$(id).setAttribute('aria-selected',category===cat);}
}
function stopPreview(){if(playing||wakePreviewing)send('stopPreview');playing='';wakePreviewing=false;$('previewWake').textContent='Preview the complete volume rise';renderSounds();}
function previewStopped(){playing='';wakePreviewing=false;$('previewWake').textContent='Preview the complete volume rise';renderSounds();}
function confirmAction(title,text,fn,label='Remove'){$('yesConfirm').textContent=label;$('confirmTitle').textContent=title;$('confirmText').textContent=text;confirmFn=fn;$('confirmDialog').showModal();}
document.querySelectorAll('nav button').forEach(el=>el.onclick=()=>page(el.dataset.page));

function downloadAge(ms){if(!ms)return 'No successful download yet';const minutes=Math.max(0,Math.floor((Date.now()-ms)/60000));return minutes<1?'Just now':minutes<60?minutes+' min ago':minutes<1440?Math.floor(minutes/60)+' hr ago':Math.floor(minutes/1440)+' days ago';}
function renderConfidence(){
 if(!st)return;const health=st.calendarHealth||{},status=health.status||(!st.source?'unlinked':st.linked?'never':'imported');
 const labels={fresh:'Calendar downloaded',failed:'Calendar refresh failed',stale:'Calendar download overdue',never:'Calendar not checked yet',imported:'Imported calendar',unlinked:'No calendar connected'};
 $('calendarConfidence').hidden=status==='unlinked';$('confidenceTitle').textContent=labels[status];$('confidenceTime').textContent=(status==='imported'?'Imported ':'Last download: ')+downloadAge(health.lastSuccess||st.lastSync)+(status==='imported'?' · Refresh by importing again':'');$('calendarConfidence').classList.toggle('needs-attention',['failed','stale','never'].includes(status));
 const detail=$('calendarHealthDetail');if(detail){detail.replaceChildren();const summary=document.createElement('p');summary.textContent=labels[status]+'. '+(status==='failed'?'Previously saved alarms stay scheduled.':status==='stale'?'No successful download in over 8 hours.':'');detail.append(summary);for(const [label,ms] of [[status==='imported'?'Imported':'Last successful download',health.lastSuccess||st.lastSync],['Last attempt',health.lastAttempt]])if(ms){const line=document.createElement('p');line.className='fine';line.textContent=label+': '+new Date(ms).toLocaleString();detail.append(line);}const caveat=document.createElement('p');caveat.className='fine';caveat.textContent=status==='imported'?'This is a snapshot. Import again after schedule changes.':'A download confirms receipt; your calendar service may publish changes later.';detail.append(caveat);}
 $('privateLockScreen').checked=st.settings.privateLockScreen!==false;$('syncAlerts').checked=health.alertsEnabled!==false;$('syncAlertStatus').textContent=!st.linked?'Applies to shared calendar links. Imported files do not refresh automatically.':health.alertsEnabled===false?'Sync notices are off. Status is still shown in the app.':!health.canNotify?'Allow Calendar sync notices in Android notification settings to receive them.':'Silent notices are enabled. No calendar link or shift details appear in them.';
 $('quietPresetStatus').textContent=st.settings.quietPreset?'Active':'Optional preset';$('useQuietPreset').textContent=st.settings.quietPreset?'Quiet wake-up is active':'Use Quiet wake-up';$('restorePlayback').hidden=!st.settings.canRestorePlayback;
}
$('calendarConfidence').onclick=()=>page('calendar');$('syncAlerts').onchange=event=>send('syncAlerts',{value:event.target.checked});$('privateLockScreen').onchange=event=>send('privateLockScreen',{value:event.target.checked});$('manageLinks').onclick=()=>page('calendar');$('viewHistory').onclick=()=>{page('today');$('historyList').scrollIntoView({behavior:'smooth',block:'center'});};
$('eveningCheck').onchange=()=>$('eveningTime').value&&send('eveningCheck',{value:$('eveningCheck').checked,minutes:timeMinutes($('eveningTime').value)});
$('eveningTime').onchange=()=>$('eveningTime').value&&send('eveningCheck',{value:$('eveningCheck').checked,minutes:timeMinutes($('eveningTime').value)});
$('useQuietPreset').onclick=()=>{presetPending='quiet';send('quietPreset');};$('restorePlayback').onclick=()=>{presetPending='restore';send('restorePlayback');};

$('viewAllShifts').onclick=()=>page('calendar');
$('refreshTop').onclick=()=>send('refresh');$('editLead').onclick=()=>{page('settings');$('lead').focus();};$('openSounds').onclick=()=>page('sounds');
$('enabled').onchange=e=>{const value=e.target.checked;e.target.checked=!!st?.settings.enabled;e.target.setAttribute('aria-checked',String(e.target.checked));e.target.disabled=true;send('enable',{value});};
$('connectForm').onsubmit=e=>{e.preventDefault();const url=$('calendarUrl').value.trim();if(!url)return;const connect=()=>{setBusy(true);send('connect',{url,name:$('calendarName').value,add:$('addCalendar').checked});};if(st?.source&&!$('addCalendar').checked)confirmAction('Replace first calendar link?','Petal Wake will test the new link before saving it. After confirming the new shifts, revoke the old link at your calendar service.',connect,'Test and replace');else connect();};
$('showTypedLink').onclick=()=>{const input=$('calendarUrl'),show=input.type==='password';input.type=show?'text':'password';$('showTypedLink').textContent=show?'Hide entered link':'Show entered link';$('showTypedLink').setAttribute('aria-pressed',String(show));};
$('addCalendar').onchange=()=>{$('connectButton').textContent=$('addCalendar').checked?'Add calendar':'Replace first calendar';$('replaceLinkNote').hidden=$('addCalendar').checked;};
function openCalendarRules(id,needsTime=false){
 const source=(st?.calendars||[]).find(c=>c.id===id);if(!source)return;
 $('calendarRuleId').value=source.id;$('calendarRuleName').value=source.name;$('calendarRuleFilter').value=source.filter||'';$('calendarRuleLead').value=source.lead;
 $('calendarRuleAllDay').checked=needsTime||source.allDayEnabled;
 $('calendarRuleTime').value=needsTime&&!source.allDayEnabled?'':minuteTime(source.allDayMinutes);
 $('calendarRuleHeading').textContent=needsTime?'Set an all-day shift start time':'Edit calendar';
 $('calendarRuleAllDayHelp').hidden=!needsTime;
 updateCalendarRuleTime();
 $('calendarRuleDialog').showModal();
 if(needsTime)$('calendarRuleTime').focus();
}
function updateCalendarRuleTime(){
 $('calendarRuleTime').disabled=!$('calendarRuleAllDay').checked;
 $('calendarRuleTime').required=$('calendarRuleAllDay').checked;
}
$('calendarRuleAllDay').onchange=updateCalendarRuleTime;
$('calendarSources').onclick=e=>{const edit=e.target.closest('[data-edit-calendar]'),remove=e.target.closest('[data-remove-calendar]'),copy=e.target.closest('[data-copy-calendar]');if(copy)send('copyCalendarLink',{id:copy.dataset.copyCalendar});if(edit)openCalendarRules(edit.dataset.editCalendar);if(remove){const source=st.calendars.find(c=>c.id===remove.dataset.removeCalendar);if(source)confirmAction('Remove '+source.name+'?','Its work alarms will be removed. Your other calendars and personal alarms stay scheduled.',()=>send('removeCalendar',{id:source.id}),'Remove');}};
$('calendarRuleForm').onsubmit=e=>{e.preventDefault();const source=(st?.calendars||[]).find(c=>c.id===$('calendarRuleId').value);const allDayEnabled=$('calendarRuleAllDay').checked;const minutes=allDayEnabled?timeMinutes($('calendarRuleTime').value):(source?.allDayMinutes??480);if(allDayEnabled&&(!Number.isInteger(minutes)||minutes<0||minutes>1439)){toast('Enter the actual all-day shift start time.');return;}send('updateCalendar',{id:$('calendarRuleId').value,name:$('calendarRuleName').value,filter:$('calendarRuleFilter').value,lead:+$('calendarRuleLead').value,allDayEnabled,allDayMinutes:minutes});dirty=false;$('calendarRuleDialog').close();};
$('importFile').onclick=()=>send('import');
$('gentleTab').onclick=()=>{stopPreview();category='Gentle';renderSounds();};$('natureTab').onclick=()=>{stopPreview();category='Nature';renderSounds();};$('classicalTab').onclick=()=>{stopPreview();category='Classical';renderSounds();};$('standardTab').onclick=()=>{stopPreview();category='Standard';renderSounds();};$('soundSearch').oninput=renderSounds;
$('soundList').onclick=e=>{const play=e.target.closest('[data-preview]'),use=e.target.closest('[data-sound]');if(play){const file=play.dataset.preview;if(file===playing)stopPreview();else{playing=file;send('preview',{file});renderSounds();}}if(use)send('sound',{file:use.dataset.sound});};
$('soundPackAction').onclick=()=>{if(st?.settings.soundPackInstalled)confirmAction('Remove orchestra pack?','Downloaded orchestra sounds will be unavailable until you download the pack again. Bundled sounds stay ready.',()=>send('removePack'),'Remove');else{setBusy(true);send('downloadPack');}};
for(const id of ['shiftList','manualList','calendarShiftList'])$(id).onclick=e=>{const setTime=e.target.closest('[data-set-all-day]');if(setTime){openCalendarRules(setTime.dataset.setAllDay,true);return;}const b=e.target.closest('[data-alarm-action]');if(b)send(b.dataset.alarmAction,{id:b.dataset.id});};
$('settingsForm').oninput=()=>{$('allDayTime').disabled=!$('allDayEnabled').checked;$('allDayTime').required=$('allDayEnabled').checked;dirty=true;$('unsaved').hidden=false;$('volumeValue').textContent=$('volume').value+'%';};
$('settingsForm').onsubmit=e=>{e.preventDefault();send('settings',{lead:+$('lead').value,filter:$('filter').value,snooze:+$('snooze').value,volume:+$('volume').value,fade:+$('fade').value,fallbackMinutes:+$('fallbackMinutes').value,vibrate:$('vibrate').checked,allDayEnabled:$('allDayEnabled').checked,allDayMinutes:timeMinutes($('allDayTime').value),shiftRulesEnabled:$('shiftRulesEnabled').checked,earlyBefore:timeMinutes($('earlyBefore').value),earlyLead:+$('earlyLead').value,lateAfter:timeMinutes($('lateAfter').value),lateLead:+$('lateLead').value,eveningCheck:$('eveningCheck').checked,eveningMinutes:timeMinutes($('eveningTime').value)});dirty=false;$('unsaved').hidden=true;};
$('previewWake').onclick=()=>{if(wakePreviewing){stopPreview();return;}stopPreview();wakePreviewing=true;$('previewWake').textContent='Stop volume preview';send('previewWake');};$('exportSettings').onclick=()=>send('exportSettings');$('importSettings').onclick=()=>send('importSettings');$('clearHistory').onclick=()=>confirmAction('Clear alarm activity?','This removes the local activity log.',()=>send('clearHistory'),'Clear');$('historyMore').onclick=()=>{historyExpanded=!historyExpanded;renderHistory();};
$('permissions').onclick=e=>{const b=e.target.closest('[data-permission]');if(b)send(b.dataset.permission);};$('testAlarm').onclick=()=>send('test');$('battery').onclick=()=>send('batterySettings');
$('addManual').onclick=()=>{const d=new Date(Date.now()+3600000);d.setSeconds(0,0);$('manualAt').value=new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16);$('manualDialog').showModal();};
$('manualForm').onsubmit=e=>{e.preventDefault();const at=new Date($('manualAt').value).getTime();if(!Number.isFinite(at)||at<=Date.now()){toast('Choose a future alarm time.');return;}send('manual',{title:$('manualTitle').value.trim()||'Personal alarm',at});$('manualDialog').close();};
document.querySelectorAll('.closeDialog').forEach(b=>b.onclick=()=>b.closest('dialog').close());
$('cancelConfirm').onclick=()=>$('confirmDialog').close();$('yesConfirm').onclick=()=>{$('confirmDialog').close();confirmFn?.();};
$('showCredits').onclick=()=>{$('creditsList').innerHTML=catalog.filter(a=>a.category!=='Standard').map(a=>`<div class="credit">${esc(a.title)} — ${esc(a.composer)}<small>${esc(a.source)}<br>${esc(a.performer||'')}<br>${esc(a.license)} · ${esc(a.arrangement)}</small></div>`).join('');$('creditsDialog').showModal();};
window.openPage=page;window.receive=receive;window.toast=toast;window.setBusy=setBusy;window.previewStopped=previewStopped;window.connectionSucceeded=connectionSucceeded;
send('state');renderSounds();
