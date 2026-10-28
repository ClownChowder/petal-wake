// DOM contract test: no browser rendering or Android persistence is claimed.
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const assets=__dirname+'/../app/src/main/assets/';
function fresh(){
 const elements=new Map(),tokens={}; const get=id=>{if(!elements.has(id))elements.set(id,{value:'',innerHTML:'',textContent:'',style:{},dataset:{},setAttribute(){},querySelector(){return {style:{}}}});return elements.get(id)};
 const c=vm.createContext({window:{},location:{hash:'#rose/petal'},document:{getElementById:get,querySelectorAll:()=>[],documentElement:{dataset:{},style:{setProperty:(k,v)=>tokens[k]=v,removeProperty:k=>delete tokens[k]}}}});
 for(const f of ['appearance.js','icon-templates.js','custom-themes.js'])vm.runInContext(fs.readFileSync(assets+f,'utf8'),c);
 const app=fs.readFileSync(assets+'app.js','utf8');vm.runInContext(app.slice(0,app.indexOf('renderAppearanceOptions();applyAppearance')),c);
 return {c,elements,tokens,get};
}
let x=fresh(); const all=x.c.window.APPEARANCE.themes;assert.equal(all.length,19);
for(const t of all){vm.runInContext(`applyAppearance('${t.id}','petal')`,x.c);assert.equal(x.get('themeSelect').value,t.id);assert.equal(x.tokens['--paper'],t.background);assert.equal(x.get('appearanceName').textContent,t.name);assert(x.get('brandMark').src.startsWith('data:image/svg+xml'));assert.equal((x.get('themeSelect').innerHTML.match(/<option /g)||[]).length,19);
 for(const shape of x.c.window.APPEARANCE.icons){const icon=vm.runInContext(`customIconUrl(appearanceCatalog.themes.find(t=>t.id==='${t.id}'),'${shape.id}')`,x.c);if(shape.id.startsWith('rouse_'))assert.equal(icon,`icons/fourwags_${shape.id}.svg`);else assert(!decodeURIComponent(icon).includes('{{'));}
 const reopened=fresh();vm.runInContext(`applyAppearance('${t.id}','petal')`,reopened.c);assert.equal(reopened.get('themeSelect').value,t.id);assert.deepEqual(reopened.tokens,x.tokens);
}
console.log('PASS: 19 palette switches, 19 fresh-context restores from selected ID, dropdown labels/options, token resets, 171 icon previews including fixed Rouse designs. Android relaunch persistence and visual rendering not tested.');
