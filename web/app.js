/* ===== Aplikasi Koreksi LJK (web) ===== */
const APP_VER = '__VER__';
const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];
const LET = 'ABCDE', TINGKAT = ['X', 'XI', 'XII'], JURUSAN = ['TSM', 'TKR', 'DKV', 'MP', 'AK', 'BR'];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
const sleep = ms => new Promise(r => setTimeout(r, ms));
function toast(msg, ms = 2600) { const t = $('#toast'); t.textContent = msg; t.classList.add('on'); clearTimeout(toast._t); toast._t = setTimeout(() => t.classList.remove('on'), ms); }

/* ---------- Penyimpanan (IndexedDB) ---------- */
const DB = (() => {
  let db;
  const open = () => new Promise((res, rej) => {
    if (db) return res(db);
    const r = indexedDB.open('koreksi-ljk', 1);
    r.onupgradeneeded = () => { const d = r.result; d.createObjectStore('exams', { keyPath: 'id' }); d.createObjectStore('results', { keyPath: 'id' }).createIndex('examId', 'examId'); };
    r.onsuccess = () => { db = r.result; res(db); }; r.onerror = () => rej(r.error);
  });
  const tx = async (store, mode, fn) => {
    const d = await open();
    return new Promise((res, rej) => { const t = d.transaction(store, mode); const q = fn(t.objectStore(store)); t.oncomplete = () => res(q ? q.result : undefined); t.onerror = () => rej(t.error); });
  };
  return {
    all: s => tx(s, 'readonly', st => st.getAll()),
    put: (s, v) => tx(s, 'readwrite', st => st.put(v)),
    del: (s, k) => tx(s, 'readwrite', st => st.delete(k)),
    byExam: id => tx('results', 'readonly', st => st.index('examId').getAll(id)),
  };
})();

/* ---------- Pengaturan ---------- */
const SET = Object.assign({ kelas: 'X TSM 1', th: { ...OMR.DEF } }, JSON.parse(localStorage.getItem('ljk-set2') || '{}'));
const saveSet = () => localStorage.setItem('ljk-set2', JSON.stringify(SET));

/* ---------- State ---------- */
let EXAMS = [], ACTIVE = localStorage.getItem('ljk-active') || null, PAGE = 'ujian', MODE = 'siswa', RKELAS = null;
const ex = () => EXAMS.find(e => e.id === ACTIVE) || null;
async function loadExams() {
  EXAMS = (await DB.all('exams')).sort((a, b) => b.created - a.created);
  if (!ex()) ACTIVE = EXAMS[0]?.id || null;
  localStorage.setItem('ljk-active', ACTIVE || '');
  $('#exSel').innerHTML = EXAMS.length ? EXAMS.map(e => `<option value="${e.id}" ${e.id === ACTIVE ? 'selected' : ''}>${esc(e.nama)} · ${esc(e.mapel)}</option>`).join('') : '<option>(belum ada ujian)</option>';
}

/* ---------- Penilaian ---------- */
const keyOf = (E, p) => (E.kunci?.[p || 'A'] || '');
function finalAns(r, q) {
  if (r.ovr && q in r.ovr) { const o = r.ovr[q]; return { ch: o || null, st: o ? 'ok' : 'kosong', ovr: true }; }
  const a = r.ans[q] || { c: null, s: 'kosong' };
  return { ch: a.c, st: a.s, ovr: false };
}
function score(E, r) {
  const key = keyOf(E, r.paket);
  let benar = 0, salah = 0, kosong = 0, ganda = 0, ragu = 0, tot = 0;
  for (let q = 0; q < E.n; q++) {
    const f = finalAns(r, q), k = key[q];
    if (f.st === 'ganda') ganda++; if (f.st === 'ragu') ragu++;
    if (!k || k === '-') continue;
    tot++;
    if (f.st === 'ganda') salah++;
    else if (!f.ch) kosong++;
    else if (f.ch === k) benar++; else salah++;
  }
  return { benar, salah, kosong, ganda, ragu, tot, nilai: tot ? Math.round(benar / tot * 10000) / 100 : null };
}
const fmt = v => v === null || v === undefined ? '–' : (Math.round(v * 100) / 100).toString().replace('.', ',');

/* ---------- Pemilih kelas: "XI AK 2" ---------- */
function kelasPicker(el, value, onChange) {
  const p = (value || '').split(' '), t = TINGKAT.includes(p[0]) ? p[0] : 'X', j = JURUSAN.includes(p[1]) ? p[1] : 'TSM', r = +p[2] || 1;
  const sel = (arr, v) => `<select>${arr.map(x => `<option ${x == v ? 'selected' : ''}>${x}</option>`).join('')}</select>`;
  el.innerHTML = sel(TINGKAT, t) + sel(JURUSAN, j) + sel([1, 2, 3, 4, 5, 6], r);
  el.querySelectorAll('select').forEach(s => s.onchange = () => onChange([...el.querySelectorAll('select')].map(x => x.value).join(' ')));
}
const paketOpts = (E, v) => LET.slice(0, E.paket).split('').map(p => `<option ${p === v ? 'selected' : ''}>${p}</option>`).join('');

