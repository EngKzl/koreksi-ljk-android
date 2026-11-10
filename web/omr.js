/* ===== Mesin pembaca LJK sekolah (OMR) — port dari omr/Omr.kt, tanpa library ===== */
const OMR = (() => {
  // Tata letak LJK SMK PGRI 2 Kota Jambi (mm, A4): dua tabel 25 baris x 5 kolom (A–E)
  const L = {
    W: 210, H: 297, ROWS: 25, MAXQ: 50,
    xLines: [[33.30, 46.02, 58.76, 70.48, 81.16, 93.09], [123.97, 135.62, 147.56, 159.21, 171.14, 182.98]],
    yTop: [91.0, 90.8], yBot: [251.3, 251.3],
    nameBox: { x: 70, y: 59, w: 56, h: 22 },
  };
  const ansBox = (q, o) => {
    const t = Math.floor(q / L.ROWS), r = q % L.ROWS, rh = (L.yBot[t] - L.yTop[t]) / L.ROWS;
    return { x: L.xLines[t][o], y: L.yTop[t] + r * rh, w: L.xLines[t][o + 1] - L.xLines[t][o], h: rh };
  };
  const DEF = { tEmpty: 0.03, tMark: 0.055, tFull: 0.6, tStrike: 5.2 };

  function toGray(img) {
    const { data, width: w, height: h } = img, g = new Uint8Array(w * h);
    for (let i = 0, j = 0; i < g.length; i++, j += 4) g[i] = (data[j] * 77 + data[j + 1] * 150 + data[j + 2] * 29) >> 8;
    return { w, h, g };
  }
  function makeCtx(G, rDiv) {
    const { w, h, g } = G, W = w + 1, I = new Uint32Array(W * (h + 1));
    for (let y = 0; y < h; y++) { let s = 0; for (let x = 0; x < w; x++) { s += g[y * w + x]; I[(y + 1) * W + x + 1] = I[y * W + x + 1] + s; } }
    return { G, I, r: Math.max(8, Math.round(Math.max(w, h) / rDiv)) };
  }
  function dark(ctx, fx, fy) {
    const x = Math.round(fx), y = Math.round(fy), G = ctx.G;
    if (!(x >= 0 && y >= 0 && x < G.w && y < G.h)) return 0;
    const r = ctx.r, W = G.w + 1, I = ctx.I;
    const x0 = Math.max(0, x - r), y0 = Math.max(0, y - r), x1 = Math.min(G.w, x + r + 1), y1 = Math.min(G.h, y + r + 1);
    const m = (I[y1 * W + x1] - I[y0 * W + x1] - I[y1 * W + x0] + I[y0 * W + x0]) / ((x1 - x0) * (y1 - y0));
    const v = G.g[y * G.w + x];
    return (v < m * 0.78 && v < m - 22) ? 1 : 0;
  }
  // perkecil dengan nilai tergelap tiap blok agar garis tabel tipis tidak putus
  function minPool(G, maxDim) {
    const f = Math.max(1, Math.ceil(Math.max(G.w, G.h) / maxDim)), w = Math.floor(G.w / f), h = Math.floor(G.h / f), g = new Uint8Array(w * h);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      let m = 255;
      for (let j = 0; j < f; j++) for (let i = 0; i < f; i++) { const v = G.g[(y * f + j) * G.w + x * f + i]; if (v < m) m = v; }
      g[y * w + x] = m;
    }
    return { D: { w, h, g }, f };
  }

  function homography(src, dst) {
    const A = [], b = [];
    for (let i = 0; i < 4; i++) {
      const [x, y] = src[i], [u, v] = dst[i];
      A.push([x, y, 1, 0, 0, 0, -u * x, -u * y]); b.push(u);
      A.push([0, 0, 0, x, y, 1, -v * x, -v * y]); b.push(v);
    }
    for (let c = 0; c < 8; c++) {
      let p = c;
      for (let r = c + 1; r < 8; r++) if (Math.abs(A[r][c]) > Math.abs(A[p][c])) p = r;
      if (Math.abs(A[p][c]) < 1e-12) return null;
      [A[c], A[p]] = [A[p], A[c]]; [b[c], b[p]] = [b[p], b[c]];
      for (let r = 0; r < 8; r++) if (r !== c) { const f = A[r][c] / A[c][c]; for (let k = c; k < 8; k++) A[r][k] -= f * A[c][k]; b[r] -= f * b[c]; }
    }
    return b.map((v, i) => v / A[i][i]);
  }
  const apply = (H, x, y) => { const d = H[6] * x + H[7] * y + 1; return [(H[0] * x + H[1] * y + H[2]) / d, (H[3] * x + H[4] * y + H[5]) / d]; };

  // ---------- garis & segi empat tabel ----------
  const ldist = (l, p) => Math.abs(l.a * p[0] + l.b * p[1] - l.c);
  function lineThrough(p, q) {
    const dx = q[0] - p[0], dy = q[1] - p[1], n = Math.hypot(dx, dy);
    if (n < 1e-9) return null;
    const a = -dy / n, b = dx / n; return { a, b, c: a * p[0] + b * p[1] };
  }
  function fitLine(pts, tol) {
    const n = pts.length; if (n < 8) return null;
    let best = null, bestCnt = 0;
    for (const k of [n / 2, n / 3, n / 4, n / 6].map(Math.floor)) {
      if (k < 2) continue;
      const step = Math.max(1, Math.floor(n / 40));
      for (let i = 0; i + k < n; i += step) {
        const l = lineThrough(pts[i], pts[i + k]); if (!l) continue;
        let c = 0; for (const p of pts) if (ldist(l, p) <= tol) c++;
        if (c > bestCnt) { bestCnt = c; best = l; }
      }
    }
    if (!best || bestCnt < n * 0.3) return null;
    const inl = pts.filter(p => ldist(best, p) <= tol);
    let mx = 0, my = 0; for (const p of inl) { mx += p[0]; my += p[1]; } mx /= inl.length; my /= inl.length;
    let sxx = 0, sxy = 0, syy = 0; for (const p of inl) { const dx = p[0] - mx, dy = p[1] - my; sxx += dx * dx; sxy += dx * dy; syy += dy * dy; }
    const th = 0.5 * Math.atan2(2 * sxy, sxx - syy), a = -Math.sin(th), b = Math.cos(th);
    return { a, b, c: a * mx + b * my };
  }
  function intersect(l, m) {
    const d = l.a * m.b - m.a * l.b; if (Math.abs(d) < 1e-9) return null;
    return [(l.c * m.b - m.c * l.b) / d, (l.a * m.c - m.a * l.c) / d];
  }
  function makeQuad(pts) {
    const len = (i, j) => Math.hypot(pts[i][0] - pts[j][0], pts[i][1] - pts[j][1]);
    const s0 = (len(0, 1) + len(2, 3)) / 2, s1 = (len(1, 2) + len(3, 0)) / 2;
    const [i, j] = s0 >= s1 ? [0, 1] : [1, 2], n = len(i, j);
    return { pts, cx: (pts[0][0] + pts[1][0] + pts[2][0] + pts[3][0]) / 4, cy: (pts[0][1] + pts[1][1] + pts[2][1] + pts[3][1]) / 4,
      long: Math.max(s0, s1), short: Math.min(s0, s1), u: [(pts[j][0] - pts[i][0]) / n, (pts[j][1] - pts[i][1]) / n] };
  }
  function findTables(D, ctx) {
    const { w, h } = D, n = w * h, bin = new Uint8Array(n);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) bin[y * w + x] = dark(ctx, x, y);
    const lab = new Int32Array(n), stack = new Int32Array(n), out = [];
    let id = 0;
    for (let s = 0; s < n; s++) {
      if (!bin[s] || lab[s]) continue;
      id++; let sp = 0; stack[sp++] = s; lab[s] = id;
      let cnt = 0, x0 = w, x1 = 0, y0 = h, y1 = 0;
      while (sp) {
        const p = stack[--sp], px = p % w, py = (p - px) / w; cnt++;
        if (px < x0) x0 = px; if (px > x1) x1 = px; if (py < y0) y0 = py; if (py > y1) y1 = py;
        for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
          const qx = px + dx, qy = py + dy; if (qx < 0 || qy < 0 || qx >= w || qy >= h) continue;
          const q = qy * w + qx; if (bin[q] && !lab[q]) { lab[q] = id; stack[sp++] = q; }
        }
      }
      const bw = x1 - x0 + 1, bh = y1 - y0 + 1;
      if (bw * bh < 0.015 * n || cnt > 0.5 * bw * bh || Math.min(bw, bh) < 30) continue;
      const left = [], right = [], top = [], bot = [];
      for (let y = y0; y <= y1; y++) { let a = -1, b = -1; for (let x = x0; x <= x1; x++) if (lab[y * w + x] === id) { if (a < 0) a = x; b = x; } if (a >= 0) { left.push([a, y]); right.push([b, y]); } }
      for (let x = x0; x <= x1; x++) { let a = -1, b = -1; for (let y = y0; y <= y1; y++) if (lab[y * w + x] === id) { if (a < 0) a = y; b = y; } if (a >= 0) { top.push([x, a]); bot.push([x, b]); } }
      const tol = 2, [pa, pb, qa, qb] = bh >= bw ? [left, right, top, bot] : [top, bot, left, right];
      const la = fitLine(pa, tol), lb = la && fitLine(pb, tol); if (!lb) continue;
      const rest = pts => pts.filter(p => ldist(la, p) > 2 * tol && ldist(lb, p) > 2 * tol);
      const lc = fitLine(rest(qa), tol), ld = lc && fitLine(rest(qb), tol); if (!ld) continue;
      const c = [intersect(la, lc), intersect(lc, lb), intersect(lb, ld), intersect(ld, la)];
      if (c.some(p => !p)) continue;
      const q = makeQuad(c);
      if (q.short < 20 || q.long / q.short < 1.8 || q.long / q.short > 4.2) continue;
      out.push(q);
    }
    return out;
  }
  function regionDark(ctx, H, R, nx, ny) {
    let c = 0;
    for (let j = 0; j < ny; j++) for (let i = 0; i < nx; i++) { const p = apply(H, R.x + (i + 0.5) * R.w / nx, R.y + (j + 0.5) * R.h / ny); c += dark(ctx, p[0], p[1]); }
    return c / (nx * ny);
  }
  function label(q, v, u) {
    const out = [null, null, null, null];
    for (const p of q.pts) {
      const dv = (p[0] - q.cx) * v[0] + (p[1] - q.cy) * v[1], du = (p[0] - q.cx) * u[0] + (p[1] - q.cy) * u[1];
      const i = du < 0 ? (dv < 0 ? 0 : 1) : (dv < 0 ? 3 : 2);
      if (out[i]) return null; out[i] = p;
    }
    return out;
  }
  const canon = t => [[L.xLines[t][0], L.yTop[t]], [L.xLines[t][5], L.yTop[t]], [L.xLines[t][5], L.yBot[t]], [L.xLines[t][0], L.yBot[t]]];
  function locate(full) {
    const { D, f } = minPool(full.G, 1000), quads = findTables(D, makeCtx(D, 16));
    let best = null, bestA = 0;
    for (let i = 0; i < quads.length; i++) for (let j = i + 1; j < quads.length; j++) {
      const a = quads[i], b = quads[j], r = a.long / b.long;
      if (r < 0.75 || r > 1.33 || Math.abs(a.u[0] * b.u[0] + a.u[1] * b.u[1]) < 0.95) continue;
      const dx = b.cx - a.cx, dy = b.cy - a.cy, along = Math.abs(dx * a.u[0] + dy * a.u[1]), across = Math.abs(-dx * a.u[1] + dy * a.u[0]), sh = (a.short + b.short) / 2;
      if (across < 1.1 * sh || across > 2.3 * sh || along > 0.25 * a.long) continue;
      const area = a.long * a.short + b.long * b.short;
      if (area > bestA) { bestA = area; best = [a, b]; }
    }
    if (!best) return null;
    const [a, b] = best;
    let vx = -a.u[1], vy = a.u[0];
    if (vx * (b.cx - a.cx) + vy * (b.cy - a.cy) < 0) { vx = -vx; vy = -vy; }
    const la = label(a, [vx, vy], [-vy, vx]), lb = label(b, [vx, vy], [-vy, vx]);
    if (!la || !lb) return null;
    const up = c => c.map(p => [p[0] * f + f / 2, p[1] * f + f / 2]), flip = c => [c[2], c[3], c[0], c[1]];
    const c0 = canon(0), c1 = canon(1), th = L.yBot[0] - L.yTop[0];
    let res = null, bestS = -1;
    // tegak / terbalik 180°: pilih yang kop & nomor soalnya berisi tinta
    for (const [l, r] of [[up(la), up(lb)], [flip(up(lb)), flip(up(la))]]) {
      const hp = homography([c0[0], c1[1], c1[2], c0[3]], [l[0], r[1], r[2], l[3]]); if (!hp) continue;
      const s = regionDark(full, hp, { x: 30, y: 53, w: 150, h: 31 }, 80, 30) +
        regionDark(full, hp, { x: L.xLines[0][0] - 9, y: L.yTop[0], w: 7, h: th }, 12, 150) +
        regionDark(full, hp, { x: L.xLines[1][0] - 9, y: L.yTop[1], w: 7, h: th }, 12, 150);
      if (s > bestS) { bestS = s; res = { page: hp, tables: [l, r] }; }
    }
    return res;
  }

  // ---------- garis kisi ----------
  function gridLines(ctx, H, expected, vertical, win) {
    const lo = -win - 0.01, hi = 1 + win + 0.01, bins = vertical ? 700 : 1400, m = vertical ? 150 : 60, prof = new Float64Array(bins);
    for (let k = 0; k < bins; k++) {
      const a = lo + (k + 0.5) * (hi - lo) / bins; let c = 0;
      for (let j = 0; j < m; j++) { const t = 0.01 + (j + 0.5) * 0.98 / m, p = vertical ? apply(H, a, t) : apply(H, t, a); c += dark(ctx, p[0], p[1]); }
      prof[k] = c / m;
    }
    const sm = k => prof[k] + (k > 0 ? prof[k - 1] : 0) + (k < bins - 1 ? prof[k + 1] : 0);
    const clamp = k => Math.min(bins - 1, Math.max(0, Math.trunc(k)));
    const off = expected.map(e => {
      const k0 = clamp((e - win - lo) / (hi - lo) * bins), k1 = clamp((e + win - lo) / (hi - lo) * bins);
      let bk = k0; for (let k = k0; k <= k1; k++) if (sm(k) > sm(bk)) bk = k;
      return sm(bk) >= 0.5 ? lo + (bk + 0.5) * (hi - lo) / bins - e : null;
    });
    if (off.filter(o => o !== null).length < expected.length * 0.6) return null;
    return expected.map((e, i) => { let d = 0, o = off[i]; while (o === null || o === undefined) { d++; o = off[i - d] ?? off[i + d]; } return e + o; });
  }

  // ---------- analisis kotak ----------
  const NX = 60, NY = 30;
  function rawCell(ctx, H, u0, u1, v0, v1) {
    const du = (u1 - u0) * 0.08, dv = (v1 - v0) * 0.15, m = new Uint8Array(NX * NY); let cnt = 0;
    for (let j = 0; j < NY; j++) for (let i = 0; i < NX; i++) {
      const p = apply(H, u0 + du + (i + 0.5) * (u1 - u0 - 2 * du) / NX, v0 + dv + (j + 0.5) * (v1 - v0 - 2 * dv) / NY);
      const d = dark(ctx, p[0], p[1]); m[j * NX + i] = d; cnt += d;
    }
    return { m, fill: cnt / (NX * NY) };
  }
  // huruf cetak satu kolom = piksel yang gelap di banyak baris, dilebarkan 1 piksel
  function letterMask(col) {
    const core = new Uint8Array(NX * NY), out = new Uint8Array(NX * NY);
    for (let i = 0; i < NX * NY; i++) { let c = 0; for (const r of col) c += r.m[i]; core[i] = c >= col.length * 0.4 ? 1 : 0; }
    for (let y = 0; y < NY; y++) for (let x = 0; x < NX; x++) {
      let d = 0;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) { const xx = x + dx, yy = y + dy; if (xx >= 0 && xx < NX && yy >= 0 && yy < NY && core[yy * NX + xx]) d = 1; }
      out[y * NX + x] = d;
    }
    return out;
  }
  // garis pembatalan: garis mendatar terpanjang (boleh sedikit miring) dibagi tebal goresan
  function strikeLen(m, letter) {
    const th = [];
    for (let y = 0; y < NY; y++) for (let x = 0; x < NX; x++) {
      const i = y * NX + x; if (!m[i] || letter[i]) continue;
      let a = x; while (a > 0 && m[y * NX + a - 1]) a--;
      let b = x; while (b < NX - 1 && m[y * NX + b + 1]) b++;
      let c = y; while (c > 0 && m[(c - 1) * NX + x]) c--;
      let d = y; while (d < NY - 1 && m[(d + 1) * NX + x]) d++;
      th.push(Math.min(b - a + 1, d - c + 1));
    }
    const ink = th.length; if (ink < 8) return 0;
    th.sort((p, q) => p - q);
    const t = th[Math.floor(ink / 2)];
    let bestV = 0;
    for (let s = -6; s <= 6; s++) {
      const slope = s * 0.05;
      for (let y0 = 0; y0 < NY; y0++) {
        let best = 0, cur = 0, gap = 0;
        for (let x = 0; x < NX; x++) {
          const y = Math.round(y0 + slope * (x - NX / 2));
          if (y < 0 || y >= NY) { cur = 0; gap = 0; continue; }
          const i = y * NX + x; if (letter[i]) continue;
          if (m[i]) { cur += 1 + gap; gap = 0; if (cur > best) best = cur; }
          else if (cur > 0 && gap < 1) gap++;
          else { cur = 0; gap = 0; }
        }
        const v = best / t; if (v <= bestV) continue;
        // harus ada tinta di atas DAN di bawah garis (busur lingkaran hanya satu sisi)
        let above = 0, below = 0;
        for (let x = 0; x < NX; x++) {
          const yl = Math.round(y0 + slope * (x - NX / 2));
          for (let y = 0; y < NY; y++) { const i = y * NX + x; if (!m[i] || letter[i]) continue; if (y < yl - 1) above++; else if (y > yl + 2) below++; }
        }
        if (Math.min(above, below) >= 0.15 * ink) bestV = v;
      }
    }
    return bestV;
  }
  function decide(cells, labels) {
    const xs = [], tn = [], co = [];
    cells.forEach((c, i) => { if (c.st === 'x') xs.push(i); else if (c.st === 'tanda') tn.push(i); else if (c.st === 'coret') co.push(i); });
    let choice = null, status, note = '';
    if (xs.length === 1) { choice = xs[0]; status = tn.length ? 'ragu' : 'ok'; if (co.length) note = 'ada coretan, dipakai tanda yang tidak dicoret'; if (tn.length) note = 'ada tanda lain'; }
    else if (xs.length > 1) { status = 'ganda'; note = 'tanda di ' + xs.map(i => labels[i]).join(','); }
    else if (tn.length === 1) { choice = tn[0]; status = 'ragu'; note = 'tanda kurang tegas' + (co.length ? ', ada coretan' : ''); }
    else if (tn.length > 1) { status = 'ganda'; note = 'tanda di ' + tn.map(i => labels[i]).join(','); }
    else { status = 'kosong'; if (co.length) note = 'semua dicoret/dibatalkan'; }
    return { choice: choice === null ? null : labels[choice], status, note, marks: cells.map(c => c.st), cells };
  }

  function readSheet(img, opt) {
    const th = Object.assign({}, DEF, opt.th || {}), G = img.g ? img : toGray(img), ctx = makeCtx(G, 11), n = opt.n;
    const loc = locate(ctx);
    if (!loc) return { ok: false, error: 'Tabel jawaban tidak ditemukan. Pastikan kedua tabel (1–25 dan 26–50) terlihat utuh, lembar rata, dan cahaya cukup.' };
    const LET = ['A', 'B', 'C', 'D', 'E'], answers = [];
    for (let t = 0; t < 2 && t * L.ROWS < n; t++) {
      const ht = homography([[0, 0], [1, 0], [1, 1], [0, 1]], loc.tables[t]);
      if (!ht) return { ok: false, error: 'Tabel jawaban tidak terbaca.' };
      const x0 = L.xLines[t], ex = x0.map(x => (x - x0[0]) / (x0[5] - x0[0])), ey = [...Array(L.ROWS + 1)].map((_, i) => i / L.ROWS);
      const xl = gridLines(ctx, ht, ex, true, 0.045), yl = gridLines(ctx, ht, ey, false, 0.012);
      if (!xl || !yl) return { ok: false, error: 'Garis tabel jawaban tidak terbaca jelas. Foto ulang lebih dekat, tegak lurus, dan tidak buram.' };
      const raw = [...Array(L.ROWS)].map((_, r) => [0, 1, 2, 3, 4].map(o => rawCell(ctx, ht, xl[o], xl[o + 1], yl[r], yl[r + 1])));
      const base = [0, 1, 2, 3, 4].map(o => raw.map(r => r[o].fill).sort((a, b) => a - b)[Math.floor(L.ROWS / 4)]);
      const letter = [0, 1, 2, 3, 4].map(o => letterMask(raw.map(r => r[o])));
      for (let r = 0; r < L.ROWS && t * L.ROWS + r < n; r++) {
        answers.push(decide([0, 1, 2, 3, 4].map(o => {
          const c = raw[r][o], extra = c.fill - base[o], strike = strikeLen(c.m, letter[o]);
          const st = (c.fill >= th.tFull || strike >= th.tStrike) ? 'coret' : extra >= th.tMark ? 'x' : extra >= th.tEmpty ? 'tanda' : 'kosong';
          return { st, extra, strike };
        }), LET));
      }
    }
    return { ok: true, H: loc.page, answers };
  }

  // render lembar lurus (untuk tinjauan) ke canvas
  function renderWarp(srcCanvas, H, scale) {
    const sw = srcCanvas.width, sh = srcCanvas.height, sd = srcCanvas.getContext('2d').getImageData(0, 0, sw, sh).data;
    const W = Math.round(L.W * scale), Hh = Math.round(L.H * scale), c = document.createElement('canvas'); c.width = W; c.height = Hh;
    const cx = c.getContext('2d'), out = cx.createImageData(W, Hh), od = out.data;
    for (let y = 0; y < Hh; y++) for (let x = 0; x < W; x++) {
      const [u, v] = apply(H, (x + 0.5) / scale, (y + 0.5) / scale), xi = u | 0, yi = v | 0, o = (y * W + x) * 4;
      if (u >= 0 && v >= 0 && xi < sw && yi < sh) { const s = (yi * sw + xi) * 4; od[o] = sd[s]; od[o + 1] = sd[s + 1]; od[o + 2] = sd[s + 2]; }
      else od[o] = od[o + 1] = od[o + 2] = 200;
      od[o + 3] = 255;
    }
    cx.putImageData(out, 0, 0);
    return c;
  }
  return { L, ansBox, readSheet, renderWarp, DEF };
})();
if (typeof module !== 'undefined') module.exports = OMR;
