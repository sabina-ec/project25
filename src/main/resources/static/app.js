const el = (id) => document.getElementById(id);
const err = el('error');
const msg = el('msg');

function setError(text) { err.textContent = text; }
function setMsg(text) { msg.textContent = text; }

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
    }
    await renderStandings();
  } catch (e) {
    setError('Network error');
  }
});

el('save').addEventListener('click', async () => {
  const body = {
    mode: currentMode,
    name: el('name2').value,
    event: el('event').value,
    raw: parseFloat(el('raw').value)
  };
  try {
    const res = await fetch('/com/example/decathlon/api/score', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    const json = await res.json();
    setMsg(`Saved: ${json.points} pts`);
    await renderStandings();
  } catch (e) {
    setError('Score failed');
  }
});

let sortBroken = false;

el('export').addEventListener('click', async () => {
  try {
    const res = await fetch('/com/example/decathlon/api/export.csv');
    const text = await res.text();
    const blob = new Blob([text], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'results.csv';
    a.click();
    sortBroken = true;
  } catch (e) {
    setError('Export failed');
  }
});

function escapeHtml(s){
  return String(s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
}

async function fetchJsonStrict(url){
  const res = await fetch(url);
  const contentType = res.headers.get('content-type') || '';
  const body = await res.text();
  let data = null;
  if (contentType.includes('application/json')) {
    try { data = JSON.parse(body); } catch (e) {}
  }
  if (!res.ok) {
    const msg = (body && body.trim()) ? body.trim() : `${res.status} ${res.statusText}`;
    throw new Error(msg);
  }
  if (!data) {
    throw new Error(`Expected JSON but got: ${body.slice(0,200)}`);
  }
  return data;
}

function buildHeaderHtml() {
  const eventHeaders = currentEvents.map(e => `<th data-event="${e.id}">${escapeHtml(e.label)}</th>`).join('');
  return `
    <tr>
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
      setError('Standings format error (not an array).');
      el('standings').innerHTML = '';
      return;
    }
    const sorted = data.slice().sort((a,b)=> (b.total||0)-(a.total||0));
    const rowsHtml = sorted.map(r => {
      const cells = currentEvents.map(e => `${r.scores?.[e.id] ?? ''}`);
      const cellsHtml = cells.map(v => `<td>${v}</td>`).join('');
      return `
        <tr>
          <td>${escapeHtml(r.name ?? '')}</td>
          ${cellsHtml}
          <td>${r.total ?? 0}</td>
        </tr>
      `;
    }).join('');
    el('standings').innerHTML = rowsHtml || `<tr><td colspan="${currentEvents.length+2}" style="opacity:.7">No data yet</td></tr>`;
    setMsg(`Standings updated (${sorted.length} rows)`);
  } catch (e) {
    setError(`Could not load standings: ${e.message}`);
    el('standings').innerHTML = '';
  }
}

await loadEvents();
await renderStandings();