/* ---------- Navigasi ---------- */
function go(p) {
  PAGE = p;
  $$('nav button').forEach(b => b.classList.toggle('on', b.dataset.p === p));
  $$('.page').forEach(s => s.classList.toggle('on', s.id === 'p-' + p));
  render(); window.scrollTo(0, 0);
}
function needExam(el) {
  if (ex()) return false;
  el.innerHTML = `<div class="card"><b>Belum ada ujian.</b><p class="mut">Buat ujian dulu di menu <b>Ujian</b>: isi mapel, jumlah soal, dan kunci jawaban.</p><button class="btn pri" onclick="go('ujian');openForm()">＋ Buat ujian</button></div>`;
  return true;
}
function render() {
  if (PAGE === 'ujian') renderExams();
  else if (PAGE === 'koreksi') renderKoreksi();
  else if (PAGE === 'rekap') renderRekap();
  else if (PAGE === 'analisis') renderAnalisis();
}

/* ---------- Halaman Ujian ---------- */
async function renderExams() {
  const L = $('#exList');
  if (!EXAMS.length) { L.innerHTML = `<div class="card"><b>Selamat datang!</b><p class="mut" style="margin-bottom:0">Langkah: 1) Buat ujian per mapel & isi kunci → 2) Pilih kelas lalu foto/scan LJK siswa → 3) Lihat rekap per kelas & analisis, unduh Excel.</p></div>`; return; }
  const info = {};
  for (const e of EXAMS) { const rs = await DB.byExam(e.id); info[e.id] = `${rs.length} LJK terkoreksi · ${new Set(rs.map(r => r.kelas)).size} kelas`; }
  L.innerHTML = EXAMS.map(e => {
    const keys = LET.slice(0, e.paket).split('').map(p => { const k = keyOf(e, p), filled = [...k.slice(0, e.n)].filter(c => c !== '-').length; return `<span class="pill ${k.length >= e.n ? 'ok' : 'no'}">Kunci ${e.paket > 1 ? p + ' ' : ''}${filled}/${e.n}</span>`; }).join(' ');
    return `<div class="card ex-card ${e.id === ACTIVE ? 'act' : ''}">
      <div><b>${esc(e.nama)}</b> ${e.id === ACTIVE ? '<span class="pill info">aktif</span>' : ''}<div class="mut">${esc(e.mapel)} · ${e.n} soal · ${e.paket} paket · KKTP ${e.kktp}</div><div class="mut">${info[e.id]}</div></div>
      <div style="margin-top:6px">${keys}</div>
      <div class="acts">${e.id !== ACTIVE ? `<button class="btn sm pri" onclick="setActive('${e.id}')">Aktifkan</button>` : ''}<button class="btn sm" onclick="openForm('${e.id}')">✎ Edit / kunci</button><button class="btn sm red" onclick="delExam('${e.id}')">Hapus</button></div>
    </div>`;
  }).join('');
}
function setActive(id) { ACTIVE = id; RKELAS = null; localStorage.setItem('ljk-active', id); loadExams().then(render); }
let EDIT = null;
function openForm(id) {
  EDIT = id ? EXAMS.find(e => e.id === id) : null;
  const e = EDIT || { nama: '', mapel: '', n: 50, paket: 1, kktp: 75, kunci: {} };
  $('#exFormTitle').textContent = EDIT ? 'Edit ujian' : 'Ujian baru';
  $('#f_nama').value = e.nama; $('#f_mapel').value = e.mapel; $('#f_n').value = e.n; $('#f_paket').value = e.paket; $('#f_kktp').value = e.kktp;
  $('#keyBoxes').innerHTML = ''; renderKeyBoxes(e.kunci);
  $('#exForm').classList.remove('hidden'); $('#exForm').scrollIntoView({ behavior: 'smooth' });
}
const normKey = s => (s || '').toUpperCase().replace(/\?/g, '-').replace(/[^A-E-]/g, '');
function renderKeyBoxes(kunci) {
  const P = +$('#f_paket').value, cur = {}; $$('#keyBoxes textarea').forEach(t => cur[t.dataset.p] = t.value);
  $('#keyBoxes').innerHTML = LET.slice(0, P).split('').map(p => `<div style="margin-bottom:10px"><label>Kunci jawaban${P > 1 ? ' paket ' + p : ''} <span class="kc" data-p="${p}"></span></label><textarea data-p="${p}" rows="2" placeholder="ABCDEABCDE…">${esc(cur[p] ?? (kunci?.[p] || ''))}</textarea><div class="keygrid" data-p="${p}"></div></div>`).join('');
  $$('#keyBoxes textarea').forEach(t => { t.oninput = () => keyPreview(t.dataset.p); keyPreview(t.dataset.p); });
}
function keyPreview(p) {
  const n = Math.min(50, +$('#f_n').value || 0), k = normKey($(`#keyBoxes textarea[data-p="${p}"]`).value);
  $(`#keyBoxes .kc[data-p="${p}"]`).innerHTML = k.length === n ? `<span class="pill ok">${n}/${n} ✓</span>` : `<span class="pill ${k.length > n ? 'warn' : 'no'}">${k.length}/${n}${k.length > n ? ' (kelebihan)' : ''}</span>`;
  let h = ''; for (let i = 0; i < n; i++) { const c = k[i]; h += `<span class="${!c || c === '-' ? 'miss' : ''}">${i + 1}.${c || '?'}</span>`; }
  $(`#keyBoxes .keygrid[data-p="${p}"]`).innerHTML = h;
}
async function saveForm() {
  const n = Math.max(1, Math.min(50, +$('#f_n').value | 0)), nama = $('#f_nama').value.trim();
  if (!nama) return toast('Nama ujian wajib diisi');
  const kunci = {}; $$('#keyBoxes textarea').forEach(t => kunci[t.dataset.p] = normKey(t.value).slice(0, n));
  const e = Object.assign(EDIT || { id: uid(), created: Date.now() }, { nama, mapel: $('#f_mapel').value.trim(), n, paket: +$('#f_paket').value, kktp: +$('#f_kktp').value || 0, kunci: Object.assign({}, EDIT?.kunci, kunci) });
  await DB.put('exams', e);
  if (!EDIT) ACTIVE = e.id;
  $('#exForm').classList.add('hidden'); EDIT = null;
  await loadExams(); render(); toast('Ujian tersimpan');
}
async function delExam(id) {
  const e = EXAMS.find(x => x.id === id);
  if (!confirm(`Hapus ujian "${e.nama} · ${e.mapel}" beserta semua hasil koreksinya (semua kelas)?`)) return;
  for (const r of await DB.byExam(id)) await DB.del('results', r.id);
  await DB.del('exams', id); await loadExams(); render();
}
function dl(blob, name) { const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name; document.body.appendChild(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000); }
const safe = s => String(s || '').replace(/[^\w\- ]+/g, '').trim().replace(/\s+/g, '_');

