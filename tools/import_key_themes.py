from pathlib import Path
import json,re
p=Path(__file__).resolve().parents[1]; a=p/'app/src/main'; palettes=json.loads((p/'tools/petal_key_palettes.json').read_text())['palettes']
f=a/'java/app/daybreak/Appearance.java';s=f.read_text();old=re.search(r'public static final Appearance\[\] ALL=\{(.*?)\};',s,re.S).group(1); ids='navy rose violet moonstone ocean forest sage lavender peach blush amber cocoa sand arctic plum berry twilight mint terracotta graphite'.split()
# Retain existing stable order/IDs, append new IDs.
entries=[];styles=[];catalog=[]
def luminance(h):
 v=[int(h[n:n+2],16)/255 for n in (1,3,5)]
 return sum((x/12.92 if x<=.04045 else ((x+.055)/1.055)**2.4)*w for x,w in zip(v,[.2126,.7152,.0722]))
for t in palettes:
 k=t['keyboard'];bg=k['surface'];primary=k['key'];secondary=k['function'];text=k['stripInk'];id=t['id'];name=t['name']
 entries.append(f'keyPalette("{id}",{json.dumps(name)},R.style.Key_{id},"{bg}","{primary}","{secondary}","{text}")')
 parent='AppTheme' if luminance(bg)<.179 else 'AppTheme.Light'
 styles.append(f'<style name="Key_{id}" parent="{parent}"><item name="android:windowBackground">{bg}</item><item name="android:colorBackground">{bg}</item><item name="android:textColorPrimary">{text}</item><item name="android:colorAccent">{primary}</item><item name="android:textColorSecondary">{text}</item></style>')
 catalog.append(dict(id=id,name=name,background=bg,primary=primary,secondary=secondary,preferredText=text))
s=re.sub(r'public static final Appearance\[\] ALL=\{.*?\};','public static final Appearance[] ALL={'+',\n        '.join(entries)+'};',s,flags=re.S)
f.write_text(s)
(a/'res/values/key_themes.xml').write_text('<resources>'+''.join(styles)+'</resources>')
f=a/'assets/appearance.js';original=json.loads(f.read_text().split('=',1)[1].rstrip(';\n'));original['themes']=catalog
f.write_text('window.APPEARANCE='+json.dumps(original,separators=(',',':'))+';\n')
