let allGroups = [];
let scopeGroup = "";         // selected group for studying ("" means all)
let allCardsMode = true; // studying all cards across groups    // "All Cards" button (flat mode)
let activeTab = "active";    // active | unsure | learned | custom

// Learned tab can be viewed as a list (default) or studied like a deck
let learnedViewMode = "list"; // list | study

// Custom Set view filter
let customViewMode = "all"; // all | unsure | learned

function updateFilterHighlight(){
  const btn = $("allCardsBtn");
  const sel = $("groupSelect");
  if(!btn || !sel) return;
  const usingAll = !!allCardsMode || !sel.value;
  btn.classList.toggle("filterActive", usingAll);
  sel.classList.toggle("filterActive", !usingAll);
}


function updateLearnedViewHighlight(){
  const wrap = $("learnedViewToggle");
  if(!wrap) return;
  const show = (activeTab === "learned");
  wrap.classList.toggle("hidden", !show);
  const bList = $("learnedViewListBtn");
  const bStudy = $("learnedViewStudyBtn");
  if(bList) bList.classList.toggle("active", learnedViewMode === "list");
  if(bStudy) bStudy.classList.toggle("active", learnedViewMode === "study");
}

function updateCustomViewHighlight(){
  const wrap = $("customViewToggle");
  if(!wrap) return;
  const show = (activeTab === "custom");
  wrap.classList.toggle("hidden", !show);
  const bAll = $("customViewAllBtn");
  const bUnsure = $("customViewUnsureBtn");
  const bLearned = $("customViewLearnedBtn");
  if(bAll) bAll.classList.toggle("active", customViewMode === "all");
  if(bUnsure) bUnsure.classList.toggle("active", customViewMode === "unsure");
  if(bLearned) bLearned.classList.toggle("active", customViewMode === "learned");
}

let deck = [];
let deckIndex = 0;

let settingsAll = null;      // global settings
let settingsGroup = {};      // group overrides (loaded on demand)

const $ = (id) => document.getElementById(id);
function bind(id, evt, fn){
  const el = $(id);
  if(!el){ console.warn("Missing element:", id); return; }
  el.addEventListener(evt, fn);
}

let currentUser = null; // {id, username, display_name}

function isAdminUser(){
  try{
    return !!(currentUser && (currentUser.username||"").toString().trim().toLowerCase() === "sidscri");
  } catch(e){
    return false;
  }
}
let appInitialized = false;

let aiStatus = { openai_available: false, openai_model: "", gemini_available: false, gemini_model: "", selected_provider: "auto" };

async function postLoginInit(){
  if(appInitialized) return;
  
  // Load saved active deck from settings FIRST
  try {
    const settings = await jget("/api/settings?scope=all");
    if(settings.activeDeckId){
      activeDeckId = settings.activeDeckId;
    }
  } catch(e){}
  
  // Load decks to get deck names for header
  try {
    currentDecks = await jget("/api/decks");
    updateHeaderDeckName();
  } catch(e){}
  
  await loadGroups();
  await loadHealth();
  try{ aiStatus = await jget("/api/ai"); } catch(e){ aiStatus = { openai_available:false, openai_model:"", gemini_available:false, gemini_model:"", selected_provider:"auto" }; }
  await refreshCounts();
  // default start view
  setTab("active");
  appInitialized = true;
}


function showAuthOverlay(){ $("authOverlay").classList.remove("hidden"); }
function hideAuthOverlay(){ $("authOverlay").classList.add("hidden"); }

function setAuthView(view){
  $("loginBox").classList.toggle("hidden", view !== "login");
  $("registerBox").classList.toggle("hidden", view !== "register");
}

async function loadVersionIntoMenu(){
  try{
    const v = await jget("/api/version");
    const el = $("userMenuVersion");
    if(el) el.textContent = `Version: ${v.version} (build ${v.build})`;
    const adminLink = $("userMenuAdmin");
    if(adminLink){
      adminLink.style.display = isAdminUser() ? "block" : "none";
    }
  }catch(e){
    const el = $("userMenuVersion");
    if(el) el.textContent = "Version: unavailable";
  }
}

function toggleUserMenu(force){
  const menu = $("userMenu");
  const line = $("userLine");
  if(!menu || !line) return;
  const isHidden = menu.classList.contains("hidden");
  const wantOpen = (force === true) ? true : (force === false) ? false : isHidden;
  menu.classList.toggle("hidden", !wantOpen);
  line.setAttribute("aria-expanded", wantOpen ? "true" : "false");
  if(wantOpen){
    loadVersionIntoMenu();
  }
}

function wireUserMenu(){
  const line = $("userLine");
  const menu = $("userMenu");
  if(!line || !menu) return;

  line.addEventListener("click", (e) => {
    if(!currentUser) return;
    e.stopPropagation();
    toggleUserMenu();
  });

  line.addEventListener("keydown", (e) => {
    if(!currentUser) return;
    if(e.key === "Enter" || e.key === " "){
      e.preventDefault();
      toggleUserMenu();
    }
  });

  document.addEventListener("click", (e) => {
    if(menu.classList.contains("hidden")) return;
    if(e.target !== line && !menu.contains(e.target)){
      toggleUserMenu(false);
    }
  });
}

function setUserLine(){
  if(currentUser){
    $("userLine").textContent = `User: ${currentUser.display_name || currentUser.username}`;
  } else {
    $("userLine").textContent = "";
  }
}

function clearAuthForms(){
  $("loginUsername").value = "";
  $("loginPassword").value = "";
  $("regUsername").value = "";
  $("regPassword").value = "";
  $("regDisplayName").value = "";
}

async function ensureLoggedIn(){
  const me = await fetch("/api/me").then(r=>r.json());
  if(me.logged_in){
    currentUser = me.user;
    setUserLine();
    hideAuthOverlay();
    if(!appInitialized){
      try{ await postLoginInit(); } catch(e){}
    }
    return true;
  }

  currentUser = null;
  setUserLine();
  showAuthOverlay();
  clearAuthForms();
  $("authMessage").textContent = "Sign in to continue";
  setAuthView("login");
  return false;
}


async function jget(url){
  const r = await fetch(url);
  if(r.status === 401){
    await ensureLoggedIn();
    throw new Error("login_required");
  }
  if(!r.ok) throw new Error(await r.text());
  return r.json();
}
async function jpost(url, body){
  const r = await fetch(url, {method:"POST", headers:{"Content-Type":"application/json"}, body: JSON.stringify(body||{})});
  if(r.status === 401){
    await ensureLoggedIn();
    throw new Error("login_required");
  }
  if(!r.ok) throw new Error(await r.text());
  return r.json();
}

function setStatus(msg){ $("status").textContent = msg; }

function escapeHtml(str){
  return String(str ?? "")
    .replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;")
    .replaceAll('"',"&quot;").replaceAll("'","&#039;");
}

function shuffle(a){
  for(let i=a.length-1;i>0;i--){
    const j = Math.floor(Math.random()*(i+1));
    [a[i],a[j]]=[a[j],a[i]];
  }
}

function updateSearchClearButton(){
  const searchBox = $("searchBox");
  const clearBtn = $("searchClearBtn");
  if(!clearBtn) return;
  if(searchBox && searchBox.value.trim()){
    clearBtn.classList.remove("hidden");
  } else {
    clearBtn.classList.add("hidden");
  }
}

function reshuffleDeck(){
  if(deck.length > 1){
    shuffle(deck);
    deckIndex = 0;
    renderStudyCard();
    setStatus("Deck reshuffled!");
    setTimeout(() => setStatus(""), 1500);
  }
}

function isFlipped(){ return $("card").classList.contains("flipped"); }
function flip(){ 
  const wasFlipped = isFlipped();
  $("card").classList.toggle("flipped");
  
  // Speak definition when flipped to back (definition side)
  const settings = window.__activeSettings || settingsAll || {};
  if(!wasFlipped && settings.speak_definition_on_flip && deck.length){
    const c = deck[deckIndex];
    const reversed = !!settings.reverse_faces;
    // When flipping to back: if not reversed, back shows definition; if reversed, back shows term
    const textToSpeak = reversed ? c.term : c.meaning;
    if(textToSpeak){
      setTimeout(() => speakText(textToSpeak), 100);
    }
  }
}

function speakText(text){
  if(!text || !("speechSynthesis" in window)) return;
  
  const settings = window.__activeSettings || settingsAll || {};
  window.speechSynthesis.cancel();
  
  const u = new SpeechSynthesisUtterance(text);
  u.rate = settings.speech_rate || 1.0;
  
  const voiceName = settings.speech_voice || "";
  if(voiceName){
    const voices = window.speechSynthesis.getVoices();
    const voice = voices.find(v => v.name === voiceName);
    if(voice) u.voice = voice;
  }
  
  window.speechSynthesis.speak(u);
}

function setTab(tab){
  activeTab = tab;

  // Show/hide the Learned view toggle
  const lvt = $("learnedViewToggle");
  if(lvt) lvt.classList.toggle("hidden", tab !== "learned");
  const lvList = $("learnedViewListBtn");
  const lvStudy = $("learnedViewStudyBtn");
  if(lvList) lvList.classList.toggle("active", tab === "learned" && learnedViewMode === "list");
  if(lvStudy) lvStudy.classList.toggle("active", tab === "learned" && learnedViewMode === "study");

  // Show/hide the Custom Set view toggle
  updateCustomViewHighlight();

  // Update study action button labels based on current mode
  updateStudyActionButtons();
  for(const [id, name] of [["tabActive","active"],["tabUnsure","unsure"],["tabLearned","learned"],["tabAll","all"],["tabCustom","custom"]]){
    const el = $(id);
    if(el) el.classList.toggle("active", tab===name);
  }

  const study = (tab === "active" || tab === "unsure" || tab === "custom" || (tab === "learned" && learnedViewMode === "study"));
  $("viewStudy").classList.toggle("hidden", !study);
  $("viewList").classList.toggle("hidden", study);
  $("viewSettings").classList.add("hidden");

  refresh();
}

function updateStudyActionButtons(){
  const got = $("gotItBtn");
  const ub  = $("unsureBtn");
  if(!got || !ub) return;

  // Default (Unlearned/Unsure study)
  got.textContent = "Got it ✓ (mark learned)";
  got.classList.remove("neutral", "warn");
  got.classList.add("good");

  ub.textContent = (activeTab === "unsure") ? "Relearn" : "Unsure";
  ub.classList.remove("good", "neutral");
  ub.classList.add("secondary");

  // Learned study mode: replace buttons with Relearn / Still Unsure
  if(activeTab === "learned" && learnedViewMode === "study"){
    got.textContent = "Relearn";
    got.classList.remove("good");
    got.classList.add("neutral");

    ub.textContent = "Still Unsure";
    ub.classList.remove("secondary");
    ub.classList.add("warn");
  }
}

async function refreshCounts(){
  try{
    const groupParam = scopeGroup ? `&group=${encodeURIComponent(scopeGroup)}` : "";
    const deckParam = activeDeckId ? `&deck_id=${encodeURIComponent(activeDeckId)}` : "";
    const c = await jget(`/api/counts?${groupParam}${deckParam}`);
    $("countsLine").textContent = `Unlearned: ${c.active} | Unsure: ${c.unsure} | Learned: ${c.learned}`;
    // Update header card count
    updateHeaderCardCount(c.total);
  } catch(e){
    $("countsLine").textContent = `Unlearned: — | Unsure: — | Learned: —`;
  }
}

async function loadHealth(){
  const h = await jget("/api/health");
  // This will be updated by refreshCounts with actual deck card count
  $("healthLine").textContent = `Cards loaded: ${h.cards_loaded}`;
}

// Update the header card count based on current deck
function updateHeaderCardCount(total){
  $("healthLine").textContent = `Cards loaded: ${total}`;
}

// Update header to show current deck name
function updateHeaderDeckName(){
  const el = $("headerDeckName");
  if(!el) return;
  
  if(currentDecks && currentDecks.length > 0){
    const deck = currentDecks.find(d => d.id === activeDeckId);
    el.textContent = deck ? deck.name : "Kenpo Vocabulary";
  } else {
    el.textContent = "Kenpo Vocabulary";
  }
}

async function loadGroups(){
  const deckParam = activeDeckId ? `?deck_id=${encodeURIComponent(activeDeckId)}` : "";
  allGroups = await jget("/api/groups" + deckParam);

  const sel = $("groupSelect");
  sel.innerHTML = "";
  const optPick = document.createElement("option");
  optPick.value = "";
  optPick.textContent = "Select group…";
  sel.appendChild(optPick);

  for(const g of allGroups){
    const o = document.createElement("option");
    o.value = g;
    o.textContent = g;
    sel.appendChild(o);
  }

  scopeGroup = "";
  sel.value = "";
  allCardsMode = true;

  updateFilterHighlight();
  updateLearnedViewHighlight();

  // Build a dark custom dropdown (native <select> option list is white on Windows).
  setupGroupDropdown(allGroups);

  sel.addEventListener("change", () => {
    scopeGroup = sel.value;
    allCardsMode = scopeGroup ? false : true;
    updateFilterHighlight();
  updateLearnedViewHighlight();
    refresh();
  });

  const scopeSel = $("settingsScope");
  scopeSel.innerHTML = "";
  const sAll = document.createElement("option");
  sAll.value = "all";
  sAll.textContent = "All Groups";
  scopeSel.appendChild(sAll);

  for(const g of allGroups){
    const o = document.createElement("option");
    o.value = g;
    o.textContent = g;
    scopeSel.appendChild(o);
  }
}