/* ---------- Halaman Koreksi ---------- */
let SESSION = []; // id hasil sesi ini
async function renderKoreksi() {
  const L = $('#corrList');
  if (needExam(L)) return;
  const E = ex();
  $('#modeHint').innerHTML = MODE === 'siswa'
    ? 'Pilih kelas, lalu foto LJK satu per satu atau pilih banyak file hasil scan sekaligus. Nama siswa diisi setelah scan (LJK hanya memuat tulisan tangan).'
    : '<b>Mode kunci:</b> isi satu LJK kosong dengan jawaban benar (silang), lalu foto. Kunci terisi otomatis.';
  $('#kelasRow').firstElementChild.style.display = $('#kelasSel').style.display = MODE === 'siswa' ? '' : 'none';
  kelasPicker($('#kelasSel'), SET.kelas, v => { SET.kelas = v; saveSet(); });
  $('#paketWrap').style.display = E.paket > 1 ? '' : 'none';
  $('#paketSel').innerHTML = paketOpts(E, $('#paketSel').value || 'A');
  const all = await DB.byExam(E.id), rs = SESSION.map(id => all.find(r => r.id === id)).filter(Boolean);
  L.innerHTML = rs.length ? '<h3 style="margin-top:6px">Hasil sesi ini</h3>' + rs.map(r => resCard(E, r)).join('') : '';
}
function resCard(E, r) {
  const s = score(E, r), warn = [];
  if (!r.nama) warn.push('<span class="pill no">nama belum diisi</span>');
  if (s.ganda) warn.push(`<span class="pill warn">${s.ganda} ganda</span>`);
  if (s.ragu) warn.push(`<span class="pill warn">${s.ragu} ragu</span>`);
  return `<div class="card res click" onclick="openDet('${r.id}')" style="cursor:pointer">
   <div>${r.nama ? `<b>${esc(r.nama)}</b>` : `<div class="kn" style="background-image:url(${r.img})"></div>`}<div class="mut">${esc(r.kelas)}${E.paket > 1 ? ' · Paket ' + r.paket : ''} · B ${s.benar} · S ${s.salah} · K ${s.kosong}</div><div>${warn.join(' ')}</div></div>
   <div class="sc" style="color:${s.nilai === null ? 'var(--mut)' : s.nilai >= E.kktp ? 'var(--ok)' : 'var(--no)'}">${fmt(s.nilai)}</div></div>`;
}
function loadCanvas(file) {
  return new Promise((res, rej) => {
    const url = URL.createObjectURL(file), img = new Image();
    img.onload = () => {
      const sc = Math.min(1, 2000 / Math.max(img.naturalWidth, img.naturalHeight));
      const c = document.createElement('canvas'); c.width = Math.round(img.naturalWidth * sc); c.height = Math.round(img.naturalHeight * sc);
      c.getContext('2d', { willReadFrequently: true }).drawImage(img, 0, 0, c.width, c.height);
      URL.revokeObjectURL(url); res(c);
    };
    img.onerror = () => { URL.revokeObjectURL(url); rej(new Error('File bukan gambar yang bisa dibuka (gunakan JPG/PNG).')); };
    img.src = url;
  });
}
async function handleFiles(files) {
  const E = ex(); if (!E) return toast('Buat/aktifkan ujian dulu');
  files = [...files]; if (!files.length) return;
  const pg = $('#prog'), paket = E.paket > 1 ? ($('#paketSel').value || 'A') : 'A'; pg.classList.remove('hidden');
  let okc = 0, errs = [], last = null;
  for (let i = 0; i < files.length; i++) {
    pg.firstElementChild.style.width = (i / files.length * 100) + '%';
    await sleep(30);
    try {
      const cv = await loadCanvas(files[i]);
      const r = OMR.readSheet(cv.getContext('2d').getImageData(0, 0, cv.width, cv.height), { n: E.n, th: SET.th });
      if (!r.ok) { errs.push(`${files[i].name}: ${r.error}`); continue; }
      const ans = r.answers.map(a => ({ c: a.choice, s: a.status, m: a.marks.map(m => ({ kosong: '.', x: 'x', tanda: 't', coret: 'c' }[m])).join(''), n: a.note }));
      if (MODE === 'kunci') { await saveKeyFromScan(E, paket, ans); okc++; continue; }
      const rec = { id: uid(), examId: E.id, kelas: SET.kelas, nama: '', paket, ans, ovr: {}, img: OMR.renderWarp(cv, r.H, 3).toDataURL('image/jpeg', 0.72), ts: Date.now() };
      await DB.put('results', rec);
      SESSION.unshift(rec.id); last = rec.id; okc++;
    } catch (e) { errs.push(`${files[i].name}: ${e.message}`); }
  }
  pg.firstElementChild.style.width = '100%'; setTimeout(() => pg.classList.add('hidden'), 400);
  if (errs.length) alert('Sebagian gagal dibaca:\n\n' + errs.join('\n'));
  else if (MODE === 'siswa') toast(`${okc} LJK terkoreksi`);
  await renderKoreksi();
  // satu foto: langsung buka detail untuk mengisi nama & memeriksa hasil
  if (MODE === 'siswa' && files.length === 1 && last) openDet(last);
}
async function saveKeyFromScan(E, p, ans) {
  const good = a => (a.s === 'ok' || a.s === 'ragu') && a.c;
  const key = ans.map(a => good(a) ? a.c : '-').join(''), bad = ans.map((a, i) => good(a) ? null : i + 1).filter(Boolean);
  if (!confirm(`Kunci paket ${p} terbaca:\n${key.match(/.{1,10}/g).join(' ')}\n\n${bad.length ? 'Tidak terbaca/ganda: no. ' + bad.join(', ') + ' (ditandai "-", perbaiki lewat Edit)' : 'Semua soal terbaca.'}\n\nSimpan sebagai kunci paket ${p}?`)) return;
  E.kunci = E.kunci || {}; E.kunci[p] = key; await DB.put('exams', E); await loadExams();
  toast(`Kunci paket ${p} tersimpan`);
}

