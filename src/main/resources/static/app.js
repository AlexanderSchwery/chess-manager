'use strict';

const MODE_LABEL = {
  SWISS: 'Schweizer System',
  DOUBLE_ROUND_ROBIN: 'Doppelrundenturnier',
  FREE_PLAY: 'Freie Paarungen',
};
const STATUS_LABEL = {
  CREATED: 'Erstellt', IN_PROGRESS: 'Läuft', COMPLETED: 'Abgeschlossen',
  SCHEDULED: 'Angesetzt', CANCELLED: 'Abgesagt',
};

const state = {
  players: [],
  tournaments: [],
  selectedTournamentId: null, // shared by detail / play / standings views
};

const $ = (sel) => document.querySelector(sel);

/** Escape user-provided text before putting it into innerHTML. */
function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function toast(message, isError = false) {
  const el = $('#toast');
  el.textContent = message;
  el.className = 'fixed bottom-4 right-4 max-w-sm px-4 py-3 rounded-lg shadow-lg text-white '
    + (isError ? 'bg-red-600' : 'bg-emerald-600');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => el.classList.add('hidden'), 3500);
}

/**
 * The API lives on the Spring Boot server (port 8080). If the page is opened from
 * anywhere else (file://, IDE preview, other dev server) a relative '/api' would hit
 * the wrong server and return 404, so use an absolute URL in that case.
 */
const API_BASE = (location.protocol === 'file:' || (location.port && location.port !== '8080'))
  ? 'http://localhost:8080/api' : '/api';