let _groupDropdownWired = false;
function setupGroupDropdown(groupsList){
  const root = $("groupDropdown");
  const btn = $("groupDropdownBtn");
  const menu = $("groupDropdownMenu");
  const sel = $("groupSelect");
  if(!root || !btn || !menu || !sel) return;

  // Render menu
  const current = sel.value || "";
  const items = [{value:"", label:"Select group…", muted:true}].concat(
    (groupsList || []).map(g => ({value:g, label:g, muted:false}))
  );
  menu.innerHTML = items.map(it => {
    const cls = ["dropdownItem", it.muted ? "muted" : "", it.value===current ? "selected" : ""].filter(Boolean).join(" ");
    const safe = (it.label||"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;");
    return `<div class="${cls}" role="option" data-value="${(it.value||"").replace(/"/g,"&quot;")}">${safe}</div>`;
  }).join("");

  const setBtnLabel = (v) => { btn.textContent = v ? v : "Select group…"; };
  setBtnLabel(current);

  // One-time wiring
  if(!_groupDropdownWired){
    _groupDropdownWired = true;

    btn.addEventListener("click", (e) => {
      e.preventDefault();
      const isOpen = !menu.classList.contains("hidden");
      if(isOpen){
        menu.classList.add("hidden");
        btn.setAttribute("aria-expanded","false");
      }else{
        menu.classList.remove("hidden");
        btn.setAttribute("aria-expanded","true");
      }
    });

    menu.addEventListener("click", (e) => {
      const item = e.target.closest(".dropdownItem");
      if(!item) return;
      const v = item.getAttribute("data-value") || "";
      sel.value = v;
      setBtnLabel(v);
      // Update selected styling
      [...menu.querySelectorAll(".dropdownItem")].forEach(el => el.classList.toggle("selected", el.getAttribute("data-value") === v));
      menu.classList.add("hidden");
      btn.setAttribute("aria-expanded","false");
      sel.dispatchEvent(new Event("change"));
    });

    // Close on outside click / ESC
    document.addEventListener("click", (e) => {
      if(!root.contains(e.target)){
        menu.classList.add("hidden");
        btn.setAttribute("aria-expanded","false");
      }
    }, true);

    document.addEventListener("keydown", (e) => {
      if(e.key === "Escape"){
        menu.classList.add("hidden");
        btn.setAttribute("aria-expanded","false");
      }
    });
  }
}

async function getScopeSettings(){
  if(!settingsAll){
    const res = await jget("/api/settings?scope=all");
    settingsAll = res.settings;
  }

  if(!scopeGroup){
    return settingsAll;
  }

  if(!(scopeGroup in settingsGroup)){
    const res = await jget(`/api/settings?scope=${encodeURIComponent(scopeGroup)}`);
    settingsGroup[scopeGroup] = res.settings || {};
  }

  return { ...settingsAll, ...settingsGroup[scopeGroup] };
}

function labelFor(card, settings){
  const showGroup = settings.show_group_label !== false;
  const showSub  = settings.show_subgroup_label !== false;

  const g = (card.group || "").trim();
  const sg = (card.subgroup || "").trim();

  const parts = [];
  if(showGroup && g) parts.push(g);
  if(showSub && sg) parts.push(sg);

  return parts.join(" • ");
}


function getStudyRandomizeFlag(settings){
  const base = (settings && typeof settings.randomize === "boolean") ? settings.randomize : false;
  const link = (settings && settings.link_randomize_study_tabs !== false);
  // Per-tab flags live in All-Groups scope (global)
  if(activeTab === "active"){
    return (settings && typeof settings.randomize_unlearned === "boolean") ? settings.randomize_unlearned : base;
  }
  if(activeTab === "unsure"){
    return (settings && typeof settings.randomize_unsure === "boolean") ? settings.randomize_unsure : base;
  }
  if(activeTab === "learned" && learnedViewMode === "study"){
    return (settings && typeof settings.randomize_learned_study === "boolean") ? settings.randomize_learned_study : base;
  }
  if(activeTab === "custom"){
    return (settings && typeof settings.randomize_custom_set === "boolean") ? settings.randomize_custom_set : base;
  }
  return base;
}

async function setStudyRandomizeFlag(value){
  const settings = await getScopeSettings();
  const link = (settings && settings.link_randomize_study_tabs !== false);
  const patch = {};
  if(activeTab === "custom"){
    // Custom set has its own flag, not linked
    patch.randomize_custom_set = !!value;
  } else if(link){
    patch.randomize_unlearned = !!value;
    patch.randomize_unsure = !!value;
    patch.randomize_learned_study = !!value;
  } else {
    if(activeTab === "active") patch.randomize_unlearned = !!value;
    else if(activeTab === "unsure") patch.randomize_unsure = !!value;
    else if(activeTab === "learned" && learnedViewMode === "study") patch.randomize_learned_study = !!value;
  }
  await jpost("/api/settings", {scope:"all", settings: patch});
  // refresh settings cache
  settingsAll = null;
}


function updateRandomStudyUI(){
  try{
    const chk = $("randomStudyChk");
    const btn = $("randomRefreshBtn");
    if(!chk || !btn) return;
    const on = !!chk.checked;
    btn.style.display = on ? "inline-flex" : "none";
  } catch(e){}
}

let breakdownCurrentCard = null;
let breakdownCurrentData = null;

// Cache for inline breakdown lookups (used to show breakdown on study card definition side)
const breakdownInlineCache = Object.create(null); // id -> breakdown or null
const breakdownInlinePending = Object.create(null); // id -> Promise
let breakdownInlineRenderToken = 0;

function breakdownHasContent(b){
  if(!b) return false;
  const parts = Array.isArray(b.parts) ? b.parts : [];
  const anyParts = parts.some(p => p && ((p.part||"").trim() || (p.meaning||"").trim()));
  const lit = (b.literal || "").trim();
  return anyParts || !!lit;
}

async function getBreakdownInline(cardId){
  if(!cardId) return null;
  if(Object.prototype.hasOwnProperty.call(breakdownInlineCache, cardId)) return breakdownInlineCache[cardId];
  if(breakdownInlinePending[cardId]) return breakdownInlinePending[cardId];

  breakdownInlinePending[cardId] = (async ()=>{
    try{
      const res = await jget(`/api/breakdown?id=${encodeURIComponent(cardId)}`);
      const b = res && res.breakdown ? res.breakdown : null;
      breakdownInlineCache[cardId] = b;
      return b;
    } catch(e){
      breakdownInlineCache[cardId] = null;
      return null;
    } finally {
      delete breakdownInlinePending[cardId];
    }
  })();

  return breakdownInlinePending[cardId];
}

function renderBreakdownInlineHTML(b){
  if(!breakdownHasContent(b)) return "";
  const parts = Array.isArray(b.parts) ? b.parts : [];
  const cleanParts = parts
    .filter(p => p && ((p.part||"").trim() || (p.meaning||"").trim()))
    .map(p => ({part:(p.part||"").trim(), meaning:(p.meaning||"").trim()}));

  const pieces = [];
  if(cleanParts.length){
    const spans = cleanParts.map(p => {
      const left = escapeHtml(p.part || "");
      const right = escapeHtml(p.meaning || "");
      if(left && right) return `<span><b>${left}</b> = ${right}</span>`;
      if(left) return `<span><b>${left}</b></span>`;
      return `<span>${right}</span>`;
    });
    pieces.push(
      `<div class="label">Term breakdown</div>` +
      `<div class="value parts">${spans.join('<span class="sep">•</span>')}</div>`
    );
  }
  const lit = (b.literal || "").trim();
  if(lit){
    pieces.push(
      `<div class="label">Literal meaning</div>` +
      `<div class="value">${escapeHtml(lit)}</div>`
    );
  }
  return pieces.join("");
}

async function updateStudyDefinitionExtras(card, settings){
  const front = $("frontExtras");
  const back = $("backExtras");
  if(!front || !back) return;

  // Clear by default
  front.innerHTML = "";
  back.innerHTML = "";
  front.classList.add("hidden");
  back.classList.add("hidden");

  if(!card || !settings) return;

  // Show breakdown by default unless the "Remove breakdown" toggle is enabled for this Study tab
  const isLearnedStudy = (activeTab === "learned" && learnedViewMode === "study");
  const tabKey = (activeTab === "active") ? "unlearned"
    : (activeTab === "unsure") ? "unsure"
    : (isLearnedStudy ? "learned_study" : "");
  if(!tabKey) return;

  const applyAll = !!settings.breakdown_apply_all_tabs;
  let remove = false;
  if(applyAll){
    remove = !!settings.breakdown_remove_all_tabs;
  } else {
    if(tabKey === "unlearned") remove = !!settings.breakdown_remove_unlearned;
    else if(tabKey === "unsure") remove = !!settings.breakdown_remove_unsure;
    else if(tabKey === "learned_study") remove = !!settings.breakdown_remove_learned_study;
  }

  if(remove) return;

  const token = ++breakdownInlineRenderToken;
  const b = await getBreakdownInline(card.id);
  if(token !== breakdownInlineRenderToken) return; // stale
  if(!breakdownHasContent(b)) return;

  const html = renderBreakdownInlineHTML(b);
  if(!html) return;

  const reversed = !!settings.reverse_faces;
  // Definition side is back when NOT reversed, front when reversed
  const target = reversed ? front : back;
  target.innerHTML = html;
  target.classList.remove("hidden");
}



function renderBreakdownParts(parts){
  const wrap = $("breakdownParts");
  wrap.innerHTML = "";
  const arr = Array.isArray(parts) ? parts : [];
  if(!arr.length){
    const empty = document.createElement("div");
    empty.className = "mini muted";
    empty.textContent = "No parts yet. Click “+ Add part” or Auto-fill.";
    wrap.appendChild(empty);
  }
  for(let i=0;i<arr.length;i++){
    const row = document.createElement("div");
    row.className = "breakdownRow";

    const part = document.createElement("input");
    part.className = "input";
    part.placeholder = "Part (e.g., Tae)";
    part.value = arr[i].part || "";
    part.setAttribute("data-idx", String(i));
    part.setAttribute("data-field", "part");

    const meaning = document.createElement("input");
    meaning.className = "input";
    meaning.placeholder = "Meaning (e.g., Foot)";
    meaning.value = arr[i].meaning || "";
    meaning.setAttribute("data-idx", String(i));
    meaning.setAttribute("data-field", "meaning");

    const del = document.createElement("button");
    del.className = "secondary tinyBtn";
    del.textContent = "✕";
    del.title = "Remove part";
    del.addEventListener("click", ()=>{
      breakdownCurrentData.parts.splice(i,1);
      renderBreakdownParts(breakdownCurrentData.parts);
    });

    row.appendChild(part);
    row.appendChild(meaning);
    row.appendChild(del);
    wrap.appendChild(row);
  }
}

async function openBreakdown(card){
  if(!card) return;
  breakdownCurrentCard = card;

  const res = await jget(`/api/breakdown?id=${encodeURIComponent(card.id)}`);
  const existing = res.breakdown || null;

  breakdownCurrentData = {
    id: card.id,
    term: card.term || "",
    parts: (existing && Array.isArray(existing.parts)) ? existing.parts.map(p=>({part:p.part||"", meaning:p.meaning||""})) : [],
    literal: (existing && existing.literal) ? existing.literal : "",
    notes: (existing && existing.notes) ? existing.notes : "",
    updated_at: (existing && existing.updated_at) ? existing.updated_at : null,
    updated_by: (existing && existing.updated_by) ? existing.updated_by : null
  };

  $("breakdownTitle").textContent = `Breakdown: ${card.term || ""}`;
  $("breakdownLiteral").value = breakdownCurrentData.literal || "";
  $("breakdownNotes").value = breakdownCurrentData.notes || "";

  const meta = $("breakdownMeta");
  if(breakdownCurrentData.updated_at){
    const d = new Date(breakdownCurrentData.updated_at*1000);
    meta.textContent = `Last saved: ${d.toLocaleString()}${breakdownCurrentData.updated_by ? " • " + breakdownCurrentData.updated_by : ""}`;
  } else {
    meta.textContent = "Not saved yet.";
  }

  // Only admin (sidscri) can overwrite an existing saved breakdown
  const saveBtn = $("breakdownSaveBtn");
  if(saveBtn){
    const readOnly = !!existing && !isAdminUser();
    saveBtn.disabled = readOnly;
    saveBtn.classList.toggle("disabled", readOnly);
    if(readOnly){
      meta.textContent = (meta.textContent ? meta.textContent + " • " : "") + "Read-only (admin only)";
    }
  }

  renderBreakdownParts(breakdownCurrentData.parts);

  // Update the Auto-fill button label based on server AI availability
  const autoBtn = $("breakdownAutoBtn");
  if(autoBtn){
    const hasAI = !!(aiStatus && (aiStatus.openai_available || aiStatus.gemini_available));
    if(hasAI){
      autoBtn.textContent = "Auto-fill (AI)";
      const prov = (aiStatus.selected_provider || "auto").toLowerCase();
      const bits = [];
      if(aiStatus.openai_available) bits.push(`OpenAI: ${aiStatus.openai_model || "available"}`);
      if(aiStatus.gemini_available) bits.push(`Gemini: ${aiStatus.gemini_model || "available"}`);
      autoBtn.title = `AI provider: ${prov}. ${bits.join(" • ")}`;
    } else {
      autoBtn.textContent = "Auto-fill";
      autoBtn.title = "Uses built-in suggestions unless server AI is configured";
    }
  }

  $("breakdownOverlay").classList.remove("hidden");
}

function closeBreakdown(){
  $("breakdownOverlay").classList.add("hidden");
  breakdownCurrentCard = null;
  breakdownCurrentData = null;
}

async function loadBreakdownsList(){
  const q = ($("breakdownsSearch").value || "").trim();
  const qParam = q ? `?q=${encodeURIComponent(q)}` : "";
  const res = await jget(`/api/breakdowns${qParam}`);
  const items = (res.items || []);
  const list = $("breakdownsList");
  list.innerHTML = "";
  if(!items.length){
    const empty = document.createElement("div");
    empty.className = "mini muted";
    empty.textContent = "No saved breakdowns yet.";
    list.appendChild(empty);
    return;
  }
  for(const it of items){
    const row = document.createElement("div");
    row.className = "breakdownListItem";
    const title = document.createElement("div");
    title.className = "bTitle";
    title.textContent = it.term || "(unknown)";
    const sub = document.createElement("div");
    sub.className = "mini muted";
    const parts = (it.parts||[]).filter(p=>p && (p.part||p.meaning)).map(p=>`${p.part}${p.meaning ? " = " + p.meaning : ""}`).join(" • ");
    sub.textContent = (it.literal ? it.literal : parts);
    row.appendChild(title);
    row.appendChild(sub);
    row.addEventListener("click", async ()=>{
      // Need the card to open by id; fall back to a minimal object
      await openBreakdown({id: it.id, term: it.term});
    });
    list.appendChild(row);
  }
}


function buildDeck(cards, settings){
  // All Cards (across groups)
  if(allCardsMode){
    if(settings.all_mode === "flat"){
      const d = cards.slice();
      if(settings.randomize) shuffle(d);
      else if(settings.card_order === "alpha"){
        d.sort((a,b)=> (a.term||"").localeCompare(b.term||""));
      }
      return d;
    }

    // grouped
    const byGroup = new Map();
    for(const c of cards){
      const g = c.group || "General";
      if(!byGroup.has(g)) byGroup.set(g, []);
      byGroup.get(g).push(c);
    }

    let groupList = Array.from(byGroup.keys());
    if(settings.group_order === "alpha"){
      groupList.sort((a,b)=> a.localeCompare(b));
    }

    const out = [];
    for(const g of groupList){
      const arr = byGroup.get(g);
      if(settings.randomize){
        shuffle(arr);
      } else if(settings.card_order === "alpha"){
        arr.sort((a,b)=> (a.term||"").localeCompare(b.term||""));
      }
      out.push(...arr);
    }
    return out;
  }

  // Studying a specific group
  const d = cards.slice();
  if(settings.randomize) shuffle(d);
  else if(settings.card_order === "alpha"){
    d.sort((a,b)=> (a.term||"").localeCompare(b.term||""));
  }
  return d;
}

