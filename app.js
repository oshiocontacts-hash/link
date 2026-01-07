/* ==========================
   Utils
========================== */
const $ = (sel) => document.querySelector(sel);

function setYear() {
  const y = new Date().getFullYear();
  const el = $("#year");
  if (el) el.textContent = String(y);
}

async function fetchJson(path) {
  const r = await fetch(path, { cache: "no-store" });
  if (!r.ok) throw new Error(`Failed to load ${path}`);
  return r.json();
}

/* ==========================
   PHOTO (3 visible carousel)
========================== */
function buildPhotoCard(item) {
  // item: { src, title, creditName, profileUrl, postUrl }
  const card = document.createElement("div");
  card.className = "photoCard";

  const imgWrap = document.createElement("a");
  imgWrap.className = "photoCard__imgWrap";
  imgWrap.href = item.postUrl || item.src;
  imgWrap.target = "_blank";
  imgWrap.rel = "noreferrer";

  const img = document.createElement("img");
  img.loading = "lazy";
  img.alt = item.title ? `PHOTO: ${item.title}` : "PHOTO";
  img.src = item.src;

  imgWrap.appendChild(img);

  const meta = document.createElement("div");
  meta.className = "photoCard__meta";

  const t = document.createElement("div");
  t.className = "photoCard__title";
  t.textContent = item.title || "Untitled";

  const sub = document.createElement("div");
  sub.className = "photoCard__sub";

  if (item.creditName && item.profileUrl) {
    const a = document.createElement("a");
    a.href = item.profileUrl;
    a.target = "_blank";
    a.rel = "noreferrer";
    a.textContent = item.creditName;
    sub.appendChild(a);
  } else if (item.creditName) {
    const span = document.createElement("span");
    span.textContent = item.creditName;
    sub.appendChild(span);
  } else {
    const span = document.createElement("span");
    span.textContent = "—";
    sub.appendChild(span);
  }

  meta.appendChild(t);
  meta.appendChild(sub);

  card.appendChild(imgWrap);
  card.appendChild(meta);
  return card;
}

function scrollCarouselByOne(track, direction) {
  const firstCard = track.querySelector(".photoCard");
  if (!firstCard) return;

  const styles = getComputedStyle(track);
  // gap はブラウザによって gap/columnGap のどちらかになる
  const gap = parseFloat(styles.gap || styles.columnGap || "10") || 10;
  const cardWidth = firstCard.getBoundingClientRect().width;
  const delta = (cardWidth + gap) * direction;

  track.scrollBy({ left: delta, behavior: "smooth" });
}

function mulberry32(seed) {
  return function () {
    seed |= 0; seed = seed + 0x6D2B79F5 | 0;
    let t = Math.imul(seed ^ seed >>> 15, 1 | seed);
    t ^= t + Math.imul(t ^ t >>> 7, 61 | t);
    return ((t ^ t >>> 14) >>> 0) / 4294967296;
  };
}

function shuffleWithSeed(arr, seed) {
  const a = [...arr];
  const rnd = mulberry32(seed);
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rnd() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function timeBucketSeed() {
  // 0: 朝(5-10), 1: 昼(11-16), 2: 夜(17-4)
  const h = new Date().getHours();
  const bucket = (h >= 5 && h <= 10) ? 0 : (h >= 11 && h <= 16) ? 1 : 2;

  // 日付も混ぜると、同じ時間帯でも毎日変わる
  const d = new Date();
  const y = d.getFullYear(), m = d.getMonth() + 1, day = d.getDate();
  const dateSeed = y * 10000 + m * 100 + day;

  return dateSeed * 10 + bucket;
}


async function initPhoto() {
  const track = $("#photoTrack");
  if (!track) return;

  try {
    const items = await fetchJson("data/photos.json");
    const seed = timeBucketSeed();                 // 時間帯＋日付で固定シード
    const ordered = shuffleWithSeed(items, seed);  // そのシードでシャッフル
    track.innerHTML = "";
for (const it of ordered) track.appendChild(buildPhotoCard(it));

  } catch (e) {
    track.innerHTML = `<div class="photo__loading">photos.json が読み込めませんでした。</div>`;
  }

  const prev = $("#photoPrev");
  const next = $("#photoNext");

  prev?.addEventListener("click", () => scrollCarouselByOne(track, -1));
  next?.addEventListener("click", () => scrollCarouselByOne(track, 1));

    // --- Auto slide (10s) ---
  const AUTO_MS = 10_000;
  let autoTimer = null;

  function startAuto() {
    stopAuto();
    autoTimer = setInterval(() => scrollCarouselByOne(track, 1), AUTO_MS);
  }
  function stopAuto() {
    if (autoTimer) clearInterval(autoTimer);
    autoTimer = null;
  }

  // ユーザー操作中は止める（触ったらストレスなので）
  const pause = () => stopAuto();
  const resume = () => startAuto();

  track.addEventListener("pointerdown", pause);
  track.addEventListener("mouseenter", pause);
  track.addEventListener("mouseleave", resume);

  // スクロール（ホイール/スワイプ）したら一旦停止→数秒後再開
  let resumeTimeout = null;
  track.addEventListener("scroll", () => {
    pause();
    if (resumeTimeout) clearTimeout(resumeTimeout);
    resumeTimeout = setTimeout(resume, 2500);
  }, { passive: true });

  // タブ非表示中は止める
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) stopAuto();
    else startAuto();
  });

  startAuto();

}

