'use strict';
const uiPaths={
 alarm:'<circle cx="12" cy="13" r="8"/><path d="M12 9v4l3 2M5 3 2 6m17-3 3 3M6 20l-1 2m13-2 1 2"/>',
 calendar:'<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v4m10-4v4M3 10h18m-13 4h2m4 0h2m-8 3h2"/>',
 music:'<path d="M9 18V5l12-2v13M9 9l12-2"/><ellipse cx="6" cy="18" rx="3" ry="3"/><ellipse cx="18" cy="16" rx="3" ry="3"/>',
 settings:'<path d="M3 6h6m4 0h8M3 12h12m4 0h2M3 18h2m4 0h12"/><circle cx="11" cy="6" r="2"/><circle cx="17" cy="12" r="2"/><circle cx="7" cy="18" r="2"/>',
 refresh:'<path d="M20 7a9 9 0 1 0 1 8M20 3v5h-5"/>',
 chevron:'<path d="m9 5 7 7-7 7"/>',back:'<path d="m12 5-7 7 7 7m-7-7h16"/>',
 plus:'<path d="M12 5v14M5 12h14"/>',close:'<path d="m6 6 12 12M6 18 18 6"/>',
 search:'<circle cx="10.5" cy="10.5" r="7.5"/><path d="m16 16 5 5"/>',
 play:'<path d="m9 5 11 7-11 7z"/>',stop:'<rect x="6" y="6" width="12" height="12" rx="2"/>'
};
function uiIcon(name){return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'+(uiPaths[name]||'')+'</svg>';}
document.querySelectorAll('[data-ui-icon]').forEach(el=>el.innerHTML=uiIcon(el.dataset.uiIcon));