function renderStudyCard(){
  $("card").classList.remove("flipped");

  if(!deck.length){
    $("pillLabel").textContent = scopeGroup ? scopeGroup : "All Cards";
    $("frontText").textContent = "No cards left 🎉";
    $("frontSub").textContent = "";
    $("backText").textContent = "Everything here has been moved out of this list.";
    $("backSub").textContent = "";
    $("cardPos").textContent = "Card 0 / 0";
    // Clear any inline breakdown extras
    updateStudyDefinitionExtras(null, window.__activeSettings || settingsAll || {});
    return;
  }

  const c = deck[deckIndex];
  const settings = window.__activeSettings || settingsAll || {};

  const label = labelFor(c, settings);
  $("pillLabel").textContent = label;
  $("pillLabel").style.visibility = label ? "visible" : "hidden";

  const reversed = !!settings.reverse_faces;

  const frontMain = reversed ? c.meaning : c.term;
  const backMain  = reversed ? c.term : c.meaning;

  const pron = (c.pron || "").trim();

  // keep pron optional; hide if empty
  $("frontSub").textContent = (!reversed && pron) ? `Pron: ${pron}` : "";
  $("backSub").textContent  = (reversed && pron) ? `Pron: ${pron}` : "";

  $("frontText").textContent = frontMain || "";
  $("backText").textContent = backMain || "";

  $("cardPos").textContent = `Card ${deckIndex+1} / ${deck.length}`;

  // Update star button
  updateStudyStarButton(c);

  // Optional: show saved breakdown + literal meaning on the definition side
  updateStudyDefinitionExtras(c, settings);
}