/* ---------- Detail / koreksi manual ---------- */
let DET = null;
async function openDet(id) {
  const E = ex(), all = await DB.all('results'), r = all.find(x => x.id === id); if (!r) return;
  DET = { E, r: JSON.parse(JSON.stringify(r)), sel: null, img: new Image(), all };
  DET.img.onload = drawDet; DET.img.src = r.img;
  $('#detNama').value = r.nama || '';
  kelasPicker($('#detKelas'), r.kelas, v => { DET.r.kelas = v; updDet(); });
  $('#detPaketWrap').style.display = E.paket > 1 ? '' : 'none';
  $('#detPaket').innerHTML = paketOpts(E, r.paket);
  $('#detPick').classList.add('hidden');
  updDet(); $('#mDet').classList.add('on');
}
function updDet() {
  const { E, r, all } = DET, s = score(E, r);
  $('#detTitle').textContent = r.nama || 'LJK (nama belum diisi)';
  // saran: nama di kelas ini yang pernah diisi dan belum dipakai pada ujian ini
  const used = new Set(all.filter(x => x.examId === E.id && x.id !== r.id && x.kelas === r.kelas).map(x => (x.nama || '').toLowerCase()));
  $('#detNamaList').innerHTML = [...new Set(all.filter(x => x.kelas === r.kelas && x.nama).map(x => x.nama))].filter(n => !used.has(n.toLowerCase())).sort().map(n => `<option value="${esc(n)}">`).join('');
  $('#detScore').innerHTML = `<span style="font-size:22px;font-weight:800;color:${s.nilai !== null && s.nilai >= E.kktp ? 'var(--ok)' : 'var(--no)'}">${fmt(s.nilai)}</span> <span class="mut">Benar ${s.benar} · Salah ${s.salah} · Kosong ${s.kosong}${s.ganda ? ' · Ganda ' + s.ganda : ''}${s.ragu ? ' · Ragu ' + s.ragu : ''}</span>${keyOf(E, r.paket).length < E.n ? '<div class="mut" style="color:var(--no)">Kunci paket ini belum lengkap.</div>' : ''}`;
  const key = keyOf(E, r.paket);
  let h = '';
  for (let q = 0; q < E.n; q++) {
    const f = finalAns(r, q), k = key[q];
    let cls = f.st === 'ganda' ? 'ganda' : !f.ch ? 'kosong' : (!k || k === '-') ? 'nk' : f.ch === k ? 'ok' : 'no';
    if (f.st === 'ragu') cls += ' ragu'; if (f.ovr) cls += ' ovr';
    h += `<div class="q ${cls}" onclick="pickQ(${q})">${q + 1}<b>${f.st === 'ganda' ? '⚠' : f.ch || '–'}</b></div>`;
  }
  $('#detQ').innerHTML = h;
  drawDet();
}
function drawDet() {
  if (!DET || !DET.img.complete || !DET.img.naturalWidth) return;
  const { E, r, sel } = DET, cv = $('#detCv'), S = DET.img.naturalWidth / 210;
  cv.width = DET.img.naturalWidth; cv.height = DET.img.naturalHeight;
  const c = cv.getContext('2d'); c.drawImage(DET.img, 0, 0);
  const key = keyOf(E, r.paket);
  const R = (b, fill, stroke, lw = 2) => { if (fill) { c.fillStyle = fill; c.fillRect(b.x * S, b.y * S, b.w * S, b.h * S); } if (stroke) { c.strokeStyle = stroke; c.lineWidth = lw; c.strokeRect(b.x * S - 1.5, b.y * S - 1.5, b.w * S + 3, b.h * S + 3); } };
  for (let q = 0; q < E.n; q++) {
    const f = finalAns(r, q), k = key[q], a = r.ans[q];
    if (f.st === 'ganda') { [...(a.m || '')].forEach((m, o) => { if (m === 'x' || m === 't') R(OMR.ansBox(q, o), 'rgba(240,140,0,.45)', '#d98a00'); }); }
    else if (f.ch) { const good = f.ch === k; R(OMR.ansBox(q, LET.indexOf(f.ch)), (!k || k === '-') ? 'rgba(120,120,120,.35)' : good ? 'rgba(26,143,78,.40)' : 'rgba(200,55,45,.42)', f.st === 'ragu' ? '#d98a00' : null); }
    if (k && k !== '-' && f.ch !== k) R(OMR.ansBox(q, LET.indexOf(k)), null, '#1a8f4e', 2.5);
  }
  if (sel !== null) { const b0 = OMR.ansBox(sel, 0), b4 = OMR.ansBox(sel, 4); c.strokeStyle = '#2a6fd6'; c.lineWidth = 3; c.strokeRect((b0.x - 10) * S, b0.y * S, (b4.x + b4.w - b0.x + 11) * S, b0.h * S); }
}
const MARKNAME = { x: 'tanda jawaban', t: 'tanda lain', c: 'dicoret', '.': '' };
function pickQ(q) {
  DET.sel = q; const { E, r } = DET, a = r.ans[q], f = finalAns(r, q), k = keyOf(E, r.paket)[q];
  $('#detPick').classList.remove('hidden');
  $('#detPickT').textContent = `Soal ${q + 1} · kunci ${k && k !== '-' ? k : '(tidak dinilai)'} · jawaban ${f.st === 'ganda' ? 'GANDA' : f.ch || 'kosong'}${f.ovr ? ' (diubah manual)' : ''}`;
  const det = [...(a.m || '')].map((m, o) => m !== '.' ? `${LET[o]}=${MARKNAME[m]}` : '').filter(Boolean).join(', ');
  $('#detPickN').textContent = 'Terbaca mesin: ' + (det || 'tidak ada tanda') + (a.n ? ` — ${a.n}` : '');
  $('#detPickB').innerHTML = LET.split('').map(L => `<button class="btn ${f.ch === L && f.st !== 'ganda' ? 'pri' : ''}" onclick="setOvr(${q},'${L}')">${L}</button>`).join('') +
    `<button class="btn" onclick="setOvr(${q},'')">Kosong/batal</button>` + (f.ovr ? `<button class="btn" onclick="setOvr(${q},null)">↺ Hasil mesin</button>` : '');
  drawDet();
}
function setOvr(q, v) { if (v === null) delete DET.r.ovr[q]; else DET.r.ovr[q] = v; updDet(); pickQ(q); }
async function saveDet() {
  const { E, r, all } = DET; r.nama = $('#detNama').value.trim();
  const dup = r.nama && all.find(x => x.examId === E.id && x.id !== r.id && x.kelas === r.kelas && (x.nama || '').toLowerCase() === r.nama.toLowerCase());
  if (dup && !confirm(`${r.nama} (${r.kelas}) sudah punya hasil pada ujian ini. Timpa hasil lama?`)) return;
  if (dup) await DB.del('results', dup.id);
  await DB.put('results', r); $('#mDet').classList.remove('on'); toast('Tersimpan'); render();
}
async function delDet() { if (!confirm('Hapus hasil LJK ini?')) return; await DB.del('results', DET.r.id); SESSION = SESSION.filter(x => x !== DET.r.id); $('#mDet').classList.remove('on'); render(); }

