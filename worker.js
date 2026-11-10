// Worker Cloudflare "koreksi-ljk" (ljk.pak-eng.net).
// Web dan APK diambil dari GitHub Release terbaru, jadi setiap rilis memperbarui keduanya sekaligus.
const REPO = 'https://github.com/EngKzl/koreksi-ljk-android';
const ORIGIN = 'https://ljk.pak-eng.net';
const FILES = {
  '/': ['index.html', 'text/html; charset=utf-8'],
  '/sw.js': ['sw.js', 'text/javascript; charset=utf-8'],
  '/manifest.webmanifest': ['manifest.webmanifest', 'application/manifest+json'],
  '/icon.svg': ['icon.svg', 'image/svg+xml'],
};

// Tag rilis terbaru (mis. "v1.0.9"), dari redirect github.com/.../releases/latest. Disimpan 2 menit;
// bila GitHub tidak terjangkau dipakai tag terakhir yang diketahui.
async function latestTag(ctx) {
  const cache = caches.default, kNow = new Request(ORIGIN + '/__tag'), kLast = new Request(ORIGIN + '/__tag_last');
  const hit = await cache.match(kNow);
  if (hit) return hit.text();
  try {
    const g = await fetch(REPO + '/releases/latest', { redirect: 'manual', headers: { 'user-agent': 'koreksi-ljk-worker' } });
    const m = (g.headers.get('location') || '').match(/\/releases\/tag\/(v[0-9.]+)$/);
    if (m) {
      ctx.waitUntil(cache.put(kNow, new Response(m[1], { headers: { 'cache-control': 'public, max-age=120' } })));
      ctx.waitUntil(cache.put(kLast, new Response(m[1], { headers: { 'cache-control': 'public, max-age=31536000' } })));
      return m[1];
    }
  } catch (e) { }
  const last = await cache.match(kLast);
  return last ? last.text() : null;
}

// Berkas web milik rilis [tag]; disimpan lama per tag, plus salinan "terakhir yang berhasil".
async function webFile(ctx, tag, name, type) {
  const cache = caches.default, key = new Request(`${ORIGIN}/__web/${tag}/${name}`), kLast = new Request(`${ORIGIN}/__web/last/${name}`);
  let r = tag && await cache.match(key);
  if (!r && tag) {
    try {
      const g = await fetch(`${REPO}/releases/download/${tag}/${name}`, { headers: { 'user-agent': 'koreksi-ljk-worker' } });
      if (g.ok) {
        const body = await g.arrayBuffer(), h = { 'content-type': type, 'cache-control': 'public, max-age=31536000' };
        ctx.waitUntil(cache.put(key, new Response(body, { headers: h })));
        ctx.waitUntil(cache.put(kLast, new Response(body, { headers: h })));
        r = new Response(body, { headers: h });
      }
    } catch (e) { }
  }
  return r || cache.match(kLast);
}

export default {
  async fetch(request, env, ctx) {
    const u = new URL(request.url), tag = await latestTag(ctx);
    if (u.pathname === '/api/android/latest') {
      const body = tag
        ? { versionCode: parseInt(tag.match(/(\d+)$/)[1]), versionName: tag.slice(1), apkUrl: `${REPO}/releases/download/${tag}/KoreksiLJK.apk`, notes: '', error: null }
        : { versionCode: 0, versionName: '', apkUrl: null, notes: '', error: 'belum ada rilis' };
      return new Response(JSON.stringify(body), { headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-cache', 'access-control-allow-origin': '*' } });
    }
    if (u.pathname === '/unduh' || u.pathname === '/unduh/') {
      if (tag) return Response.redirect(`${REPO}/releases/download/${tag}/KoreksiLJK.apk`, 302);
      return new Response('Aplikasi Android belum dirilis. Coba lagi nanti.', { status: 404, headers: { 'content-type': 'text/plain; charset=utf-8' } });
    }
    const [name, type] = FILES[u.pathname === '/index.html' ? '/' : u.pathname] || FILES['/'];
    const r = await webFile(ctx, tag, name, type);
    if (!r) return new Response('Koreksi LJK sedang tidak bisa dimuat. Coba lagi sebentar.', { status: 503, headers: { 'content-type': 'text/plain; charset=utf-8', 'retry-after': '30' } });
    return new Response(r.body, { headers: { 'content-type': type, 'cache-control': 'no-cache', 'x-content-type-options': 'nosniff', 'x-ljk-version': tag || 'cache' } });
  },
};