function cleanPronunciationForSpeech(text){
  if(!text) return text;
  
  let cleaned = text;
  
  // Remove ALL hyphens - replace with spaces
  cleaned = cleaned.replace(/-/g, ' ');
  
  // Remove ALL periods - they cause TTS to spell things out
  cleaned = cleaned.replace(/\./g, '');
  
  // Handle apostrophes that might cause issues
  cleaned = cleaned.replace(/'/g, '');
  
  // Replace problematic phonetic patterns with real words or better phonetics
  // These are specific fixes for syllables that TTS spells out
  const specificFixes = {
    // Double vowels at end that get spelled out - use real word sounds
    '\\boo kee may\\b': 'oo kee may',
    '\\boo kay\\b': 'oo kay', 
    '\\bah ee\\b': 'eye',
    '\\bkee ah ee\\b': 'kee eye',
    '\\bah ee kee doh\\b': 'eye kee doe',
    '\\btah ee kwon doh\\b': 'tie kwon doe',
    '\\bsah ee\\b': 'sigh',
    '\\bkw oon\\b': 'kwoon',
    '\\bsh in\\b': 'shin',
    // More general patterns
    '\\bsoh keh\\b': 'so kay',
    '\\broh shee\\b': 'roe shee'
  };
  
  for(const [pattern, replacement] of Object.entries(specificFixes)){
    const regex = new RegExp(pattern, 'gi');
    cleaned = cleaned.replace(regex, replacement);
  }
  
  // Handle remaining double vowels that might get spelled out
  // Replace with phonetic equivalents using real word sounds
  const doubleVowelFixes = {
    '\\boo\\b': 'oo',      // Beginning is usually ok
    '\\bee\\b': 'ee',      // Beginning is usually ok  
    '\\bah ee\\b': 'eye',  // "ah ee" sounds like "eye"
    '\\beh ee\\b': 'ay',   // "eh ee" sounds like "ay"
    '\\boh ee\\b': 'oy',   // "oh ee" sounds like "oy"
  };
  
  for(const [pattern, replacement] of Object.entries(doubleVowelFixes)){
    const regex = new RegExp(pattern, 'gi');
    cleaned = cleaned.replace(regex, replacement);
  }
  
  // Expand standalone two-letter syllables that TTS spells out
  const twoLetterExpansions = {
    '\\bkoh\\b': 'koe',
    '\\bkeh\\b': 'kay',
    '\\bsoh\\b': 'so',
    '\\btoh\\b': 'toe',
    '\\bnoh\\b': 'no',
    '\\bmoh\\b': 'moe',
    '\\broh\\b': 'roe',
    '\\bgoh\\b': 'go',
    '\\bboh\\b': 'bow',
    '\\bdoh\\b': 'doe',
    '\\bhoh\\b': 'hoe',
    '\\bjoh\\b': 'joe',
    '\\bloh\\b': 'low',
    '\\bpoh\\b': 'poe',
    '\\bwoh\\b': 'woe',
    '\\byoh\\b': 'yo',
    '\\bah\\b': 'ah',
    '\\boh\\b': 'oh',
    '\\buh\\b': 'uh',
    '\\beh\\b': 'eh',
    '\\bsh\\b': 'sh'
  };
  
  for(const [pattern, replacement] of Object.entries(twoLetterExpansions)){
    const regex = new RegExp(pattern, 'gi');
    cleaned = cleaned.replace(regex, replacement);
  }
  
  // Clean up multiple spaces
  cleaned = cleaned.replace(/\s+/g, ' ').trim();
  
  return cleaned;
}

function speakCurrent(){
  if(!deck.length) return;
  const c = deck[deckIndex];
  const settings = window.__activeSettings || settingsAll || {};
  const reversed = !!settings.reverse_faces;

  // Determine what to say based on which face is showing
  let say;
  if(isFlipped()){
    // Back face showing
    say = reversed ? c.term : c.meaning;
  } else {
    // Front face showing - use pronunciation if available for the term
    if(reversed){
      // Front shows meaning when reversed
      say = c.meaning;
    } else {
      // Front shows term - prefer pronunciation
      say = c.pron ? cleanPronunciationForSpeech(c.pron) : c.term;
    }
  }

  if(!say) return;

  if(!("speechSynthesis" in window)){
    setStatus("Speech not supported in this browser.");
    return;
  }

  window.speechSynthesis.cancel();
  const u = new SpeechSynthesisUtterance(say);
  
  // Apply voice settings
  const rate = settings.speech_rate || 1.0;
  const voiceName = settings.speech_voice || "";
  
  u.rate = rate;
  
  if(voiceName){
    const voices = window.speechSynthesis.getVoices();
    const voice = voices.find(v => v.name === voiceName);
    if(voice) u.voice = voice;
  }
  
  window.speechSynthesis.speak(u);
}

function speakCard(card){
  if(!card) return;
  
  const settings = window.__activeSettings || settingsAll || {};
  
  // Use pronunciation if speak_pronunciation_only is enabled and pron exists, otherwise use term
  let say;
  if(settings.speak_pronunciation_only && card.pron){
    say = cleanPronunciationForSpeech(card.pron);
  } else {
    say = card.pron ? cleanPronunciationForSpeech(card.pron) : card.term;
  }

  if(!say) return;

  if(!("speechSynthesis" in window)){
    setStatus("Speech not supported in this browser.");
    return;
  }

  window.speechSynthesis.cancel();
  const u = new SpeechSynthesisUtterance(say);
  
  // Apply voice settings
  const rate = settings.speech_rate || 1.0;
  const voiceName = settings.speech_voice || "";
  
  u.rate = rate;
  
  if(voiceName){
    const voices = window.speechSynthesis.getVoices();
    const voice = voices.find(v => v.name === voiceName);
    if(voice) u.voice = voice;
  }
  
  window.speechSynthesis.speak(u);
}

async function loadDeckForStudy(){
  const settings = await getScopeSettings();
  window.__activeSettings = settings;

  const q = ($("searchBox").value || "").trim();
  const groupParam = scopeGroup ? `&group=${encodeURIComponent(scopeGroup)}` : "";
  const statusParam = `status=${encodeURIComponent(activeTab)}`;
  const qParam = q ? `&q=${encodeURIComponent(q)}` : "";
  const deckParam = activeDeckId ? `&deck_id=${encodeURIComponent(activeDeckId)}` : "";

  const cards = await jget(`/api/cards?${statusParam}${groupParam}${qParam}${deckParam}`);
  const deckSettings = Object.assign({}, settings);
  deckSettings.randomize = getStudyRandomizeFlag(settings);
  deck = buildDeck(cards, deckSettings);
  deckIndex = 0;

  updateRandomStudyUI();
  renderStudyCard();
}

// Custom Set deck loading
let customSetData = null;

let customRandomLimit = 0; // 0 means no limit

async function loadCustomSetForStudy(){
  const settings = await getScopeSettings();
  window.__activeSettings = settings;

  const q = ($("searchBox").value || "").trim();
  
  try {
    const res = await jget("/api/custom_set");
    customSetData = res;
    
    let cards = res.cards || [];
    
    // Filter by custom view mode
    if(customViewMode === "unsure"){
      cards = cards.filter(c => c.custom_status === "unsure");
    } else if(customViewMode === "learned"){
      cards = cards.filter(c => c.custom_status === "learned");
    } else {
      // "all" - show active and unsure only (exclude custom-learned)
      cards = cards.filter(c => c.custom_status !== "learned");
    }
    
    // Apply search filter
    if(q){
      const ql = q.toLowerCase();
      cards = cards.filter(c => {
        const hay = `${c.term || ""} ${c.meaning || ""} ${c.pron || ""}`.toLowerCase();
        return hay.includes(ql);
      });
    }
    
    // Apply randomization if enabled or if random limit is set
    if(settings.randomize_custom_set || customRandomLimit > 0){
      shuffle(cards);
    }
    
    // Apply random limit if set
    if(customRandomLimit > 0 && cards.length > customRandomLimit){
      cards = cards.slice(0, customRandomLimit);
    }
    
    deck = cards;
    deckIndex = 0;
    
    updateRandomStudyUI();
    renderStudyCard();
    
    // Update counts display for custom set
    if(customSetData){
      const counts = customSetData.counts || {};
      let statusMsg = `Custom Set: ${counts.total || 0} cards`;
      if(customRandomLimit > 0){
        statusMsg = `🎲 Random ${Math.min(customRandomLimit, deck.length)} of ${counts.total || 0} cards`;
      }
      setStatus(statusMsg);
    }
  } catch(e){
    console.error("Failed to load custom set:", e);
    deck = [];
    deckIndex = 0;
    renderStudyCard();
  }
}

async function toggleCustomSet(cardId){
  try {
    const res = await jpost("/api/custom_set/toggle", { id: cardId });
    return res.in_custom_set;
  } catch(e){
    console.error("Failed to toggle custom set:", e);
    return null;
  }
}

async function setCustomSetStatus(cardId, status){
  try {
    await jpost("/api/custom_set/set_status", { id: cardId, status: status });
    await refresh();
  } catch(e){
    console.error("Failed to set custom set status:", e);
  }
}

async function setCurrentStatus(status){
  if(!deck.length) return;
  const c = deck[deckIndex];
  
  // For custom set, use custom set status API
  if(activeTab === "custom"){
    await jpost("/api/custom_set/set_status", { id: c.id, status: status });
  } else {
    await jpost("/api/set_status", {id: c.id, status});
  }

  deck.splice(deckIndex, 1);
  if(deckIndex >= deck.length) deckIndex = 0;

  await refreshCounts();
  renderStudyCard();
}

function nextCard(){
  if(!deck.length) return;
  deckIndex = (deckIndex + 1) % deck.length;
  renderStudyCard();
  
  // Auto-speak on card change
  const settings = window.__activeSettings || settingsAll || {};
  if(settings.auto_speak_on_card_change && deck.length){
    setTimeout(() => speakCard(deck[deckIndex]), 100);
  }
}
function prevCard(){
  if(!deck.length) return;
  deckIndex = (deckIndex - 1 + deck.length) % deck.length;
  renderStudyCard();
  
  // Auto-speak on card change
  const settings = window.__activeSettings || settingsAll || {};
  if(settings.auto_speak_on_card_change && deck.length){
    setTimeout(() => speakCard(deck[deckIndex]), 100);
  }
}

async function loadList(status){
  // List settings (definitions shown/hidden)
  const settings = await getScopeSettings();
  const showDefsAll = (settings.show_definitions_all_list !== false);
  const showDefsLearned = (settings.show_definitions_learned_list !== false);
  const showAllUUButtons = (settings.all_list_show_unlearned_unsure_buttons !== false);
  // Learned list-only settings
  const showLearnedMoveButtons = (settings.learned_list_show_relearn_unsure_buttons !== false);
  const showLearnedGroupLabel  = (settings.learned_list_show_group_label !== false);

  const q = ($("searchBox").value || "").trim();
  const groupParam = scopeGroup ? `&group=${encodeURIComponent(scopeGroup)}` : "";
  const qParam = q ? `&q=${encodeURIComponent(q)}` : "";
  const deckParam = activeDeckId ? `&deck_id=${encodeURIComponent(activeDeckId)}` : "";
  let cards = await jget(`/api/cards?status=${encodeURIComponent(status)}${groupParam}${qParam}${deckParam}`);

  const titleMap = {learned:"Learned", deleted:"Deleted", all:"All"};
  $("listTitle").textContent = titleMap[status] || "List";

  // Show/hide sort dropdown for All list
  const sortField = $("sortByStatusField");
  if(sortField){
    sortField.style.display = (status === "all") ? "flex" : "none";
  }

  // Apply sorting for All list
  if(status === "all"){
    const sortBy = $("sortByStatus") ? $("sortByStatus").value : "";
    if(sortBy === "unlearned"){
      cards.sort((a,b) => {
        const order = {active: 0, unsure: 1, learned: 2};
        return (order[a.status] || 3) - (order[b.status] || 3);
      });
    } else if(sortBy === "unsure"){
      cards.sort((a,b) => {
        const order = {unsure: 0, active: 1, learned: 2};
        return (order[a.status] || 3) - (order[b.status] || 3);
      });
    } else if(sortBy === "learned"){
      cards.sort((a,b) => {
        const order = {learned: 0, unsure: 1, active: 2};
        return (order[a.status] || 3) - (order[b.status] || 3);
      });
    } else if(sortBy === "alpha"){
      cards.sort((a,b) => (a.term || "").localeCompare(b.term || ""));
    }
  }

  const bulk = $("bulkBtns");
  bulk.innerHTML = "";

  bulk.style.display = "none"; // bulk actions disabled
  const mkBtn = (txt, cls, onClick) => {
    const b = document.createElement("button");
    b.textContent = txt;
    b.className = cls;
    b.addEventListener("click", onClick);
    return b;
  };

  const selectedIds = () => Array.from(document.querySelectorAll("input[data-id]:checked")).map(x => x.getAttribute("data-id"));

  if(status === "deleted"){
    bulk.appendChild(mkBtn("Restore to Active", "secondary", async ()=>{
      const ids = selectedIds(); if(!ids.length) return;
      await jpost("/api/bulk_set_status", {ids, status:"active"});
      await refresh();
    }));
    bulk.appendChild(mkBtn("Restore to Unsure", "secondary", async ()=>{
      const ids = selectedIds(); if(!ids.length) return;
      await jpost("/api/bulk_set_status", {ids, status:"unsure"});
      await refresh();
    }));
  }

  const list = $("list");
  list.innerHTML = "";

  if(!cards.length){
    list.textContent = "No cards found in this list.";
    return;
  }

  for(const c of cards){
    const row = document.createElement("div");
    row.className = "item";


    const left = document.createElement("div");
    left.className = "itemLeft";

    let chk = null;


    // No selection checkboxes (per-card move buttons are used instead)
    chk = null;

    const text = document.createElement("div");
    const lbl = (c.group || "").trim();
    const sName = (c.status || "").toString();
    let sLabel = sName ? (sName.charAt(0).toUpperCase() + sName.slice(1)) : "";
    if(status === "all" && sName === "active") sLabel = "Unlearned";
    const showMeaning = (status === "all") ? showDefsAll : (status === "learned") ? showDefsLearned : true;

    const lines = [];
    if(showMeaning) lines.push(`<span class="defText">${escapeHtml(c.meaning)}</span>`);
    if(c.pron) lines.push(`Pron: ${escapeHtml(c.pron)}`);
    if(lbl && !(status === "learned" && !showLearnedGroupLabel)) lines.push(`${escapeHtml(lbl)}`);
    if(status === "all" && sLabel) lines.push(`Status: ${escapeHtml(sLabel)}`);

    text.innerHTML = `<b>${escapeHtml(c.term)}</b>${lines.length ? `<small>${lines.join("<br/>")}</small>` : ""}`;

if(chk) left.appendChild(chk);
    left.appendChild(text);

    const right = document.createElement("div");
    right.className = "itemRight";


    // Actions for the All list: quick-move cards between statuses
    if(status === "all"){
      right.classList.add("itemActions");
      const cur = (c.status || "active").toString();
      const addMove = (label, to, cls) => {
        if(cur === to) return;
        const b = document.createElement("button");
        b.className = "mini " + cls;
        b.textContent = label;
        b.addEventListener("click", async ()=>{
          await jpost("/api/set_status", {id:c.id, status:to});
          await refresh();
        });
        right.appendChild(b);
      };
      if(showAllUUButtons){
        addMove("Unlearned", "active", "neutral");
        addMove("Unsure", "unsure", "warn");
      }
      addMove("Learned", "learned", "good");
    }
    if(status === "all"){
      const speakBtn = document.createElement("button");
      speakBtn.className = "good"; speakBtn.textContent = "🔊 Speak";
      speakBtn.addEventListener("click", ()=>{ speakCard(c); });
      right.appendChild(speakBtn);

      // Star button for Custom Set
      const starBtn = document.createElement("button");
      starBtn.className = c.in_custom_set ? "itemStar starred" : "itemStar"; 
      starBtn.textContent = c.in_custom_set ? "★" : "☆"; 
      starBtn.title = c.in_custom_set ? "Remove from Custom Set" : "Add to Custom Set"; 
      starBtn.setAttribute("aria-label", starBtn.title);
      starBtn.addEventListener("click", async ()=>{ 
        const inSet = await toggleCustomSet(c.id);
        if(inSet !== null){
          c.in_custom_set = inSet;
          starBtn.textContent = inSet ? "★" : "☆";
          starBtn.className = inSet ? "itemStar starred" : "itemStar";
          starBtn.title = inSet ? "Remove from Custom Set" : "Add to Custom Set";
          setStatus(inSet ? "Added to Custom Set" : "Removed from Custom Set");
        }
      });
      right.appendChild(starBtn);

      const brBtn = document.createElement("button");
      brBtn.className = "secondary iconOnly"; brBtn.textContent = "🧩"; brBtn.title = "Breakdown"; brBtn.setAttribute("aria-label","Breakdown");
      brBtn.addEventListener("click", ()=>{ openBreakdown(c); });
      right.appendChild(brBtn);
    }

    if(status === "learned"){
      // Match the "All" list layout: compact buttons on the right (no arrow labels)
      const addMove = (label, to, cls) => {
        const b = document.createElement("button");
        b.className = cls;
        b.textContent = label;
        b.addEventListener("click", async ()=>{
          await jpost("/api/set_status", {id:c.id, status:to});
          await refresh();
        });
        right.appendChild(b);
      };

      // Learned cards can be moved back to Unlearned (active) or Unsure
      // Rename buttons for the Learned page only
      if(showLearnedMoveButtons){
        addMove("Relearn", "active", "neutral");
        addMove("Still Unsure", "unsure", "warn");
      }

      const speakBtn = document.createElement("button");
      speakBtn.className = "good"; speakBtn.textContent = "🔊 Speak";
      speakBtn.addEventListener("click", ()=>{ speakCard(c); });
      right.appendChild(speakBtn);

      const brBtn = document.createElement("button");
      brBtn.className = "secondary iconOnly"; brBtn.textContent = "🧩"; brBtn.title = "Breakdown"; brBtn.setAttribute("aria-label","Breakdown");
      brBtn.addEventListener("click", ()=>{ openBreakdown(c); });
      right.appendChild(brBtn);
    }

    if(status === "deleted"){
      const b1 = document.createElement("button");
      b1.className = "secondary"; b1.textContent = "Restore";
      b1.addEventListener("click", async ()=>{ await jpost("/api/set_status", {id:c.id, status:"active"}); await refresh(); });
      right.appendChild(b1);
    }

    row.appendChild(left);
    row.appendChild(right);
    list.appendChild(row);
  }
}

async function refresh(){
  updateFilterHighlight();
  updateLearnedViewHighlight();
  updateCustomViewHighlight();
  if(!currentUser){
    const ok = await ensureLoggedIn();
    if(!ok) return;
  }
  await refreshCounts();

  if(!$("viewSettings").classList.contains("hidden")){
    return;
  }

  const isStudyMode = (activeTab === "active" || activeTab === "unsure" || activeTab === "custom" || (activeTab === "learned" && learnedViewMode === "study"));

  const rWrap = $("randomStudyWrap");
  if(rWrap){
    rWrap.classList.toggle("hidden", !isStudyMode);
    if(isStudyMode){
      const s = await getScopeSettings();
      $("randomStudyChk").checked = !!getStudyRandomizeFlag(s);
      updateRandomStudyUI();
    }
  }

  if(isStudyMode){
    if(activeTab === "custom"){
      await loadCustomSetForStudy();
    } else {
      await loadDeckForStudy();
    }
    const s = window.__activeSettings || settingsAll || {};
    const allLabel = (s.all_mode === "flat") ? "All (flat)" : "All (grouped)";
    const studyLabel = (activeTab === "active") ? "Unlearned" : (activeTab === "learned" ? "Learned" : (activeTab === "unsure" ? "Unsure" : (activeTab === "custom" ? "Custom Set" : (activeTab||""))));
    setStatus(`${(scopeGroup || (allCardsMode ? allLabel : ""))} • Studying: ${studyLabel}`);
  } else {
    $("viewStudy").classList.add("hidden");
    $("viewList").classList.remove("hidden");
    await loadList(activeTab);
    setStatus(`${(scopeGroup||"All")} • Viewing: ${activeTab}`);
  }
}

async function openSettings(){
  if(!appInitialized){
    try{ await postLoginInit(); } catch(e){}
  }
  $("viewSettings").classList.remove("hidden");
  $("viewStudy").classList.add("hidden");
  $("viewList").classList.add("hidden");

  $("settingsScope").value = "all";
  try{ aiStatus = await jget("/api/ai"); } catch(e){}
  await loadSettingsForm("all");
}

async function loadSettingsForm(scope){
  if(!scope) scope = "all";
  const res = await jget(`/api/settings?scope=${encodeURIComponent(scope)}`);
  const s = res.settings || {};

  let effective = s;
  if(scope !== "all"){
    if(!settingsAll){
      const g = await jget("/api/settings?scope=all");
      settingsAll = g.settings;
    }
    effective = { ...settingsAll, ...s };
  }

  $("setRandomize").checked = !!effective.randomize;
  if($("setLinkRandomize")) $("setLinkRandomize").checked = (effective.link_randomize_study_tabs !== false);
  const baseRand = !!effective.randomize;
  if($("setRandomizeUnlearned")) $("setRandomizeUnlearned").checked = (typeof effective.randomize_unlearned === "boolean") ? effective.randomize_unlearned : baseRand;
  if($("setRandomizeUnsure")) $("setRandomizeUnsure").checked = (typeof effective.randomize_unsure === "boolean") ? effective.randomize_unsure : baseRand;
  if($("setRandomizeLearnedStudy")) $("setRandomizeLearnedStudy").checked = (typeof effective.randomize_learned_study === "boolean") ? effective.randomize_learned_study : baseRand;
  if($("setRandomizeCustomSet")) $("setRandomizeCustomSet").checked = (typeof effective.randomize_custom_set === "boolean") ? effective.randomize_custom_set : baseRand;

$("setShowGroup").checked = effective.show_group_label !== false;
  $("setShowSubgroup").checked = effective.show_subgroup_label !== false;
  $("setReverseFaces").checked = !!effective.reverse_faces;
  if($("setBreakdownApplyAll")) $("setBreakdownApplyAll").checked = !!effective.breakdown_apply_all_tabs;
  if($("setBreakdownRemoveAll")) $("setBreakdownRemoveAll").checked = !!effective.breakdown_remove_all_tabs;
  if($("setBreakdownRemoveUnlearned")) $("setBreakdownRemoveUnlearned").checked = !!effective.breakdown_remove_unlearned;
  if($("setBreakdownRemoveUnsure")) $("setBreakdownRemoveUnsure").checked = !!effective.breakdown_remove_unsure;
  if($("setBreakdownRemoveLearned")) $("setBreakdownRemoveLearned").checked = !!effective.breakdown_remove_learned_study;
  if(typeof updateBreakdownSettingsUI === "function") updateBreakdownSettingsUI();

  $("setAllMode").value = effective.all_mode || "grouped";
  $("setGroupOrder").value = effective.group_order || "alpha";
  $("setCardOrder").value = effective.card_order || "json";
  
  // Voice settings
  $("setSpeechRate").value = effective.speech_rate || 1.0;
  updateRateLabel(effective.speech_rate || 1.0);
  if($("setSpeakPronunciationOnly")) $("setSpeakPronunciationOnly").checked = !!effective.speak_pronunciation_only;
  
  // Populate voice dropdown and select saved voice
  await populateVoiceDropdown();
  $("setSpeechVoice").value = effective.speech_voice || "";

  updateBreakdownSettingsUI();

  // List-page settings are global (All Groups only)
  const listSection = $("listSettingsSection");
  if(listSection) listSection.style.display = (scope === "all") ? "block" : "none";
  if($("setShowDefAllList")) $("setShowDefAllList").checked = (effective.show_definitions_all_list !== false);
  if($("setShowDefLearnedList")) $("setShowDefLearnedList").checked = (effective.show_definitions_learned_list !== false);
  if($("setAllListShowUnlearnedUnsureBtns")) $("setAllListShowUnlearnedUnsureBtns").checked = (effective.all_list_show_unlearned_unsure_buttons !== false);
  if($("setLearnedListShowMoveBtns")) $("setLearnedListShowMoveBtns").checked = (effective.learned_list_show_relearn_unsure_buttons !== false);
  if($("setLearnedListShowGroupLabel")) $("setLearnedListShowGroupLabel").checked = (effective.learned_list_show_group_label !== false);

  // AI breakdown provider (global)
  const aiSection = $("aiSettingsSection");
  if(aiSection) aiSection.style.display = (scope === "all") ? "block" : "none";
  const provSel = $("setBreakdownProvider");
  if(provSel) provSel.value = (effective.breakdown_ai_provider || "auto");
  const provStatus = $("aiStatusLine");
  if(provStatus){
    const bits = [];
    if(aiStatus && aiStatus.openai_available) bits.push(`OpenAI: ${aiStatus.openai_model || "available"}`);
    if(aiStatus && aiStatus.gemini_available) bits.push(`Gemini: ${aiStatus.gemini_model || "available"}`);
    provStatus.textContent = bits.length ? (`Available: ${bits.join(" • ")}`) : "No AI keys detected on server (will use built-in suggestions).";
  }
}


function updateBreakdownSettingsUI(){
  const applyAllEl = $("setBreakdownApplyAll");
  const rowAll = $("rowBreakdownRemoveAll");
  const removeAllEl = $("setBreakdownRemoveAll");
  const rowU = $("rowBreakdownRemoveUnlearned");
  const rowS = $("rowBreakdownRemoveUnsure");
  const rowL = $("rowBreakdownRemoveLearned");
  const rmU = $("setBreakdownRemoveUnlearned");
  const rmS = $("setBreakdownRemoveUnsure");
  const rmL = $("setBreakdownRemoveLearned");
  if(!applyAllEl) return;

  const applyAll = !!applyAllEl.checked;

  // "All Study tabs" row is visible but disabled unless Apply-to-all is enabled (Option 2)
  if(rowAll && removeAllEl){
    removeAllEl.disabled = !applyAll;
    rowAll.classList.toggle("disabled", !applyAll);
  }

  // Per-tab toggles grey out when Apply-to-all is enabled
  const lock = applyAll;
  const rows = [
    [rowU, rmU],
    [rowS, rmS],
    [rowL, rmL]
  ];
  for(const [row, el] of rows){
    if(!row || !el) continue;
    el.disabled = lock;
    row.classList.toggle("disabled", lock);
  }
}

async function saveSettings(){
  let scope = $("settingsScope").value;
  if(!scope) scope = "all";

  const patch = {
    randomize: $("setRandomize").checked,
    show_group_label: $("setShowGroup").checked,
    show_subgroup_label: $("setShowSubgroup").checked,
    reverse_faces: $("setReverseFaces").checked,
    show_breakdown_on_definition: $("setShowBreakdownOnDef") ? $("setShowBreakdownOnDef").checked : true,
    breakdown_apply_all_tabs: $("setBreakdownApplyAll") ? $("setBreakdownApplyAll").checked : false,
    breakdown_remove_all_tabs: $("setBreakdownRemoveAll") ? $("setBreakdownRemoveAll").checked : false,
    breakdown_remove_unlearned: $("setBreakdownRemoveUnlearned") ? $("setBreakdownRemoveUnlearned").checked : false,
    breakdown_remove_unsure: $("setBreakdownRemoveUnsure") ? $("setBreakdownRemoveUnsure").checked : false,
    breakdown_remove_learned_study: $("setBreakdownRemoveLearned") ? $("setBreakdownRemoveLearned").checked : false,
    all_mode: $("setAllMode").value,
    group_order: $("setGroupOrder").value,
    card_order: $("setCardOrder").value,
    speech_rate: parseFloat($("setSpeechRate").value) || 1.0,
    speech_voice: $("setSpeechVoice").value || "",
    auto_speak_on_card_change: $("setAutoSpeakOnCardChange") ? $("setAutoSpeakOnCardChange").checked : false,
    speak_definition_on_flip: $("setSpeakDefinitionOnFlip") ? $("setSpeakDefinitionOnFlip").checked : false,
    speak_pronunciation_only: $("setSpeakPronunciationOnly") ? $("setSpeakPronunciationOnly").checked : false
  };

  // Only save tab-specific + list-page settings at the All-Groups scope
  if(scope === "all"){
    patch.link_randomize_study_tabs = $("setLinkRandomize") ? $("setLinkRandomize").checked : true;
    patch.randomize_unlearned = $("setRandomizeUnlearned") ? $("setRandomizeUnlearned").checked : patch.randomize;
    patch.randomize_unsure = $("setRandomizeUnsure") ? $("setRandomizeUnsure").checked : patch.randomize;
    patch.randomize_learned_study = $("setRandomizeLearnedStudy") ? $("setRandomizeLearnedStudy").checked : patch.randomize;
    patch.randomize_custom_set = $("setRandomizeCustomSet") ? $("setRandomizeCustomSet").checked : patch.randomize;

    patch.show_definitions_all_list = $("setShowDefAllList").checked;
    patch.show_definitions_learned_list = $("setShowDefLearnedList").checked;
    patch.all_list_show_unlearned_unsure_buttons = $("setAllListShowUnlearnedUnsureBtns").checked;
    patch.learned_list_show_relearn_unsure_buttons = $("setLearnedListShowMoveBtns").checked;
    patch.learned_list_show_group_label = $("setLearnedListShowGroupLabel").checked;
    if($("setBreakdownProvider")) patch.breakdown_ai_provider = $("setBreakdownProvider").value || "auto";
  }

  await jpost("/api/settings", {scope, settings: patch});

  settingsAll = null;
  settingsGroup = {};
  window.__activeSettings = null;

  await refreshCounts();
  await refresh();
}

function getErrorMessage(error){
  const msg = error.message || "";
  if(msg.includes("username_and_password_required")) return "Please enter both username and password.";
  if(msg.includes("username_too_short")) return "Username must be at least 3 characters.";
  if(msg.includes("password_too_short")) return "Password must be at least 4 characters.";
  if(msg.includes("username_taken")) return "That username is already taken.";
  if(msg.includes("invalid_credentials")) return "Invalid username or password.";
  return "An error occurred. Please try again.";
}

function updateRateLabel(value){
  const label = $("rateLabel");
  if(label) label.textContent = `${value}x`;
}

async function populateVoiceDropdown(){
  const sel = $("setSpeechVoice");
  if(!sel) return;
  
  // Force voices to load by calling getVoices
  let voices = window.speechSynthesis.getVoices();
  
  // If voices not loaded yet, wait for them
  if(!voices.length){
    await new Promise(resolve => {
      const checkVoices = () => {
        voices = window.speechSynthesis.getVoices();
        if(voices.length > 0){
          resolve();
        }
      };
      
      // Set up the event listener
      window.speechSynthesis.onvoiceschanged = checkVoices;
      
      // Also poll in case the event doesn't fire (some browsers)
      const interval = setInterval(() => {
        voices = window.speechSynthesis.getVoices();
        if(voices.length > 0){
          clearInterval(interval);
          resolve();
        }
      }, 100);
      
      // Timeout fallback after 2 seconds
      setTimeout(() => {
        clearInterval(interval);
        resolve();
      }, 2000);
    });
    
    voices = window.speechSynthesis.getVoices();
  }
  
  const currentValue = sel.value;
  sel.innerHTML = "";
  
  // Default option
  const defaultOpt = document.createElement("option");
  defaultOpt.value = "";
  defaultOpt.textContent = "Default voice";
  sel.appendChild(defaultOpt);
  
  if(!voices.length){
    const noVoices = document.createElement("option");
    noVoices.value = "";
    noVoices.textContent = "(No voices available)";
    noVoices.disabled = true;
    sel.appendChild(noVoices);
    return;
  }
  
  // Group voices by full language code (e.g., "EN-AU", "EN-US", "EN-GB")
  const byLangCountry = new Map();
  for(const v of voices){
    // Parse language code like "en-AU" or "en_AU" or just "en"
    const langParts = v.lang.replace('_', '-').split('-');
    const lang = langParts[0].toUpperCase();
    const country = langParts[1] ? langParts[1].toUpperCase() : '';
    const key = country ? `${lang} > ${country}` : lang;
    
    if(!byLangCountry.has(key)) byLangCountry.set(key, []);
    byLangCountry.get(key).push(v);
  }
  
  // Sort language-country keys: EN first, then alphabetical, within EN sort by country
  const keys = Array.from(byLangCountry.keys()).sort((a, b) => {
    const aIsEN = a.startsWith("EN");
    const bIsEN = b.startsWith("EN");
    
    // EN languages come first
    if(aIsEN && !bIsEN) return -1;
    if(!aIsEN && bIsEN) return 1;
    
    // Within same language prefix, sort alphabetically
    return a.localeCompare(b);
  });
  
  for(const key of keys){
    const group = document.createElement("optgroup");
    group.label = key;
    
    const langVoices = byLangCountry.get(key).sort((a, b) => a.name.localeCompare(b.name));
    for(const v of langVoices){
      const opt = document.createElement("option");
      opt.value = v.name;
      opt.textContent = v.name + (v.localService ? "" : " (online)");
      group.appendChild(opt);
    }
    
    sel.appendChild(group);
  }
  
  // Restore selection
  if(currentValue) sel.value = currentValue;
}

function testVoice(){
  const voiceName = $("setSpeechVoice").value;
  const rate = parseFloat($("setSpeechRate").value) || 1.0;
  
  if(!("speechSynthesis" in window)){
    setStatus("Speech not supported in this browser.");
    return;
  }
  
  window.speechSynthesis.cancel();
  
  // Test with sample pronunciations including problematic ones
  const testText = cleanPronunciationForSpeech("oo-kee-may, sh-in, kee-ah-ee, ah-ee-kee-doh, sah-ee, oo-kay");
  const u = new SpeechSynthesisUtterance(testText);
  u.rate = rate;
  
  // Get voices and apply selected one
  const voices = window.speechSynthesis.getVoices();
  
  if(voiceName && voices.length > 0){
    const voice = voices.find(v => v.name === voiceName);
    if(voice){
      u.voice = voice;
    }
  }
  
  // If voices aren't loaded yet, wait and try again
  if(voices.length === 0){
    window.speechSynthesis.onvoiceschanged = () => {
      const loadedVoices = window.speechSynthesis.getVoices();
      if(voiceName){
        const voice = loadedVoices.find(v => v.name === voiceName);
        if(voice) u.voice = voice;
      }
      window.speechSynthesis.speak(u);
    };
    return;
  }
  
  window.speechSynthesis.speak(u);
}

function resetVoiceToDefault(){
  $("setSpeechVoice").value = "";
  $("setSpeechRate").value = 1.0;
  updateRateLabel(1.0);
}

async function main(){

  // Auth modal buttons - Login
  bind("btnLogin","click", async ()=>{
    const username = ($("loginUsername").value || "").trim();
    const password = ($("loginPassword").value || "");
    
    if(!username || !password){
      $("authMessage").textContent = "Please enter both username and password.";
      return;
    }
    
    try{
      const res = await fetch("/api/login", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({username, password})
      }).then(r => r.json());
      
      if(res.error){
        $("authMessage").textContent = getErrorMessage({message: res.error});
        return;
      }
      
      currentUser = res.user;
      setUserLine();
      hideAuthOverlay();
      appInitialized = false;
      await postLoginInit();
      await refresh();
    } catch(e){
      $("authMessage").textContent = getErrorMessage(e);
    }
  });

  // Switch to register view
  bind("btnShowRegister","click", ()=>{
    $("authMessage").textContent = "Create a new account";
    clearAuthForms();
    setAuthView("register");
  });

  // Switch to login view
  bind("btnShowLogin","click", ()=>{
    $("authMessage").textContent = "Sign in to continue";
    clearAuthForms();
    setAuthView("login");
  });

  // Register new user
  bind("btnRegister","click", async ()=>{
    const username = ($("regUsername").value || "").trim();
    const password = ($("regPassword").value || "");
    const displayName = ($("regDisplayName").value || "").trim();
    
    if(!username || !password){
      $("authMessage").textContent = "Please enter both username and password.";
      return;
    }
    
    try{
      const res = await fetch("/api/register", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({username, password, display_name: displayName})
      }).then(r => r.json());
      
      if(res.error){
        $("authMessage").textContent = getErrorMessage({message: res.error});
        return;
      }
      
      currentUser = res.user;
      setUserLine();
      hideAuthOverlay();
      appInitialized = false;
      await postLoginInit();
      await refresh();
    } catch(e){
      $("authMessage").textContent = getErrorMessage(e);
    }
  });

  bind("logoutBtn","click", doLogout);
  // Also bind to user menu logout
  bind("userMenuLogout","click", doLogout);
  
  bind("tabActive","click", ()=>setTab("active"));
  bind("tabUnsure","click", ()=>setTab("unsure"));
  bind("tabLearned","click", ()=>setTab("learned"));
  bind("tabAll","click", ()=>setTab("all"));
  bind("tabCustom","click", ()=>setTab("custom"));

  // Learned tab view toggle
  bind("learnedViewListBtn","click", async ()=>{
    learnedViewMode = "list";
    // Use setTab so the active highlight updates correctly
    setTab("learned");
  });
  bind("learnedViewStudyBtn","click", async ()=>{
    learnedViewMode = "study";
    // Use setTab so the active highlight updates correctly
    setTab("learned");
  });

  // Custom Set view toggle
  bind("customViewAllBtn","click", async ()=>{
    customViewMode = "all";
    customRandomLimit = 0;
    updateCustomViewHighlight();
    await refresh();
  });
  bind("customViewUnsureBtn","click", async ()=>{
    customViewMode = "unsure";
    customRandomLimit = 0;
    updateCustomViewHighlight();
    await refresh();
  });
  bind("customViewLearnedBtn","click", async ()=>{
    customViewMode = "learned";
    customRandomLimit = 0;
    updateCustomViewHighlight();
    await refresh();
  });
  bind("customRandomPickBtn","click", async ()=>{
    const input = prompt("How many random cards to study?", "10");
    if(input === null) return;
    const n = parseInt(input, 10);
    if(isNaN(n) || n < 1){
      setStatus("Please enter a valid number");
      return;
    }
    customRandomLimit = n;
    customViewMode = "all";
    updateCustomViewHighlight();
    await refresh();
  });

  bind("nextBtn","click", nextCard);
  bind("prevBtn","click", prevCard);
  bind("speakBtn","click", speakCurrent);


  

  bind("randomRefreshBtn","click", async ()=>{
    try{
      // Reshuffle works even if random is not checked - instant shuffle
      if(deck && deck.length){
        shuffle(deck);
        deckIndex = 0;
        renderStudyCard();
        setStatus("Deck reshuffled!");
        setTimeout(() => setStatus(""), 1500);
      } else {
        await refresh();
      }
    } catch(e){ console.error(e); }
  });

