/* ============================================================
   RESQ · Smart Disaster Response — scroll engine + canvases
   ONE rAF-throttled scroll handler writes CSS custom properties;
   four pinned stages scrub off those properties.
   ============================================================ */
"use strict";

const $ = (id) => document.getElementById(id);
const REDUCED = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const clamp01 = (v) => Math.max(0, Math.min(1, v));
const lerp = (a, b, t) => a + (b - a) * t;

async function apiGet(path) {
  const r = await fetch("/api" + path);
  const body = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(body.error || r.status + " " + r.statusText);
  return body;
}
async function apiPost(path, data) {
  const r = await fetch("/api" + path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data || {}),
  });
  const body = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(body.error || r.status + " " + r.statusText);
  return body;
}
function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}
function badge(text, kind) { return el("span", "badge badge-" + kind, text); }
function statusKind(s) { return s === "OPEN" ? "ok" : s === "BLOCKED" ? "bad" : s === "HIGH_RISK" ? "warn" : "info"; }

/* ---------------- per-word split ---------------- */
function splitWords(node, baseIndex) {
  const words = node.textContent.trim().split(/\s+/);
  node.textContent = "";
  let i = baseIndex || 0;
  for (const w of words) {
    const s = el("span", "w", w);
    s.style.setProperty("--d", (i++ * 36) + "ms");
    node.appendChild(s);
    node.appendChild(document.createTextNode(" "));
  }
  return i;
}
function splitAll() {
  let k = 0;
  document.querySelectorAll("[data-split]").forEach((node) => { k = splitWords(node, k); });
  // headline words reveal when the element enters
  const io = new IntersectionObserver((ents) => {
    for (const en of ents) if (en.isIntersecting) {
      en.target.querySelectorAll(".w").forEach((w) => w.classList.add("in"));
      io.unobserve(en.target);
    }
  }, { threshold: 0.12 });
  document.querySelectorAll("[data-split]").forEach((n) => io.observe(n));
}

/* ---------------- per-atom data-rev reveals ---------------- */
function initReveals() {
  let i = 0;
  document.querySelectorAll("[data-rev]").forEach((n) => {
    n.style.setProperty("--d", ((i++ % 12) * 45 + 40) + "ms");
  });
  const io = new IntersectionObserver((ents) => {
    for (const en of ents) if (en.isIntersecting) { en.target.classList.add("in"); io.unobserve(en.target); }
  }, { threshold: 0.12 });
  document.querySelectorAll("[data-rev]").forEach((n) => io.observe(n));
}

/* ---------------- stage progress driver ---------------- */
const stages = {};
function measureStages() {
  document.querySelectorAll("[data-stage]").forEach((s) => {
    const r = s.getBoundingClientRect();
    stages[s.dataset.stage] = { top: r.top + window.scrollY, height: s.offsetHeight, el: s.querySelector(".stage-inner") };
  });
}
function stageProgress(name) {
  const st = stages[name];
  if (!st) return 0;
  const scroll = window.scrollY - st.top;
  const travel = st.height - window.innerHeight;
  return clamp01(travel > 0 ? scroll / travel : 0);
}

let scrollTicking = false;
const curveState = { progress: -1 };
function onScrollFrame() {
  scrollTicking = false;

  const hero = stageProgress("hero");
  document.documentElement.style.setProperty("--p-hero", hero.toFixed(4));
  if (stages.hero && stages.hero.el) stages.hero.el.style.setProperty("--p", hero.toFixed(4));

  // wipe completes slightly before the stage ends (x1.2, clamped)
  const wipe = stageProgress("wipe");
  document.documentElement.style.setProperty("--p-wipe", wipe.toFixed(4));
  if (stages.wipe && stages.wipe.el) stages.wipe.el.style.setProperty("--p", clamp01(wipe * 1.2).toFixed(4));

  const curve = stageProgress("curve");
  document.documentElement.style.setProperty("--p-curve", curve.toFixed(4));
  curveState.progress = curve;
  if (window.curveDirty) window.curveDirty(curve);

  const gather = stageProgress("gather");
  document.documentElement.style.setProperty("--p-gather", gather.toFixed(4));
  if (stages.gather && stages.gather.el) stages.gather.el.style.setProperty("--p", gather.toFixed(4));
  if (window.gatherDirty) window.gatherDirty(gather);
}
function requestScrollFrame() {
  if (scrollTicking) return;
  scrollTicking = true;
  requestAnimationFrame(() => { scrollTicking = false; onScrollFrame(); });
  // fallback for environments where rAF is throttled/occluded
  setTimeout(() => { if (scrollTicking) { scrollTicking = false; onScrollFrame(); } }, 50);
}
function initScrollDriver() {
  measureStages();
  requestScrollFrame();
  window.addEventListener("scroll", requestScrollFrame, { passive: true });
  window.addEventListener("resize", () => { measureStages(); requestScrollFrame(); });
  // polling fallback: some embedded/occluded webviews never deliver scroll events
  let lastY = window.scrollY;
  setInterval(() => {
    if (window.scrollY !== lastY) { lastY = window.scrollY; measureStages(); requestScrollFrame(); }
  }, 120);
}