/* ---------- Rekap ---------- */
// yang sudah bernama urut abjad; yang belum bernama di akhir menurut urutan scan
const cmp = (a, b) => (a.kelas || '').localeCompare(b.kelas || '') || (!a.nama - !b.nama) || (a.nama || '').toLowerCase().localeCompare((b.nama || '').toLowerCase()) || a.ts - b.ts;
async function rekapData(E) {
  const all = await DB.byExam(E.id), classes = [...new Set(all.map(r => r.kelas))].sort();
  if (RKELAS && !classes.includes(RKELAS)) RKELAS = null;
  const rs = (RKELAS ? all.filter(r => r.kelas === RKELAS) : all).sort(cmp);
  return { all, classes, rs, label: RKELAS || 'Semua kelas', rows: rs.map(r => ({ r, s: score(E, r) })) };
}
async function renderRekap() {
  const T = $('#rekapTbl'); $('#stats').innerHTML = ''; $('#rekapKelas').innerHTML = '';
  if (needExam($('#stats'))) { T.innerHTML = ''; return; }
  const E = ex(), D = await rekapData(E), vals = D.rows.map(x => x.s.nilai).filter(v => v !== null);
  $('#rekapKelas').innerHTML = `<button class="btn sm ${RKELAS ? '' : 'pri'}" onclick="setRK(null)">Semua (${D.all.length})</button>` + D.classes.map(k => `<button class="btn sm ${RKELAS === k ? 'pri' : ''}" onclick="setRK('${esc(k)}')">${esc(k)} (${D.all.filter(r => r.kelas === k).length})</button>`).join('');
  const tuntas = vals.filter(v => v >= E.kktp).length, avg = vals.length ? vals.reduce((a, b) => a + b, 0) / vals.length : null;
  $('#stats').innerHTML = [['Terkoreksi', D.rows.length], ['Rata-rata', fmt(avg)], ['Tertinggi', fmt(vals.length ? Math.max(...vals) : null)], ['Terendah', fmt(vals.length ? Math.min(...vals) : null)], ['Tuntas', `${tuntas} (${vals.length ? Math.round(tuntas / vals.length * 100) : 0}%)`], ['Remedial', vals.length - tuntas]].map(([a, b]) => `<div class="stat"><b>${b}</b><span>${a}</span></div>`).join('');
  T.innerHTML = '<tr><th>No</th><th>Nama</th><th>Kelas</th><th>Benar</th><th>Salah</th><th>Kosong</th><th>Ganda</th><th>Nilai</th><th>Ket.</th><th></th></tr>' + D.rows.map((x, i) => {
    const s = x.s;
    return `<tr class="click" onclick="openDet('${x.r.id}')"><td>${i + 1}</td><td>${x.r.nama ? esc(x.r.nama) : '<span style="color:var(--no)">(nama belum diisi)</span>'}</td><td>${esc(x.r.kelas)}</td><td>${s.benar}</td><td>${s.salah}</td><td>${s.kosong}</td><td>${s.ganda || ''}</td><td><b>${fmt(s.nilai)}</b></td><td>${s.nilai === null ? '<span class="pill">kunci?</span>' : s.nilai >= E.kktp ? '<span class="pill ok">Tuntas</span>' : '<span class="pill no">Remedial</span>'}</td><td>${s.ragu ? `<span class="pill warn">cek ${s.ragu}</span>` : ''}</td></tr>`;
  }).join('');
}
function setRK(k) { RKELAS = k; renderRekap(); }