// Study random order toggle (per tab)
  bind("randomStudyChk","change", async ()=>{
    try{
      const on = $("randomStudyChk").checked;
      await setStudyRandomizeFlag(on);
      updateRandomStudyUI();
      await refresh();
    } catch(e){ console.error(e); }
  });



  // Breakdown (study card)
  bind("breakdownBtn","click", async ()=>{
    try{
      if(!deck.length) return;
      const c = deck[deckIndex];
      await openBreakdown(c);
    } catch(e){ console.error(e); }
  });

  // Breakdown modal controls
  bind("breakdownCloseBtn","click", closeBreakdown);
  bind("breakdownAddPartBtn","click", ()=>{
    if(!breakdownCurrentData) return;
    breakdownCurrentData.parts.push({part:"", meaning:""});
    renderBreakdownParts(breakdownCurrentData.parts);
  });
  bind("breakdownAutoBtn","click", async ()=>{
    try{
      if(!breakdownCurrentCard) return;
      const res = await jpost("/api/breakdown_autofill", {
        term: breakdownCurrentCard.term || "",
        meaning: breakdownCurrentCard.meaning || "",
        group: breakdownCurrentCard.group || ""
      });
      if(res && res.suggestion){
        breakdownCurrentData.parts = (res.suggestion.parts || []).map(p=>({part:p.part||"", meaning:p.meaning||""}));
        breakdownCurrentData.literal = res.suggestion.literal || "";
        $("breakdownLiteral").value = breakdownCurrentData.literal || "";
        renderBreakdownParts(breakdownCurrentData.parts);

        // Update meta line to show where the suggestion came from
        const meta = $("breakdownMeta");
        if(meta){
          const src = (res.source || "").toLowerCase();
          if(src === "openai" || src === "gemini"){
            const who = src === "openai" ? "AI (OpenAI)" : "AI (Gemini)";
            meta.textContent = `Auto-filled using ${who}. Review and edit, then Save.`;
          } else {
            // curated fallback
            const err = res.ai_error && res.ai_error.message ? String(res.ai_error.message) : "";
            if(err){
              meta.textContent = `AI auto-fill failed (${err}). Using built-in suggestions instead. Review and edit, then Save.`;
            } else {
              meta.textContent = "Auto-filled using built-in suggestions. Review and edit, then Save.";
            }
          }
        }
      }
    } catch(e){ console.error(e); }
  });
  bind("breakdownSaveBtn","click", async ()=>{
    try{
      if(!breakdownCurrentCard || !breakdownCurrentData) return;
      // pull latest from inputs
      const inputs = Array.from(document.querySelectorAll("#breakdownParts input.input"));
      const parts = [];
      for(const el of inputs){
        const idx = parseInt(el.getAttribute("data-idx")||"0",10);
        const field = el.getAttribute("data-field");
        if(!parts[idx]) parts[idx] = {part:"", meaning:""};
        parts[idx][field] = el.value;
      }
      breakdownCurrentData.parts = parts.filter(p=>p && (p.part||p.meaning));
      breakdownCurrentData.literal = $("breakdownLiteral").value || "";
      breakdownCurrentData.notes = $("breakdownNotes").value || "";

      await jpost("/api/breakdown", {
        id: breakdownCurrentCard.id,
        term: breakdownCurrentCard.term || breakdownCurrentData.term || "",
        parts: breakdownCurrentData.parts,
        literal: breakdownCurrentData.literal,
        notes: breakdownCurrentData.notes
      });
      // Keep inline cache in sync so study cards can immediately show the saved breakdown
      breakdownInlineCache[breakdownCurrentCard.id] = {
        parts: breakdownCurrentData.parts,
        literal: breakdownCurrentData.literal,
        notes: breakdownCurrentData.notes
      };
      setStatus("Saved breakdown.");
      closeBreakdown();
    } catch(e){
      console.error(e);
      let msg = "Could not save breakdown.";
      try{
        const raw = (e && e.message) ? String(e.message) : "";
        if(raw && raw.trim().startsWith("{")){
          const j = JSON.parse(raw);
          if(j && (j.message || j.error)) msg = j.message || j.error;
        }
      } catch(_){ }
      setStatus(msg);
    }
  });

  // Breakdowns list overlay
  bind("breakdownsBtn","click", async ()=>{
    $("breakdownsOverlay").classList.remove("hidden");
    await loadBreakdownsList();
  });
  bind("breakdownsCloseBtn","click", ()=>{ $("breakdownsOverlay").classList.add("hidden"); });
  bind("breakdownsRefreshBtn","click", loadBreakdownsList);
  bind("breakdownsSearch","input", ()=>{
    // lightweight debounce
    clearTimeout(window.__bdSearchTimer);
    window.__bdSearchTimer = setTimeout(loadBreakdownsList, 200);
  });


  bind("gotItBtn","click", ()=>{
    // Default: mark learned
    if(activeTab === "learned" && learnedViewMode === "study"){
      // Learned study: move back to Unlearned
      return setCurrentStatus("active");
    }
    if(activeTab === "custom"){
      return setCurrentStatus("learned");
    }
    return setCurrentStatus("learned");
  });
  bind("unsureBtn","click", ()=>{
    if(activeTab === "learned" && learnedViewMode === "study"){
      // Learned study: move to Unsure
      return setCurrentStatus("unsure");
    }
    if(activeTab === "unsure") return setCurrentStatus("active");
    if(activeTab === "custom") return setCurrentStatus("unsure");
    return setCurrentStatus("unsure");
  });
  
  // Star button on study card
  bind("starStudyBtn","click", async ()=>{
    if(!deck.length) return;
    const c = deck[deckIndex];
    const inSet = await toggleCustomSet(c.id);
    if(inSet !== null){
      c.in_custom_set = inSet;
      updateStudyStarButton(c);
      setStatus(inSet ? "Added to Custom Set" : "Removed from Custom Set");
    }
  });
  
  // Sort by status dropdown for All list
  bind("sortByStatus","change", async ()=>{
    await refresh();
  });
  
  // Settings nav tabs
  document.querySelectorAll(".settingsNavBtn").forEach(btn => {
    btn.addEventListener("click", () => {
      const section = btn.getAttribute("data-section");
      switchSettingsSection(section);
    });
  });
  
  // Sync buttons
  bind("syncPushBtn","click", doSyncPush);
  bind("syncPullBtn","click", doSyncPull);
  bind("syncBreakdownsBtn","click", doSyncBreakdowns);
  
