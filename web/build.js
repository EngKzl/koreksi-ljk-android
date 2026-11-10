// Membangun versi web ke web/dist. Pemakaian: node web/build.js <versi>
const fs = require('fs'), path = require('path');
const VER = process.argv[2] || 'dev';
const r = f => fs.readFileSync(path.join(__dirname, f), 'utf8');
const html = r('index.html').replace('/*__OMR__*/', () => r('omr.js')).replace('/*__XLSX__*/', () => r('xlsx.js')).replace('/*__APP__*/', () => r('app.js')).split('__VER__').join(VER);
const icon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><rect width="512" height="512" rx="96" fill="#0f5c4d"/><rect x="120" y="90" width="272" height="332" rx="18" fill="#fff"/><g fill="none" stroke="#0f5c4d" stroke-width="10"><rect x="160" y="150" width="44" height="44"/><rect x="234" y="150" width="44" height="44"/><rect x="308" y="150" width="44" height="44"/><rect x="160" y="230" width="44" height="44"/><rect x="234" y="230" width="44" height="44"/><rect x="308" y="230" width="44" height="44"/></g><path d="M240 156l32 32m0-32l-32 32M166 236l32 32m0-32l-32 32" stroke="#c8372d" stroke-width="10" stroke-linecap="round"/><path d="M170 340l50 45 100-95" fill="none" stroke="#1a8f4e" stroke-width="30" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
const manifest = JSON.stringify({ name: 'Koreksi LJK', short_name: 'Koreksi LJK', description: 'Koreksi lembar jawaban ujian dari foto/scan — bisa offline', start_url: '/', scope: '/', display: 'standalone', background_color: '#f3f5f4', theme_color: '#0f5c4d', lang: 'id', icons: [{ src: '/icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any maskable' }] });
// Halaman dilayani dari cache lalu diperbarui di latar; /api dan /unduh tidak disentuh.
const sw = `const C='ljk-${VER}';const A=['/','/manifest.webmanifest','/icon.svg'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(C).then(c=>c.addAll(A)).then(()=>self.skipWaiting()))});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k!==C).map(k=>caches.delete(k)))).then(()=>self.clients.claim()))});
self.addEventListener('fetch',e=>{const u=new URL(e.request.url);if(e.request.method!=='GET'||u.origin!==location.origin||u.pathname.startsWith('/api/')||u.pathname.startsWith('/unduh'))return;const key=e.request.mode==='navigate'?'/':u.pathname;
e.respondWith(caches.open(C).then(async c=>{const hit=await c.match(key);const net=fetch(e.request).then(r=>{if(r.ok&&A.includes(key))c.put(key,r.clone());return r}).catch(()=>hit);return hit||net}))});`;
const out = path.join(__dirname, 'dist');
fs.mkdirSync(out, { recursive: true });
fs.writeFileSync(path.join(out, 'index.html'), html); fs.writeFileSync(path.join(out, 'sw.js'), sw);
fs.writeFileSync(path.join(out, 'manifest.webmanifest'), manifest); fs.writeFileSync(path.join(out, 'icon.svg'), icon);
console.log('web', VER, html.length, 'byte');
