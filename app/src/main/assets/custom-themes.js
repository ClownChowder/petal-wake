'use strict';
let editingCustomId='';
const colorParts=['Background','Primary','Secondary','Text'];
function colorHex(value){if(!/^#[0-9a-f]{6}$/i.test(value))throw new Error('Use a six-digit color, such as #D89AAA.');return value.toUpperCase();}
function colorMix(a,b,f){return '#'+[1,3,5].map(i=>Math.round(parseInt(a.slice(i,i+2),16)*(1-f)+parseInt(b.slice(i,i+2),16)*f).toString(16).padStart(2,'0')).join('').toUpperCase();}
function colorLuminance(c){return [1,3,5].map(i=>parseInt(c.slice(i,i+2),16)/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((s,v,n)=>s+v*[.2126,.7152,.0722][n],0);}
function colorContrast(a,b){const x=colorLuminance(a),y=colorLuminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
function colorBest(bg){return colorContrast('#FFFFFF',bg)>=colorContrast('#000000',bg)?'#FFFFFF':'#000000';}
function colorReadable(preferred,a,b,fallback){if(colorContrast(preferred,a)>=4.5&&colorContrast(preferred,b)>=4.5)return preferred;for(let n=1;n<=100;n++){const c=colorMix(preferred,fallback,n/100);if(colorContrast(c,a)>=4.5&&colorContrast(c,b)>=4.5)return c;}return fallback;}
function colorSurface(base,text){for(let f=.07;f>0;f-=.01){const c=colorMix(base,text,f);if(colorContrast(text,c)>=4.5)return c;}return base;}
function customColorMath(background,primary,secondary,preferredText){
 [background,primary,secondary,preferredText]=[background,primary,secondary,preferredText].map(colorHex);
 const dark=colorLuminance(background)<.179;
 const text=colorReadable(preferredText,background,background,colorBest(background));
 const surface=dark?colorMix(background,'#000000',.12):colorMix(background,'#FFFFFF',.42);
 const control=colorSurface(surface,text);
 const link=colorReadable(primary,background,control,text),secondaryLink=colorReadable(secondary,background,control,text),muted=colorReadable(colorMix(text,background,.22),background,control,text),onAction=colorBest(primary);
 const tokens={paper:background,ink:text,muted,lavender:link,mint:secondaryLink,card:surface,control,line:colorMix(background,text,.25),placeholder:muted,outline:muted,horizon:colorMix(surface,secondary,.12),track:colorMix(background,text,.25),secondary:colorMix(surface,secondary,.06),'input-line':muted,input:colorMix(background,surface,.4),action:primary,'on-action':onAction,nav:background+'F5',glass:background+'33','glass-card':background+'8C',empty:surface+'55',overlay:'#14121A99','error-bg':dark?'#51343C':'#FBE1DD','error-text':dark?'#FFD0C7':'#7B332C'};
 return {custom:true,background,primary,secondary,preferredText,text,surface,action:primary,onAction,accent:link,dark,tokens};
}
function customIconSvg(theme,shape){
 const colors={background:theme.background,primary:theme.primary,secondary:theme.secondary,text:theme.text,surface:theme.surface,soft:colorMix(theme.text,theme.primary,.22),onAction:theme.onAction};
 return window.ICON_TEMPLATES[shape].replace(/\{\{(\w+)\}\}/g,(_,key)=>colors[key]);
}
function customIconUrl(theme,shape){return 'data:image/svg+xml;charset=utf-8,'+encodeURIComponent(customIconSvg(theme,shape));}
function renderCustomThemes(){
 $('customThemesEmpty').hidden=customThemes.length>0;
 $('customThemeList').innerHTML=customThemes.map(theme=>`<div class="custom-theme-row" aria-current="${theme.id===currentTheme}"><img src="${customIconUrl(theme,currentIcon)}" alt=""><strong class="custom-name">${esc(theme.name)}${theme.id===currentTheme?' · Selected':''}</strong><div class="custom-actions"><button type="button" class="text-button" data-custom-use="${theme.id}">${theme.id===currentTheme?'Selected':'Use theme'}</button><button type="button" class="text-button" data-custom-edit="${theme.id}">Edit</button><button type="button" class="text-button" data-custom-delete="${theme.id}">Remove</button></div></div>`).join('');
}
function openThemeEditor(id=''){
 const selected=[...appearanceCatalog.themes,...customThemes].find(t=>t.id===(id||currentTheme))||appearanceCatalog.themes[1];editingCustomId=id;
 const values=selected.tokens?[selected.background,selected.primary,selected.secondary,selected.preferredText]:[selected.background,selected.action,selected.icon.secondary,selected.text];
 $('customThemeName').value=id?selected.name:'My theme';colorParts.forEach((part,n)=>{$('color'+part).value=values[n];$('hex'+part).value=values[n].toUpperCase();});
 $('editorHeading').textContent=id?'Edit theme':'Create theme';$('saveCustomTheme').textContent=id?'Save changes & use':'Save & use theme';page('themeEditor');updateThemePreview();
}
function draftTheme(){return {...customColorMath(...colorParts.map(part=>$('hex'+part).value)),name:$('customThemeName').value.trim()||'My theme'};}
function updateThemePreview(){
 try{const theme=draftTheme();$('editorIcon').src=customIconUrl(theme,currentIcon);$('editorPreviewName').textContent=theme.name;
 const panel=$('customThemePreview');panel.style.background=theme.background;panel.style.color=theme.text;
 const snooze=panel.querySelector('.editor-alarm span'),awake=panel.querySelector('.editor-alarm strong');snooze.style.background=theme.surface;snooze.style.color=theme.text;awake.style.background=theme.primary;awake.style.color=theme.onAction;
 $('customContrastNote').textContent=theme.text!==theme.preferredText?`Text adjusted to ${theme.text} for readability.`:'Your text color has readable contrast.';
 }catch(e){$('customContrastNote').textContent=e.message;}
}
function setupCustomThemes(){
 $('newCustomTheme').onclick=()=>openThemeEditor();$('editorBack').onclick=$('cancelCustomTheme').onclick=()=>page('appearance');
 colorParts.forEach(part=>{$('color'+part).oninput=()=>{$('hex'+part).value=$('color'+part).value.toUpperCase();updateThemePreview();};$('hex'+part).oninput=()=>{if(/^#[0-9a-f]{6}$/i.test($('hex'+part).value))$('color'+part).value=$('hex'+part).value;updateThemePreview();};});$('customThemeName').oninput=updateThemePreview;
 $('customThemeForm').onsubmit=event=>{event.preventDefault();try{const theme=draftTheme();if(!$('customThemeName').value.trim())return;send('saveCustomTheme',{theme:{id:editingCustomId,name:$('customThemeName').value.trim(),background:theme.background,primary:theme.primary,secondary:theme.secondary,preferredText:theme.preferredText}});}catch(e){toast(e.message);}};
 $('customThemeList').onclick=event=>{const use=event.target.closest('[data-custom-use]'),edit=event.target.closest('[data-custom-edit]'),remove=event.target.closest('[data-custom-delete]');if(use)send('appearance',{profile:use.dataset.customUse});if(edit)openThemeEditor(edit.dataset.customEdit);if(remove){const id=remove.dataset.customDelete,theme=customThemes.find(t=>t.id===id);confirmAction('Remove custom theme?',`Remove “${theme.name}”? If it is selected, Rose Porcelain will be selected instead.`,()=>send('deleteCustomTheme',{id}));$('yesConfirm').textContent='Remove';}};
 window.customThemeSaved=()=>page('appearance');
}