bind("card","click", flip);
  bind("card","keydown", (e)=>{
    if(e.code === "Space" || e.code === "Enter"){
      e.preventDefault(); flip();
    }
  });

  // Search box with clear button
  bind("searchBox","input", async ()=>{ 
    updateSearchClearButton();
    await refresh(); 
  });
  bind("searchClearBtn","click", async ()=>{
    $("searchBox").value = "";
    updateSearchClearButton();
    await refresh();
  });

  bind("allCardsBtn","click", async ()=>{
    scopeGroup = "";
    $("groupSelect").value = "";
    allCardsMode = true;
    updateFilterHighlight();
  updateLearnedViewHighlight();
    await refresh();
  });

  bind("settingsBtn","click", openSettings);
  bind("closeSettingsBtn","click", async ()=>{
    $("viewSettings").classList.add("hidden");
    if(activeTab === "active" || activeTab === "unsure" || (activeTab === "learned" && learnedViewMode === "study")){
      $("viewStudy").classList.remove("hidden");
    } else {
      $("viewList").classList.remove("hidden");
    }
    await refresh();
  });

  bind("settingsScope","change", async ()=>{
    await loadSettingsForm($("settingsScope").value);
  });

  bind("setBreakdownApplyAll","change", ()=>{ updateBreakdownSettingsUI(); });

  bind("saveSettingsBtn","click", saveSettings);
  bind("resetSettingsBtn","click", async ()=>{
    let scope = $("settingsScope").value;
    if(!scope) scope = "all";
    if(!confirm("Reset settings to defaults for this scope?")) return;
    try{
      await jpost("/api/settings_reset", {scope});
      // clear cached settings + reload form
      settingsAll = null;
      settingsGroup = {};
      window.__activeSettings = null;
      await loadSettingsForm(scope);
      await refresh();
    } catch(e){
      console.error(e);
    }
  });

  // Voice settings bindings
  bind("setSpeechRate","input", ()=>{
    updateRateLabel($("setSpeechRate").value);
  });
  bind("testVoiceBtn","click", testVoice);
  bind("resetVoiceBtn","click", resetVoiceToDefault);

  // Allow Enter key to submit login/register forms
  $("loginPassword").addEventListener("keydown", (e) => {
    if(e.key === "Enter") $("btnLogin").click();
  });
  $("regPassword").addEventListener("keydown", (e) => {
    if(e.key === "Enter") $("btnRegister").click();
  });

  const ok = await ensureLoggedIn();
  if(!ok) return;
  await postLoginInit();
}

main().catch(err=>{
  console.error(err);
  setStatus("Error: " + err.message);
});

try{ wireUserMenu(); }catch(e){}

// Logout function
async function doLogout(){
  // Confirm logout
  if(!confirm("Are you sure you want to logout?")) return;
  
  try{ await jpost("/api/logout", {}); } catch(e){}
  currentUser = null;
  setUserLine();
  appInitialized = false;
  allGroups = [];
  try{ $("groupSelect").innerHTML = ""; } catch(e){}
  try{ $("settingsScope").innerHTML = ""; } catch(e){}
  // Close user menu
  const menu = $("userMenu");
  if(menu) menu.classList.add("hidden");
  await ensureLoggedIn();
}

// Settings nav section switching
function switchSettingsSection(section){
  // Update nav buttons
  document.querySelectorAll(".settingsNavBtn").forEach(btn => {
    btn.classList.toggle("active", btn.getAttribute("data-section") === section);
  });
  // Update sections
  document.querySelectorAll(".settingsSection").forEach(sec => {
    sec.classList.toggle("active", sec.getAttribute("data-section") === section);
  });
  // Update sync section info if switching to sync
  if(section === "sync"){
    updateSyncSectionInfo();
  }
  // Update AI section info
  if(section === "ai"){
    updateAISectionInfo();
  }
}

// Update sync section with current user info
function updateSyncSectionInfo(){
  const loginLabel = $("syncLoginLabel");
  const userLabel = $("syncUserLabel");
  const banner = $("syncLoginStatus");
  
  if(currentUser){
    if(loginLabel) loginLabel.textContent = "Logged In" + (isAdminUser() ? " (Admin)" : "");
    if(userLabel) userLabel.textContent = `User: ${currentUser.display_name || currentUser.username}` + (isAdminUser() ? " (Admin)" : "");
    if(banner) banner.classList.remove("loggedOut");
  } else {
    if(loginLabel) loginLabel.textContent = "Not Logged In";
    if(userLabel) userLabel.textContent = "User: --";
    if(banner) banner.classList.add("loggedOut");
  }
  
  // Update last sync time from localStorage
  const lastSync = localStorage.getItem("kenpo_last_sync");
  const syncTime = $("syncLastTime");
  if(syncTime){
    if(lastSync){
      const d = new Date(parseInt(lastSync));
      syncTime.textContent = d.toLocaleString();
    } else {
      syncTime.textContent = "Never";
    }
  }
}

// Update AI section info
function updateAISectionInfo(){
  const chatgptStatus = $("aiChatgptStatus");
  const geminiStatus = $("aiGeminiStatus");
  
  if(chatgptStatus){
    if(aiStatus && aiStatus.openai_available){
      chatgptStatus.textContent = aiStatus.openai_model || "Available";
      chatgptStatus.className = "aiStatusValue active";
    } else {
      chatgptStatus.textContent = "Not configured";
      chatgptStatus.className = "aiStatusValue inactive";
    }
  }
  
  if(geminiStatus){
    if(aiStatus && aiStatus.gemini_available){
      geminiStatus.textContent = aiStatus.gemini_model || "Available";
      geminiStatus.className = "aiStatusValue active";
    } else {
      geminiStatus.textContent = "Not configured";
      geminiStatus.className = "aiStatusValue inactive";
    }
  }
}

// Sync push
async function doSyncPush(){
  const statusEl = $("syncStatus");
  try{
    statusEl.className = "syncStatus";
    statusEl.textContent = "Pushing...";
    statusEl.style.display = "block";
    
    // Get current progress and push
    const res = await jpost("/api/sync/push", {});
    
    localStorage.setItem("kenpo_last_sync", Date.now().toString());
    updateSyncSectionInfo();
    
    statusEl.className = "syncStatus success";
    statusEl.textContent = "✓ Push complete!";
    setTimeout(()=>{ statusEl.style.display = "none"; }, 3000);
  } catch(e){
    statusEl.className = "syncStatus error";
    statusEl.textContent = "✗ Push failed: " + (e.message || "Unknown error");
  }
}

// Sync pull
async function doSyncPull(){
  const statusEl = $("syncStatus");
  try{
    statusEl.className = "syncStatus";
    statusEl.textContent = "Pulling...";
    statusEl.style.display = "block";
    
    const res = await jget("/api/sync/pull");
    
    localStorage.setItem("kenpo_last_sync", Date.now().toString());
    updateSyncSectionInfo();
    
    // Refresh counts and view
    await refreshCounts();
    
    statusEl.className = "syncStatus success";
    statusEl.textContent = "✓ Pull complete!";
    setTimeout(()=>{ statusEl.style.display = "none"; }, 3000);
  } catch(e){
    statusEl.className = "syncStatus error";
    statusEl.textContent = "✗ Pull failed: " + (e.message || "Unknown error");
  }
}

// Sync breakdowns
async function doSyncBreakdowns(){
  const statusEl = $("syncStatus");
  try{
    statusEl.className = "syncStatus";
    statusEl.textContent = "Syncing breakdowns...";
    statusEl.style.display = "block";
    
    const res = await jget("/api/sync/breakdowns");
    
    // Update local breakdown cache
    if(res && res.breakdowns){
      for(const [id, bd] of Object.entries(res.breakdowns)){
        breakdownInlineCache[id] = bd;
      }
    }
    
    statusEl.className = "syncStatus success";
    statusEl.textContent = `✓ Synced ${Object.keys(res.breakdowns || {}).length} breakdowns!`;
    setTimeout(()=>{ statusEl.style.display = "none"; }, 3000);
  } catch(e){
    statusEl.className = "syncStatus error";
    statusEl.textContent = "✗ Sync failed: " + (e.message || "Unknown error");
  }
}

// Update star button on study card
function updateStudyStarButton(card){
  const btn = $("starStudyBtn");
  if(!btn) return;
  
  const inSet = card && card.in_custom_set;
  btn.textContent = inSet ? "★" : "☆";
  btn.classList.toggle("starred", inSet);
  btn.title = inSet ? "Remove from Custom Set" : "Add to Custom Set";
}

// ============================================================
// EDIT DECKS PAGE
// ============================================================

let currentDecks = [];
let userCards = [];
let activeDeckId = "kenpo";

// Hide all main views
function hideAllViews(){
  $("viewStudy")?.classList.add("hidden");
  $("viewList")?.classList.add("hidden");
  $("viewSettings")?.classList.add("hidden");
  $("viewEditDecks")?.classList.add("hidden");
}

// Open Edit Decks page
function openEditDecks(){
  hideAllViews();
  $("viewEditDecks").classList.remove("hidden");
  loadDecks();
  loadUserCards();
  loadDeletedCards();
}

// Close Edit Decks page
function closeEditDecks(){
  hideAllViews();
  $("viewStudy").classList.remove("hidden");
  refresh();
}

// Switch Edit Decks tabs
function switchEditDecksTab(tabName){
  document.querySelectorAll(".editDecksTab").forEach(t => t.classList.remove("active"));
  document.querySelectorAll(".editDecksSection").forEach(s => s.classList.remove("active"));
  
  document.querySelector(`.editDecksTab[data-tab="${tabName}"]`)?.classList.add("active");
  document.querySelector(`.editDecksSection[data-section="${tabName}"]`)?.classList.add("active");
}

// Show status message in Edit Decks
function showEditDecksStatus(message, type = "info"){
  const el = $("editDecksStatus");
  el.textContent = message;
  el.className = `editDecksStatus ${type}`;
  el.style.display = "block";
  if(type === "success"){
    setTimeout(() => { el.style.display = "none"; }, 3000);
  }
}

// Load decks from server
async function loadDecks(){
  try {
    currentDecks = await jget("/api/decks");
    
    // Load saved active deck from settings
    try {
      const settings = await jget("/api/settings?scope=all");
      if(settings.activeDeckId){
        activeDeckId = settings.activeDeckId;
      }
    } catch(e){}
    
    renderDecksList();
    updateDeckDropdown();
  } catch(e){
    showEditDecksStatus("Failed to load decks: " + e.message, "error");
  }
}