/* ==========================
   SCHEDULE (monthly + modal)
========================== */
const ICONS = {
  youtube: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21.6 7.2s-.2-1.5-.8-2.1c-.8-.8-1.7-.8-2.1-.9C15.7 4 12 4 12 4s-3.7 0-6.7.2c-.4.1-1.3.1-2.1.9-.6.6-.8 2.1-.8 2.1S2 8.9 2 10.6v1.6c0 1.7.4 3.4.4 3.4s.2 1.5.8 2.1c.8.8 1.9.8 2.4.9 1.7.2 6.4.2 6.4.2s3.7 0 6.7-.2c.4-.1 1.3-.1 2.1-.9.6-.6.8-2.1.8-2.1s.4-1.7.4-3.4v-1.6c0-1.7-.4-3.4-.4-3.4zM10 14.8V8.7l6 3.1-6 3z"/></svg>`,
  twitch: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 3h18v11l-5 5h-4l-2 2H8v-2H4V3zm16 10V5H6v12h4v2l2-2h5l3-3z"/><path d="M16 7h-2v5h2V7zm-4 0h-2v5h2V7z"/></svg>`,
  tiktok: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M16 3c.3 2.3 1.7 4 4 4v3c-1.6 0-3-.5-4-1.4V16a6 6 0 1 1-6-6c.3 0 .7 0 1 .1v3.2c-.3-.2-.6-.3-1-.3a3 3 0 1 0 3 3V3h3z"/></svg>`,
  x: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18.9 2H22l-6.8 7.8L23 22h-6.2l-4.9-7.2L5.6 22H2l7.3-8.4L1 2h6.3l4.4 6.6L18.9 2zm-1.1 18h1.7L6.2 3.9H4.4L17.8 20z"/></svg>`,
};