/* ---------- Analisis butir ---------- */
async function analyze(E, p, rsAll) {
  const rs = (rsAll || await DB.byExam(E.id)).filter(r => (r.paket || 'A') === p);
  const key = keyOf(E, p), N = rs.length;
  const tot = rs.map(r => score(E, r).benar);
  const order = rs.map((r, i) => i).sort((a, b) => tot[b] - tot[a]);
  const g = N >= 20 ? Math.round(N * 0.27) : Math.floor(N / 2);
  const up = new Set(order.slice(0, g)), lo = new Set(order.slice(N - g));
  const items = []; let spq = 0, k = 0;
  for (let q = 0; q < E.n; q++) {
    const kc = key[q]; const cnt = { A: 0, B: 0, C: 0, D: 0, E: 0, kosong: 0, ganda: 0 }; let bu = 0, bl = 0, b = 0;
    rs.forEach((r, i) => { const f = finalAns(r, q); if (f.st === 'ganda') cnt.ganda++; else if (!f.ch) cnt.kosong++; else cnt[f.ch]++; const ok = f.st !== 'ganda' && f.ch && f.ch === kc; if (ok) { b++; if (up.has(i)) bu++; if (lo.has(i)) bl++; } });
    if (!kc || kc === '-') { items.push({ q, kc: '-', cnt }); continue; }
    const P = N ? b / N : null, D = g ? (bu - bl) / g : null;
    if (N) { spq += P * (1 - P); k++; }
    const pengecoh = LET.split('').filter(o => o !== kc && N && cnt[o] / N < 0.05);
    const katP = P === null ? '' : P < 0.3 ? 'Sukar' : P <= 0.7 ? 'Sedang' : 'Mudah';
    const katD = D === null ? '' : D >= 0.4 ? 'Sangat baik' : D >= 0.3 ? 'Baik' : D >= 0.2 ? 'Cukup' : D >= 0 ? 'Jelek' : 'Negatif';
    const rek = D === null ? '' : D < 0 ? 'Buang/periksa kunci' : (D < 0.2 || P < 0.15 || P > 0.95) ? 'Revisi' : 'Diterima';
    items.push({ q, kc, cnt, P, D, katP, katD, pengecoh, rek });
  }
  const mean = N ? tot.reduce((a, b) => a + b, 0) / N : 0, vr = N ? tot.reduce((a, b) => a + (b - mean) ** 2, 0) / N : 0;
  const kr20 = k > 1 && vr > 0 ? (k / (k - 1)) * (1 - spq / vr) : null;
  return { N, items, kr20, mean, k };
}
async function renderAnalisis() {
  const T = $('#anTbl'); const sel = $('#anPaket');
  if (needExam($('#anStats'))) { T.innerHTML = ''; sel.innerHTML = ''; return; }
  const E = ex(); const cur = sel.value && sel.value <= LET[E.paket - 1] ? sel.value : 'A';
  sel.innerHTML = LET.slice(0, E.paket).split('').map(p => `<option ${p === cur ? 'selected' : ''} value="${p}">Paket ${p}</option>`).join('');
  sel.style.display = E.paket > 1 ? '' : 'none';
  const A = await analyze(E, cur);
  const kat = A.kr20 === null ? '' : A.kr20 >= 0.7 ? 'reliabel' : A.kr20 >= 0.5 ? 'cukup' : 'rendah';
  $('#anStats').innerHTML = [['Peserta (semua kelas)', A.N], ['Rata-rata benar', fmt(A.mean)], ['Reliabilitas KR-20', A.kr20 === null ? '–' : fmt(A.kr20) + ' <small class="mut">' + kat + '</small>'], ['Soal diterima', A.items.filter(i => i.rek === 'Diterima').length], ['Perlu revisi', A.items.filter(i => i.rek && i.rek !== 'Diterima').length]].map(([a, b]) => `<div class="stat"><b>${b}</b><span>${a}</span></div>`).join('');
  if (A.N < 2) { T.innerHTML = '<tr><td class="mut">Butuh minimal 2 LJK terkoreksi untuk analisis.</td></tr>'; return; }
  const pc = (n) => A.N ? Math.round(n / A.N * 100) + '%' : '';
  T.innerHTML = '<tr><th>No</th><th>Kunci</th>' + LET.split('').map(o => `<th>${o}</th>`).join('') + '<th>Kosong</th><th>Ganda</th><th>P</th><th>Kesukaran</th><th>D</th><th>Daya beda</th><th>Pengecoh tdk berfungsi</th><th>Keputusan</th></tr>' +
    A.items.map(it => `<tr><td>${it.q + 1}</td><td><b>${it.kc}</b></td>${LET.split('').map(o => `<td style="${o === it.kc ? 'font-weight:800;color:var(--ok)' : ''}">${it.cnt[o]} <span class="mut">${pc(it.cnt[o])}</span></td>`).join('')}<td>${it.cnt.kosong || ''}</td><td>${it.cnt.ganda || ''}</td><td>${it.P === undefined ? '' : fmt(it.P)}</td><td>${it.katP || ''}</td><td>${it.D === undefined || it.D === null ? '' : fmt(it.D)}</td><td>${it.katD || ''}</td><td>${(it.pengecoh || []).join(', ')}</td><td>${it.rek ? `<span class="pill ${it.rek === 'Diterima' ? 'ok' : it.rek === 'Revisi' ? 'warn' : 'no'}">${it.rek}</span>` : '<span class="mut">tidak dinilai</span>'}</td></tr>`).join('');
}

