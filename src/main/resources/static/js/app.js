const $ = (id) => document.getElementById(id);
let token = localStorage.getItem('token');
let user = localStorage.getItem('user');
let convoId = null;
let mode = 'login';

/* ---------- API helper ---------- */
async function api(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
  });
  const text = await res.text();
  let data = text;
  try { data = JSON.parse(text); } catch { /* plain text (login token) */ }
  if (res.status === 401 && token) { logout(); throw new Error('Session expired. Please log in again.'); }
  if (!res.ok) throw new Error((data && data.error) || 'Request failed (' + res.status + ')');
  return data;
}

/* ---------- Auth ---------- */
function setMode(m) {
  mode = m;
  $('tab-login').classList.toggle('active', m === 'login');
  $('tab-register').classList.toggle('active', m === 'register');
  $('auth-submit').textContent = m === 'login' ? 'Log in' : 'Create account';
  $('password').autocomplete = m === 'login' ? 'current-password' : 'new-password';
  $('auth-msg').textContent = '';
}
$('tab-login').onclick = () => setMode('login');
$('tab-register').onclick = () => setMode('register');

$('auth-form').onsubmit = async (e) => {
  e.preventDefault();
  const body = JSON.stringify({ username: $('username').value.trim(), password: $('password').value });
  const msg = $('auth-msg');
  msg.className = 'msg'; msg.textContent = '';
  try {
    if (mode === 'register') {
      await api('/api/auth/register', { method: 'POST', body });
      setMode('login');
      msg.className = 'msg ok'; msg.textContent = 'Account created. You can log in now.';
      return;
    }
    token = await api('/api/auth/login', { method: 'POST', body });
    user = $('username').value.trim();
    localStorage.setItem('token', token);
    localStorage.setItem('user', user);
    $('password').value = '';
    showApp();
  } catch (err) { msg.textContent = err.message; }
};

function logout() {
  token = null; user = null; convoId = null;
  localStorage.removeItem('token'); localStorage.removeItem('user');
  $('app').hidden = true; $('auth').hidden = false;
}
$('logout').onclick = logout;

/* ---------- Chat UI ---------- */
function addBubble(role, text) {
  const empty = document.querySelector('.empty');
  if (empty) empty.remove();
  const el = document.createElement('div');
  el.className = 'bubble ' + role;
  el.textContent = text; // textContent: model output is never parsed as HTML
  $('messages').appendChild(el);
  $('messages').scrollTop = $('messages').scrollHeight;
  return el;
}

function addSources(sources) {
  if (!sources || !sources.length) return;
  const d = document.createElement('details');
  d.className = 'sources';
  const sum = document.createElement('summary');
  sum.textContent = 'Sources (' + sources.length + ')';
  const ol = document.createElement('ol');
  for (const s of sources) {
    const li = document.createElement('li');
    const b = document.createElement('b');
    b.textContent = '[' + s.index + '] ' + s.filename;
    li.append(b, ' — ' + s.snippet);
    ol.appendChild(li);
  }
  d.append(sum, ol);
  $('messages').appendChild(d);
}

function newChat() {
  convoId = crypto.randomUUID();
  $('messages').innerHTML = '<div class="empty">Start a conversation.</div>';
  document.querySelectorAll('#convos li').forEach((li) => li.classList.remove('active'));
  $('input').focus();
}
$('new-chat').onclick = newChat;

async function loadConvos() {
  try {
    const list = await api('/api/chat/conversations');
    const ul = $('convos');
    ul.innerHTML = '';
    for (const c of list) {
      const li = document.createElement('li');
      li.textContent = c.title;
      li.title = c.title;
      li.dataset.id = c.id;
      if (c.id === convoId) li.classList.add('active');
      li.onclick = () => openConvo(c.id);
      ul.appendChild(li);
    }
  } catch { /* sidebar is non-critical */ }
}