function ymd(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function monthLabel(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  return `${y}-${m}`;
}

function jpDate(isoYmd) {
  return String(isoYmd || "").replaceAll("-", "/");
}

/* ---- modal ---- */
function openModal(ev) {
  const modal = $("#eventModal");
  const body = $("#modalBody");
  const link = $("#modalLink");
  if (!modal || !body || !link) return;

  const iconKey = String(ev?.icon || "youtube").toLowerCase();
  const icon = ICONS[iconKey] ?? ICONS.youtube;
  const platform = ev?.platform ?? ev?.icon ?? "platform";

  body.innerHTML = `
    <div class="modalRow"><div class="modalKey">日付</div><div class="modalVal">${jpDate(ev?.date)}</div></div>
    <div class="modalRow"><div class="modalKey">時間</div><div class="modalVal">${ev?.time ?? "—"}</div></div>
    <div class="modalRow"><div class="modalKey">内容</div><div class="modalVal">${ev?.title ?? "—"}</div></div>
    <div class="modalRow"><div class="modalKey">配信</div><div class="modalVal" style="display:flex;gap:8px;align-items:center;">${icon}<span>${platform}</span></div></div>
    <div class="modalRow"><div class="modalKey">詳細</div><div class="modalVal">${ev?.description ?? "—"}</div></div>
  `;

  if (ev?.url) {
    link.href = ev.url;
    link.style.display = "inline-flex";
  } else {
    link.href = "#";
    link.style.display = "none";
  }

  modal.setAttribute("aria-hidden", "false");
  document.body.style.overflow = "hidden";
}

function closeModal() {
  const modal = $("#eventModal");
  if (!modal) return;
  modal.setAttribute("aria-hidden", "true");
  document.body.style.overflow = "";
}

function initModal() {
  $("#modalClose")?.addEventListener("click", closeModal);
  $("#eventModal")?.addEventListener("click", (e) => {
    if (e.target?.dataset?.close) closeModal();
  });
  window.addEventListener("keydown", (e) => {
    if (e.key === "Escape") closeModal();
  });
}

/* ---- calendar render ---- */
function buildCalendarGrid(targetMonthDate, eventsByDate) {
  const grid = $("#calendarGrid");
  if (!grid) return;

  grid.innerHTML = "";

  const year = targetMonthDate.getFullYear();
  const month = targetMonthDate.getMonth();

  const first = new Date(year, month, 1);
  const last = new Date(year, month + 1, 0);

  // grid starts on Sunday
  const start = new Date(first);
  start.setDate(first.getDate() - first.getDay());

  // grid ends on Saturday
  const end = new Date(last);
  end.setDate(last.getDate() + (6 - last.getDay()));

  const todayKey = ymd(new Date());

  const cur = new Date(start);
  while (cur <= end) {
    const inMonth = cur.getMonth() === month;
    const key = ymd(cur);

    const cell = document.createElement("div");
    cell.className =
      "dayCell" +
      (inMonth ? "" : " dayCell--muted") +
      (key === todayKey ? " dayCell--today" : "");

    const num = document.createElement("div");
    num.className = "dayCell__num";
    num.textContent = String(cur.getDate());
    cell.appendChild(num);

    const eventsWrap = document.createElement("div");
    eventsWrap.className = "dayCell__events";

    const evs = eventsByDate.get(key) || [];
    const maxShow = 4;
    const show = evs.slice(0, maxShow);

    for (const ev of show) {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "evIcon";
      btn.setAttribute(
        "aria-label",
        `${ev?.time ?? ""} ${ev?.title ?? "予定"}`.trim()
      );

      const iconKey = String(ev?.icon || "youtube").toLowerCase();
      btn.innerHTML = ICONS[iconKey] ?? ICONS.youtube;

      btn.addEventListener("click", () => openModal(ev));
      eventsWrap.appendChild(btn);
    }

    if (evs.length > maxShow) {
      const more = document.createElement("button");
      more.type = "button";
      more.className = "evIcon";
      more.textContent = `+${evs.length - maxShow}`;
      more.addEventListener("click", () => openModal(evs[0]));
      eventsWrap.appendChild(more);
    }

    cell.appendChild(eventsWrap);
    grid.appendChild(cell);

    cur.setDate(cur.getDate() + 1);
  }
}

/* ---- init schedule ---- */
async function initSchedule() {
  const label = $("#monthLabel");
  const prev = $("#monthPrev");
  const next = $("#monthNext");
  const grid = $("#calendarGrid");

  // SCHEDULE UIが無いページでは何もしない
  if (!label || !grid || !prev || !next) return;

  let current = new Date();
  current = new Date(current.getFullYear(), current.getMonth(), 1);

  let events = [];
  try {
    events = await fetchJson("data/schedule.json");
  } catch {
    events = [];
  }

  // Map: "YYYY-MM-DD" -> events[]
  const eventsByDate = new Map();
  for (const ev of events) {
    if (!ev?.date) continue;
    const arr = eventsByDate.get(ev.date) || [];
    arr.push(ev);
    eventsByDate.set(ev.date, arr);
  }

  function render() {
    label.textContent = monthLabel(current);
    buildCalendarGrid(current, eventsByDate);
  }

  prev.addEventListener("click", () => {
    current = new Date(current.getFullYear(), current.getMonth() - 1, 1);
    render();
  });

  next.addEventListener("click", () => {
    current = new Date(current.getFullYear(), current.getMonth() + 1, 1);
    render();
  });

  render();
}

/* ==========================
   Boot
========================== */
document.addEventListener("DOMContentLoaded", () => {
  setYear();
  initModal();
  initPhoto();
  initSchedule();
});