// Render decks list
function renderDecksList(){
  const list = $("decksList");
  if(!list) return;
  
  list.innerHTML = "";
  
  for(const deck of currentDecks){
    const isActive = deck.id === activeDeckId;
    const div = document.createElement("div");
    div.className = "deckItem" + (isActive ? " active" : "");
    div.innerHTML = `
      <div class="deckRadio"></div>
      <div class="deckInfo">
        <div class="deckName">
          ${escapeHtml(deck.name)}
          ${deck.isBuiltIn ? '<span class="deckBadge">Built-in</span>' : ''}
          ${deck.isDefault ? '<span class="deckBadge default">★ Default</span>' : ''}
        </div>
        <div class="deckDesc">${escapeHtml(deck.description || "")}</div>
        <div class="deckCount">${deck.cardCount} cards</div>
      </div>
      <div class="deckActions">
        ${!isActive ? '<button class="deckSwitchBtn appBtn primary small" title="Switch to this deck">Switch</button>' : '<span class="deckActiveLabel">✓ Active</span>'}
        ${!deck.isDefault ? '<button class="deckDefaultBtn" title="Set as default startup deck">★</button>' : ''}
        ${!deck.isBuiltIn ? '<button class="deckEditBtn" title="Edit deck">✏️</button>' : ''}
        ${!deck.isBuiltIn ? '<button class="deckDeleteBtn" title="Delete deck">🗑️</button>' : ''}
      </div>
    `;
    
    const switchBtn = div.querySelector(".deckSwitchBtn");
    if(switchBtn){
      switchBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        switchToDeck(deck.id);
      });
    }
    
    const defaultBtn = div.querySelector(".deckDefaultBtn");
    if(defaultBtn){
      defaultBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        setDefaultDeck(deck.id, deck.name);
      });
    }
    
    const editBtn = div.querySelector(".deckEditBtn");
    if(editBtn){
      editBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        showEditDeckModal(deck);
      });
    }
    
    const deleteBtn = div.querySelector(".deckDeleteBtn");
    if(deleteBtn){
      deleteBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        deleteDeck(deck.id, deck.name);
      });
    }
    
    list.appendChild(div);
  }
  
  // Update current deck banner
  const currentDeck = currentDecks.find(d => d.id === activeDeckId) || currentDecks[0];
  if(currentDeck){
    $("currentDeckName").textContent = currentDeck.name;
    $("currentDeckCount").textContent = `${currentDeck.cardCount} cards`;
  }
}

// Select a deck (visual only - just highlights it)
function selectDeck(deckId){
  // Just visual highlight, no actual switch
  document.querySelectorAll(".deckItem").forEach(item => {
    item.classList.remove("selected");
  });
}

// Actually switch to a deck (loads cards)
async function switchToDeck(deckId){
  const deck = currentDecks.find(d => d.id === deckId);
  const deckName = deck?.name || deckId;
  
  activeDeckId = deckId;
  renderDecksList();
  
  // Save active deck preference
  try {
    await jpost("/api/settings", { scope: "all", settings: { activeDeckId: deckId } });
  } catch(e){
    console.error("Failed to save deck preference:", e);
  }
  
  showEditDecksStatus(`Switched to "${deckName}"`, "success");
  
  // Update header deck name
  updateHeaderDeckName();
  
  // Reload groups for the new deck
  await reloadGroupsForDeck();
  
  // Reload counts and cards for the new deck
  await refreshCounts();
  
  // Reload study deck
  await loadDeckForStudy();
  
  // Update AI generator deck display
  updateAiGenDeckName();
  
  // Reload user cards list
  loadUserCards();
}

// Reload groups dropdown for current deck
async function reloadGroupsForDeck(){
  const deckParam = activeDeckId ? `?deck_id=${encodeURIComponent(activeDeckId)}` : "";
  allGroups = await jget("/api/groups" + deckParam);
  
  const sel = $("groupSelect");
  sel.innerHTML = "";
  const optPick = document.createElement("option");
  optPick.value = "";
  optPick.textContent = "Select group…";
  sel.appendChild(optPick);
  
  for(const g of allGroups){
    const o = document.createElement("option");
    o.value = g;
    o.textContent = g;
    sel.appendChild(o);
  }
  
  scopeGroup = "";
  sel.value = "";
  allCardsMode = true;
  
  updateFilterHighlight();
  setupGroupDropdown(allGroups);
}

// Delete a deck
async function deleteDeck(deckId, deckName){
  if(!confirm(`Delete deck "${deckName}"? This cannot be undone.`)) return;
  
  try {
    await fetch(`/api/decks/${deckId}`, { method: "DELETE" });
    showEditDecksStatus(`Deleted "${deckName}"`, "success");
    loadDecks();
  } catch(e){
    showEditDecksStatus("Failed to delete deck: " + e.message, "error");
  }
}

// Set a deck as the default startup deck
async function setDefaultDeck(deckId, deckName){
  try {
    await jpost(`/api/decks/${deckId}/set_default`, {});
    showEditDecksStatus(`Set "${deckName}" as default startup deck`, "success");
    loadDecks();
  } catch(e){
    showEditDecksStatus("Failed to set default: " + e.message, "error");
  }
}

// Show edit deck modal
function showEditDeckModal(deck){
  const modal = $("editDeckModal");
  if(!modal) return;
  
  $("editDeckId").value = deck.id;
  $("editDeckName").value = deck.name;
  $("editDeckDescription").value = deck.description || "";
  
  modal.classList.remove("hidden");
}

// Close edit deck modal
function closeEditDeckModal(){
  const modal = $("editDeckModal");
  if(modal) modal.classList.add("hidden");
}

// Save deck edits
async function saveEditDeck(){
  const deckId = $("editDeckId").value;
  const name = $("editDeckName").value.trim();
  const description = $("editDeckDescription").value.trim();
  
  if(!name){
    showEditDecksStatus("Deck name is required", "error");
    return;
  }
  
  try {
    await jpost(`/api/decks/${deckId}`, { name, description });
    closeEditDeckModal();
    showEditDecksStatus(`Updated "${name}"`, "success");
    loadDecks();
  } catch(e){
    showEditDecksStatus("Failed to update deck: " + e.message, "error");
  }
}

// Create a new deck
async function createDeck(){
  const name = $("newDeckName").value.trim();
  const description = $("newDeckDescription").value.trim();
  
  if(!name){
    showEditDecksStatus("Please enter a deck name", "error");
    return;
  }
  
  try {
    await jpost("/api/decks", { name, description });
    $("newDeckName").value = "";
    $("newDeckDescription").value = "";
    showEditDecksStatus(`Created deck "${name}"`, "success");
    loadDecks();
  } catch(e){
    showEditDecksStatus("Failed to create deck: " + e.message, "error");
  }
}

// Update deck dropdown in Add Cards tab
function updateDeckDropdown(){
  const select = $("addCardDeck");
  if(!select) return;
  
  select.innerHTML = "";
  for(const deck of currentDecks){
    const opt = document.createElement("option");
    opt.value = deck.id;
    opt.textContent = deck.name;
    select.appendChild(opt);
  }
}

// Load user cards
async function loadUserCards(){
  try {
    userCards = await jget("/api/user_cards");
    renderUserCards();
  } catch(e){
    console.error("Failed to load user cards:", e);
  }
}

// Render user cards list
function renderUserCards(){
  const list = $("userCardsList");
  if(!list) return;
  
  if(userCards.length === 0){
    list.innerHTML = '<div class="emptyState"><div class="icon">📝</div>No cards added yet</div>';
    return;
  }
  
  list.innerHTML = "";
  for(const card of userCards){
    const div = document.createElement("div");
    div.className = "userCardItem";
    div.innerHTML = `
      <div class="userCardInfo">
        <div class="userCardTerm">${escapeHtml(card.term)}</div>
        <div class="userCardMeaning">${escapeHtml(card.meaning)}</div>
      </div>
      <div class="userCardActions">
        <button class="edit" title="Edit">✏️</button>
        <button class="delete" title="Delete">🗑️</button>
      </div>
    `;
    
    div.querySelector(".edit").addEventListener("click", () => editUserCard(card));
    div.querySelector(".delete").addEventListener("click", () => deleteUserCard(card.id, card.term));
    
    list.appendChild(div);
  }
}

// Toggle user cards visibility
function toggleUserCards(){
  const list = $("userCardsList");
  const btn = $("toggleUserCardsBtn");
  if(list.classList.contains("hidden")){
    list.classList.remove("hidden");
    btn.textContent = "Hide";
  } else {
    list.classList.add("hidden");
    btn.textContent = "Show";
  }
}

// Add a new card
async function addUserCard(){
  const term = $("addCardTerm").value.trim();
  const meaning = $("addCardMeaning").value.trim();
  const pron = $("addCardPron").value.trim();
  const group = $("addCardGroup").value.trim();
  const deckId = $("addCardDeck").value;
  
  if(!term){
    showEditDecksStatus("Please enter a term", "error");
    return;
  }
  if(!meaning){
    showEditDecksStatus("Please enter a definition", "error");
    return;
  }
  
  try {
    await jpost("/api/user_cards", { term, meaning, pron, group, deckId });
    clearAddCardForm();
    showEditDecksStatus(`Added "${term}"`, "success");
    loadUserCards();
    loadDecks(); // Refresh card counts
  } catch(e){
    showEditDecksStatus("Failed to add card: " + e.message, "error");
  }
}

// Clear add card form
function clearAddCardForm(){
  $("addCardTerm").value = "";
  $("addCardMeaning").value = "";
  $("addCardPron").value = "";
  $("addCardGroup").value = "";
  $("aiDefDropdown").classList.add("hidden");
  $("aiGroupDropdown").classList.add("hidden");
}

// Edit user card (simple prompt-based for now)
function editUserCard(card){
  const newTerm = prompt("Edit term:", card.term);
  if(newTerm === null) return;
  
  const newMeaning = prompt("Edit definition:", card.meaning);
  if(newMeaning === null) return;
  
  updateUserCard(card.id, { term: newTerm, meaning: newMeaning });
}

