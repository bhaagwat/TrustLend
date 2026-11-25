package com.trustlend.web;

/** The single-page mobile app served by {@link PhoneServer} (HTML, CSS and JavaScript in one file). */
final class PhonePage {

    private PhonePage() { }

    static final String HTML = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>TrustLend</title>
<style>
  :root { --ink:#1c2140; --ink2:#2b2f63; --teal:#0f766e; --saffron:#f29f05; --paper:#f5f3ee; --line:#e6e2d8;
          --text:#1f2333; --muted:#6b6f80; --green:#0f9d58; --red:#d93025; }
  * { box-sizing: border-box; }
  body { margin:0; font-family: -apple-system, "Segoe UI", Roboto, sans-serif; background: var(--paper); color: var(--text); }
  header { background: linear-gradient(135deg, var(--ink), var(--ink2) 60%, var(--teal)); color:#fff; padding: 22px 18px 70px; }
  .brand { display:flex; align-items:center; gap:10px; font-weight:700; font-size:20px; }
  .mark { width:34px; height:34px; border-radius:10px; background:var(--saffron); color:var(--ink); display:grid; place-items:center; font-weight:800; }
  header p { margin:8px 0 0; color:#cfd3ee; font-size:14px; }
  main { padding: 0 14px 40px; margin-top:-52px; max-width:520px; margin-left:auto; margin-right:auto; }
  .card { background:#fff; border:1px solid var(--line); border-radius:16px; padding:16px; margin-bottom:14px; box-shadow:0 4px 18px #1c214014; }
  h2 { margin:0 0 10px; font-size:16px; }
  label { display:block; font-size:12px; font-weight:700; color:var(--muted); margin:10px 0 4px; }
  input { width:100%; padding:12px; border:1px solid var(--line); border-radius:10px; font-size:16px; }
  button { border:0; border-radius:10px; padding:12px 14px; font-size:15px; font-weight:700; cursor:pointer; }
  .primary { background:var(--teal); color:#fff; width:100%; margin-top:14px; }
  .accent { background:var(--saffron); color:var(--ink); }
  .ghost { background:#fff; border:1px solid var(--line); color:var(--text); }
  .danger { background:#fde7e5; color:var(--red); }
  .ok { background:#dcf3e6; color:#0b7a43; }
  .row { display:flex; gap:8px; } .row > * { flex:1; }
  .score { display:flex; align-items:center; gap:16px; }
  .ring { position:relative; width:130px; height:130px; flex:none; }
  .ring svg { transform: rotate(-90deg); }
  .ring .num { position:absolute; inset:0; display:grid; place-items:center; font-size:34px; font-weight:800; }
  .chip { display:inline-block; padding:4px 10px; border-radius:20px; font-size:12px; font-weight:700; color:#fff; }
  .muted { color:var(--muted); font-size:13px; }
  .bar { height:8px; background:#eeebe3; border-radius:5px; overflow:hidden; margin:4px 0 10px; }
  .bar > div { height:100%; background:var(--teal); border-radius:5px; transition: width .8s; }
  .claim { border:1px solid var(--line); border-radius:12px; padding:12px; margin-bottom:10px; }
  .claim b { display:block; margin-bottom:4px; }
  .tag { font-size:11px; font-weight:700; padding:2px 8px; border-radius:10px; background:#dcf3e6; color:#0b7a43; }
  .tag.late { background:#fff3d1; color:#a36f00; }
  .alert { background:#fff3db; border:1px solid #f3d78c; border-radius:12px; padding:10px 12px; margin-bottom:10px; font-size:14px; }
  .result { border-radius:12px; padding:12px; margin-top:12px; font-size:14px; }
  .result.good { background:#e5f4e8; } .result.bad { background:#fde2df; } .result.mid { background:#fff3d1; }
  ul { padding-left:18px; margin:6px 0; } li { margin:3px 0; font-size:14px; }
  .error { color:var(--red); font-size:14px; margin-top:8px; }
  .hidden { display:none; }
</style>
</head>
<body>
<header>
  <div class="brand"><div class="mark">T</div> TrustLend</div>
  <p id="sub">Credit built on community trust</p>
</header>
<main>
  <section id="login" class="card">
    <h2>Borrower login</h2>
    <p class="muted">Use the same username and password as on the laptop (demo: javed / demo123).</p>
    <label>USERNAME</label><input id="u" autocapitalize="none" autocomplete="username">
    <label>PASSWORD</label><input id="p" type="password" autocomplete="current-password">
    <button class="primary" onclick="login()">Log in</button>
    <div id="loginError" class="error"></div>
  </section>

  <section id="home" class="hidden">
    <div class="card">
      <div class="score">
        <div class="ring">
          <svg width="130" height="130"><circle cx="65" cy="65" r="54" stroke="#eeebe3" stroke-width="12" fill="none"/>
            <circle id="arc" cx="65" cy="65" r="54" stroke="#0f9d58" stroke-width="12" fill="none" stroke-linecap="round"
                    stroke-dasharray="339.3" stroke-dashoffset="339.3" style="transition: stroke-dashoffset 1s"/></svg>
          <div class="num" id="score">-</div>
        </div>
        <div>
          <div id="name" style="font-weight:800;font-size:18px"></div>
          <div class="muted" id="tlid"></div>
          <div style="margin:8px 0"><span class="chip" id="band"></span></div>
          <div class="muted" id="offer"></div>
        </div>
      </div>
    </div>

    <div class="card" id="claimsCard">
      <h2>Waiting for your confirmation</h2>
      <div id="claims"></div>
    </div>

    <div class="card">
      <h2>Upload your UPI statement</h2>
      <p class="muted">Export it as CSV from PhonePe, Google Pay or your bank. Money you received counts as sales.</p>
      <input type="file" id="file" accept=".csv,text/csv,text/plain" onchange="upload()">
      <div class="row" style="margin-top:10px">
        <button class="ghost" onclick="sample('good')">Try a sample</button>
        <button class="ghost" onclick="sample('bad')">Try a fake one</button>
      </div>
      <div id="result"></div>
    </div>

    <div class="card">
      <h2>What my score is made of</h2>
      <div id="factors"></div>
      <h2 style="margin-top:14px">Evidence</h2>
      <ul id="evidence"></ul>
    </div>
    <button class="ghost" style="width:100%" onclick="logout()">Log out</button>
  </section>
</main>
<script>
let token = null;
try { token = sessionStorage.getItem("tl-token"); } catch (e) { token = null; }

async function call(path, options) {
  const res = await fetch(path, options || {});
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || "Something went wrong");
  return data;
}
function q(extra) { return "?token=" + encodeURIComponent(token) + (extra || ""); }
function esc(s) { const d = document.createElement("div"); d.textContent = s; return d.innerHTML; }

async function login() {
  document.getElementById("loginError").textContent = "";
  try {
    const body = new URLSearchParams({ username: document.getElementById("u").value, password: document.getElementById("p").value });
    const data = await call("/api/login", { method: "POST", body: body });
    token = data.token;
    try { sessionStorage.setItem("tl-token", token); } catch (e) { }
    await load();
  } catch (e) { document.getElementById("loginError").textContent = e.message; }
}
function logout() {
  token = null;
  try { sessionStorage.removeItem("tl-token"); } catch (e) { }
  show(false);
}
function show(home) {
  document.getElementById("login").classList.toggle("hidden", home);
  document.getElementById("home").classList.toggle("hidden", !home);
}
async function load() {
  try { render(await call("/api/me" + q())); show(true); }
  catch (e) { show(false); }
}
function render(me) {
  document.getElementById("sub").textContent = "Namaste, " + me.name.split(" ")[0];
  document.getElementById("name").textContent = me.name;
  document.getElementById("tlid").textContent = "TrustLend ID " + me.id;
  const band = document.getElementById("band");
  band.textContent = me.band; band.style.background = me.color;
  document.getElementById("offer").textContent = me.offer;
  animate(document.getElementById("score"), me.score);
  const frac = Math.max(0, Math.min(1, (me.score - 300) / 600));
  const arc = document.getElementById("arc");
  arc.style.stroke = me.color;
  arc.style.strokeDashoffset = 339.3 * (1 - frac);
  document.getElementById("factors").innerHTML = me.factors.map(f =>
    "<div class='muted'>" + esc(f.name) + " · " + f.value + "%</div><div class='bar'><div style='width:" + f.value + "%'></div></div>").join("");
  document.getElementById("evidence").innerHTML = me.evidence.length
    ? me.evidence.map(e => "<li>" + esc(e) + "</li>").join("") : "<li>No evidence yet - upload a statement</li>";
  const claims = document.getElementById("claims");
  if (!me.claims.length) { claims.innerHTML = "<p class='muted'>Nothing to confirm - you're all caught up.</p>"; }
  else {
    claims.innerHTML = "<div class='alert'>Confirm the true ones - they then count fully towards your score.</div>" +
      me.claims.map(c => "<div class='claim'><b>" + esc(c.lender) + "</b><div>" + esc(c.text) + "</div>" +
        "<div style='margin:8px 0'><span class='tag " + (c.onTime ? "" : "late") + "'>" + (c.onTime ? "On time" : "Late") + "</span></div>" +
        "<div class='row'><button class='ok' onclick='confirmClaim(" + c.id + ")'>Yes, I paid</button>" +
        "<button class='danger' onclick='dispute(" + c.id + ")'>This is wrong</button></div></div>").join("");
  }
}
function animate(el, to) {
  const from = parseInt(el.textContent) || 300, start = performance.now();
  function step(t) {
    const k = Math.min(1, (t - start) / 900);
    el.textContent = Math.round(from + (to - from) * (1 - Math.pow(1 - k, 3)));
    if (k < 1) requestAnimationFrame(step);
  }
  requestAnimationFrame(step);
}
async function confirmClaim(id) {
  try { render(await call("/api/confirm" + q("&id=" + id), { method: "POST" })); }
  catch (e) { alert(e.message); }
}
async function dispute(id) {
  const reason = prompt("What is wrong with this report?", "I never borrowed from them");
  if (reason === null) return;
  try { render(await call("/api/dispute" + q("&id=" + id + "&reason=" + encodeURIComponent(reason)), { method: "POST" })); }
  catch (e) { alert(e.message); }
}
function showResult(r) {
  const cls = r.status === "Accepted" ? "good" : r.status === "Rejected" ? "bad" : "mid";
  let html = "<div class='result " + cls + "'><b>Quality " + r.quality + "/100 - " + esc(r.status) + "</b>";
  if (r.positives.length) html += "<ul>" + r.positives.map(p => "<li>&#10003; " + esc(p) + "</li>").join("") + "</ul>";
  if (r.issues.length) html += "<ul>" + r.issues.map(p => "<li>&#9888; " + esc(p) + "</li>").join("") + "</ul>";
  html += "<div>Score: " + r.before + " &rarr; <b>" + r.after + "</b></div></div>";
  document.getElementById("result").innerHTML = html;
  load();
}
function upload() {
  const f = document.getElementById("file").files[0];
  if (!f) return;
  const reader = new FileReader();
  reader.onload = async () => {
    try { showResult(await call("/api/upload" + q("&name=" + encodeURIComponent(f.name)), { method: "POST", body: reader.result })); }
    catch (e) { document.getElementById("result").innerHTML = "<div class='error'>" + esc(e.message) + "</div>"; }
  };
  reader.readAsText(f);
}
async function sample(kind) {
  try { showResult(await call("/api/sample" + q("&kind=" + kind), { method: "POST" })); }
  catch (e) { document.getElementById("result").innerHTML = "<div class='error'>" + esc(e.message) + "</div>"; }
}
if (token) load();
</script>
</body>
</html>
""";
}
