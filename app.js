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
   RL GAME (Q-learning demo)
========================== */
function initRlGame() {
  const arena = $("#rlArena");
  const trainBtn = $("#rlTrainBtn");
  const resetBtn = $("#rlResetBtn");
  const epInput = $("#rlEpisodes");
  const trainStatus = $("#rlTrainStatus");
  const pScoreEl = $("#rlPlayerScore");
  const aiScoreEl = $("#rlAiScore");
  const roundEl = $("#rlRound");
  const actionBtns = [...document.querySelectorAll("[data-rl-action]")];

  if (!arena || !trainBtn || !resetBtn || !epInput || !trainStatus) return;

  const laneSize = 7;
  const maxRounds = 20;
  const ACTIONS = [-1, 0, 1];
  const qTable = new Map();

  const state = {
    playerPos: 0,
    aiPos: laneSize - 1,
    goal: 3,
    playerScore: 0,
    aiScore: 0,
    round: 1,
    trained: false,
    log: "まずはAIを学習してみよう。",
  };

  const clamp = (n, min, max) => Math.min(max, Math.max(min, n));
  const keyOf = (ap, gp) => `${ap}|${gp}`;

  function getQs(ap, gp) {
    const k = keyOf(ap, gp);
    if (!qTable.has(k)) qTable.set(k, [0, 0, 0]);
    return qTable.get(k);
  }

  function chooseAction(ap, gp, epsilon = 0) {
    if (Math.random() < epsilon) return ACTIONS[Math.floor(Math.random() * ACTIONS.length)];
    const qs = getQs(ap, gp);
    const maxQ = Math.max(...qs);
    const best = ACTIONS.filter((_, i) => qs[i] === maxQ);
    return best[Math.floor(Math.random() * best.length)];
  }

  function reward(aiPos, playerPos, goal) {
    if (aiPos === goal && playerPos !== goal) return 2;
    if (playerPos === goal && aiPos !== goal) return -2;
    const dAi = Math.abs(aiPos - goal);
    const dPlayer = Math.abs(playerPos - goal);
    return (dPlayer - dAi) * 0.08;
  }

  function train(episodes) {
    qTable.clear();
    const alpha = 0.18;
    const gamma = 0.92;

    for (let ep = 0; ep < episodes; ep++) {
      let aiPos = laneSize - 1;
      let playerPos = 0;
      let goal = 1 + Math.floor(Math.random() * (laneSize - 2));
      let epsilon = Math.max(0.02, 0.25 - ep / episodes * 0.22);

      for (let t = 0; t < 10; t++) {
        const action = chooseAction(aiPos, goal, epsilon);
        const actIdx = ACTIONS.indexOf(action);

        const playerAction = ACTIONS[Math.floor(Math.random() * ACTIONS.length)];
        const nextAi = clamp(aiPos + action, 0, laneSize - 1);
        const nextPlayer = clamp(playerPos + playerAction, 0, laneSize - 1);

        const r = reward(nextAi, nextPlayer, goal);

        const qs = getQs(aiPos, goal);
        const nextQs = getQs(nextAi, goal);
        const tdTarget = r + gamma * Math.max(...nextQs);
        qs[actIdx] = qs[actIdx] + alpha * (tdTarget - qs[actIdx]);

        aiPos = nextAi;
        playerPos = nextPlayer;

        if (aiPos === goal || playerPos === goal) break;
      }
    }

    state.trained = true;
    trainStatus.textContent = `学習済み (${episodes.toLocaleString()} episodes)`;
  }

  function render() {
    const lane = document.createElement("div");
    lane.className = "rlLane";

    for (let i = 0; i < laneSize; i++) {
      const cell = document.createElement("div");
      cell.className = "rlCell";
      if (i === state.goal) {
        cell.classList.add("rlCell--goal");
        cell.textContent = "⚡";
      }
      if (i === state.playerPos) {
        cell.classList.add("rlCell--player");
        cell.textContent += "🙂";
      }
      if (i === state.aiPos) {
        cell.classList.add("rlCell--ai");
        cell.textContent += "🤖";
      }
      lane.appendChild(cell);
    }

    arena.innerHTML = "";
    arena.appendChild(lane);

    const log = document.createElement("p");
    log.className = "rlLog";
    log.textContent = state.log;
    arena.appendChild(log);

    pScoreEl.textContent = String(state.playerScore);
    aiScoreEl.textContent = String(state.aiScore);
    roundEl.textContent = `${state.round} / ${maxRounds}`;
  }

  function nextRound() {
    state.round += 1;
    if (state.round > maxRounds) {
      if (state.playerScore > state.aiScore) state.log = "あなたの勝ち！AIをさらに学習して再戦しよう。";
      else if (state.playerScore < state.aiScore) state.log = "AIの勝ち！エピソード数を増やして進化を観察しよう。";
      else state.log = "引き分け！次は学習回数を変えて比較してみよう。";
      actionBtns.forEach((b) => (b.disabled = true));
      state.round = maxRounds;
      render();
      return false;
    }

    state.playerPos = 0;
    state.aiPos = laneSize - 1;
    state.goal = 1 + Math.floor(Math.random() * (laneSize - 2));
    return true;
  }

  function judgeRound() {
    if (state.playerPos === state.goal && state.aiPos === state.goal) {
      state.playerScore += 1;
      state.aiScore += 1;
      state.log = "同時到達！両者に1点。";
      return true;
    }
    if (state.playerPos === state.goal) {
      state.playerScore += 1;
      state.log = "プレイヤー先取！+1点";
      return true;
    }
    if (state.aiPos === state.goal) {
      state.aiScore += 1;
      state.log = "AI先取！+1点";
      return true;
    }
    return false;
  }

  function playerStep(playerAction) {
    if (!state.trained) {
      state.log = "先にAI学習を実行してね。";
      render();
      return;
    }

    state.playerPos = clamp(state.playerPos + playerAction, 0, laneSize - 1);
    const aiAction = chooseAction(state.aiPos, state.goal, 0);
    state.aiPos = clamp(state.aiPos + aiAction, 0, laneSize - 1);

    if (judgeRound()) {
      render();
      setTimeout(() => {
        if (nextRound()) {
          state.log = `Round ${state.round}: エネルギーを先に取ろう。`;
          render();
        }
      }, 420);
      return;
    }

    state.log = `あなた:${state.playerPos} / AI:${state.aiPos} / 目標:${state.goal}`;
    render();
  }

  trainBtn.addEventListener("click", () => {
    const episodes = clamp(Number(epInput.value) || 4000, 200, 30000);
    epInput.value = String(episodes);
    trainStatus.textContent = "学習中...";
    setTimeout(() => {
      train(episodes);
      state.log = "学習完了！ボタンで行動してAIと勝負。";
      resetGame();
    }, 30);
  });

  function resetGame() {
    state.playerPos = 0;
    state.aiPos = laneSize - 1;
    state.goal = 1 + Math.floor(Math.random() * (laneSize - 2));
    state.playerScore = 0;
    state.aiScore = 0;
    state.round = 1;
    state.log = state.trained ? "Round 1: エネルギーを先に取ろう。" : "まずはAIを学習してみよう。";
    actionBtns.forEach((b) => (b.disabled = false));
    render();
  }

  resetBtn.addEventListener("click", resetGame);
  actionBtns.forEach((btn) => btn.addEventListener("click", () => playerStep(Number(btn.dataset.rlAction))));

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
  initRlGame();
});