/* ---------------- stage 1 · molten glow field ---------------- */
function initGlow() {
  const canvas = $("glowCanvas");
  const ctx = canvas.getContext("2d");
  const blobs = [];
  for (let i = 0; i < 5; i++) {
    blobs.push({
      bx: 0.14 + 0.72 * (i / 4) + Math.sin(i * 2.1) * 0.06,
      by: 0.24 + 0.5 * ((i * 0.7) % 1),
      ampX: 0.10 + (i % 3) * 0.035,
      ampY: 0.07 + (i % 2) * 0.05,
      sx: 0.9 + i * 0.23,
      sy: 0.7 + i * 0.31,
      ph: i * 1.7,
      rad: 0.30 + (i % 3) * 0.09,
      alpha: 0.16 + (i % 3) * 0.06,
    });
  }
  function draw(t) {
    const w = canvas.width = canvas.clientWidth;
    const h = canvas.height = canvas.clientHeight;
    ctx.clearRect(0, 0, w, h);
    ctx.globalCompositeOperation = "lighter";
    for (const b of blobs) {
      const x = (b.bx + b.ampX * Math.sin(t * b.sx + b.ph)) * w;
      const y = (b.by + b.ampY * Math.sin(t * b.sy + b.ph * 1.6)) * h;
      const rad = b.rad * Math.max(w, h);
      const g = ctx.createRadialGradient(x, y, 0, x, y, rad);
      g.addColorStop(0, "rgba(228, 75, 60, " + b.alpha + ")");
      g.addColorStop(0.55, "rgba(160, 40, 30, " + (b.alpha * 0.4) + ")");
      g.addColorStop(1, "rgba(0, 0, 0, 0)");
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(x, y, rad, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.globalCompositeOperation = "source-over";
  }
  if (REDUCED) { draw(1.5); return; }
  const t0 = performance.now();
  (function loop(now) {
    if (document.getElementById("glowCanvas").isConnected) {
      draw((now - t0) / 1000);
      requestAnimationFrame(loop);
    }
  })(t0);
}

/* ---------------- stage 2 · wipe (CSS-driven via --p) ---------------- */
function initWipe() {
  const base = $("wipeBase"), clone = $("wipeClone");
  // exact-clone verification: rects must agree on x, y, width
  requestAnimationFrame(() => {
    const a = base.getBoundingClientRect(), b = clone.getBoundingClientRect();
    if (Math.abs(a.x - b.x) > 0.5 || Math.abs(a.y - b.y) > 0.5 || Math.abs(a.width - b.width) > 0.5) {
      clone.style.top = a.top - b.top + "px";
      clone.style.left = a.left - b.left + "px";
    }
  });
}

/* ---------------- stage 3 · curve ---------------- */
const curve = {
  values: [],          // real costs, km scale
  labels: [],          // row labels
  notes: [],
  progress: 0,
};
function curveValueAt(p) {
  const n = curve.values.length;
  if (!n) return 0;
  const x = clamp01(p) * (n - 1);
  const i = Math.min(n - 2, Math.floor(x));
  return lerp(curve.values[i], curve.values[i + 1], x - i);
}
function initCurve() {
  const canvas = $("curveCanvas");
  const ctx = canvas.getContext("2d");
  function draw() {
    const w = canvas.width = canvas.clientWidth;
    const h = canvas.height = canvas.clientHeight;
    ctx.clearRect(0, 0, w, h);
    if (curve.values.length < 2) return;
    const padL = 46, padR = 20, padT = 46, padB = 34;
    const minV = Math.min(...curve.values), maxV = Math.max(...curve.values);
    const span = Math.max(0.001, maxV - minV);
    const X = (i) => padL + (i / (curve.values.length - 1)) * (w - padL - padR);
    const Y = (v) => padT + (1 - (v - minV) / span) * (h - padT - padB);

    // grid hairlines
    ctx.strokeStyle = "rgba(233,237,230,0.08)";
    ctx.lineWidth = 1;
    for (let g = 0; g <= 3; g++) {
      const y = padT + (g / 3) * (h - padT - padB);
      ctx.beginPath(); ctx.moveTo(padL, y); ctx.lineTo(w - padR, y); ctx.stroke();
    }
    // x ticks
    ctx.fillStyle = "rgba(169,181,178,0.7)";
    ctx.font = "9px Manrope, sans-serif";
    ctx.textAlign = "center";
    curve.labels.forEach((lb, i) => ctx.fillText(lb, X(i), h - 12));

    // full curve in bone at 22%
    ctx.strokeStyle = "rgba(233,237,230,0.22)";
    ctx.lineWidth = 1.6;
    ctx.beginPath();
    curve.values.forEach((v, i) => (i ? ctx.lineTo(X(i), Y(v)) : ctx.moveTo(X(0), Y(v))));
    ctx.stroke();

    // ember trace only as far as progress
    const p = clamp01(curve.progress) * (curve.values.length - 1);
    ctx.strokeStyle = "#e44b3c";
    ctx.lineWidth = 2.4;
    ctx.lineJoin = "round";
    ctx.beginPath();
    const whole = Math.floor(p);
    for (let i = 0; i <= whole && i < curve.values.length; i++) {
      i === 0 ? ctx.moveTo(X(0), Y(curve.values[0])) : ctx.lineTo(X(i), Y(curve.values[i]));
    }
    if (whole < curve.values.length - 1) {
      const frac = p - whole;
      ctx.lineTo(lerp(X(whole), X(whole + 1), frac), lerp(Y(curve.values[whole]), Y(curve.values[whole + 1]), frac));
    }
    ctx.stroke();

    // pulsing head dot with halo (indices clamped so p=1 never overruns)
    const i2 = Math.min(whole + 1, curve.values.length - 1);
    const hx = lerp(X(whole), X(i2), Math.min(1, p - whole));
    const hy = lerp(Y(curve.values[whole]), Y(curve.values[i2]), Math.min(1, p - whole));
    const pulse = REDUCED ? 0 : Math.sin(performance.now() / 300) * 2.2;
    const halo = ctx.createRadialGradient(hx, hy, 0, hx, hy, 16 + pulse * 3);
    halo.addColorStop(0, "rgba(228,75,60,0.5)");
    halo.addColorStop(1, "rgba(228,75,60,0)");
    ctx.fillStyle = halo;
    ctx.beginPath(); ctx.arc(hx, hy, 16 + pulse * 3, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = "#e44b3c";
    ctx.beginPath(); ctx.arc(hx, hy, 3.4, 0, Math.PI * 2); ctx.fill();
  }
  window.curveDirty = (p) => {
    curve.progress = p;
    const v = curveValueAt(Math.min(1, p * 1.15));
    $("curveReadout").innerHTML = v.toFixed(1) + "<small>&nbsp;KM</small>";
    const lit = Math.min(curve.values.length, Math.floor(clamp01(p * 1.08) * curve.values.length) + (p > 0.02 ? 1 : 0));
    document.querySelectorAll(".prow").forEach((row, i) => row.classList.toggle("lit", i < lit));
    draw();
  };
  window.addEventListener("resize", draw);
  draw();
}

/* ---------------- stage 4 · nested molten forms ---------------- */
function initForms() {
  const canvas = $("formCanvas");
  const ctx = canvas.getContext("2d");
  const forms = [
    { scale: 1.0, f1: 3, f2: 5, sp: 0.32, alpha: 0.16, stroke: 0.34 },
    { scale: 0.72, f1: 4, f2: 7, sp: -0.24, alpha: 0.15, stroke: 0.30 },
    { scale: 0.48, f1: 5, f2: 9, sp: 0.4, alpha: 0.14, stroke: 0.28 },
  ];
  function draw(p, t) {
    const w = canvas.width = canvas.clientWidth;
    const h = canvas.height = canvas.clientHeight;
    ctx.clearRect(0, 0, w, h);
    ctx.save();
    const s = lerp(0.72, 1.22, p) * Math.min(w, h) * 0.5;
    ctx.globalCompositeOperation = "lighter";
    for (const f of forms) {
      const cx = w / 2 + Math.sin(t * 0.3 + f.f1) * w * 0.015;
      const cy = h * 0.46 + Math.cos(t * 0.26 + f.f2) * h * 0.02;
      const R = s * f.scale;
      ctx.beginPath();
      for (let a = 0; a <= Math.PI * 2 + 0.02; a += Math.PI / 90) {
        const wob = 1 + 0.16 * Math.sin(f.f1 * a + t * f.sp) + 0.10 * Math.sin(f.f2 * a - t * f.sp * 1.7);
        const x = cx + Math.cos(a) * R * wob;
        const y = cy + Math.sin(a) * R * wob * 0.92;
        a === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
      }
      const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, R * 1.25);
      g.addColorStop(0, "rgba(228, 75, 60, " + f.alpha + ")");
      g.addColorStop(0.7, "rgba(150, 40, 32, " + (f.alpha * 0.45) + ")");
      g.addColorStop(1, "rgba(0, 0, 0, 0)");
      ctx.fillStyle = g;
      ctx.fill();
      ctx.strokeStyle = "rgba(228, 75, 60, " + f.stroke * 0.6 + ")";
      ctx.lineWidth = 1.2;
      ctx.stroke();
    }
    ctx.restore();
  }
  if (REDUCED) { draw(1, 2); window.gatherDirty = () => {}; return; }
  const t0 = performance.now();
  (function loop(now) {
    if (document.getElementById("formCanvas").isConnected) {
      draw(stageProgress("gather"), (now - t0) / 1000);
      requestAnimationFrame(loop);
    }
  })(t0);
  window.gatherDirty = () => {};
}

/* ============================================================
   ICON SYSTEM — professional inline SVG symbols.
   No emoji, no new dependencies. Every icon is defined ONCE here and
   used by both maps: the schematic rasterises it into a cached image,
   the Leaflet layer inlines the same markup into divIcons/popups.
   "{c}" is the colour placeholder.
   ============================================================ */
const ICONS = {
  warn: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M12 2.7 22.6 21.3H1.4z" fill="none" stroke="{c}" stroke-width="2.2" stroke-linejoin="round"/><path d="M12 9.1v5.3" fill="none" stroke="{c}" stroke-width="2.2" stroke-linecap="round"/><circle cx="12" cy="17.9" r="1.3" fill="{c}"/></svg>',
  block: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9.5" fill="none" stroke="{c}" stroke-width="2.2"/><path d="M7.2 7.2 16.8 16.8M16.8 7.2 7.2 16.8" fill="none" stroke="{c}" stroke-width="2.6" stroke-linecap="round"/></svg>',
  roadblock: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect x="2.2" y="8.4" width="19.6" height="7.2" fill="none" stroke="{c}" stroke-width="2.2"/><path d="M6.4 8.4v7.2M11 8.4v7.2M15.6 8.4v7.2M3.6 20.4h16.8" fill="none" stroke="{c}" stroke-width="1.7" stroke-linecap="round"/></svg>',
  crack: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M1.8 12h4.4l2.6-4.6 2.4 9 2.3-6.4 1.7 2h6.5" fill="none" stroke="{c}" stroke-width="2.2" stroke-linejoin="round" stroke-linecap="round"/></svg>',
  flood: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M1.6 7.6q2.6-3.4 5.2 0t5.2 0 5.2 0 5.2 0" fill="none" stroke="{c}" stroke-width="2" stroke-linecap="round"/><path d="M1.6 12.8q2.6-3.4 5.2 0t5.2 0 5.2 0 5.2 0" fill="none" stroke="{c}" stroke-width="2" stroke-linecap="round"/><path d="M1.6 18q2.6-3.4 5.2 0t5.2 0 5.2 0 5.2 0" fill="none" stroke="{c}" stroke-width="2" stroke-linecap="round"/></svg>',
  fire: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M12 2.4c3.1 4 6.4 6.3 6.4 11a6.4 6.4 0 0 1-12.8 0c0-2.2 1-3.8 2.4-5.3.4 1.5 1.2 2.3 2.2 2.6-.4-2.7.7-5.6 1.8-8.3z" fill="none" stroke="{c}" stroke-width="2" stroke-linejoin="round"/></svg>',
  quake: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M1.8 12h3.6l2-5.4 3 11 2.8-8.4 1.8 2.8h6.2" fill="none" stroke="{c}" stroke-width="2.2" stroke-linejoin="round" stroke-linecap="round"/></svg>',
  collapse: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M3.4 20.6V5.2l6.4-2.8v18.2" fill="none" stroke="{c}" stroke-width="2" stroke-linejoin="round"/><path d="M14.2 20.6V9.4h6.4v11.2" fill="none" stroke="{c}" stroke-width="2" stroke-linejoin="round"/><path d="M9.8 4.4 12 12l-2 3.6" fill="none" stroke="{c}" stroke-width="2" stroke-linejoin="round"/><path d="M1.6 20.6h20.8" fill="none" stroke="{c}" stroke-width="2.2" stroke-linecap="round"/></svg>',
  hazard: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M12 2.6c3.6 4.4 6 7.1 6 9.9a6 6 0 0 1-12 0c0-2.8 2.4-5.5 6-9.9z" fill="none" stroke="{c}" stroke-width="2" stroke-linejoin="round"/><path d="M12 9.4v4.8" fill="none" stroke="{c}" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="16.9" r="1.2" fill="{c}"/></svg>',
  storm: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M6.6 14.6A3.6 3.6 0 0 1 7.2 7.5 4.6 4.6 0 0 1 15.8 8a3.4 3.4 0 0 1 .3 6.6" fill="none" stroke="{c}" stroke-width="2" stroke-linejoin="round"/><path d="M12.8 12.2 10 16.4h3.2l-1.8 4.4 4.8-6h-3.1z" fill="{c}"/></svg>',
  slide: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M1.8 20.4 9.2 8.6l3.6 6 2.6-4.2 6.8 10" fill="none" stroke="{c}" stroke-width="2" stroke-linejoin="round"/><circle cx="6.4" cy="17.4" r="1.7" fill="{c}"/><circle cx="11.4" cy="18.6" r="1.2" fill="{c}"/></svg>',
  drought: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><circle cx="12" cy="9.6" r="4.2" fill="none" stroke="{c}" stroke-width="2"/><path d="M12 1.9v2.4M12 15v2.4M1.9 9.6h2.4M19.7 9.6h2.4M4.8 2.4l1.7 1.7M17.5 15.1l1.7 1.7M19.2 2.4l-1.7 1.7M6.5 15.1l-1.7 1.7" fill="none" stroke="{c}" stroke-width="1.7" stroke-linecap="round"/><path d="M3.4 21.2h3.4l1.6-1.8 1.8 1.8h3.4l1.6-1.8 1.8 1.8h3.6" fill="none" stroke="{c}" stroke-width="1.7" stroke-linejoin="round" stroke-linecap="round"/></svg>',
  shelter: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M12 2.8 2.6 11h2.8v9.6h13.2V11h2.8z" fill="none" stroke="{c}" stroke-width="2" stroke-linejoin="round"/><path d="M9.6 20.6v-5.8h4.8v5.8" fill="none" stroke="{c}" stroke-width="2" stroke-linejoin="round"/></svg>',
  star: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M12 2.4l2.9 6.1 6.7.8-4.9 4.6 1.3 6.6L12 17.3l-6 3.2 1.3-6.6L2.4 9.3l6.7-.8z" fill="{c}"/></svg>',
  route: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M3 12h13.6" fill="none" stroke="{c}" stroke-width="2.2" stroke-linecap="round"/><path d="M12.6 7.1 17.6 12l-5 4.9" fill="none" stroke="{c}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/><circle cx="3.4" cy="12" r="1.9" fill="{c}"/></svg>',
  refresh: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M20.4 12a8.4 8.4 0 1 1-2.5-6" fill="none" stroke="{c}" stroke-width="2.2" stroke-linecap="round"/><path d="M20.6 3.4v5h-5" fill="none" stroke="{c}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
};

const C = {
  safe: "#3fbf8f", warn: "#e8a13c", block: "#e44b3c", damaged: "#cd5c34",
  bone: "#e9ede6", mist: "#a9b5b2", off: "#6d7674", ember: "#e44b3c",
};
const ROAD_ICON = { BLOCKED: "roadblock", HIGH_RISK: "warn", DAMAGED: "crack" };
const ROAD_ICON_COLOR = { BLOCKED: C.block, HIGH_RISK: C.warn, DAMAGED: C.damaged };
const DISASTER_ICON = {
  FLOOD: "flood", TSUNAMI: "flood", DAM_FAILURE: "flood",
  INDUSTRIAL_FIRE: "fire",
  EARTHQUAKE: "quake",
  BUILDING_COLLAPSE: "collapse",
  CHEMICAL_LEAK: "hazard", GAS_LEAK: "hazard", NUCLEAR_EMERGENCY: "hazard",
  CYCLONE: "storm", THUNDERSTORM: "storm", TORNADO: "storm",
  LANDSLIDE: "slide", AVALANCHE: "slide",
  DROUGHT: "drought",
};
const SEVERITY_COLOR = { LOW: C.safe, MEDIUM: "#d8a233", HIGH: C.warn, CRITICAL: C.block };
const ROUTE_STATUS = {
  UNAFFECTED: { icon: "route", color: C.safe, label: "ACTIVE ROUTE" },
  WATCHING: { icon: "route", color: C.safe, label: "ROUTE PLANNED" },
  ROUTE_AFFECTED: { icon: "warn", color: C.warn, label: "ROUTE AFFECTED" },
  ALTERNATIVE_ROUTE_FOUND: { icon: "refresh", color: C.warn, label: "ALTERNATIVE ROUTE" },
  NO_SAFE_ROUTE: { icon: "block", color: C.block, label: "NO SAFE ROUTE" },
};

const iconCache = new Map();
/** Rasterise an icon once per key+colour and reuse the image every frame. */
function iconImage(key, color) {
  const k = key + "|" + color;
  let img = iconCache.get(k);
  if (!img) {
    img = new Image();
    img.onload = () => { if (typeof map !== "undefined" && map.ctx) renderMap(); };
    img.src = "data:image/svg+xml;charset=utf-8," + encodeURIComponent((ICONS[key] || ICONS.warn).replace(/\{c\}/g, color));
    iconCache.set(k, img);
  }
  return img;
}
function svgIcon(key, color, size) {
  return (ICONS[key] || ICONS.warn)
    .replace(/\{c\}/g, color)
    .replace("<svg", '<svg width="' + (size || 16) + '" height="' + (size || 16) + '"');
}
function drawIcon(g, key, cx, cy, size, color, alpha) {
  const img = iconImage(key, color);
  if (!img.complete || !img.naturalWidth) return;
  g.save();
  if (alpha != null) g.globalAlpha = alpha;
  g.drawImage(img, cx - size / 2, cy - size / 2, size, size);
  g.restore();
}
function pickRoadByKey(key) {
  if (!key) return null;
  const parts = key.split("|");
  const a = state.byId.get(parts[0]), b = state.byId.get(parts[1]);
  if (!a || !b) return null;
  return state.roads.find((r) => (r.fromId === a.id && r.toId === b.id) || (r.fromId === b.id && r.toId === a.id)) || null;
}
function roadKey(a, b) { return a < b ? a + "|" + b : b + "|" + a; }

/* ---- shared detail-card markup (schematic panel + Leaflet popups) ---- */
function esc(s) {
  return String(s == null ? "" : s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
}
function iconSpan(key, color, size) { return '<i class="ico">' + svgIcon(key, color, size || 15) + "</i>"; }
function cardHead(title, icon, color) {
  return '<h4>' + (icon ? iconSpan(icon, color || C.bone) : "") + esc(title) + "</h4>";
}
function statusBadgeHtml(text, kind) { return '<span class="badge badge-' + kind + '">' + esc(text) + "</span>"; }
function roadStatusKind(s) { return s === "OPEN" ? "ok" : s === "BLOCKED" ? "bad" : s === "HIGH_RISK" ? "warn" : "info"; }
function riskKindOf(r) { return r === "LOW" ? "ok" : r === "CRITICAL" ? "bad" : r === "UNKNOWN" ? "info" : "warn"; }
function kvRows(pairs) {
  let out = '<div class="kv">';
  for (const p of pairs) {
    if (p == null) continue;
    out += '<div><div class="k">' + esc(p[0]) + '</div><div class="v">' + (p[2] ? p[1] : esc(p[1])) + "</div></div>";
  }
  return out + "</div>";
}
function pathHtml(names) {
  return '<div class="route-path">' + (names || []).map((n, i) =>
    (i ? '<span class="arrow">\u2192</span>' : "") +
    '<span class="node' + (i === 0 || i === names.length - 1 ? " node-end" : "") + '">' + esc(n) + "</span>"
  ).join("") + "</div>";
}
/** Does the current route set use this road? */
function routeUseOf(from, to) {
  const uses = [];
  const has = (path) => {
    for (let i = 0; path && i < path.length - 1; i++) {
      if ((path[i] === from && path[i + 1] === to) || (path[i] === to && path[i + 1] === from)) return true;
    }
    return false;
  };
  if (has(state.routePath)) uses.push("Active route");
  if (has(state.shortestPath) && state.shortestPath.length) uses.push("Shortest route");
  if (has(state.previousPath)) uses.push("Previous route");
  return uses;
}
function roadCardHtml(road) {
  if (!road) return '<p class="mini">No road selected.</p>';
  const blockReason = road.disasterPenalty > 0 ? "disaster damage on this road" : "manually closed";
  let html = cardHead(road.from + " \u2192 " + road.to, ROAD_ICON[road.status] || "route", ROAD_ICON_COLOR[road.status] || C.mist);
  html += '<p class="mono">' + esc(road.terrain) + " \u00b7 " + esc(road.riskLevel) + " risk \u00b7 traffic L" + road.trafficLevel + "</p>";
  html += '<p>' + statusBadgeHtml(road.status.replace("_", " "), roadStatusKind(road.status)) +
    (road.status === "BLOCKED" ? ' <span class="mini">' + esc(blockReason) + "</span>" : "") + "</p>";
  html += kvRows([
    ["distance", road.distanceKm.toFixed(1) + " km"],
    ["cost", road.cost == null ? "\u221e" : String(road.cost)],
    ["risk penalty", "+" + (road.riskPenalty != null ? road.riskPenalty : 0)],
    ["disaster", "+" + (road.disasterPenalty != null ? road.disasterPenalty : 0)],
  ]);
  const uses = routeUseOf(road.from, road.to);
  const planned = !!(state.routePath && state.routePath.length) || !!(state.shortestPath && state.shortestPath.length);
  let usage;
  if (uses.length) usage = "Used by: " + uses.join(", ");
  else if (road.status === "BLOCKED") usage = "Excluded from every route (blocked)";
  else if (planned) usage = "Avoided by: current routes";
  else usage = "Not used by any planned route";
  html += '<p class="mini">' + esc(usage) + "</p>";
  return html;
}
function incidentCardHtml(inc) {
  if (!inc) return '<p class="mini">No active incident.</p>';
  const col = SEVERITY_COLOR[inc.severity] || C.warn;
  const key = DISASTER_ICON[inc.type] || "warn";
  let html = cardHead((inc.typeDisplay || inc.type) + " at " + (inc.epicenter || ""), key, col);
  html += kvRows([
    ["severity", inc.severity],
    ["status", inc.preview ? "Preview (not applied)" : "Active"],
    ["roads hit", String((inc.affectedRoads || []).length)],
    ["blocked", String((inc.affectedRoads || []).filter((r) => r.status === "BLOCKED").length)],
  ]);
  if (inc.description) html += '<p class="mini">' + esc(inc.description) + "</p>";
  if (inc.recommendedAction) html += '<p class="mini">' + esc(inc.recommendedAction) + "</p>";
  const roads = inc.affectedRoads || [];
  if (roads.length) {
    html += '<p class="mini">Affected roads</p>';
    html += roads.slice(0, 6).map((r) =>
      '<p class="mini"><b>' + esc(r.from) + " \u2192 " + esc(r.to) + "</b> \u00b7 " +
      statusBadgeHtml(r.status.replace("_", " "), roadStatusKind(r.status)) + "</p>").join("");
    if (roads.length > 6) html += '<p class="mini">+ ' + (roads.length - 6) + " more</p>";
  }
  const rr = state.watch;
  if (rr && rr.hadRoute) html += '<p class="mini">Route impact: ' + esc(rr.statusLabel || "\u2014") + "</p>";
  return html;
}
function shelterCardHtml(name) {
  const info = shelterState(name);
  if (!info) return '<p class="mini">No shelter here.</p>';
  const z = info.z, rec = info.rec || {};
  let html = cardHead(z.name, "shelter", info.colour);
  html += '<p>' + statusBadgeHtml(info.label, info.kind) + "</p>";
  html += kvRows([
    ["occupancy", z.occupied + " / " + z.capacity],
    ["available", String(z.available)],
    ["capacity", z.occupancyPercent + "% full"],
    ["status", z.status],
    ["route", rec.distanceKm != null ? rec.distanceKm + " km" : "\u2014"],
    ["est. time", rec.estMinutes != null ? rec.estMinutes + " min" : "\u2014"],
    ["risk", rec.riskLevel || "\u2014"],
    ["reachability", rec.reachable === false ? "UNREACHABLE" : (rec.reachable ? "REACHABLE" : "not checked")],
    ["recommendation", rec.recommended ? "RECOMMENDED" : (rec.rank ? "#" + rec.rank : "\u2014")],
  ]);
  if (rec.path && rec.path.length) html += pathHtml(rec.path);
  if (rec.reason) html += '<p class="mini">' + esc(rec.reason) + "</p>";
  return html;
}
function routeCardHtml() {
  const r = state.routeInfo;
  if (!r) return '<p class="mini">No route planned yet. Use the Routes band to plan one.</p>';
  const st = ROUTE_STATUS[r.status] || ROUTE_STATUS.UNAFFECTED;
  let html = cardHead((r.from || "") + " \u2192 " + (r.to || ""), st.icon, st.color);
  html += '<p>' + statusBadgeHtml(st.label, r.status === "NO_SAFE_ROUTE" ? "bad" : r.status === "ROUTE_AFFECTED" || r.status === "ALTERNATIVE_ROUTE_FOUND" ? "warn" : "ok") + "</p>";
  if (r.path && r.path.length) html += pathHtml(r.path);
  html += kvRows([
    ["distance", r.distanceKm != null ? r.distanceKm + " km" : "\u2014"],
    ["cost", r.cost != null ? r.cost : "\u2014"],
    ["risk", r.riskLevel || "\u2014"],
    ["roads", r.path ? String(Math.max(0, r.path.length - 1)) : "\u2014"],
  ]);
  (r.warnings || []).forEach((w) => { html += '<p class="mini">' + esc(w) + "</p>"; });
  return html;
}
function locationCardHtml(name) {
  const l = state.byName.get(name);
  if (!l) return '<p class="mini">Location not found.</p>';
  const incident = state.roads.filter((r) => r.fromId === l.id || r.toId === l.id);
  const open = incident.filter((r) => r.status === "OPEN").length;
  const blocked = incident.filter((r) => r.status === "BLOCKED").length;
  const highRisk = incident.filter((r) => r.status === "HIGH_RISK").length;
  let html = cardHead(l.name, l.terrain === "OPEN" && shelterState(l.name) ? "shelter" : "route", C.bone);
  html += '<p class="mono">' + esc(l.id.toUpperCase()) + " \u00b7 " + esc(l.terrain) + " \u00b7 (" + l.x + ", " + l.y + ")</p>";
  html += "<p>" + incident.length + " roads \u00b7 " + open + " open" +
    (blocked ? " \u00b7 " + blocked + " blocked" : "") + (highRisk ? " \u00b7 " + highRisk + " high risk" : "") + "</p>";
  return html;
}

/* ---------------- interactive map (real graph) ---------------- */
const map = {
  canvas: null, ctx: null,
  scale: 1, ox: 0, oy: 0,
  dragging: false, moved: false, lastX: 0, lastY: 0,
  hover: null, fitted: false,
};
const ROAD_COLORS = { OPEN: "#57604f", HIGH_RISK: "#e8a13c", DAMAGED: "#cd5c34", BLOCKED: "#e44b3c" };
const sx = (x) => x * map.scale + map.ox;
const sy = (y) => y * map.scale + map.oy;

/** Live state of a shelter location: capacity + reachability + recommendation. */
function shelterState(name) {
  const z = (state.shelters || []).find((s) => s.name === name || s.location === name);
  if (!z) return null;
  const rec = state.shelterRecs ? state.shelterRecs[z.name] : null;
  if (rec && rec.reachable === false) return { z, rec, colour: C.off, icon: "shelter", label: "UNREACHABLE", kind: "info" };
  if (z.status === "FULL") return { z, rec, colour: C.block, icon: "shelter", label: "FULL", kind: "bad" };
  if (z.occupancyPercent >= 80) return { z, rec, colour: C.warn, icon: "shelter", label: "LIMITED", kind: "warn" };
  if (rec && rec.recommended) return { z, rec, colour: C.safe, icon: "shelter", label: "RECOMMENDED", kind: "ok" };
  return { z, rec, colour: C.safe, icon: "shelter", label: "AVAILABLE", kind: "ok" };
}

/* ---------------- procedural scenery (deterministic, real-data derived) ---------------- */
function hashStr(s) { let h = 2166136261; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return h >>> 0; }
function mulberry32(a) { return function () { a |= 0; a = (a + 0x6D2B79F5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }

const scenery = { buildings: new Map(), patches: new Map() };

function tooCloseToRoad(loc, dx, dy, roadsOf) {
  const px = loc.x + dx, py = loc.y + dy;
  for (const r of roadsOf) {
    const other = state.byId.get(r.fromId === loc.id ? r.toId : r.fromId);
    if (!other) continue;
    const x1 = loc.x, y1 = loc.y, x2 = other.x, y2 = other.y;
    const l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
    let t = l2 ? ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2 : 0;
    t = Math.max(0, Math.min(1, t));
    const cx = x1 + t * (x2 - x1), cy = y1 + t * (y2 - y1);
    if (Math.hypot(px - cx, py - cy) < 15) return true;
  }
  return false;
}

function buildingsFor(loc) {
  if (scenery.buildings.has(loc.id)) return scenery.buildings.get(loc.id);
  const rnd = mulberry32(hashStr(loc.id) ^ 0x9E3779B9);
  const roadsOf = state.roads.filter((r) => r.fromId === loc.id || r.toId === loc.id);
  const list = [];
  let tries = 0;
  while (list.length < 7 && tries++ < 50) {
    const ang = rnd() * Math.PI * 2, dist = 26 + rnd() * 58;
    const dx = Math.cos(ang) * dist, dy = Math.sin(ang) * dist;
    if (tooCloseToRoad(loc, dx, dy, roadsOf)) continue;
    list.push({ dx, dy, w: 11 + rnd() * 17, h: 9 + rnd() * 14, rot: (rnd() - 0.5) * 0.5, shade: 0.72 + rnd() * 0.6 });
  }
  scenery.buildings.set(loc.id, list);
  return list;
}

const BUILDING_BASE = {
  URBAN: [34, 34, 38], COMMERCIAL: [36, 33, 31], RESIDENTIAL: [33, 32, 31],
  INDUSTRIAL: [38, 33, 27], TRANSPORT: [36, 36, 39], COASTAL: [26, 34, 40],
  FORESTED: [28, 36, 30], OPEN: [33, 35, 33],
};
function buildingFill(terrain, shade) {
  const b = BUILDING_BASE[terrain] || [33, 33, 35];
  return "rgb(" + Math.round(b[0] * shade) + "," + Math.round(b[1] * shade) + "," + Math.round(b[2] * shade) + ")";
}

const PATCH_STYLE = {
  FORESTED: "rgba(26, 48, 34, 0.55)", OPEN: "rgba(32, 44, 32, 0.5)",
  COASTAL: "rgba(16, 34, 46, 0.62)", INDUSTRIAL: "rgba(42, 35, 26, 0.5)",
  TRANSPORT: "rgba(28, 31, 36, 0.55)", RESIDENTIAL: "rgba(31, 31, 29, 0.5)",
  URBAN: "rgba(33, 33, 37, 0.5)", COMMERCIAL: "rgba(37, 33, 31, 0.5)",
};
function patchFor(loc) {
  if (scenery.patches.has(loc.id)) return scenery.patches.get(loc.id);
  const rnd = mulberry32(hashStr(loc.id) ^ 0x51A3C0DE);
  const ang = rnd() * Math.PI * 2, dist = 52 + rnd() * 46;
  const big = loc.terrain === "FORESTED" || loc.terrain === "OPEN" || loc.terrain === "COASTAL";
  const p = {
    x: loc.x + Math.cos(ang) * dist, y: loc.y + Math.sin(ang) * dist,
    w: (big ? 100 : 78) + rnd() * 40, h: (big ? 66 : 54) + rnd() * 30,
    rot: (rnd() - 0.5) * 0.5,
    fill: PATCH_STYLE[loc.terrain] || "rgba(32,32,34,0.5)",
  };
  scenery.patches.set(loc.id, p);
  return p;
}

function drawPatches(g) {
  for (const loc of state.locations) {
    const p = patchFor(loc);
    const cx = sx(p.x), cy = sy(p.y);
    const pw = p.w * map.scale, ph = p.h * map.scale;
    if (cx + pw < 0 || cy + ph < 0 || cx - pw > map.canvas.clientWidth || cy - ph > map.canvas.clientHeight) continue;
    g.save();
    g.translate(cx, cy);
    g.rotate(p.rot);
    g.fillStyle = p.fill;
    if (g.roundRect) { g.beginPath(); g.roundRect(-pw / 2, -ph / 2, pw, ph, Math.min(pw, ph) * 0.2); g.fill(); }
    else g.fillRect(-pw / 2, -ph / 2, pw, ph);
    g.restore();
  }
}

function drawBuildings(g) {
  if (map.scale < 0.42) return;   // declutter when zoomed far out
  const W = map.canvas.clientWidth, H = map.canvas.clientHeight;
  for (const loc of state.locations) {
    const bx = sx(loc.x), by = sy(loc.y);
    if (bx < -160 || by < -160 || bx > W + 160 || by > H + 160) continue;
    for (const b of buildingsFor(loc)) {
      const cx = bx + b.dx * map.scale, cy = by + b.dy * map.scale;
      const bw = Math.max(2, b.w * map.scale), bh = Math.max(2, b.h * map.scale);
      g.save();
      g.translate(cx, cy);
      g.rotate(b.rot);
      g.fillStyle = "rgba(0,0,0,0.45)";                 // contact shadow
      g.fillRect(-bw / 2 + 1.5, -bh / 2 + 2, bw, bh);
      g.fillStyle = buildingFill(loc.terrain, b.shade);  // footprint
      g.fillRect(-bw / 2, -bh / 2, bw, bh);
      g.fillStyle = "rgba(233,237,230,0.10)";            // lit roof edge
      g.fillRect(-bw / 2 + 1, -bh / 2, Math.max(1, bw - 2), 1);
      g.restore();
    }
  }
}

function routeTrace(g, pts) {
  g.beginPath();
  pts.forEach((p, i) => (i ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1])));
  g.stroke();
}

function routePoints(names) {
  if (!names || names.length < 2 || !state.byName) return null;
  const pts = names.map((n) => state.byName.get(n)).filter(Boolean).map((l) => [sx(l.x), sy(l.y)]);
  return pts.length < 2 ? null : pts;
}

function layerOn(name) { return !state.layers || state.layers[name] !== false; }
/** Is this graph node one of the shelters (so the SHELTERS layer can hide it)? */
function isShelterLoc(name) {
  return !!(state.shelters || []).some((s) => (s.location || s.name) === name);
}

function drawRouteOverlay(g) {
  g.lineCap = "round";
  g.lineJoin = "round";

  // previously planned route (phase 1): a ghost line, so a reroute never
  // silently replaces the old plan — you can still see what changed.
  const prevPts = routePoints(state.previousPath);
  if (prevPts && (state.previousPath || []).join(">") !== (state.routePath || []).join(">")) {
    g.strokeStyle = "rgba(228, 75, 60, 0.30)";
    g.lineWidth = 5; routeTrace(g, prevPts);
    g.strokeStyle = "rgba(233, 237, 230, 0.45)";
    g.lineWidth = 1.8;
    g.setLineDash([3, 7]);
    routeTrace(g, prevPts);
    g.setLineDash([]);
  }

  // shortest route (phase 3) — bone dashes under the safest one
  const shortPts = routePoints(state.shortestPath);
  if (shortPts) {
    g.strokeStyle = "rgba(4, 6, 5, 0.9)";           // casing
    g.lineWidth = 5.5; routeTrace(g, shortPts);
    g.strokeStyle = "#e9ede6";
    g.lineWidth = 2.6;
    g.setLineDash([7, 6]);
    routeTrace(g, shortPts);
    g.setLineDash([]);
  }

  // safest route — the ember line the whole page is built around
  const pts = routePoints(state.routePath);
  if (!pts) return;
  const breathe = REDUCED ? 0 : (Math.sin(performance.now() / 650) * 0.5 + 0.5);
  g.strokeStyle = "rgba(228, 75, 60, " + (0.10 + 0.10 * breathe) + ")";  // breathing ember glow
  g.lineWidth = 11; routeTrace(g, pts);
  g.strokeStyle = "rgba(233, 237, 230, 0.30)";                            // bone underlay
  g.lineWidth = 7; routeTrace(g, pts);
  g.strokeStyle = "#e44b3c";                                              // solid ember route
  g.lineWidth = 3.2; routeTrace(g, pts);
  g.strokeStyle = "rgba(233, 237, 230, 0.85)";                            // marching bone dashes
  g.lineWidth = 1.5;
  g.setLineDash([8, 12]);
  g.lineDashOffset = REDUCED ? 0 : -((performance.now() / 40) % 20);
  routeTrace(g, pts);
  g.setLineDash([]);
  g.lineDashOffset = 0;
}

/* ---------------- road state symbols (blocked / high risk / damaged) ---------------- */
function drawRoadIcons(g) {
  const zoomed = map.scale >= 0.5;
  const seen = new Set();
  const W = map.canvas.clientWidth, H = map.canvas.clientHeight;
  for (const r of state.roads) {
    const key = r.fromId < r.toId ? r.fromId + "|" + r.toId : r.toId + "|" + r.fromId;
    if (seen.has(key)) continue;
    seen.add(key);
    const colour = ROAD_ICON_COLOR[r.status];
    if (!colour) continue;                        // OPEN stays visually quiet
    if (r.status === "DAMAGED" && !zoomed) continue;  // declutter when zoomed out
    const a = state.byId.get(r.fromId), b = state.byId.get(r.toId);
    if (!a || !b) continue;
    const mx = sx((a.x + b.x) / 2), my = sy((a.y + b.y) / 2);
    const size = r.status === "BLOCKED" ? 19 : 15;
    if (mx < -40 || my < -40 || mx > W + 40 || my > H + 40) continue;
    // dark disc keeps the symbol readable over any road/route colour
    g.beginPath(); g.arc(mx, my, size * 0.8, 0, Math.PI * 2);
    g.fillStyle = "rgba(7, 9, 8, 0.82)"; g.fill();
    g.strokeStyle = colour; g.globalAlpha = 0.85; g.lineWidth = 1.2; g.stroke(); g.globalAlpha = 1;
    drawIcon(g, ROAD_ICON[r.status], mx, my, size, colour);
  }
}

/* ---------------- incidents + shelter markers ---------------- */
function incidentAnchor(inc) {
  const loc = inc.epicenterId ? state.byId.get(inc.epicenterId) : null;
  if (!loc) return null;
  const x = sx(loc.x), y = sy(loc.y);
  const r = inc.severity === "CRITICAL" ? 15 : 12;
  return { x, y: y - r - 20, r, nodeY: y };
}

function drawIncidentMarkers(g) {
  const inc = state.incident;
  if (!inc) return;
  const a = incidentAnchor(inc);
  if (!a) return;
  const col = SEVERITY_COLOR[inc.severity] || C.warn;
  const pulse = REDUCED ? 0 : (Math.sin(performance.now() / 380) * 0.5 + 0.5);
  // connector back to the affected location
  g.strokeStyle = "rgba(233,237,230,0.30)"; g.lineWidth = 1;
  g.beginPath(); g.moveTo(a.x, a.y + a.r); g.lineTo(a.x, a.nodeY - 6); g.stroke();
  if (inc.severity === "CRITICAL" || inc.severity === "HIGH") {
    g.beginPath(); g.arc(a.x, a.y, a.r + 4 + pulse * 3, 0, Math.PI * 2);
    g.strokeStyle = "rgba(228,75,60," + (0.18 + 0.22 * pulse) + ")"; g.lineWidth = 2; g.stroke();
  }
  g.beginPath(); g.arc(a.x, a.y, a.r, 0, Math.PI * 2);
  g.fillStyle = "rgba(7, 9, 8, 0.92)"; g.fill();
  g.strokeStyle = col; g.lineWidth = 2; g.stroke();
  drawIcon(g, DISASTER_ICON[inc.type] || "warn", a.x, a.y, a.r * 1.15, C.bone);
  g.font = "10px Manrope, sans-serif"; g.textAlign = "center";
  const label = (inc.typeDisplay || inc.type).toUpperCase() + " \u00b7 " + inc.severity;
  g.lineWidth = 3; g.strokeStyle = "rgba(7,9,8,0.85)"; g.strokeText(label, a.x, a.y - a.r - 6);
  g.fillStyle = col; g.fillText(label, a.x, a.y - a.r - 6);
}

/** Temporary scenario preview: dashed amber roads + marker, live network untouched. */
function drawScenarioPreview(g) {
  const prev = state.scenarioPreview;
  if (!prev || !layerOn("incidents")) return;
  const seen = new Set();
  for (const rd of prev.affectedRoads || []) {
    const a = state.byName.get(rd.from), b = state.byName.get(rd.to);
    if (!a || !b) continue;
    const key = rd.from + "|" + rd.to;
    if (seen.has(key)) continue;
    seen.add(key);
    const colour = ROAD_ICON_COLOR[rd.status] || C.warn;
    g.strokeStyle = "rgba(7,9,8,0.85)";
    g.lineWidth = 5;
    g.beginPath(); g.moveTo(sx(a.x), sy(a.y)); g.lineTo(sx(b.x), sy(b.y)); g.stroke();
    g.strokeStyle = colour;
    g.lineWidth = 2.6;
    g.setLineDash([6, 5]);
    g.beginPath(); g.moveTo(sx(a.x), sy(a.y)); g.lineTo(sx(b.x), sy(b.y)); g.stroke();
    g.setLineDash([]);
    g.globalAlpha = 0.85;
    drawIcon(g, ROAD_ICON[rd.status] || "warn", sx((a.x + b.x) / 2), sy((a.y + b.y) / 2), 15, colour);
    g.globalAlpha = 1;
  }
  const routePts = routePoints(prev.path);
  if (routePts) {
    g.strokeStyle = "rgba(7,9,8,0.8)";
    g.lineWidth = 6; routeTrace(g, routePts);
    g.strokeStyle = C.warn;
    g.lineWidth = 2.6;
    g.setLineDash([2, 6]);
    g.globalAlpha = 0.9;
    routeTrace(g, routePts);
    g.setLineDash([]);
    g.globalAlpha = 1;
  }
  if (prev.epicenterId) {
    const loc = state.byId.get(prev.epicenterId);
    if (loc) {
      const x = sx(loc.x), y = sy(loc.y) - 32;
      const col = SEVERITY_COLOR[prev.severity] || C.warn;
      g.beginPath(); g.arc(x, y, 13, 0, Math.PI * 2);
      g.fillStyle = "rgba(7,9,8,0.85)"; g.fill();
      g.strokeStyle = col; g.lineWidth = 2; g.setLineDash([4, 4]); g.stroke(); g.setLineDash([]);
      drawIcon(g, DISASTER_ICON[prev.type] || "warn", x, y, 15, C.bone);
      g.font = "10px Manrope, sans-serif"; g.textAlign = "center";
      const label = "PREVIEW \u00b7 " + (prev.typeDisplay || prev.type || "MANUAL").toUpperCase();
      g.lineWidth = 3; g.strokeStyle = "rgba(7,9,8,0.85)"; g.strokeText(label, x, y - 19);
      g.fillStyle = col; g.fillText(label, x, y - 19);
    }
  }
}

function drawNodes(g) {
  const showShelters = layerOn("shelters");
  for (const l of state.locations) {
    const x = sx(l.x), y = sy(l.y);
    const info = showShelters ? shelterState(l.name) : null;
    const selected = state.selected === l || map.hover === l;

    if (info) {
      const r = 11;
      if (!REDUCED) {
        g.beginPath();
        g.arc(x, y, r + 5 + Math.sin(performance.now() / 400) * 2, 0, Math.PI * 2);
        g.strokeStyle = info.colour === C.off ? "rgba(109,118,116,0.45)" : "rgba(63,191,143,0.45)";
        g.lineWidth = 1.5; g.stroke();
      }
      if (info.rec && info.rec.recommended) {
        g.beginPath(); g.arc(x, y, r + 8, 0, Math.PI * 2);
        g.strokeStyle = "rgba(233,237,230,0.5)"; g.lineWidth = 2; g.stroke();
      }
      g.beginPath(); g.arc(x + 1.5, y + 2.5, r, 0, Math.PI * 2);
      g.fillStyle = "rgba(0,0,0,0.5)"; g.fill();
      g.beginPath(); g.arc(x, y, r, 0, Math.PI * 2);
      g.fillStyle = "rgba(7,9,8,0.92)"; g.fill();
      g.strokeStyle = info.colour; g.lineWidth = 2; g.stroke();
      drawIcon(g, info.icon, x, y, 13, info.colour);
      if (info.rec && info.rec.recommended) {
        g.beginPath(); g.arc(x + r * 0.95, y - r * 0.95, 6.5, 0, Math.PI * 2);
        g.fillStyle = "rgba(7,9,8,0.95)"; g.fill();
        g.strokeStyle = C.safe; g.lineWidth = 1.2; g.stroke();
        drawIcon(g, "star", x + r * 0.95, y - r * 0.95, 9, C.safe);
      }
      if (selected) {
        g.beginPath(); g.arc(x, y, r + 5, 0, Math.PI * 2);
        g.strokeStyle = C.bone; g.lineWidth = 1.4; g.stroke();
      }
      g.font = "11px Manrope, sans-serif"; g.textAlign = "center";
      g.lineWidth = 3; g.strokeStyle = "rgba(7,9,8,0.85)"; g.strokeText(l.name, x, y + r + 15);
      g.fillStyle = C.bone; g.fillText(l.name, x, y + r + 15);
      g.font = "9px Manrope, sans-serif";
      g.lineWidth = 3; g.strokeText(info.label, x, y + r + 26);
      g.fillStyle = info.colour; g.fillText(info.label, x, y + r + 26);
      continue;
    }
    // SHELTERS layer off → hide shelter markers entirely (keep other waypoints)
    if (!showShelters && isShelterLoc(l.name)) continue;

    const r = 6;
    g.beginPath(); g.arc(x + 1.5, y + 2.5, r, 0, Math.PI * 2);
    g.fillStyle = "rgba(0, 0, 0, 0.5)"; g.fill();
    g.beginPath(); g.arc(x, y, r, 0, Math.PI * 2);
    g.fillStyle = C.bone; g.fill();
    if (selected) {
      g.beginPath(); g.arc(x, y, r + 4, 0, Math.PI * 2);
      g.strokeStyle = C.bone; g.lineWidth = 1.4; g.stroke();
    }
    g.font = "11px Manrope, sans-serif"; g.textAlign = "center";
    g.lineWidth = 3; g.strokeStyle = "rgba(7, 9, 8, 0.85)";
    g.strokeText(l.name, x, y + r + 14);
    g.fillStyle = C.mist; g.fillText(l.name, x, y + r + 14);
  }
}

function mapFit() {
  const c = map.canvas;
  if (!state.locations.length) return;
  let minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
  for (const l of state.locations) {
    minX = Math.min(minX, l.x); minY = Math.min(minY, l.y);
    maxX = Math.max(maxX, l.x); maxY = Math.max(maxY, l.y);
  }
  const pad = 70;
  map.scale = Math.min((c.clientWidth - pad * 2) / Math.max(1, maxX - minX),
                       (c.clientHeight - pad * 2) / Math.max(1, maxY - minY));
  map.ox = c.clientWidth / 2 - (minX + maxX) / 2 * map.scale;
  map.oy = c.clientHeight / 2 - (minY + maxY) / 2 * map.scale;
  map.fitted = true;
}

function renderMap() {
  const c = map.canvas, g = map.ctx;
  if (!c) return;
  const w = c.clientWidth, h = c.clientHeight;
  if (c.width !== w || c.height !== h) { c.width = w; c.height = h; mapFit(); }
  g.clearRect(0, 0, w, h);
  g.strokeStyle = "rgba(233,237,230,0.05)";
  g.lineWidth = 1;
  const step = 50 * map.scale;
  if (step > 16) {
    for (let x = map.ox % step; x < w; x += step) { g.beginPath(); g.moveTo(x, 0); g.lineTo(x, h); g.stroke(); }
    for (let y = map.oy % step; y < h; y += step) { g.beginPath(); g.moveTo(0, y); g.lineTo(w, y); g.stroke(); }
  }

  drawPatches(g);

  // roads: dark casing under open roads, dashed colour for incidents
  const seen = new Set();
  for (const r of state.roads) {
    const key = r.fromId < r.toId ? r.fromId + "|" + r.toId : r.toId + "|" + r.fromId;
    if (seen.has(key)) continue;
    seen.add(key);
    const a = state.byId.get(r.fromId), b = state.byId.get(r.toId);
    if (!a || !b) continue;
    if (layerOn("routes") && state.highlighted.has(a.name) && state.highlighted.has(b.name)) continue; // drawn by route overlay
    g.beginPath();
    g.moveTo(sx(a.x), sy(a.y));
    g.lineTo(sx(b.x), sy(b.y));
    if (r.status === "OPEN") {
      g.strokeStyle = "rgba(4, 6, 5, 0.9)";   // casing
      g.lineWidth = 5;
      g.stroke();
      g.strokeStyle = ROAD_COLORS.OPEN;
      g.lineWidth = 2;
    } else {
      g.strokeStyle = ROAD_COLORS[r.status] || "#57604f";
      g.lineWidth = 2.4;
      if (r.status === "BLOCKED" || r.status === "DAMAGED") g.setLineDash([7, 5]);
    }
    g.stroke();
    g.setLineDash([]);
    if (map.hoverRoad && roadKey(map.hoverRoad.fromId, map.hoverRoad.toId) === key) {
      g.beginPath();
      g.moveTo(sx(a.x), sy(a.y));
      g.lineTo(sx(b.x), sy(b.y));
      g.strokeStyle = "rgba(233, 237, 230, 0.55)";   // hover affordance
      g.lineWidth = 4;
      g.stroke();
    }
  }

  // Draw order sets the visual priority: quiet roads first, then route
  // overlays, then the emergency symbols and markers that must never be hidden.
  if (layerOn("routes")) drawRouteOverlay(g);
  drawBuildings(g);
  if (layerOn("roads")) drawRoadIcons(g);
  if (layerOn("incidents")) drawIncidentMarkers(g);
  drawScenarioPreview(g);
  drawNodes(g);
}
function mapLoop() {
  if (map.canvas && map.canvas.isConnected) {
    // skip painting while the schematic pane is hidden behind the real map
    if (map.canvas.offsetParent !== null) renderMap();
    if (!REDUCED) requestAnimationFrame(mapLoop);
  }
}
function initMap() {
  map.canvas = $("mapCanvas");
  if (!map.canvas) return;
  map.ctx = map.canvas.getContext("2d");
  map.canvas.addEventListener("mousedown", (e) => {
    map.dragging = true; map.moved = false;
    map.lastX = e.offsetX; map.lastY = e.offsetY;
  });
  window.addEventListener("mousemove", (e) => {
    if (!map.dragging) return;
    const rect = map.canvas.getBoundingClientRect();
    const mx = e.clientX - rect.left, my = e.clientY - rect.top;
    const dx = mx - map.lastX, dy = my - map.lastY;
    if (Math.abs(dx) + Math.abs(dy) > 3) map.moved = true;
    map.ox += dx; map.oy += dy;
    map.lastX = mx; map.lastY = my;
  });
  window.addEventListener("mouseup", (e) => {
    if (!map.dragging) return;
    map.dragging = false;
    if (!map.moved) {
      const rect = map.canvas.getBoundingClientRect();
      const mx = e.clientX - rect.left, my = e.clientY - rect.top;
      const pick = mapPick(mx, my);
      if (!pick) { state.focus = null; state.selected = null; }
      else if (state.focus && state.focus.kind === pick.kind && state.focus.key === pick.key) {
        state.focus = null;                      // click the same thing again to close
        state.selected = null;
      } else {
        state.focus = { kind: pick.kind, key: pick.key, name: pick.name, road: pick.road };
        state.selected = pick.kind === "location" || pick.kind === "shelter" ? pick.loc : null;
      }
      showDetail();
    }
  });
  map.canvas.addEventListener("mousemove", (e) => {
    const pick = mapPick(e.offsetX, e.offsetY);
    map.hover = pick && (pick.kind === "location" || pick.kind === "shelter") ? pick.loc : null;
    map.hoverRoad = pick && pick.kind === "road" ? pick.road : null;
    map.hoverIncident = !!(pick && pick.kind === "incident");
    map.canvas.style.cursor = pick ? "pointer" : "crosshair";
  });
  map.canvas.addEventListener("wheel", (e) => {
    // plain wheel = page scroll; hold Ctrl/Cmd to zoom the schematic
    if (!e.ctrlKey && !e.metaKey) return;
    e.preventDefault();
    const f = e.deltaY < 0 ? 1.15 : 1 / 1.15;
    map.ox = e.offsetX - (e.offsetX - map.ox) * f;
    map.oy = e.offsetY - (e.offsetY - map.oy) * f;
    map.scale *= f;
  }, { passive: false });
  window.addEventListener("resize", () => { map.fitted = false; });
  mapLoop();
}
/**
 * What is under the cursor: an incident badge, a location (or shelter) marker,
 * or a road segment. Everything is hit-tested in screen space against the same
 * data the renderer drew, so clicks always match what is visible.
 */
function mapPick(mx, my) {
  if (layerOn("incidents") && state.incident) {
    const a = incidentAnchor(state.incident);
    if (a && Math.hypot(a.x - mx, a.y - my) <= a.r + 6) {
      return { kind: "incident", key: "incident", name: state.incident.typeDisplay || state.incident.type };
    }
  }
  let bestLoc = null, bestD = 17;
  for (const l of state.locations) {
    const d = Math.hypot(sx(l.x) - mx, sy(l.y) - my);
    if (d < bestD) { bestLoc = l; bestD = d; }
  }
  if (bestLoc) {
    const shelter = layerOn("shelters") && shelterState(bestLoc.name);
    return { kind: shelter ? "shelter" : "location", key: bestLoc.id, loc: bestLoc, name: bestLoc.name };
  }
  // road segment: nearest line within a few pixels
  let road = null, roadD = 8;
  const seen = new Set();
  for (const r of state.roads) {
    const key = r.fromId < r.toId ? r.fromId + "|" + r.toId : r.toId + "|" + r.fromId;
    if (seen.has(key)) continue;
    seen.add(key);
    const a = state.byId.get(r.fromId), b = state.byId.get(r.toId);
    if (!a || !b) continue;
    const x1 = sx(a.x), y1 = sy(a.y), x2 = sx(b.x), y2 = sy(b.y);
    const dx = x2 - x1, dy = y2 - y1;
    const len2 = dx * dx + dy * dy;
    let t = len2 ? ((mx - x1) * dx + (my - y1) * dy) / len2 : 0;
    t = Math.max(0, Math.min(1, t));
    const d = Math.hypot(mx - (x1 + t * dx), my - (y1 + t * dy));
    if (d < roadD) { roadD = d; road = r; }
  }
  if (road) return { kind: "road", key: roadKey(road.fromId, road.toId), road, name: road.from + " \u2192 " + road.to };
  return null;
}

function showDetail() {
  const box = $("mapDetail");
  if (!box) return;
  const f = state.focus;
  if (!f) {
    box.hidden = true;
    if (typeof restoreActiveRoute === "function") restoreActiveRoute();
    return;
  }
  box.hidden = false;
  if (f.kind === "road") box.innerHTML = roadCardHtml(pickRoadByKey(f.key));
  else if (f.kind === "incident") box.innerHTML = incidentCardHtml(state.scenarioPreview || state.incident);
  else if (f.kind === "shelter") {
    box.innerHTML = shelterCardHtml(f.name);
    // selecting a shelter draws the real graph route to it
    if (typeof focusShelterRoute === "function") focusShelterRoute(f.name);
  } else box.innerHTML = locationCardHtml(f.name);
}

/** Fit the schematic to the active route (or the whole network when there is none). */
function mapFitRoute() {
  const names = (state.routePath && state.routePath.length) ? state.routePath : null;
  const pts = names ? names.map((n) => state.byName.get(n)).filter(Boolean) : state.locations;
  if (!pts.length) return;
  let minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
  for (const l of pts) {
    minX = Math.min(minX, l.x); minY = Math.min(minY, l.y);
    maxX = Math.max(maxX, l.x); maxY = Math.max(maxY, l.y);
  }
  const pad = 90;
  map.scale = Math.min((map.canvas.clientWidth - pad * 2) / Math.max(1, maxX - minX),
                       (map.canvas.clientHeight - pad * 2) / Math.max(1, maxY - minY));
  map.ox = map.canvas.clientWidth / 2 - (minX + maxX) / 2 * map.scale;
  map.oy = map.canvas.clientHeight / 2 - (minY + maxY) / 2 * map.scale;
  map.fitted = true;
}
function mapResetView() { map.fitted = false; mapFit(); }
function highlightPath(names, altNames, prevNames) {
  state.routePath = names || [];
  state.shortestPath = altNames || [];
  if (prevNames !== undefined) state.previousPath = prevNames || [];
  state.highlighted = new Set([...(names || []), ...(altNames || [])]);
  scenery.buildings.clear();
  scenery.patches.clear();
  if (typeof drawGeoRoute === "function") drawGeoRoute();   // keep the real map in sync
}