// Update user card
async function updateUserCard(cardId, updates){
  try {
    await fetch(`/api/user_cards/${cardId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(updates)
    });
    showEditDecksStatus("Card updated", "success");
    loadUserCards();
  } catch(e){
    showEditDecksStatus("Failed to update card: " + e.message, "error");
  }
}

// Delete user card
async function deleteUserCard(cardId, term){
  if(!confirm(`Delete "${term}"?`)) return;
  
  try {
    await fetch(`/api/user_cards/${cardId}`, { method: "DELETE" });
    showEditDecksStatus(`Deleted "${term}"`, "success");
    loadUserCards();
    loadDecks();
  } catch(e){
    showEditDecksStatus("Failed to delete card: " + e.message, "error");
  }
}

// AI Generate Definition
async function aiGenerateDefinition(){
  const term = $("addCardTerm").value.trim();
  if(!term){
    showEditDecksStatus("Please enter a term first", "error");
    return;
  }
  
  // Get current deck context for better AI results
  const deck = currentDecks.find(d => d.id === activeDeckId);
  const deckName = deck?.name || "General";
  const deckDesc = deck?.description || "";
  
  const dropdown = $("aiDefDropdown");
  const list = $("aiDefList");
  dropdown.classList.remove("hidden");
  list.innerHTML = '<div class="aiLoading">🤖 Generating...</div>';
  
  try {
    const res = await jpost("/api/ai/generate_definition", { term, deckName, deckDescription: deckDesc });
    list.innerHTML = "";
    
    for(const def of (res.definitions || [])){
      const opt = document.createElement("div");
      opt.className = "aiOption";
      opt.textContent = def;
      opt.addEventListener("click", () => {
        $("addCardMeaning").value = def;
        dropdown.classList.add("hidden");
      });
      list.appendChild(opt);
    }
  } catch(e){
    list.innerHTML = `<div class="aiOption" style="color:#f87171">Error: ${e.message}</div>`;
  }
}

// AI Generate Pronunciation
async function aiGeneratePronunciation(){
  const term = $("addCardTerm").value.trim();
  if(!term){
    showEditDecksStatus("Please enter a term first", "error");
    return;
  }
  
  const btn = $("aiGenPronBtn");
  btn.textContent = "...";
  btn.disabled = true;
  
  try {
    const res = await jpost("/api/ai/generate_pronunciation", { term });
    $("addCardPron").value = res.pronunciation || "";
    showEditDecksStatus("Pronunciation generated", "success");
  } catch(e){
    showEditDecksStatus("Failed to generate: " + e.message, "error");
  } finally {
    btn.textContent = "🤖";
    btn.disabled = false;
  }
}

// AI Generate Group
async function aiGenerateGroup(){
  const term = $("addCardTerm").value.trim();
  const meaning = $("addCardMeaning").value.trim();
  if(!term){
    showEditDecksStatus("Please enter a term first", "error");
    return;
  }
  
  const dropdown = $("aiGroupDropdown");
  const list = $("aiGroupList");
  dropdown.classList.remove("hidden");
  list.innerHTML = '<div class="aiLoading">🤖 Generating...</div>';
  
  // Get existing groups from cards
  const existingGroups = [...new Set(allGroups || [])];
  
  try {
    const res = await jpost("/api/ai/generate_group", { term, meaning, existingGroups });
    list.innerHTML = "";
    
    for(const grp of (res.groups || [])){
      const opt = document.createElement("div");
      opt.className = "aiOption";
      opt.textContent = grp;
      opt.addEventListener("click", () => {
        $("addCardGroup").value = grp;
        dropdown.classList.add("hidden");
      });
      list.appendChild(opt);
    }
  } catch(e){
    list.innerHTML = `<div class="aiOption" style="color:#f87171">Error: ${e.message}</div>`;
  }
}

// Load deleted cards
async function loadDeletedCards(){
  try {
    const cards = await jget("/api/cards?status=deleted");
    renderDeletedCards(cards);
  } catch(e){
    console.error("Failed to load deleted cards:", e);
  }
}

// Render deleted cards list
function renderDeletedCards(cards){
  const list = $("deletedCardsList");
  const countEl = $("deletedCount");
  const searchEl = $("deletedSearch");
  
  if(!list) return;
  
  const searchTerm = (searchEl?.value || "").toLowerCase();
  const filtered = searchTerm 
    ? cards.filter(c => c.term.toLowerCase().includes(searchTerm) || c.meaning.toLowerCase().includes(searchTerm))
    : cards;
  
  countEl.textContent = `${filtered.length} deleted`;
  
  if(filtered.length === 0){
    list.innerHTML = '<div class="emptyState"><div class="icon">🗑️</div>No deleted cards</div>';
    return;
  }
  
  list.innerHTML = "";
  for(const card of filtered){
    const div = document.createElement("div");
    div.className = "deletedCardItem";
    div.innerHTML = `
      <div class="deletedCardInfo">
        <div class="deletedCardTerm">${escapeHtml(card.term)}</div>
        <div class="deletedCardMeaning">${escapeHtml(card.meaning)}</div>
      </div>
      <button class="restoreBtn">Restore</button>
    `;
    
    div.querySelector(".restoreBtn").addEventListener("click", async () => {
      try {
        await jpost("/api/set_status", { id: card.id, status: "active" });
        showEditDecksStatus(`Restored "${card.term}"`, "success");
        loadDeletedCards();
        refreshCounts();
      } catch(e){
        showEditDecksStatus("Failed to restore: " + e.message, "error");
      }
    });
    
    list.appendChild(div);
  }
}

// Event bindings for Edit Decks
document.addEventListener("DOMContentLoaded", () => {
  // Open/Close Edit Decks
  $("openEditDecksBtn")?.addEventListener("click", openEditDecks);
  $("closeEditDecksBtn")?.addEventListener("click", closeEditDecks);
  
  // Tab switching
  document.querySelectorAll(".editDecksTab").forEach(tab => {
    tab.addEventListener("click", () => switchEditDecksTab(tab.dataset.tab));
  });
  
  // Create deck
  $("createDeckBtn")?.addEventListener("click", createDeck);
  
  // Add card
  $("addCardBtn")?.addEventListener("click", addUserCard);
  $("clearAddCardBtn")?.addEventListener("click", clearAddCardForm);
  $("toggleUserCardsBtn")?.addEventListener("click", toggleUserCards);
  
  // AI buttons
  $("aiGenDefBtn")?.addEventListener("click", aiGenerateDefinition);
  $("aiGenPronBtn")?.addEventListener("click", aiGeneratePronunciation);
  $("aiGenGroupBtn")?.addEventListener("click", aiGenerateGroup);
  
  // Deleted search
  $("deletedSearch")?.addEventListener("input", () => loadDeletedCards());
  
  // AI Generator bindings
  initAiGenerator();
  
  // Edit Deck Modal - close on outside click
  $("editDeckModal")?.addEventListener("click", (e) => {
    if(e.target.id === "editDeckModal") closeEditDeckModal();
  });
});

// ========== AI DECK GENERATOR ==========
let aiGeneratedCards = [];
let aiSelectedIndices = new Set();
let aiPhotoData = null;
let aiDocData = null;

function initAiGenerator(){
  // Method tabs
  document.querySelectorAll(".genMethodTab").forEach(tab => {
    tab.addEventListener("click", () => {
      const method = tab.dataset.method;
      document.querySelectorAll(".genMethodTab").forEach(t => t.classList.remove("active"));
      document.querySelectorAll(".genMethodSection").forEach(s => s.classList.add("hidden"));
      tab.classList.add("active");
      document.querySelector(`.genMethodSection[data-method="${method}"]`)?.classList.remove("hidden");
    });
  });
  
  // Keywords search
  $("aiGenSearchBtn")?.addEventListener("click", aiGenFromKeywords);
  
  // Photo upload
  const photoZone = $("photoUploadZone");
  const photoInput = $("photoFileInput");
  if(photoZone && photoInput){
    photoZone.addEventListener("click", () => photoInput.click());
    photoZone.addEventListener("dragover", (e) => { e.preventDefault(); photoZone.classList.add("dragover"); });
    photoZone.addEventListener("dragleave", () => photoZone.classList.remove("dragover"));
    photoZone.addEventListener("drop", (e) => {
      e.preventDefault();
      photoZone.classList.remove("dragover");
      if(e.dataTransfer.files.length) handlePhotoFile(e.dataTransfer.files[0]);
    });
    photoInput.addEventListener("change", (e) => {
      if(e.target.files.length) handlePhotoFile(e.target.files[0]);
    });
  }
  $("clearPhotoBtn")?.addEventListener("click", clearPhotoUpload);
  $("aiGenPhotoBtn")?.addEventListener("click", aiGenFromPhoto);
  
  // Document upload
  const docZone = $("docUploadZone");
  const docInput = $("docFileInput");
  if(docZone && docInput){
    docZone.addEventListener("click", () => docInput.click());
    docZone.addEventListener("dragover", (e) => { e.preventDefault(); docZone.classList.add("dragover"); });
    docZone.addEventListener("dragleave", () => docZone.classList.remove("dragover"));
    docZone.addEventListener("drop", (e) => {
      e.preventDefault();
      docZone.classList.remove("dragover");
      if(e.dataTransfer.files.length) handleDocFile(e.dataTransfer.files[0]);
    });
    docInput.addEventListener("change", (e) => {
      if(e.target.files.length) handleDocFile(e.target.files[0]);
    });
  }
  $("clearDocBtn")?.addEventListener("click", clearDocUpload);
  $("aiGenDocBtn")?.addEventListener("click", aiGenFromDocument);
  
  // Result actions
  $("aiGenSelectAll")?.addEventListener("click", () => {
    aiSelectedIndices = new Set(aiGeneratedCards.map((_, i) => i));
    renderAiGenResults();
  });
  $("aiGenSelectNone")?.addEventListener("click", () => {
    aiSelectedIndices.clear();
    renderAiGenResults();
  });
  $("aiGenAddSelectedBtn")?.addEventListener("click", addSelectedAiCards);
  
  // Check AI availability
  checkAiAvailability();
}

async function checkAiAvailability(){
  try {
    const status = await jget("/api/ai/status");
    const available = status.openai_available || status.gemini_available;
    $("aiGenWarning")?.classList.toggle("hidden", available);
    $("aiGenSearchBtn")?.toggleAttribute("disabled", !available);
  } catch(e){
    $("aiGenWarning")?.classList.remove("hidden");
  }
}

function handlePhotoFile(file){
  if(!file.type.startsWith("image/")){
    showEditDecksStatus("Please select an image file", "error");
    return;
  }
  const reader = new FileReader();
  reader.onload = (e) => {
    aiPhotoData = e.target.result;
    $("photoPreviewImg").src = aiPhotoData;
    $("photoPreview")?.classList.remove("hidden");
    $("photoUploadZone")?.classList.add("hidden");
    $("aiGenPhotoBtn")?.removeAttribute("disabled");
  };
  reader.readAsDataURL(file);
}

function clearPhotoUpload(){
  aiPhotoData = null;
  $("photoPreview")?.classList.add("hidden");
  $("photoUploadZone")?.classList.remove("hidden");
  $("aiGenPhotoBtn")?.setAttribute("disabled", "");
  $("photoFileInput").value = "";
}

function handleDocFile(file){
  const validTypes = ["application/pdf", "text/plain", "text/markdown"];
  const validExts = [".pdf", ".txt", ".md", ".text"];
  const ext = file.name.toLowerCase().slice(file.name.lastIndexOf("."));
  
  if(!validTypes.includes(file.type) && !validExts.includes(ext)){
    showEditDecksStatus("Please select a PDF or text file", "error");
    return;
  }
  
  const reader = new FileReader();
  reader.onload = (e) => {
    aiDocData = {
      name: file.name,
      type: file.type || (ext === ".pdf" ? "application/pdf" : "text/plain"),
      content: e.target.result
    };
    $("docFileName").textContent = file.name;
    $("docPreview")?.classList.remove("hidden");
    $("docUploadZone")?.classList.add("hidden");
    $("aiGenDocBtn")?.removeAttribute("disabled");
  };
  
  if(file.type === "application/pdf" || ext === ".pdf"){
    reader.readAsDataURL(file);
  } else {
    reader.readAsText(file);
  }
}

function clearDocUpload(){
  aiDocData = null;
  $("docPreview")?.classList.add("hidden");
  $("docUploadZone")?.classList.remove("hidden");
  $("aiGenDocBtn")?.setAttribute("disabled", "");
  $("docFileInput").value = "";
}

async function aiGenFromKeywords(){
  let keywords = $("aiGenKeywords")?.value.trim();
  const maxCards = parseInt($("aiGenMaxCards")?.value) || 20;
  
  // If no keywords, use current deck name and description
  if(!keywords){
    const deck = currentDecks.find(d => d.id === activeDeckId);
    if(deck && deck.name && deck.name !== "Kenpo Vocabulary"){
      keywords = deck.name;
      if(deck.description) keywords += " " + deck.description;
    } else {
      showEditDecksStatus("Please enter search keywords or create a deck with a descriptive name", "error");
      return;
    }
  }
  
  await generateCards({ type: "keywords", keywords, maxCards });
}

async function aiGenFromPhoto(){
  if(!aiPhotoData){
    showEditDecksStatus("Please upload an image first", "error");
    return;
  }
  const maxCards = parseInt($("aiGenPhotoMaxCards")?.value) || 20;
  await generateCards({ type: "photo", imageData: aiPhotoData, maxCards });
}

async function aiGenFromDocument(){
  if(!aiDocData){
    showEditDecksStatus("Please upload a document first", "error");
    return;
  }
  const maxCards = parseInt($("aiGenDocMaxCards")?.value) || 20;
  await generateCards({ type: "document", document: aiDocData, maxCards });
}

async function generateCards(params){
  $("aiGenLoading")?.classList.remove("hidden");
  $("aiGenResults")?.classList.add("hidden");
  
  try {
    const result = await jpost("/api/ai/generate_deck", params);
    
    if(result.cards && result.cards.length > 0){
      // Get existing terms to filter out duplicates
      const existingTerms = new Set();
      
      // Add terms from main cards
      if(window.allCards){
        window.allCards.forEach(c => {
          if(c.term) existingTerms.add(c.term.toLowerCase().trim());
        });
      }
      
      // Add terms from user cards
      const userCardsData = await jget("/api/user_cards?deck_id=" + activeDeckId).catch(() => []);
      if(Array.isArray(userCardsData)){
        userCardsData.forEach(c => {
          if(c.term) existingTerms.add(c.term.toLowerCase().trim());
        });
      }
      
      // Filter out cards that already exist
      const newCards = result.cards.filter(card => {
        const termLower = card.term.toLowerCase().trim();
        return !existingTerms.has(termLower);
      });
      
      const filteredCount = result.cards.length - newCards.length;
      
      if(newCards.length > 0){
        aiGeneratedCards = newCards;
        aiSelectedIndices = new Set(newCards.map((_, i) => i)); // Select all by default
        renderAiGenResults();
        $("aiGenResults")?.classList.remove("hidden");
        
        let msg = `Generated ${newCards.length} new cards.`;
        if(filteredCount > 0){
          msg += ` (${filteredCount} duplicates filtered out)`;
        }
        showEditDecksStatus(msg, "success");
      } else {
        showEditDecksStatus(`All ${result.cards.length} generated cards already exist in your deck.`, "error");
      }
    } else {
      showEditDecksStatus("AI could not generate cards. Try different input.", "error");
    }
  } catch(e){
    showEditDecksStatus("Error: " + (e.message || "Failed to generate"), "error");
  }
  
  $("aiGenLoading")?.classList.add("hidden");
}

function renderAiGenResults(){
  const list = $("aiGenResultsList");
  if(!list) return;
  
  list.innerHTML = aiGeneratedCards.map((card, idx) => {
    const selected = aiSelectedIndices.has(idx);
    return `
      <div class="aiGenResultItem ${selected ? "selected" : ""}" data-idx="${idx}">
        <input type="checkbox" ${selected ? "checked" : ""} />
        <div class="aiGenResultInfo">
          <div class="aiGenResultTerm">${escapeHtml(card.term)}</div>
          <div class="aiGenResultDef">${escapeHtml(card.definition)}</div>
          <div class="aiGenResultMeta">
            ${card.group ? `<span class="aiGenResultGroup">${escapeHtml(card.group)}</span>` : ""}
            ${card.pronunciation ? `<span class="aiGenResultPron">${escapeHtml(card.pronunciation)}</span>` : ""}
          </div>
        </div>
      </div>
    `;
  }).join("");
  
  // Add click handlers
  list.querySelectorAll(".aiGenResultItem").forEach(item => {
    item.addEventListener("click", (e) => {
      if(e.target.type === "checkbox") return;
      const idx = parseInt(item.dataset.idx);
      toggleAiCardSelection(idx);
    });
    item.querySelector("input")?.addEventListener("change", (e) => {
      const idx = parseInt(item.dataset.idx);
      toggleAiCardSelection(idx);
    });
  });
  
  updateAiGenCount();
}

function toggleAiCardSelection(idx){
  if(aiSelectedIndices.has(idx)){
    aiSelectedIndices.delete(idx);
  } else {
    aiSelectedIndices.add(idx);
  }
  renderAiGenResults();
}

function updateAiGenCount(){
  const count = $("aiGenResultsCount");
  if(count){
    count.textContent = `(${aiSelectedIndices.size}/${aiGeneratedCards.length} selected)`;
  }
  // Also update current deck name display
  updateAiGenDeckName();
}

function updateAiGenDeckName(){
  const nameEl = $("aiGenCurrentDeckName");
  if(nameEl){
    const deck = currentDecks.find(d => d.id === activeDeckId);
    nameEl.textContent = deck ? deck.name : "Kenpo Vocabulary";
  }
}

async function addSelectedAiCards(){
  if(aiSelectedIndices.size === 0){
    showEditDecksStatus("Please select at least one card to add", "error");
    return;
  }
  
  // Use the currently active deck
  const deckId = activeDeckId || "kenpo";
  const deck = currentDecks.find(d => d.id === deckId);
  const deckName = deck ? deck.name : "the deck";
  
  const cardsToAdd = Array.from(aiSelectedIndices).map(i => aiGeneratedCards[i]);
  
  let added = 0;
  let failed = 0;
  
  for(const card of cardsToAdd){
    try {
      await jpost("/api/user_cards", {
        term: card.term,
        meaning: card.definition,
        pron: card.pronunciation || "",
        group: card.group || "",
        deckId
      });
      added++;
    } catch(e){
      console.error("Failed to add card:", e);
      failed++;
    }
  }
  
  if(added > 0){
    showEditDecksStatus(`Added ${added} cards to ${deckName}${failed > 0 ? ` (${failed} failed)` : ""}`, "success");
    // Clear results
    aiGeneratedCards = [];
    aiSelectedIndices.clear();
    $("aiGenResults")?.classList.add("hidden");
    // Refresh user cards list and main app
    loadUserCards();
    loadDecks();
    // Also refresh counts and study deck
    await refreshCounts();
    await loadDeckForStudy();
  } else {
    showEditDecksStatus("Failed to add cards", "error");
  }
}
