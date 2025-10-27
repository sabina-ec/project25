const el = (id) => document.getElementById(id);
const err = el('error');
const msg = el('msg');

function setError(text) { err.textContent = text || ''; }
function setMsg(text) { msg.textContent = text || ''; if (text) err.textContent = ''; }

let currentMode = 'DEC';
let currentEvents = [];

el('mode').addEventListener('change', async () => {
  currentMode = el('mode').value;
  await loadEvents();
  await renderStandings();
});

el('add').addEventListener('click', async () => {
  const name = el('name').value;
  try {
    const res = await fetch('/com/example/decathlon/api/competitors', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name })
    });
    if (!res.ok) {
      const t = await res.text();
      setError(t || 'Failed to add competitor');
    } else {
      setMsg('Added');
      el('name').value = '';
    }
    await renderStandings();
  } catch (e) { setError('Network error'); }
});

el('save').addEventListener('click', async () => {
  const body = {
    mode: currentMode,
    name: el('name2').value,
    event: el('event').value,
    raw: parseFloat((el('raw').value || '').toString().replace(',', '.'))
  };
  if (!body.name || !body.event || Number.isNaN(body.raw)) { setError('Fill all fields'); return; }
  try {
    const res = await fetch('/com/example/decathlon/api/score', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    if (!res.ok) {
      const t = await res.text();
      setError(t || 'Score failed');
      return;
    }
    const json = await res.json();
    setMsg(`Saved: ${json.points} pts`);
    el('raw').value = '';
    await renderStandings();
  } catch (e) { setError('Score failed'); }
});

el('export').addEventListener('click', async () => {
  try {
    const res = await fetch(`/com/example/decathlon/api/export.csv?mode=${encodeURIComponent(currentMode)}`);
    const text = await res.text();
    const blob = new Blob([text], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `results_${currentMode}.csv`;
    a.click();
    setMsg('Exported');
  } catch (e) { setError('Export failed'); }
});

el('importBtn').addEventListener('click', () => el('importFile').click());

el('importFile').addEventListener('change', async (ev) => {
  const f = ev.target.files?.[0];
  if (!f) return;
  try {
    const text = await f.text();
    const res = await fetch('/com/example/decathlon/api/import.csv', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain;charset=utf-8' },
      body: text
    });
    if (!res.ok) {
      const t = await res.text();
      setError(t || 'Import failed');
      return;
    }
    const modeFromFile = parseModeFromCsv(text);
    if (modeFromFile && modeFromFile !== currentMode) {
      currentMode = modeFromFile;
      el('mode').value = currentMode;
      await loadEvents();
    }
    await renderStandings();
    setMsg('Import complete');
  } catch (e) { setError('Import failed'); }
  ev.target.value = '';
});

function parseModeFromCsv(csv) {
  const first = (csv.split(/\r?\n/)[0] || '').trim();
  if (first.startsWith('MODE,')) {
    const m = first.slice(5).trim();
    if (m === 'DEC' || m === 'HEP') return m;
  }
  return null;
}

async function fetchJsonStrict(url){
  const res = await fetch(url);
  const body = await res.text();
  const ct = res.headers.get('content-type') || '';
  let data = null;
  if (ct.includes('application/json')) { try { data = JSON.parse(body); } catch(_){} }
  if (!res.ok) throw new Error((body && body.trim()) || `${res.status} ${res.statusText}`);
  if (!data) throw new Error('Bad JSON');
  return data;
}

function escapeHtml(s){
  return String(s ?? '').replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
}

function buildHeaderHtml() {
  const eventHeaders = currentEvents.map(e => `<th data-event="${e.id}">${escapeHtml(e.label)}</th>`).join('');
  return `
    <tr>
      <th>Rank</th>
      <th>Name</th>
      ${eventHeaders}
      <th>Total</th>
    </tr>
  `;
}

function fillEventSelect() {
  const sel = el('event');
  sel.innerHTML = currentEvents.map(e => {
    const suffix = e.unit ? ` (${e.unit})` : '';
    return `<option value="${e.id}">${escapeHtml(e.label + suffix)}</option>`;
  }).join('');
}

async function loadEvents() {
  const map = await fetchJsonStrict(`/com/example/decathlon/api/events?mode=${encodeURIComponent(currentMode)}`);
  const list = Object.values(map);
  currentEvents = list;
  el('thead').innerHTML = buildHeaderHtml();
  fillEventSelect();
}

async function renderStandings() {
  try {
    setMsg('');
    const data = await fetchJsonStrict('/com/example/decathlon/api/standings');
    if (!Array.isArray(data)) {
      setError('Standings format error');
      el('standings').innerHTML = '';
      return;
    }
    const sorted = data.slice().sort((a,b)=> (b.total||0)-(a.total||0));
    let rowsHtml = '';
    let prev = null, pos = 0, rank = 0;
    for (const r of sorted) {
      pos++;
      const t = r.total || 0;
      if (t !== prev) { rank = pos; prev = t; }
      const cells = currentEvents.map(e => `${r.scores?.[e.id] ?? ''}`);
      const cellsHtml = cells.map(v => `<td>${v}</td>`).join('');
      rowsHtml += `
        <tr>
          <td>${rank}</td>
          <td>${escapeHtml(r.name ?? '')}</td>
          ${cellsHtml}
          <td>${t}</td>
        </tr>
      `;
    }
    el('standings').innerHTML = rowsHtml || `<tr><td colspan="${currentEvents.length+3}" style="opacity:.7">No data yet</td></tr>`;
    setMsg(`Standings updated (${sorted.length})`);
  } catch (e) {
    setError(`Could not load standings: ${e.message}`);
    el('standings').innerHTML = '';
  }
}

await loadEvents();
await renderStandings();