/* ---------- Ekspor Excel ---------- */
async function exportXlsx() {
  const E = ex(); if (!E) return;
  const D = await rekapData(E), B = v => ({ v, b: 1 });
  const s1 = [[B('REKAP NILAI ' + E.nama.toUpperCase())], ['Mapel', E.mapel], ['Kelas', D.label], ['Jumlah soal', E.n], ['KKTP', E.kktp], [],
    [B('No'), B('Nama'), B('Kelas'), B('Paket'), B('Benar'), B('Salah'), B('Kosong'), B('Ganda'), B('Nilai'), B('Keterangan')],
    ...D.rows.map((x, i) => [i + 1, x.r.nama, x.r.kelas, x.r.paket, x.s.benar, x.s.salah, x.s.kosong, x.s.ganda, x.s.nilai, x.s.nilai === null ? 'Kunci belum lengkap' : x.s.nilai >= E.kktp ? 'Tuntas' : 'Remedial'])];
  const s2 = [[B('No'), B('Nama'), B('Kelas'), B('Paket'), ...[...Array(E.n)].map((_, i) => B(i + 1))]];
  D.rows.forEach((x, i) => s2.push([i + 1, x.r.nama, x.r.kelas, x.r.paket, ...[...Array(E.n)].map((_, q) => { const f = finalAns(x.r, q); return f.st === 'ganda' ? 'GANDA' : f.ch || ''; })]));
  LET.slice(0, E.paket).split('').forEach(p => s2.push(['', 'KUNCI ' + p, '', p, ...[...keyOf(E, p).padEnd(E.n, '-')].slice(0, E.n)]));
  const sheets = [{ name: 'Rekap', rows: s1, widths: [6, 32, 12, 7, 8, 8, 8, 8, 9, 16] }, { name: 'Jawaban', rows: s2, widths: [6, 28, 12, 7] }];
  for (const p of LET.slice(0, E.paket).split('')) {
    const A = await analyze(E, p, D.rs); if (A.N < 2) continue;
    const s = [[B(`ANALISIS BUTIR SOAL${E.paket > 1 ? ' PAKET ' + p : ''}`)], ['Peserta', A.N], ['Reliabilitas KR-20', A.kr20 === null ? '' : +A.kr20.toFixed(3)], [], [B('No'), B('Kunci'), B('A'), B('B'), B('C'), B('D'), B('E'), B('Kosong'), B('Ganda'), B('P'), B('Kesukaran'), B('D'), B('Daya beda'), B('Pengecoh tdk berfungsi'), B('Keputusan')]];
    A.items.forEach(it => s.push([it.q + 1, it.kc, it.cnt.A, it.cnt.B, it.cnt.C, it.cnt.D, it.cnt.E, it.cnt.kosong, it.cnt.ganda, it.P === undefined ? '' : +it.P.toFixed(2), it.katP || '', it.D === undefined || it.D === null ? '' : +it.D.toFixed(2), it.katD || '', (it.pengecoh || []).join(', '), it.rek || 'Tidak dinilai']));
    sheets.push({ name: 'Analisis ' + p, rows: s, widths: [6, 7, 6, 6, 6, 6, 6, 8, 7, 7, 11, 7, 12, 22, 18] });
  }
  dl(XLSX_MINI.book(sheets), `Rekap_${safe(E.nama)}_${safe(E.mapel)}_${safe(D.label)}.xlsx`);
}