async function api(path, options = {}) {
  const res = await fetch(API_BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText} (${options.method || 'GET'} ${API_BASE}${path})`;
    try { msg = (await res.json()).message || msg; } catch { /* no JSON body */ }
    throw new Error(msg);
  }
  return res.status === 204 ? null : res.json();
}

/** Runs an async action and reports errors as toast. */
async function run(action, successMessage) {
  try {
    const result = await action();
    if (successMessage) toast(successMessage);
    return result;
  } catch (e) {
    toast(e.message, true);
    return undefined;
  }
}

/* ---------- Tabs ---------- */

function showTab(name) {
  document.querySelectorAll('.tab-panel').forEach((p) => p.classList.add('hidden'));
  $('#tab-' + name).classList.remove('hidden');
  document.querySelectorAll('.tab-btn').forEach((b) =>
    b.classList.toggle('active', b.dataset.tab === name));
  if (name === 'play') loadMatches();
  if (name === 'standings') loadStandings();
}

document.querySelectorAll('.tab-btn').forEach((b) =>
  b.addEventListener('click', () => showTab(b.dataset.tab)));

/* ---------- Data loading ---------- */

async function loadPlayers() {
  state.players = await api('/players'); // always the full list (used for enrolling too)
  renderPlayers();
  renderTournamentDetail();
}

async function loadTournaments() {
  state.tournaments = await api('/tournaments');
  if (!state.tournaments.some((t) => t.id === state.selectedTournamentId)) {
    state.selectedTournamentId = state.tournaments.length ? state.tournaments[0].id : null;
  }
  renderTournaments();
  renderTournamentSelects();
}

/* ---------- Players ---------- */

function renderPlayers() {
  const q = $('#player-search').value.trim().toLowerCase();
  const shown = state.players.filter((p) =>
    !q || p.name.toLowerCase().includes(q) || (p.ssbNumber || '').toLowerCase().includes(q));
  $('#player-rows').innerHTML = shown.length
    ? shown.map((p) => `
      <tr>
        <td class="font-medium">${esc(p.name)}</td>
        <td>${esc(p.ssbNumber || '–')}</td>
        <td class="text-right tabular-nums">${p.currentElo}</td>
        <td class="text-right whitespace-nowrap">
          <button class="btn-sm" data-history="${p.id}">Historie</button>
          <button class="btn-sm" data-edit-elo="${p.id}">Elo bearbeiten</button></td>
      </tr>`).join('')
    : '<tr><td colspan="4" class="text-slate-500 py-4">Keine Spieler vorhanden.</td></tr>';
}

$('#player-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = new FormData(e.target);
  const body = {
    name: f.get('name'),
    currentElo: Number(f.get('currentElo')),
    ssbNumber: f.get('ssbNumber') || null,
  };
  const created = await run(() => api('/players', { method: 'POST', body: JSON.stringify(body) }),
    'Spieler erfasst');
  if (created) {
    e.target.reset();
    e.target.currentElo.value = 1500;
    await loadPlayers();
  }
});

$('#player-search').addEventListener('input', renderPlayers);

$('#player-rows').addEventListener('click', async (e) => {
  if (e.target.dataset.history) return openHistory(e.target.dataset.history);
  const id = e.target.dataset.editElo;
  if (!id) return;
  const player = state.players.find((p) => String(p.id) === id);
  const input = prompt(`Neues Elo für ${player.name}:`, player.currentElo);
  if (input === null) return;
  const elo = Number(input);
  if (!Number.isInteger(elo) || elo < 0) return toast('Ungültiges Elo', true);
  const updated = await run(() => api(`/players/${id}/elo`, {
    method: 'PATCH', body: JSON.stringify({ currentElo: elo }),
  }), 'Elo aktualisiert');
  if (updated) await loadPlayers();
});

/* ---------- Tournaments ---------- */

let showArchive = false;

function renderTournaments() {
  const list = state.tournaments.filter((t) => t.archived === showArchive);
  const archivedCount = state.tournaments.filter((t) => t.archived).length;
  $('#archive-toggle').textContent = showArchive
    ? '← Zurück zu aktiven Turnieren' : `Archiv anzeigen (${archivedCount})`;
  $('#tournament-list-title').textContent = showArchive ? 'Archivierte Turniere' : 'Turniere';
  $('#tournament-list').innerHTML = list.length
    ? list.map((t) => `
      <div class="card t-card ${t.id === state.selectedTournamentId ? 'selected' : ''}" data-tournament="${t.id}">
        <div class="flex items-start justify-between gap-2">
          <div>
            <div class="font-semibold">${esc(t.name)}</div>
            <div class="text-sm text-slate-500">${MODE_LABEL[t.type]} · ${t.participants.length} Teilnehmer</div>
          </div>
          <span class="badge badge-${t.status}">${STATUS_LABEL[t.status]}</span>
        </div>
        <div class="flex items-center justify-between mt-1">
          <div class="text-xs text-slate-500">Runde ${t.currentRound}${t.totalRounds ? ' / ' + t.totalRounds : ''}</div>
          <div class="flex gap-1">
            <button class="btn-sm" data-archive="${t.id}">${t.archived ? 'Wiederherstellen' : 'Archivieren'}</button>
            <button class="btn-sm danger" data-delete="${t.id}">Löschen</button>
          </div>
        </div>
      </div>`).join('')
    : `<p class="text-slate-500">${showArchive ? 'Keine archivierten Turniere.' : 'Noch keine Turniere.'}</p>`;
  renderTournamentDetail();
}

$('#archive-toggle').addEventListener('click', () => {
  showArchive = !showArchive;
  const first = state.tournaments.find((t) => t.archived === showArchive);
  if (first) state.selectedTournamentId = first.id;
  renderTournaments();
  renderTournamentSelects();
});

function renderTournamentDetail() {
  const box = $('#tournament-detail');
  const t = state.tournaments.find((x) => x.id === state.selectedTournamentId);
  if (!t) {
    box.innerHTML = '<p class="text-slate-500">Turnier auswählen, um Details zu sehen.</p>';
    return;
  }
  const enrolled = new Set(t.participants.map((p) => p.id));
  const dq = new Set(t.disqualifiedPlayerIds);
  const options = state.players.filter((p) => !enrolled.has(p.id))
    .map((p) => `<option value="${p.id}">${esc(p.name)} (${p.currentElo})</option>`).join('');

  let generateBtn = '';
  if (!t.archived) {
    if (t.type === 'DOUBLE_ROUND_ROBIN') {
      generateBtn = '<button class="btn-secondary" data-generate="round-robin">Runden generieren</button>';
    } else if (t.type === 'SWISS') {
      generateBtn = '<button class="btn-secondary" data-generate="next-round">Nächste Runde generieren</button>';
    }
  }

  const rows = t.participants.map((p) => {
    const out = dq.has(p.id);
    return `
        <li class="flex flex-wrap items-center justify-between gap-1 py-1">
          <span class="${out ? 'line-through text-slate-400' : ''}">${esc(p.name)}
            <span class="text-slate-500 text-sm">(${p.currentElo})</span>
            ${out ? '<span class="badge badge-CANCELLED">Disqualifiziert</span>' : ''}</span>
          ${t.archived ? '' : `<span class="flex gap-1 flex-wrap">
            <button class="btn-sm" data-points="${p.id}">± Punkte</button>
            ${out
              ? `<button class="btn-sm" data-reinstate="${p.id}">Wieder zulassen</button>`
              : `<button class="btn-sm danger" data-dq="${p.id}">Disqualifizieren</button>`}
            <button class="btn-sm danger" data-unenroll="${p.id}">Entfernen</button>
          </span>`}
        </li>`;
  }).join('') || '<li class="text-slate-500 py-1">Noch keine Teilnehmer.</li>';

  box.innerHTML = `
    <div class="flex items-start justify-between gap-2 mb-3">
      <div>
        <h2 class="h2">${esc(t.name)}</h2>
        <div class="text-sm text-slate-500">${MODE_LABEL[t.type]}${t.archived ? ' · archiviert' : ''}</div>
      </div>
      <span class="badge badge-${t.status}">${STATUS_LABEL[t.status]}</span>
    </div>
    <h3 class="lbl">Teilnehmer (${t.participants.length})</h3>
    <ul class="divide-y divide-slate-100 mb-3">${rows}</ul>
    ${t.archived ? '' : `<div class="flex gap-2 mb-3">
      <select id="enroll-select" class="inp">${options || '<option value="">Keine weiteren Spieler</option>'}</select>
      <button class="btn-primary whitespace-nowrap" data-enroll>Spieler hinzufügen</button>
    </div>`}
    ${generateBtn}`;
}

$('#tournament-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = new FormData(e.target);
  const rounds = f.get('totalRounds');
  const body = { name: f.get('name'), type: f.get('type'), totalRounds: rounds ? Number(rounds) : null };
  const created = await run(() => api('/tournaments', { method: 'POST', body: JSON.stringify(body) }),
    'Turnier erstellt');
  if (created) {
    e.target.reset();
    state.selectedTournamentId = created.id;
    await loadTournaments();
  }
});

$('#tournament-list').addEventListener('click', async (e) => {
  const archiveId = e.target.dataset.archive;
  const deleteId = e.target.dataset.delete;
  if (archiveId || deleteId) {
    const t = state.tournaments.find((x) => String(x.id) === (archiveId || deleteId));
    if (!t) return;
    if (deleteId) {
      if (!confirm(`Turnier «${t.name}» wirklich löschen?\n\nAlle Partien und Punktkorrekturen dieses Turniers werden endgültig gelöscht. `
        + 'Bereits berechnete Elo-Werte der Spieler bleiben unverändert.\n\nTipp: «Archivieren» blendet das Turnier nur aus.')) return;
      if (await run(() => api(`/tournaments/${t.id}`, { method: 'DELETE' }), 'Turnier gelöscht')) await loadTournaments();
    } else {
      const action = t.archived ? 'unarchive' : 'archive';
      if (await run(() => api(`/tournaments/${t.id}/${action}`, { method: 'PATCH' }),
        t.archived ? 'Turnier wiederhergestellt' : 'Turnier archiviert')) await loadTournaments();
    }
    return;
  }
  const card = e.target.closest('[data-tournament]');
  if (!card) return;
  state.selectedTournamentId = Number(card.dataset.tournament);
  renderTournaments();
  renderTournamentSelects();
});

function askPoints(name) {
  const input = prompt(`Punkte für ${name} ändern:\nPositiv = Bonus (z.B. 0.5), negativ = Abzug (z.B. -1)`, '-0.5');
  if (input === null) return null;
  const points = Number(input.trim().replace(',', '.'));
  if (!Number.isFinite(points) || points === 0) { toast('Ungültige Punktzahl', true); return null; }
  const reason = prompt('Grund (optional):', '');
  return { points, reason: reason === null ? null : reason.trim() || null };
}

$('#tournament-detail').addEventListener('click', async (e) => {
  const id = state.selectedTournamentId;
  const t = e.target;
  const tournament = state.tournaments.find((x) => x.id === id);
  const nameOf = (pid) => (tournament?.participants.find((p) => String(p.id) === String(pid)) || {}).name || 'Spieler';
  let promise, msg;
  if (t.matches('[data-enroll]')) {
    const playerId = $('#enroll-select').value;
    if (!playerId) return;
    promise = () => api(`/tournaments/${id}/players/${playerId}`, { method: 'POST' });
    msg = 'Spieler hinzugefügt';
  } else if (t.dataset.unenroll) {
    if (!confirm(`${nameOf(t.dataset.unenroll)} aus dem Turnier entfernen?`)) return;
    promise = () => api(`/tournaments/${id}/players/${t.dataset.unenroll}`, { method: 'DELETE' });
    msg = 'Spieler entfernt';
  } else if (t.dataset.points) {
    const input = askPoints(nameOf(t.dataset.points));
    if (!input) return;
    promise = () => api(`/tournaments/${id}/players/${t.dataset.points}/points`, {
      method: 'POST', body: JSON.stringify(input),
    });
    msg = 'Punkte angepasst';
  } else if (t.dataset.dq) {
    if (!confirm(`${nameOf(t.dataset.dq)} disqualifizieren?\n\nOffene Partien werden kampflos für die Gegner gewertet, der Spieler wird nicht mehr gepaart und steht am Ende der Rangliste.`)) return;
    promise = () => api(`/tournaments/${id}/players/${t.dataset.dq}/disqualify`, { method: 'POST' });
    msg = 'Spieler disqualifiziert';
  } else if (t.dataset.reinstate) {
    promise = () => api(`/tournaments/${id}/players/${t.dataset.reinstate}/disqualify`, { method: 'DELETE' });
    msg = 'Spieler wieder zugelassen';
  } else if (t.dataset.generate) {
    const endpoint = t.dataset.generate === 'round-robin' ? 'generate-round-robin' : 'generate-next-round';
    promise = () => api(`/tournaments/${id}/${endpoint}`, { method: 'POST' });
    msg = 'Runden generiert';
  } else {
    return;
  }
  if (await run(promise, msg)) await loadTournaments();
});

/* ---------- Tournament selects (play + standings) ---------- */

function renderTournamentSelects() {
  const opts = state.tournaments.map((t) =>
    `<option value="${t.id}" ${t.id === state.selectedTournamentId ? 'selected' : ''}>${esc(t.name)}${t.archived ? ' (Archiv)' : ''}</option>`).join('')
    || '<option value="">Keine Turniere</option>';
  $('#play-tournament').innerHTML = opts;
  $('#standings-tournament').innerHTML = opts;
  renderMatchFormPlayers();
}

function onTournamentSelect(e) {
  state.selectedTournamentId = e.target.value ? Number(e.target.value) : null;
  renderTournaments();
  renderTournamentSelects();
  if (e.target.id === 'play-tournament') loadMatches();
  else loadStandings();
}
$('#play-tournament').addEventListener('change', onTournamentSelect);
$('#standings-tournament').addEventListener('change', onTournamentSelect);

/* ---------- Matches ---------- */

let allMatches = [];

function renderMatchFormPlayers() {
  const t = state.tournaments.find((x) => x.id === state.selectedTournamentId);
  const opts = (t ? t.participants.filter((p) => !t.disqualifiedPlayerIds.includes(p.id)) : []).map((p) =>
    `<option value="${p.id}">${esc(p.name)} (${p.currentElo})</option>`).join('');
  const form = $('#match-form');
  form.whitePlayerId.innerHTML = opts;
  form.blackPlayerId.innerHTML = opts;
  if (form.blackPlayerId.options.length > 1) form.blackPlayerId.selectedIndex = 1;
  if (t) form.roundNumber.value = t.currentRound;
}

async function loadMatches(forceRound = null) {
  const id = state.selectedTournamentId;
  if (!id) {
    allMatches = [];
    $('#play-round').innerHTML = '';
    renderMatches();
    renderPlayAction();
    return;
  }
  allMatches = await run(() => api(`/tournaments/${id}/matches`)) || [];
  const rounds = [...new Set(allMatches.map((m) => m.roundNumber))].sort((a, b) => a - b);
  const sel = $('#play-round');
  const previous = sel.value;
  sel.innerHTML = '<option value="">Alle Runden</option>'
    + rounds.map((r) => `<option value="${r}">Runde ${r}</option>`).join('');
  if (forceRound !== null && rounds.includes(forceRound)) sel.value = String(forceRound);
  else if (previous && rounds.includes(Number(previous))) sel.value = previous;
  else {
    const t = state.tournaments.find((x) => x.id === id);
    if (t && rounds.includes(t.currentRound)) sel.value = String(t.currentRound);
  }
  renderMatches();
  renderPlayAction();
}

$('#play-round').addEventListener('change', () => { renderMatches(); renderPlayAction(); });

/** Next-round button on the play tab: generates the next round and jumps to it. */
function renderPlayAction() {
  const btn = $('#play-next-round');
  const t = state.tournaments.find((x) => x.id === state.selectedTournamentId);
  if (!t || t.type === 'FREE_PLAY') { btn.classList.add('hidden'); return; }
  btn.classList.remove('hidden');
  if (t.type === 'SWISS') {
    btn.textContent = 'Nächste Runde generieren';
  } else if (!allMatches.length) {
    btn.textContent = 'Runden generieren';
  } else {
    const rounds = [...new Set(allMatches.map((m) => m.roundNumber))];
    const shown = Number($('#play-round').value) || t.currentRound;
    btn.textContent = 'Nächste Runde anzeigen';
    btn.disabled = !rounds.some((r) => r > shown);
    return;
  }
  btn.disabled = false;
}

$('#play-next-round').addEventListener('click', async () => {
  const id = state.selectedTournamentId;
  const t = state.tournaments.find((x) => x.id === id);
  if (!t) return;
  let target;
  if (t.type === 'SWISS') {
    if (!await run(() => api(`/tournaments/${id}/generate-next-round`, { method: 'POST' }), 'Nächste Runde generiert')) return;
  } else if (!allMatches.length) {
    if (!await run(() => api(`/tournaments/${id}/generate-round-robin`, { method: 'POST' }), 'Runden generiert')) return;
  } else {
    const shown = Number($('#play-round').value) || t.currentRound;
    target = Math.min(...allMatches.map((m) => m.roundNumber).filter((r) => r > shown));
  }
  await loadTournaments();
  const updated = state.tournaments.find((x) => x.id === id);
  await loadMatches(target ?? (updated ? updated.currentRound : null));
});

function deltaText(change) {
  if (change === null || change === undefined) return '';
  const cls = change >= 0 ? 'delta-pos' : 'delta-neg';
  return `<span class="${cls}">${change > 0 ? '+' : ''}${change}</span>`;
}

function resultText(r) {
  return { WHITE_WINS: '1 - 0', BLACK_WINS: '0 - 1', DRAW: '½ - ½' }[r] || '';
}

function renderMatches() {
  const round = $('#play-round').value;
  const matches = allMatches.filter((m) => !round || String(m.roundNumber) === round);
  $('#play-info').textContent = matches.length ? `${matches.length} Partien` : '';
  $('#match-list').innerHTML = matches.length
    ? matches.map((m) => {
      if (m.bye) {
        return `
      <div class="card bye-card">
        <div class="flex justify-between items-center mb-2">
          <span class="text-xs text-slate-500">Runde ${m.roundNumber}</span>
          <span class="badge badge-BYE">Spielfrei</span>
        </div>
        <div class="text-center">
          <div class="font-semibold">&#9812; ${esc(m.whitePlayerName)}</div>
          <div class="text-sm text-slate-500">hat in dieser Runde spielfrei (1 Punkt, keine Elo-Änderung)</div>
        </div>
      </div>`;
      }
      const done = m.status === 'COMPLETED';
      const scheduled = m.status === 'SCHEDULED';
      return `
      <div class="card">
        <div class="flex justify-between items-center mb-2">
          <span class="text-xs text-slate-500">Runde ${m.roundNumber}</span>
          <span class="badge badge-${m.status}">${STATUS_LABEL[m.status]}</span>
        </div>
        <div class="grid grid-cols-[1fr_auto_1fr] items-center gap-2 text-center">
          <div class="text-right"><div class="font-semibold">&#9812; ${esc(m.whitePlayerName)}</div>
            ${done ? `<div class="text-xs">${m.whiteEloBefore} ${deltaText(m.whiteEloChange)}</div>` : ''}</div>
          <div class="font-bold text-lg px-2">${done ? resultText(m.result) : 'vs'}</div>
          <div class="text-left"><div class="font-semibold">&#9818; ${esc(m.blackPlayerName)}</div>
            ${done ? `<div class="text-xs">${m.blackEloBefore} ${deltaText(m.blackEloChange)}</div>` : ''}</div>
        </div>
        ${done ? `<div class="text-center mt-2 text-sm">Elo-Delta: ${deltaText(m.whiteEloChange)} / ${deltaText(m.blackEloChange)}</div>` : ''}
        ${scheduled ? `
        <div class="flex flex-wrap justify-center gap-2 mt-3">
          <button class="btn-sm result" data-result="WHITE_WINS" data-match="${m.id}">1 - 0</button>
          <button class="btn-sm result" data-result="DRAW" data-match="${m.id}">½ - ½</button>
          <button class="btn-sm result" data-result="BLACK_WINS" data-match="${m.id}">0 - 1</button>
          <button class="btn-sm danger" data-cancel="${m.id}">Absagen</button>
        </div>` : ''}
      </div>`;
    }).join('')
    : '<p class="text-slate-500">Keine Partien. Paarungen im Tab «Turniere» generieren oder manuell anlegen.</p>';
}

$('#match-list').addEventListener('click', async (e) => {
  const t = e.target;
  let promise, msg;
  if (t.dataset.result) {
    promise = () => api(`/matches/${t.dataset.match}/result`, {
      method: 'POST', body: JSON.stringify({ result: t.dataset.result }),
    });
    msg = 'Ergebnis erfasst';
  } else if (t.dataset.cancel) {
    promise = () => api(`/matches/${t.dataset.cancel}/cancel`, { method: 'PATCH' });
    msg = 'Partie abgesagt';
  } else {
    return;
  }
  if (await run(promise, msg)) {
    await Promise.all([loadMatches(), loadPlayers(), loadTournaments()]);
  }
});

$('#match-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const id = state.selectedTournamentId;
  if (!id) return toast('Zuerst ein Turnier wählen', true);
  const f = new FormData(e.target);
  if (!f.get('whitePlayerId') || !f.get('blackPlayerId')) {
    return toast('Zuerst Teilnehmer im Turnier einschreiben', true);
  }
  if (f.get('whitePlayerId') === f.get('blackPlayerId')) {
    return toast('Weiss und Schwarz müssen verschiedene Spieler sein', true);
  }
  const body = {
    roundNumber: Number(f.get('roundNumber')),
    whitePlayerId: Number(f.get('whitePlayerId')),
    blackPlayerId: Number(f.get('blackPlayerId')),
  };
  if (await run(() => api(`/tournaments/${id}/matches`, { method: 'POST', body: JSON.stringify(body) }),
    'Paarung angelegt')) {
    await Promise.all([loadMatches(), loadTournaments()]);
  }
});

/* ---------- Standings ---------- */

const MEDAL = ['🥇', '🥈', '🥉'];

function formatPoints(p) {
  return Number.isInteger(p) ? String(p) : p.toFixed(1).replace(/\.0$/, '');
}

async function loadStandings() {
  const id = state.selectedTournamentId;
  const body = $('#standings-rows');
  const podium = $('#podium');
  if (!id) {
    podium.innerHTML = '';
    body.innerHTML = '<tr><td colspan="6" class="text-slate-500 py-4">Kein Turnier gewählt.</td></tr>';
    return;
  }
  const rows = await run(() => api(`/tournaments/${id}/standings`)) || [];
  const ranked = rows.filter((r) => !r.disqualified);

  // podium order: 2nd, 1st, 3rd
  const top = ranked.slice(0, 3);
  podium.innerHTML = top.length
    ? [1, 0, 2].filter((i) => top[i]).map((i) => `
      <div class="podium-step place-${i + 1}">
        <div class="podium-medal">${MEDAL[i]}</div>
        <div class="podium-name">${esc(top[i].name)}</div>
        <div class="podium-pts">${formatPoints(top[i].points)} Pkt · Elo ${top[i].elo}</div>
        <div class="podium-block">${i + 1}</div>
      </div>`).join('')
    : '';

  body.innerHTML = rows.length
    ? rows.map((r) => {
      const place = !r.disqualified && r.rank <= 3 ? r.rank : 0;
      const adj = r.adjustment
        ? ` <span class="${r.adjustment > 0 ? 'delta-pos' : 'delta-neg'}" title="${esc(r.adjustmentNote || '')}">(${r.adjustment > 0 ? '+' : ''}${formatPoints(r.adjustment)})</span>`
        : '';
      return `
      <tr class="${place ? 'row-place-' + place : ''} ${r.disqualified ? 'row-dq' : ''}">
        <td class="font-bold">${r.disqualified ? '–' : (place ? MEDAL[place - 1] : r.rank)}</td>
        <td class="font-medium">${esc(r.name)}${r.disqualified ? ' <span class="badge badge-CANCELLED">Disqualifiziert</span>' : ''}
          ${r.byes ? `<span class="badge badge-BYE" title="Spielfrei in ${r.byes} Runde(n)">Spielfrei ×${r.byes}</span>` : ''}</td>
        <td class="text-right tabular-nums">${r.gamesPlayed}</td>
        <td class="text-right tabular-nums font-semibold">${formatPoints(r.points)}${adj}</td>
        <td class="text-right tabular-nums">${formatPoints(r.buchholz)}</td>
        <td class="text-right tabular-nums">${r.elo}</td>
      </tr>`;
    }).join('')
    : '<tr><td colspan="6" class="text-slate-500 py-4">Keine Teilnehmer.</td></tr>';
}

/* ---------- Player history ---------- */

const OUTCOME = {
  WIN: ['Sieg', 'delta-pos'], LOSS: ['Niederlage', 'delta-neg'], DRAW: ['Remis', ''],
  BYE: ['Spielfrei', ''], SCHEDULED: ['Angesetzt', ''], CANCELLED: ['Abgesagt', 'text-slate-400'],
};

async function openHistory(playerId) {
  const h = await run(() => api(`/players/${playerId}/history`));
  if (!h) return;
  const rankText = (t) => t.disqualified ? 'disqualifiziert'
    : (t.rank && t.rank <= 3 ? MEDAL[t.rank - 1] + ' ' : '') + `Platz ${t.rank} von ${t.participants}`;
  $('#history-title').textContent = `${h.player.name} · Elo ${h.player.currentElo}`;
  $('#history-body').innerHTML = h.tournaments.length ? h.tournaments.map((t) => `
    <div class="card mb-3 border border-slate-200">
      <div class="flex flex-wrap justify-between items-start gap-2 mb-2">
        <div>
          <div class="font-semibold">${esc(t.name)}${t.archived ? ' <span class="text-xs text-slate-500">(Archiv)</span>' : ''}</div>
          <div class="text-sm text-slate-500">${MODE_LABEL[t.type]} · ${STATUS_LABEL[t.status]}</div>
        </div>
        <div class="text-right text-sm"><div class="font-semibold">${rankText(t)}</div>
          <div class="text-slate-500">${formatPoints(t.points)} Punkte</div></div>
      </div>
      ${t.games.length ? `<table class="tbl"><thead><tr><th>Runde</th><th>Gegner</th><th>Farbe</th><th>Ergebnis</th><th class="text-right">Elo</th></tr></thead><tbody>
        ${t.games.map((g) => `<tr>
          <td>${g.round}</td>
          <td>${g.opponentName ? esc(g.opponentName) : '–'}</td>
          <td>${g.color === 'WHITE' ? 'Weiss' : g.color === 'BLACK' ? 'Schwarz' : '–'}</td>
          <td class="${OUTCOME[g.outcome][1]}">${OUTCOME[g.outcome][0]}</td>
          <td class="text-right">${g.eloChange === null || g.eloChange === undefined ? '' : (g.eloBefore + ' ' + deltaText(g.eloChange))}</td>
        </tr>`).join('')}</tbody></table>` : '<p class="text-sm text-slate-500">Noch keine Partien.</p>'}
    </div>`).join('')
    : '<p class="text-slate-500">Dieser Spieler hat noch an keinem Turnier teilgenommen.</p>';
  $('#history-modal').classList.remove('hidden');
}

$('#history-close').addEventListener('click', () => $('#history-modal').classList.add('hidden'));
$('#history-modal').addEventListener('click', (e) => {
  if (e.target.id === 'history-modal') e.target.classList.add('hidden');
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') $('#history-modal').classList.add('hidden');
});

/* ---------- Init ---------- */

(async function init() {
  showTab('players');
  await run(async () => {
    await loadPlayers();
    await loadTournaments();
  });
})();