async function openConvo(id) {
  try {
    const msgs = await api('/api/chat/' + encodeURIComponent(id) + '/messages');
    convoId = id;
    $('messages').innerHTML = '';
    msgs.forEach((m) => addBubble(m.role, m.message));
    loadConvos();
  } catch (err) { addBubble('error', err.message); }
}

$('chat-form').onsubmit = async (e) => {
  e.preventDefault();
  const text = $('input').value.trim();
  if (!text) return;
  $('input').value = ''; $('input').style.height = 'auto';
  addBubble('user', text);
  const typing = addBubble('assistant typing', 'Thinking…');
  $('send').disabled = true;
  try {
    const data = await api('/api/chat', { method: 'POST', body: JSON.stringify({ conversationId: convoId, message: text, useDocuments: $('use-docs').checked }) });
    typing.className = 'bubble assistant';
    typing.textContent = data.response;
    addSources(data.sources);
    loadConvos();
    setTimeout(loadMemories, 4000); // memory is extracted in the background

  } catch (err) {
    typing.className = 'bubble error';
    typing.textContent = err.message;
  } finally {
    $('send').disabled = false;
    $('input').focus();
    $('messages').scrollTop = $('messages').scrollHeight;
  }
};

// Enter sends, Shift+Enter adds a newline; textarea grows with content
$('input').addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); $('chat-form').requestSubmit(); }
});
$('input').addEventListener('input', (e) => {
  e.target.style.height = 'auto';
  e.target.style.height = e.target.scrollHeight + 'px';
});

/* ---------- Knowledge base (RAG) ---------- */
function miniItem(ul, label, title, onDelete) {
  const li = document.createElement('li');
  const span = document.createElement('span');
  span.textContent = label; span.title = title || label;
  const del = document.createElement('button');
  del.textContent = '✕'; del.title = 'Delete'; del.onclick = onDelete;
  li.append(span, del);
  ul.appendChild(li);
}

async function loadDocs() {
  try {
    const docs = await api('/api/documents');
    const ul = $('docs'); ul.innerHTML = '';
    if (!docs.length) ul.innerHTML = '<li><span>No documents yet</span></li>';
    for (const d of docs) {
      miniItem(ul, d.filename, d.chunks + ' chunks', async () => {
        if (!confirm('Delete ' + d.filename + '?')) return;
        try { await api('/api/documents/' + d.id, { method: 'DELETE' }); loadDocs(); }
        catch (err) { $('kb-msg').textContent = err.message; }
      });
    }
  } catch { /* non-critical */ }
}

$('upload').onclick = () => $('file').click();
$('file').onchange = async () => {
  const f = $('file').files[0];
  if (!f) return;
  const msg = $('kb-msg');
  msg.className = 'msg ok'; msg.textContent = 'Indexing ' + f.name + '… (embedding can take a while)';
  $('upload').disabled = true;
  try {
    const fd = new FormData(); fd.append('file', f);
    const d = await api('/api/documents', { method: 'POST', body: fd });
    msg.textContent = 'Indexed ' + d.filename + ' (' + d.chunks + ' chunks).';
    loadDocs();
  } catch (err) { msg.className = 'msg'; msg.textContent = err.message; }
  finally { $('upload').disabled = false; $('file').value = ''; }
};

/* ---------- Long-term memory ---------- */
async function loadMemories() {
  try {
    const mems = await api('/api/memory');
    const ul = $('memories'); ul.innerHTML = '';
    if (!mems.length) ul.innerHTML = '<li><span>Nothing yet</span></li>';
    for (const m of mems) {
      miniItem(ul, m.key + ': ' + m.value, m.key + ': ' + m.value, async () => {
        await api('/api/memory/' + m.id, { method: 'DELETE' }); loadMemories();
      });
    }
  } catch { /* non-critical */ }
}

/* ---------- Boot ---------- */
function showApp() {
  $('auth').hidden = true; $('app').hidden = false;
  $('whoami').textContent = user || '';
  newChat();
  loadConvos();
  loadDocs();
  loadMemories();
}
if (token) showApp();