/* ---------- Pengaturan ---------- */
function openSet() {
  [['tm', 'tMark'], ['ts', 'tStrike']].forEach(([a, b]) => { $('#s_' + a).value = SET.th[b]; $('#v_' + a).textContent = SET.th[b]; });
  $('#verInfo').textContent = `© Pak-eng_v${APP_VER} · ${navigator.onLine ? 'online' : 'offline'} · data tersimpan lokal di perangkat ini`;
  $('#mSet').classList.add('on');
}
async function backup() {
  const data = { app: 'koreksi-ljk', ver: APP_VER, at: new Date().toISOString(), exams: await DB.all('exams'), results: await DB.all('results') };
  dl(new Blob([JSON.stringify(data)], { type: 'application/json' }), `cadangan-koreksi-ljk-${new Date().toISOString().slice(0, 10)}.json`);
}
async function restore(file) {
  try {
    const d = JSON.parse(await file.text()); if (d.app !== 'koreksi-ljk') throw new Error('Bukan file cadangan aplikasi ini');
    if (!confirm(`Pulihkan ${d.exams.length} ujian & ${d.results.length} hasil? Data dengan ID sama akan ditimpa.`)) return;
    for (const e of d.exams) await DB.put('exams', e); for (const r of d.results) await DB.put('results', r);
    await loadExams(); render(); toast('Data dipulihkan');
  } catch (e) { alert('Gagal memulihkan: ' + e.message); }
}

/* ---------- Event ---------- */
$$('nav button').forEach(b => b.onclick = () => go(b.dataset.p));
$('#exSel').onchange = e => setActive(e.target.value);
$('#btnNew').onclick = () => openForm();
$('#btnCancel').onclick = () => { $('#exForm').classList.add('hidden'); EDIT = null; };
$('#btnSave').onclick = saveForm;
$('#f_paket').onchange = () => renderKeyBoxes(EDIT?.kunci);
$('#f_n').oninput = () => $$('#keyBoxes textarea').forEach(t => keyPreview(t.dataset.p));
$$('#modeSeg button').forEach(b => b.onclick = () => { MODE = b.dataset.m; $$('#modeSeg button').forEach(x => x.classList.toggle('on', x === b)); renderKoreksi(); });
$('#inCam').onchange = e => { handleFiles(e.target.files); e.target.value = ''; };
$('#inFile').onchange = e => { handleFiles(e.target.files); e.target.value = ''; };
$('#btnXlsx').onclick = exportXlsx;
$('#btnClearRes').onclick = async () => { const E = ex(); if (!E) return; const D = await rekapData(E); if (!confirm(`Hapus ${D.rs.length} hasil koreksi "${E.nama} · ${E.mapel}" (${D.label})?`)) return; for (const r of D.rs) await DB.del('results', r.id); SESSION = SESSION.filter(id => !D.rs.some(r => r.id === id)); render(); };
$('#anPaket').onchange = renderAnalisis;
$('#detSave').onclick = saveDet; $('#detDel').onclick = delDet;
$('#detNama').oninput = e => { DET.r.nama = e.target.value; $('#detTitle').textContent = e.target.value || 'LJK (nama belum diisi)'; };
$('#detPaket').onchange = e => { DET.r.paket = e.target.value; updDet(); };
$('#detCv').onclick = e => {
  const rc = e.target.getBoundingClientRect(), x = (e.clientX - rc.left) / rc.width * 210, y = (e.clientY - rc.top) / rc.height * 297;
  for (let q = 0; q < DET.E.n; q++) { const b0 = OMR.ansBox(q, 0), b4 = OMR.ansBox(q, 4); if (x >= b0.x - 10 && x <= b4.x + b4.w + 1 && y >= b0.y && y < b0.y + b0.h) return pickQ(q); }
};
$$('[data-close]').forEach(b => b.onclick = () => b.closest('.modal').classList.remove('on'));
$$('.modal').forEach(m => m.onclick = e => { if (e.target === m) m.classList.remove('on'); });
$('#btnSet').onclick = openSet;
$('#s_tm').oninput = e => { SET.th.tMark = +e.target.value; SET.th.tEmpty = +(e.target.value * 0.55).toFixed(4); $('#v_tm').textContent = e.target.value; saveSet(); };
$('#s_ts').oninput = e => { SET.th.tStrike = +e.target.value; $('#v_ts').textContent = e.target.value; saveSet(); };
$('#s_reset').onclick = () => { SET.th = { ...OMR.DEF }; saveSet(); openSet(); };
$('#s_backup').onclick = backup;
$('#s_restore').onchange = e => { if (e.target.files[0]) restore(e.target.files[0]); e.target.value = ''; };

(async () => {
  await loadExams(); render();
  if (!EXAMS.length) openForm();
  if ('serviceWorker' in navigator && location.protocol === 'https:') navigator.serviceWorker.register('/sw.js').catch(() => { });
  if (navigator.storage?.persist) navigator.storage.persist().catch(() => { });
})();
