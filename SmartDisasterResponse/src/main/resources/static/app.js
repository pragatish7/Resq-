/* ============================================================
   RESQ · app.js — data layer + functional wiring (part 2)
   All features run on the real /api endpoints; no fake data.
   ============================================================ */
"use strict";

const state = {
  locations: [],
  roads: [],
  shelters: [],
  byId: new Map(),
  byName: new Map(),
  geoById: new Map(),          // id -> location record (with lat/lng)
  highlighted: new Set(),
  routePath: [],
  shortestPath: [],
  routePathIds: null,
  selected: null,
  routeCosts: null,
  watch: null,            // last reroute report (phase 1)
  recFrom: null,          // last recommendation origin (phase 2)
  routeInfo: null,        // last planned route (map route card)
  activePath: [],         // the user's planned route (map highlight)
  previousPath: [],       // the pre-reroute route (ghost line)
  incident: null,         // active incident for the map (from the backend)
  scenarioPreview: null,  // temporary scenario overlay (never the live network)
  shelterRecs: null,      // shelter name -> recommendation record
  compare: null,          // last shortest/safest comparison
  layers: { incidents: true, shelters: true, routes: true },
  focus: null,            // map selection: { kind, key, name, road }
  // leaflet layer handles
  geoMap: null, geoMarkers: null, geoLines: null,
  geoRoute: null, geoGlow: null, geoDash: null,
  geoRoadIcons: null, geoIncidents: null,
  scenSpecs: null, scenRows: null,
};

/* ---------------- data refresh ---------------- */
function fillSelect(sel, items, valueKey, labelKey) {
  sel.innerHTML = "";
  for (const it of items) {
    const o = document.createElement("option");
    o.value = it[valueKey];
    o.textContent = it[labelKey];
    sel.appendChild(o);
  }
}

async function refreshData() {
  const [dash, locs, zones] = await Promise.all([
    apiGet("/dashboard"), apiGet("/locations"), apiGet("/safezones"),
  ]);
  state.locations = locs.locations;
  state.roads = dash.roadsAll;
  state.shelters = zones.safeZones;
  state.byId = new Map(state.locations.map((l) => [l.id, l]));
  state.byName = new Map(state.locations.map((l) => [l.name, l]));
  state.geoById = new Map(state.locations.map((l) => [l.id, l]));
  scenery.buildings.clear();   // rebuild scenery against fresh road data
  scenery.patches.clear();

  syncLeaflet();

  if (map.canvas && !map.fitted) mapFit();

  if (!$("routeFrom").options.length) {
    const locOpts = state.locations.map((l) => ({ v: l.name, t: l.name }));
    for (const id of ["routeFrom", "routeTo", "roadFrom", "roadTo", "mrFrom", "mrTo", "travStart"]) {
      fillSelect($(id), locOpts, "v", "t");
    }
    $("routeTo").selectedIndex = Math.min(5, locOpts.length - 1);
    $("mrTo").selectedIndex = Math.min(5, locOpts.length - 1);
  }
  fillSelect($("checkinZone"), state.shelters.map((z) => ({ v: z.name, t: z.name })), "v", "t");
  if (!$("recFrom").options.length) fillSelect($("recFrom"), state.locations.map((l) => ({ v: l.name, t: l.name })), "v", "t");
  if (!$("scenFrom").options.length) {
    const locOpts2 = state.locations.map((l) => ({ v: l.name, t: l.name }));
    fillSelect($("scenFrom"), locOpts2, "v", "t");
    fillSelect($("scenTo"), locOpts2, "v", "t");
    $("scenTo").selectedIndex = Math.min(9, locOpts2.length - 1);
  }

  renderShelters();
  renderRoads();
  if (!$("scenRows").children.length) renderScenRows();
  const uniqueRoads = new Set(state.roads.map((r) => r.fromId < r.toId ? r.fromId + "|" + r.toId : r.toId + "|" + r.fromId)).size;
  $("mapCount").textContent = state.locations.length + " LOCATIONS · " + uniqueRoads + " ROADS";
  $("footApiState").textContent = "API ONLINE";
}

/* ---------------- shelters ---------------- */
function renderShelters() {
  const z = state.shelters;
  const cap = z.reduce((a, s) => a + s.capacity, 0);
  const occ = z.reduce((a, s) => a + s.occupied, 0);
  const free = z.reduce((a, s) => a + s.available, 0);
  const avail = z.filter((s) => s.status === "AVAILABLE").length;
  const stats = [
    [String(avail), "shelters open"],
    [String(cap), "total capacity"],
    [String(occ), "evacuees sheltered"],
    [String(free), "free places"],
  ];
  const row = $("shelterStats");
  row.innerHTML = "";
  let ci = 0;
  for (const [num, label] of stats) {
    const cell = el("div", "stat-cell");
    cell.setAttribute("data-rev", "");
    cell.style.setProperty("--d", ((ci++) * 45 + 40) + "ms");
    const n = el("div", "stat-num");
    n.appendChild(el("span", "lead", num[0]));
    if (num.length > 1) n.appendChild(document.createTextNode(num.slice(1)));
    cell.appendChild(n);
    cell.appendChild(el("div", "stat-cap", label));
    row.appendChild(cell);
  }
  // observe the freshly created cells
  const io = new IntersectionObserver((ents) => {
    for (const en of ents) if (en.isIntersecting) { en.target.classList.add("in"); io.unobserve(en.target); }
  }, { threshold: 0.12 });
  row.querySelectorAll("[data-rev]").forEach((n) => io.observe(n));

  const tb = $("shelterTable").querySelector("tbody");
  tb.innerHTML = "";
  for (const s of z) {
    const tr = el("tr");
    tr.appendChild(el("td", null, s.name));
    tr.appendChild(el("td", null, s.location));
    tr.appendChild(el("td", null, String(s.capacity)));
    tr.appendChild(el("td", null, String(s.occupied)));
    tr.appendChild(el("td", null, String(s.available)));
    tr.appendChild(el("td", null, s.safetyLevel));
    const td = el("td");
    td.appendChild(badge(s.status, s.status === "AVAILABLE" ? "ok" : "bad"));
    tr.appendChild(td);
    tb.appendChild(tr);
  }
}

/* ---------------- phase 2 · shelter recommendation ---------------- */
/** Re-run the recommendation when it is on screen and the network changed. */
function autoRefreshRecommendations() {
  if ($("recList") && !$("recList").hidden) runRecommend();
}

function riskKind(risk) {
  return risk === "LOW" ? "ok" : risk === "CRITICAL" ? "bad" : risk === "UNKNOWN" ? "info" : "warn";
}

async function runRecommend(ev) {
  if (ev) ev.preventDefault();
  const box = $("recList");
  const msg = $("recMsg");
  const from = $("recFrom").value;
  const evacuees = Math.max(1, parseInt($("recEvacuees").value, 10) || 1);
  if (!from) { msg.textContent = "PICK AN EVACUATION POINT"; return; }
  msg.textContent = "RANKING SHELTERS\u2026";
  try {
    const resp = await apiPost("/shelters/recommend", { from, evacuees });
    state.recFrom = from;
    state.shelterRecs = {};
    for (const r of resp.recommendations) state.shelterRecs[r.name] = r;
    syncLeaflet();
    syncMapChips();
    box.hidden = false;
    msg.textContent = resp.reachableCount + "/" + resp.totalShelters + " REACHABLE \u00b7 "
      + resp.freePlacesReachable + " FREE PLACES \u00b7 "
      + (resp.recommendedName ? "BEST " + resp.recommendedName.toUpperCase() : "NOTHING FITS " + evacuees);
    renderRecList(resp, evacuees);
  } catch (err) {
    box.hidden = true;
    msg.textContent = err.message.toUpperCase();
  }
}

function renderRecList(resp, evacuees) {
  const box = $("recList");
  box.innerHTML = "";
  const head = el("div", "rec-row rec-head");
  for (const t of ["RANK", "SHELTER", "FREE / OCC", "ROUTE", "RISK \u00b7 STATUS", ""]) {
    head.appendChild(el("span", null, t));
  }
  box.appendChild(head);

  for (const r of resp.recommendations) {
    const row = el("div", "rec-row" + (r.recommended ? " is-top" : "") + (r.reachable ? "" : " is-unreachable"));
    if (r.reason) row.title = r.reason;

    const rank = el("div");
    rank.appendChild(badge(r.recommended ? "BEST" : "#" + r.rank, r.recommended ? "ok" : "info"));
    row.appendChild(rank);

    const name = el("div");
    name.appendChild(el("span", "rec-name", r.name));
    name.appendChild(el("span", "rec-sub", r.location + " \u00b7 " + r.safetyLevel + " safety"));
    row.appendChild(name);

    const cap = el("div");
    cap.appendChild(el("span", "rec-num", r.available + " / " + r.capacity));
    cap.appendChild(el("span", "rec-sub", r.occupied + " in \u00b7 " + r.occupancyPercent + "% full"));
    row.appendChild(cap);

    const route = el("div");
    if (r.reachable) {
      route.appendChild(el("span", "rec-num", r.distanceKm + " KM"));
      route.appendChild(el("span", "rec-sub", r.estMinutes + " min \u00b7 cost " + r.cost + " \u00b7 " + r.path.length + " nodes"));
    } else {
      route.appendChild(el("span", "rec-num", "\u2014"));
      route.appendChild(el("span", "rec-sub", "no traversable route"));
    }
    row.appendChild(route);

    const risk = el("div");
    if (r.reachable) {
      risk.appendChild(badge(r.riskLevel, riskKind(r.riskLevel)));
      risk.appendChild(el("span", "rec-sub", r.status === "FULL" ? "shelter full" : (r.recommended ? "recommended" : "reachable")));
    } else {
      risk.appendChild(badge("UNREACHABLE", "bad"));
      risk.appendChild(el("span", "rec-sub", "blocked roads"));
    }
    row.appendChild(risk);

    const act = el("div", "rec-act");
    if (r.reachable && r.fitsEvacuees) {
      const btn = el("button", "btn btn-solid", "Assign " + evacuees);
      btn.type = "button";
      btn.addEventListener("click", () => assignEvacuees(r.name, evacuees));
      act.appendChild(btn);
    } else if (r.reachable) {
      act.appendChild(el("span", "rec-sub", "only " + r.available + " free"));
    } else {
      act.appendChild(el("span", "rec-sub", "unreachable"));
    }
    row.appendChild(act);
    box.appendChild(row);
  }
}

/** Assign evacuees to a shelter — the API refuses anything past capacity. */
async function assignEvacuees(zoneName, evacuees) {
  const msg = $("recMsg");
  try {
    const resp = await apiPost("/safezones/checkin", { zone: zoneName, count: evacuees });
    msg.textContent = "ASSIGNED " + evacuees + " TO " + zoneName.toUpperCase() + " \u00b7 " + resp.occupied + "/" + resp.capacity;
  } catch (err) {
    msg.textContent = err.message.toUpperCase();
  }
  await refreshData();
  await refreshMapState();        // shelter markers re-colour from live occupancy
  await runRecommend();          // re-rank: occupancy moved, full shelters roll over
  buildCurve();
}

/* ---------------- road ledger ---------------- */
function renderRoads() {
  const led = $("roadLedger");
  led.innerHTML = "";
  const head = el("div", "ledger-row ledger-head");
  for (const t of ["FROM", "TO", "KM", "TERRAIN", "STATUS", "TRAFFIC"]) {
    const s = el("span", "lg-" + t.toLowerCase(), t);
    head.appendChild(s);
  }
  led.appendChild(head);
  let i = 0;
  for (const r of state.roads) {
    const tr = el("div", "ledger-row");
    tr.setAttribute("data-rev", "");
    tr.style.setProperty("--d", ((i++ % 10) * 40 + 40) + "ms");
    tr.appendChild(el("span", "lg-from", r.from));
    tr.appendChild(el("span", "lg-to", r.to));
    tr.appendChild(el("span", "lg-km", r.distanceKm.toFixed(1)));
    tr.appendChild(el("span", "lg-terrain", r.terrain));
    const st = el("span", "lg-status");
    st.appendChild(badge(r.status.replace("_", " "), statusKind(r.status)));
    tr.appendChild(st);
    tr.appendChild(el("span", "lg-traffic", "L" + r.trafficLevel));
    led.appendChild(tr);
  }
  const io = new IntersectionObserver((ents) => {
    for (const en of ents) if (en.isIntersecting) { en.target.classList.add("in"); io.unobserve(en.target); }
  }, { threshold: 0.05 });
  led.querySelectorAll("[data-rev]").forEach((n) => io.observe(n));
}

/* ---------------- routes ---------------- */
function renderRouteResult(resp, box) {
  box.hidden = false;
  box.innerHTML = "";
  box.appendChild(el("h4", null, resp.found ? "Route found" : "No route"));
  if (!resp.found) {
    box.appendChild(el("p", "lede", resp.message || "No safe route available."));
    return;
  }
  const path = el("div", "route-path");
  resp.path.forEach((name, i) => {
    if (i) path.appendChild(el("span", "arrow", "\u2192"));
    path.appendChild(el("span", "node" + (i === 0 || i === resp.path.length - 1 ? " node-end" : ""), name));
  });
  box.appendChild(path);
  const kv = el("div", "kv");
  const add = (k, v) => { const c = el("div"); c.appendChild(el("div", "k", k)); c.appendChild(el("div", "v", v)); kv.appendChild(c); };
  add("distance", resp.totalDistanceKm + " km");
  add("cost", resp.totalCost);
  add("risk", resp.riskLevel);
  if (resp.safeZone) add("shelter", resp.safeZone);
  if (resp.safeZoneAvailable != null) add("free places", resp.safeZoneAvailable);
  box.appendChild(kv);
  if (resp.alertLevel) box.appendChild(badge("ALERT " + resp.alertLevel,
    resp.alertLevel === "GREEN" ? "ok" : resp.alertLevel === "RED" ? "bad" : "warn"));
  (resp.warnings || []).forEach((w) => box.appendChild(el("p", "lede", w)));
}

async function findRoute(ev) {
  ev.preventDefault();
  const box = $("routeResult");
  try {
    const resp = await apiPost("/route/safest", { from: $("routeFrom").value, to: $("routeTo").value });
    renderRouteResult(resp, box);
    state.routeInfo = {
      from: $("routeFrom").value, to: $("routeTo").value, status: "UNAFFECTED",
      path: resp.path || [], distanceKm: resp.totalDistanceKm, cost: resp.totalCost,
      riskLevel: resp.riskLevel, warnings: resp.warnings || [],
    };
    state.previousPath = [];
    setActivePath(resp.path || []);
    watchPlannedRoute($("routeFrom").value, $("routeTo").value, resp);
    syncLeaflet();
  } catch (err) { box.hidden = false; box.innerHTML = ""; box.appendChild(el("p", "lede", err.message)); }
}

async function findNearest(ev) {
  ev.preventDefault();
  const box = $("routeResult");
  try {
    const resp = await apiPost("/route/nearest-safe-zone", { from: $("routeFrom").value });
    renderRouteResult(resp, box);
    state.routeInfo = {
      from: $("routeFrom").value, to: resp.safeZone || $("routeTo").value, status: "UNAFFECTED",
      path: resp.path || [], distanceKm: resp.totalDistanceKm, cost: resp.totalCost,
      riskLevel: resp.riskLevel, warnings: resp.warnings || [],
    };
    state.previousPath = [];
    setActivePath(resp.path || []);
    watchPlannedRoute($("routeFrom").value, resp.safeZone || $("routeTo").value, resp);
    syncLeaflet();
  } catch (err) { box.hidden = false; box.innerHTML = ""; box.appendChild(el("p", "lede", err.message)); }
}

/* ---------------- curve stage data: real Dijkstra costs ---------------- */
async function buildCurve() {
  const stops = ["Junction A", "School", "Hospital", "Market", "Junction B", "Stadium", "Railway Station", "Relief Camp A", "Relief Camp B"];
  const values = [0];
  const labels = ["R.A.", "J.A", "SCH", "HOS", "MKT", "J.B", "STA", "RAIL", "CAMP"];
  const rows = [];
  let last = { cost: 0, dest: "Residential Area", risk: "LOW" };
  for (const stop of stops) {
    try {
      const r = await apiPost("/route/safest", { from: "Residential Area", to: stop, watch: false });
      if (r.found) {
        values.push(r.totalCost);
        labels[stops.indexOf(stop)] = stop.split(" ")[0].slice(0, 4).toUpperCase();
        last = { cost: r.totalCost, dest: stop, risk: r.riskLevel };
        rows.push({ v: r.totalCost.toFixed(1), l: stop, n: "SAFEST ROUTE · RISK " + r.riskLevel });
      }
    } catch (e) { /* skip unreachable stop under current network */ }
  }
  curve.values = values;
  curve.labels = labels;
  if (window.curveDirty) window.curveDirty(curveState.progress < 0 ? 0 : curveState.progress);
  // fill the five process rows with the farthest real routes
  const shown = rows.slice(-5);
  for (let i = 0; i < 5; i++) {
    const row = shown[i] || { v: "—", l: "—", n: "UNREACHABLE · CURRENT NETWORK" };
    document.querySelector('[data-slot="v' + i + '"]').textContent = row.v;
    document.querySelector('[data-slot="l' + i + '"]').textContent = row.l;
    document.querySelector('[data-slot="n' + i + '"]').textContent = row.n;
  }
  $("curveFrom").textContent = "FROM RESIDENTIAL AREA";
  $("curveTo").textContent = "TO " + (last.dest || "—").toUpperCase() + " · " + (rows.length) + " REAL ROUTES";
}

/* ---------------- phase 1 · route watch (dynamic rerouting) ---------------- */
function pathLine(label, names, marks) {
  const row = el("div", "watch-line");
  row.appendChild(el("span", "mono watch-label", label));
  const p = el("span", "watch-path");
  (names || []).forEach((n, i) => {
    if (i) p.appendChild(el("span", "watch-caret", "\u2192"));
    p.appendChild(el("span", "node" + (marks && marks.has(i) ? " bad" : ""), n));
  });
  row.appendChild(p);
  return row;
}

function renderWatch(rr) {
  state.watch = rr || null;
  const badgeBox = $("watchBadge");
  badgeBox.innerHTML = "";
  const kind = !rr || rr.status === "NONE" || rr.status === "WATCHING" ? "info"
    : rr.status === "UNAFFECTED" ? "ok"
    : rr.status === "NO_SAFE_ROUTE" ? "bad" : "warn";
  badgeBox.appendChild(badge(rr ? rr.statusLabel.toUpperCase() : "NO ROUTE PLANNED", kind));
  $("watchMsg").textContent = rr ? rr.message : "";
  const body = $("watchBody");
  body.innerHTML = "";
  if (!rr || !rr.hadRoute) {
    body.appendChild(el("p", "watch-hint", "Plan a route above \u2014 this panel re-checks it on every road or disaster change."));
    return;
  }
  // segments of the previously planned route that degraded or blocked
  const marks = new Set();
  const badAt = new Map();
  (rr.affectedSegments || []).forEach((s) => badAt.set(s.from + "|" + s.to, s.status));
  for (let i = 0; i < rr.previousPath.length - 1; i++) {
    if (badAt.has(rr.previousPath[i] + "|" + rr.previousPath[i + 1])) marks.add(i);
  }
  body.appendChild(pathLine("PLANNED", rr.previousPath, marks));
  body.appendChild(pathLine("NOW", rr.found ? rr.newPath : ["NO SAFE ROUTE"], new Set()));

  const bits = [];
  if (rr.found && rr.previousDistanceKm) {
    const delta = Math.round((rr.newDistanceKm - rr.previousDistanceKm) * 10) / 10;
    bits.push("DISTANCE " + rr.previousDistanceKm + " \u2192 " + rr.newDistanceKm + " KM (" + (delta >= 0 ? "+" : "") + delta + ")");
  }
  if (rr.found && rr.newCost != null) bits.push("COST " + rr.newCost);
  if (rr.previousRisk && rr.newRisk) bits.push("RISK " + rr.previousRisk + " \u2192 " + rr.newRisk);
  if (rr.blockedSegments) bits.push(rr.blockedSegments + " BLOCKED SEGMENT" + (rr.blockedSegments === 1 ? "" : "S"));
  if (rr.degradedSegments) bits.push(rr.degradedSegments + " DEGRADED SEGMENT" + (rr.degradedSegments === 1 ? "" : "S"));
  const facts = el("div", "watch-line");
  facts.appendChild(el("span", "mono watch-label", "CHANGE"));
  facts.appendChild(el("span", "watch-seg", bits.join(" \u00b7 ") || "\u2014"));
  body.appendChild(facts);

  if ((rr.affectedSegments || []).length) {
    const seg = el("div", "watch-line");
    seg.appendChild(el("span", "mono watch-label", "SEGMENTS"));
    seg.appendChild(el("span", "watch-seg", rr.affectedSegments
      .map((s) => s.from + " \u2192 " + s.to + " " + s.status.replace("_", " ")).join(" \u00b7 ")));
    body.appendChild(seg);
  }
  if (rr.found) {
    const link = el("div", "watch-line");
    link.appendChild(el("span", "mono watch-label", "ACTION"));
    link.appendChild(el("span", "watch-seg", "Result above and the map now show the recalculated route."));
    body.appendChild(link);
  }
}

/** Push the recalculated route into the ordinary route result + map highlight. */
function renderWatchedRoute(rr) {
  if (!rr || !rr.hadRoute) return;
  const box = $("routeResult");
  renderRouteResult({
    found: rr.found,
    message: rr.message,
    path: rr.newPath,
    totalDistanceKm: rr.newDistanceKm,
    totalCost: rr.newCost,
    riskLevel: rr.newRisk,
    warnings: rr.warnings,
    alertLevel: rr.alertLevel,
  }, box);
  box.insertBefore(el("p", "watch-flag",
    "ROUTE WATCH \u00b7 " + rr.statusLabel.toUpperCase() + " \u00b7 " + rr.from.toUpperCase() + " \u2192 " + rr.to.toUpperCase()),
    box.firstChild);
  state.routeInfo = {
    from: rr.from, to: rr.to, status: rr.status,
    path: rr.found ? (rr.newPath || []) : [],
    distanceKm: rr.newDistanceKm, cost: rr.newCost, riskLevel: rr.newRisk, warnings: rr.warnings || [],
  };
  // keep the discarded plan on the map as a ghost line when the route moved
  state.previousPath = rr.previousPath && rr.previousPath.length ? rr.previousPath : [];
  setActivePath(rr.found ? (rr.newPath || []) : [], state.previousPath);
  syncMapChips();
  syncLeaflet();
}

/** Show the freshly planned route in the watch panel before any change happens. */
function watchPlannedRoute(from, to, resp) {
  renderWatch({
    hadRoute: true,
    status: "WATCHING",
    statusLabel: resp.found ? "Route planned \u2014 watching" : "No route to watch",
    message: "Any road or disaster change is re-checked against this plan automatically.",
    from,      to,
    found: resp.found,
    previousPath: resp.path || [],
    newPath: resp.path || [],
    previousDistanceKm: resp.totalDistanceKm,
    newDistanceKm: resp.totalDistanceKm,
    newCost: resp.totalCost,
    previousRisk: resp.riskLevel,
    newRisk: resp.riskLevel,
    affectedSegments: [],
  });
  syncMapChips();
}

/** Apply a reroute report from any endpoint response. */
function applyReroute(rr) {
  if (!rr) return;
  renderWatch(rr);
  if (rr.hadRoute) renderWatchedRoute(rr);
  else highlightPath([], []);
}

/* ============================================================
   LIVE MAP SYNC — one source of truth for both maps.
   Every state-changing action ends in refreshMapState(), which re-reads
   the incident layer from the backend and repaints the maps. No reloads.
   ============================================================ */
function deriveIncident(payload) {
  if (!payload || !payload.type) return null;
  const roads = payload.affectedRoads || [];
  const weight = new Map();
  for (const r of roads) {
    const w = 1 + (r.penalty || 0);
    weight.set(r.from, (weight.get(r.from) || 0) + w);
    weight.set(r.to, (weight.get(r.to) || 0) + w);
  }
  let best = null, bestW = -1;
  for (const [name, w] of weight) if (w > bestW) { best = name; bestW = w; }
  const loc = best ? state.byName.get(best) : null;
  return {
    type: payload.type,
    typeDisplay: payload.typeDisplay || payload.type.replace("_", " "),
    severity: payload.severity || "MEDIUM",
    description: payload.description || "",
    recommendedAction: payload.recommendedAction || "",
    affectedRoads: roads,
    epicenter: best,
    epicenterId: loc ? loc.id : null,
  };
}

/** Re-read the incident layer + recommendation reachability, then repaint. */
async function refreshMapState() {
  try {
    const inc = await apiGet("/network/incidents");
    state.incident = inc.disaster
      ? deriveIncident(Object.assign({ affectedRoads: inc.affectedRoads }, inc.disaster))
      : null;
  } catch (e) { /* keep the last known incident if the call fails */ }
  syncMapChips();
  syncLeaflet();
}

function setIncidentFromResponse(resp) {
  state.incident = resp && resp.type ? deriveIncident(resp) : state.incident;
  syncMapChips();
  syncLeaflet();
}

function setScenarioPreview(preview) {
  state.scenarioPreview = preview || null;
  syncMapChips();
  syncLeaflet();
}

/** Shelter route from the evacuation point: the real calculated graph path. */
function focusShelterRoute(name) {
  const rec = state.shelterRecs && state.shelterRecs[name];
  if (!rec || !rec.path || rec.path.length < 2) return;
  highlightPath(rec.path, [], state.previousPath);
}
function setActivePath(names, prevNames) {
  state.activePath = names || [];
  if (prevNames !== undefined) state.previousPath = prevNames || [];
  highlightPath(state.activePath, state.shortestPath || [], state.previousPath);
}
function restoreActiveRoute() {
  highlightPath(state.activePath || [], state.shortestPath || [], state.previousPath);
}

/** Map status chip (route watch) + scenario preview banner. */
function syncMapChips() {
  const chip = $("mapStatus");
  if (chip) {
    const rr = state.watch;
    const key = rr && ROUTE_STATUS[rr.status] ? rr.status : "WATCHING";
    const st = ROUTE_STATUS[key];
    chip.className = "map-status " + (key === "NO_SAFE_ROUTE" ? "ms-bad"
      : (key === "ROUTE_AFFECTED" || key === "ALTERNATIVE_ROUTE_FOUND") ? "ms-warn" : "ms-ok");
    chip.innerHTML = iconSpan(st.icon, st.color, 14) +
      '<span class="ms-label">' + esc(st.label) + "</span>" +
      '<span class="ms-sub">' + (rr && rr.from ? esc(rr.from) + " \u2192 " + esc(rr.to) : "no route planned") + "</span>";
  }
  const prev = $("mapPreview");
  if (prev) {
    const p = state.scenarioPreview;
    prev.hidden = !p;
    if (p) {
      prev.innerHTML = iconSpan(DISASTER_ICON[p.type] || "warn", SEVERITY_COLOR[p.severity] || C.warn, 14) +
        '<span class="ms-label">PREVIEW \u00b7 ' + esc(p.label) + "</span>" +
        '<span class="ms-sub">' + (p.routeKm != null ? "route " + p.routeKm + " km \u00b7 " : "") + "live network unchanged</span>" +
        '<button type="button" class="btn btn-ghost" id="previewExit">Exit preview</button>';
      const ex = $("previewExit");
      if (ex) ex.addEventListener("click", () => setScenarioPreview(null));
    } else {
      prev.innerHTML = "";
    }
  }
  for (const [id, key] of [["tglIncidents", "incidents"], ["tglShelters", "shelters"], ["tglRoutes", "routes"]]) {
    const b = $(id);
    if (b) b.classList.toggle("active", state.layers[key] !== false);
  }
}

/** Compact grouped legend, built from the one icon definition set. */
function renderLegend() {
  const lg = $("mapLegend");
  if (!lg) return;
  const ico = (key, color) => '<i class="ico">' + svgIcon(key, color, 13) + "</i>";
  const line = (cls) => '<i class="lg-line ' + cls + '"></i>';
  const group = (title, items) => '<div class="lg-group"><span class="lg-title">' + title + "</span>" + items + "</div>";
  const item = (icon, label) => '<span class="lg-item">' + icon + esc(label) + "</span>";
  lg.innerHTML =
    group("ROADS",
      item(line("lg-open"), "Normal") +
      item(ico("warn", C.warn), "High risk") +
      item(ico("roadblock", C.block), "Blocked") +
      item(ico("crack", C.damaged), "Damaged")) +
    group("ROUTES",
      item(line("lg-safest"), "Safest / active") +
      item(line("lg-shortest"), "Shortest") +
      item(line("lg-prev"), "Previous plan") +
      item(line("lg-preview"), "Scenario preview")) +
    group("INCIDENTS",
      item(ico("flood", C.bone), "Flood / water") +
      item(ico("fire", C.bone), "Fire") +
      item(ico("quake", C.bone), "Quake / collapse") +
      item(ico("hazard", C.bone), "Hazard / leak") +
      item('<i class="lg-ring" style="--ic:' + C.warn + '"></i>', "High severity") +
      item('<i class="lg-ring" style="--ic:' + C.block + '"></i>', "Critical")) +
    group("SHELTERS",
      item(ico("shelter", C.safe), "Available") +
      item(ico("shelter", C.warn), "Limited") +
      item(ico("shelter", C.block), "Full") +
      item(ico("shelter", C.off), "Unreachable") +
      item(ico("star", C.safe), "Recommended"));
}

/* ---------------- disaster ---------------- */
async function applyDisaster(ev) {
  ev.preventDefault();
  const box = $("disasterResult");
  try {
    const resp = await apiPost("/disaster/apply", {
      type: $("disasterType").value,
      severity: $("disasterSeverity").value,
    });
    box.hidden = false;
    box.innerHTML = "";
    box.appendChild(el("h4", null, resp.typeDisplay + " · " + resp.severity));
    box.appendChild(el("p", "lede", resp.description));
    const kv = el("div", "kv");
    const add = (k, v) => { const c = el("div"); c.appendChild(el("div", "k", k)); c.appendChild(el("div", "v", v)); kv.appendChild(c); };
    add("roads hit", resp.roadsAffected);
    add("blocked", resp.blocked);
    add("high risk", resp.highRisk);
    add("damaged", resp.damaged);
    box.appendChild(kv);
    box.appendChild(el("p", "lede", resp.recommendedAction));
    applyReroute(resp.reroute);
    setIncidentFromResponse(resp);
    clearCompare();
    await refreshData();
    buildCurve();
    autoRefreshRecommendations();
    syncLeaflet();
  } catch (err) { box.hidden = false; box.innerHTML = ""; box.appendChild(el("p", "lede", err.message)); }
}

/** Restore the original network: roads, active disaster, watched route and all panels. */
async function resetNetwork() {
  await apiPost("/network/reset", {});
  $("disasterResult").hidden = true;
  $("routeResult").hidden = true;
  clearCompare();
  state.incident = null;
  state.scenarioPreview = null;
  state.shelterRecs = null;
  state.recFrom = null;
  state.compare = null;
  state.routeInfo = null;
  state.activePath = [];
  state.previousPath = [];
  highlightPath([], [], []);
  renderWatch(null);
  const recBox = $("recList");
  if (recBox) recBox.hidden = true;
  await refreshData();
  buildCurve();
  await refreshMapState();
  const scen = $("scenSummary");
  if (scen && !scen.hidden) runScenarios();
}

/* ---------------- check-in ---------------- */
async function checkIn(ev) {
  ev.preventDefault();
  const msg = $("checkinMsg");
  try {
    const resp = await apiPost("/safezones/checkin", {
      zone: $("checkinZone").value,
      count: parseInt($("checkinCount").value, 10),
    });
    msg.textContent = resp.success
      ? "CHECKED IN · " + resp.zone + " · " + resp.occupied + "/" + resp.capacity
      : resp.error;
    await refreshData();
    autoRefreshRecommendations();
    await refreshMapState();
  } catch (err) { msg.textContent = err.message; }
}

/* ---------------- road status update ---------------- */
async function updateRoad(ev) {
  ev.preventDefault();
  const msg = $("roadMsg");
  try {
    const resp = await apiPost("/roads/status", {
      from: $("roadFrom").value, to: $("roadTo").value, status: $("roadStatus").value,
    });
    msg.textContent = resp.from.toUpperCase() + " \u2192 " + resp.to.toUpperCase() + " · " + resp.status;
    applyReroute(resp.reroute);
    clearCompare();
    await refreshData();
    buildCurve();
    autoRefreshRecommendations();
    await refreshMapState();
  } catch (err) { msg.textContent = err.message; }
}

/* ---------------- analytics ---------------- */
async function runTraversal(kind) {
  const out = $("travResult");
  try {
    const resp = await apiPost("/traversal/" + kind, { start: $("travStart").value });
    out.textContent = kind.toUpperCase() + " (" + resp.visitedCount + "): " + resp.order.join(" \u2192 ");
  } catch (err) { out.textContent = err.message; }
}
async function runMinRoads() {
  const out = $("mrResult");
  try {
    const resp = await apiPost("/traversal/min-roads", { from: $("mrFrom").value, to: $("mrTo").value });
    out.textContent = resp.reachable
      ? resp.from + " \u2192 " + resp.to + ": " + resp.minRoads + " ROADS MIN"
      : "UNREACHABLE · CURRENT ROAD STATUSES";
  } catch (err) { out.textContent = err.message; }
}
async function runConnectivity() {
  const out = $("mrResult");
  try {
    const resp = await apiPost("/traversal/connectivity", { from: $("mrFrom").value, to: $("mrTo").value });
    out.textContent = resp.from + " \u2194 " + resp.to + ": " + (resp.connected ? "CONNECTED" : "DISCONNECTED");
  } catch (err) { out.textContent = err.message; }
}
async function runComponents() {
  const out = $("compResult");
  try {
    const resp = await apiGet("/network/components");
    out.textContent = resp.count + " COMPONENT(S): " + resp.components.map((c) => c.length + " LOC").join(", ");
  } catch (err) { out.textContent = err.message; }
}
async function runMST() {
  const out = $("mstResult");
  try {
    const resp = await apiGet("/network/mst");
    out.textContent = resp.edges.map((e) => e.from + "–" + e.to + " " + e.distanceKm + "KM").join("\n")
      + "\nTOTAL " + resp.totalCostKm + " KM · " + resp.edges.length + " EDGES · COMPLETE " + resp.complete;
  } catch (err) { out.textContent = err.message; }
}

/* ---------------- phase 3 · shortest vs safest comparison ---------------- */
function alertKind(level) {
  return level === "GREEN" ? "ok" : level === "RED" ? "bad" : "warn";
}

function compareCard(opt, resp) {
  const card = el("div", "cmp-card" + (!resp.identical && opt.kind === "SAFEST" ? " win" : ""));
  const head = el("h3");
  head.appendChild(document.createTextNode(opt.kind === "SAFEST" ? "Safest route" : "Shortest route"));
  head.appendChild(badge(opt.kind === "SAFEST" ? "EMBER ON MAP" : "DASHED ON MAP", "info"));
  card.appendChild(head);
  card.appendChild(el("span", "cmp-kind", opt.kind === "SAFEST"
    ? "MINIMUM COST \u00b7 DISTANCE + PENALTIES"
    : "MINIMUM DISTANCE IN KM"));

  if (!opt.found) {
    card.appendChild(el("p", "lede", opt.message || "No route found on the current network."));
    return card;
  }

  const path = el("div", "route-path");
  opt.path.forEach((name, i) => {
    if (i) path.appendChild(el("span", "arrow", "\u2192"));
    path.appendChild(el("span", "node" + (i === 0 || i === opt.path.length - 1 ? " node-end" : ""), name));
  });
  card.appendChild(path);

  const kv = el("div", "kv");
  const add = (k, v) => { const c = el("div"); c.appendChild(el("div", "k", k)); c.appendChild(el("div", "v", v)); kv.appendChild(c); };
  add("distance", opt.distanceKm + " km");
  add("est. time", opt.estMinutes + " min");
  add("cost", opt.cost);
  add("roads", opt.roadCount);
  add("risk", opt.riskLevel);
  kv.appendChild((() => { const c = el("div"); c.appendChild(el("div", "k", "alert")); const v = el("div", "v"); v.appendChild(badge(opt.alertLevel, alertKind(opt.alertLevel))); c.appendChild(v); return c; })());
  card.appendChild(kv);

  const flagged = [];
  if (opt.highRiskCount) flagged.push(opt.highRiskCount + " high-risk road" + (opt.highRiskCount === 1 ? "" : "s"));
  if (opt.damagedCount) flagged.push(opt.damagedCount + " damaged road" + (opt.damagedCount === 1 ? "" : "s"));
  card.appendChild(el("p", "lede", flagged.length ? "Crosses " + flagged.join(" and ") + "." : "Crosses no high-risk or damaged roads."));
  (opt.warnings || []).forEach((w) => card.appendChild(el("p", "lede", w)));

  const legs = el("div", "cmp-legs");
  const lgHead = el("div", "cmp-leg");
  for (const t of ["ROAD", "KM", "MIN", "COST"]) lgHead.appendChild(el("span", "mono", t));
  legs.appendChild(lgHead);
  for (const leg of opt.costs || []) {
    const row = el("div", "cmp-leg");
    row.appendChild(el("span", null, leg.from + " \u2192 " + leg.to));
    row.appendChild(el("span", "cl-km", Number(leg.distanceKm).toFixed(1)));
    row.appendChild(el("span", "cl-km", Number(leg.legMinutes).toFixed(1)));
    row.appendChild(el("span", "cl-cost", Number(leg.legCost).toFixed(1)));
    row.title = leg.status + " \u00b7 " + leg.terrain + " \u00b7 risk " + leg.riskLevel
      + " +" + leg.riskPenalty + " \u00b7 disaster +" + leg.disasterPenalty
      + " \u00b7 traffic +" + leg.trafficPenalty + " \u00b7 status +" + leg.statusPenalty;
    legs.appendChild(row);
  }
  card.appendChild(legs);
  return card;
}

/** A comparison is only valid for the network state it was run on. */
function clearCompare() {
  const wrap = $("compareWrap");
  if (wrap) wrap.hidden = true;
}

async function runCompare(ev) {
  if (ev) ev.preventDefault();
  const wrap = $("compareWrap");
  const grid = $("cmpGrid");
  try {
    const resp = await apiPost("/route/compare", { from: $("routeFrom").value, to: $("routeTo").value });
    wrap.hidden = false;
    grid.innerHTML = "";
    grid.appendChild(compareCard(resp.shortest, resp));
    grid.appendChild(compareCard(resp.safest, resp));
    const why = $("cmpWhy");
    why.innerHTML = "";
    for (const line of resp.explanation) why.appendChild(el("li", null, line));
    $("cmpFormula").textContent = resp.formula;
    const terms = $("cmpTerms");
    terms.innerHTML = "";
    for (const t of resp.formulaTerms || []) {
      const row = el("div", "cmp-term");
      row.appendChild(el("b", null, t.term));
      row.appendChild(el("span", null, t.detail + (t.note ? " \u00b7 " + t.note : "")));
      terms.appendChild(row);
    }
    // both routes on the map: safest in ember, shortest in bone dashes.
    // Identical routes are drawn ONCE (never as two overlapping lines).
    state.compare = resp;
    if (resp.safest.found) {
      const alt = resp.identical || !resp.shortest.found ? [] : resp.shortest.path;
      state.activePath = resp.safest.path;
      state.shortestPath = alt;
      highlightPath(resp.safest.path, alt, state.previousPath);
    } else if (resp.shortest.found) {
      state.shortestPath = [];
      highlightPath(resp.shortest.path, [], state.previousPath);
    }
    syncLeaflet();
  } catch (err) {
    wrap.hidden = false;
    grid.innerHTML = "";
    grid.appendChild(el("p", "lede", err.message));
  }
}

/* ---------------- phase 4 · disaster scenario comparison ---------------- */
const SCENARIO_PRESETS = [
  { label: "Flood wave", type: "FLOOD", severity: "HIGH", blocks: [] },
  { label: "Fire at the market", type: "INDUSTRIAL_FIRE", severity: "HIGH",
    blocks: [{ from: "Market", to: "Railway Station" }] },
  { label: "Multiple roads out", type: "NONE", severity: "MEDIUM",
    blocks: [{ from: "Junction A", to: "Junction B" }, { from: "Hospital", to: "Junction B" },
             { from: "Railway Station", to: "Relief Camp A" }] },
];

/** One selector row per preset; type, severity and one extra blocked road are user-editable. */
function renderScenRows() {
  const box = $("scenRows");
  box.innerHTML = "";
  const seen = new Set();
  const roadOpts = [];
  for (const r of state.roads) {
    const key = r.fromId < r.toId ? r.fromId + "|" + r.toId : r.toId + "|" + r.fromId;
    if (seen.has(key)) continue;
    seen.add(key);
    roadOpts.push({ v: r.from + "|" + r.to, t: r.from + " \u2192 " + r.to });
  }
  SCENARIO_PRESETS.forEach((preset, i) => {
    const row = el("div", "scen-row");
    row.appendChild(el("span", "mono", "SCENARIO " + (i + 1) + " \u00b7 " + preset.label.toUpperCase()));

    const typeSel = el("select", "scen-type");
    typeSel.appendChild(new Option("No disaster (manual blocks)", "NONE"));
    for (const t of DISASTER_TYPES) typeSel.appendChild(new Option(t.replace("_", " "), t));
    typeSel.value = preset.type;
    row.appendChild(typeSel);

    const sevSel = el("select", "scen-sev");
    for (const s of ["LOW", "MEDIUM", "HIGH", "CRITICAL"]) sevSel.appendChild(new Option(s, s));
    sevSel.value = preset.severity;
    sevSel.disabled = preset.type === "NONE";
    typeSel.addEventListener("change", () => { sevSel.disabled = typeSel.value === "NONE"; });
    row.appendChild(sevSel);

    const blockSel = el("select", "scen-block");
    blockSel.appendChild(new Option("No extra blocked road", ""));
    for (const o of roadOpts) blockSel.appendChild(new Option(o.t, o.v));
    row.appendChild(blockSel);

    box.appendChild(row);
  });
}

function readScenSpecs() {
  return [...$("scenRows").querySelectorAll(".scen-row")].map((row, i) => {
    const preset = SCENARIO_PRESETS[i];
    const type = row.querySelector(".scen-type").value;
    const severity = row.querySelector(".scen-sev").value;
    const extra = row.querySelector(".scen-block").value;
    const blocks = (preset.blocks || []).map((b) => ({ from: b.from, to: b.to }));
    if (extra) {
      const parts = extra.split("|");
      blocks.push({ from: parts[0], to: parts[1] });
    }
    return { label: preset.label, type: type === "NONE" ? null : type, severity, blocks };
  });
}

function scenarioTableRow(metrics, spec, baseline) {
  const tr = el("tr");
  const c1 = el("td");
  c1.appendChild(document.createTextNode(metrics.label));
  if (spec) {
    const kind = spec.type ? spec.type.replace("_", " ") + " \u00b7 " + spec.severity : "manual blocks only";
    c1.appendChild(el("span", "cell-sub", kind));
    if (spec.blocks.length) c1.appendChild(el("span", "cell-sub", "closes " + spec.blocks.map((b) => b.from + " \u2192 " + b.to).join(", ")));
  }
  tr.appendChild(c1);

  tr.appendChild(el("td", "num", String(metrics.blocked)));
  tr.appendChild(el("td", "num", String(metrics.highRisk)));
  tr.appendChild(el("td", "num", String(metrics.damaged)));

  const cut = el("td", "num", String(metrics.isolatedCount));
  if (metrics.isolatedCount) cut.appendChild(el("span", "cell-sub", metrics.isolatedLocations.slice(0, 3).join(", ") + (metrics.isolatedCount > 3 ? " \u2026" : "")));
  tr.appendChild(cut);

  const route = metrics.route || {};
  const rCell = el("td", "num", route.found ? route.distanceKm + " km" : "no route");
  if (route.found && baseline && baseline.route && baseline.route.found) {
    const delta = Math.round((route.distanceKm - baseline.route.distanceKm) * 10) / 10;
    if (delta !== 0) rCell.appendChild(el("span", "cell-sub", (delta > 0 ? "+" : "") + delta + " km vs now"));
  }
  tr.appendChild(rCell);

  tr.appendChild(el("td", "num", route.found ? route.estMinutes + " min" : "\u2014"));

  const risk = el("td");
  risk.appendChild(badge(route.found ? route.riskLevel : "BLOCKED", route.found ? riskKind(route.riskLevel) : "bad"));
  tr.appendChild(risk);

  const shelter = el("td");
  shelter.appendChild(document.createTextNode(metrics.bestShelter || "none reachable"));
  shelter.appendChild(el("span", "cell-sub", metrics.bestShelter
    ? metrics.bestShelterRisk + " risk \u00b7 " + metrics.bestShelterMinutes + " min \u00b7 " + metrics.bestShelterFree + " free"
    : "every route to a shelter is blocked"));
  tr.appendChild(shelter);

  const cap = el("td", "num", String(metrics.freePlacesReachable));
  cap.appendChild(el("span", "cell-sub", metrics.reachableShelters + " of " + state.shelters.length + " shelters reachable"));
  tr.appendChild(cap);

  const act = el("td");
  if (spec) {
    const prev = el("button", "btn btn-ghost", "Preview");
    prev.type = "button";
    prev.addEventListener("click", () => previewScenario(spec, metrics));
    act.appendChild(prev);
    const btn = el("button", "btn btn-ghost", "Apply");
    btn.type = "button";
    btn.addEventListener("click", () => applyScenario(spec));
    act.appendChild(btn);
  }
  tr.appendChild(act);
  return tr;
}

/** Temporary map overlay for one scenario — the live network is untouched. */
function previewScenario(spec, metrics) {
  const inc = deriveIncident({
    type: spec.type || "ROAD_BLOCKAGE",
    typeDisplay: spec.type ? spec.type.replace("_", " ") : "Road blockages",
    severity: spec.type ? spec.severity : "HIGH",
    affectedRoads: metrics.affectedRoads || [],
  });
  const route = metrics.route && metrics.route.found ? metrics.route.path : [];
  setScenarioPreview(Object.assign({}, inc, {
    label: spec.label,
    severity: spec.type ? spec.severity : "HIGH",
    path: route,
    routeKm: metrics.route && metrics.route.found ? metrics.route.distanceKm : null,
  }));
  const summary = $("scenSummary");
  if (summary) {
    summary.hidden = false;
    summary.innerHTML = "";
    summary.appendChild(el("p", null, "Previewing \u201c" + spec.label + "\u201d on the map \u2014 the live network is unchanged. Apply to make it the current state."));
  }
}

async function runScenarios(ev) {
  if (ev) ev.preventDefault();
  const summary = $("scenSummary");
  const from = $("scenFrom").value;
  const to = $("scenTo").value;
  const evacuees = Math.max(1, parseInt($("scenEvacuees").value, 10) || 1);
  if (!from || !to) { summary.hidden = false; summary.innerHTML = ""; summary.appendChild(el("p", null, "Pick an evacuation point and destination first.")); return; }
  summary.hidden = false;
  summary.innerHTML = "";
  summary.appendChild(el("p", null, "Running " + SCENARIO_PRESETS.length + " scenarios on a copy of the network\u2026"));
  const specs = readScenSpecs();
  const total = specs.reduce((a, s) => a + s.blocks.length, 0);
  try {
    const resp = await apiPost("/scenarios/compare", { from, to, evacuees, scenarios: specs });
    state.scenSpecs = specs;
    state.scenRows = resp.scenarios;
    renderScenTable(resp, specs);
    summary.innerHTML = "";
    for (const line of resp.summary) summary.appendChild(el("p", null, line));
  } catch (err) {
    summary.innerHTML = "";
    summary.appendChild(el("p", null, err.message));
  }
}

function renderScenTable(resp, specs) {
  const tbl = $("scenTable");
  tbl.innerHTML = "";
  const thead = el("thead");
  const hr = el("tr");
  for (const t of ["SCENARIO", "BLOCKED", "HIGH-RISK", "DAMAGED", "CUT OFF", "ROUTE", "TIME", "RISK", "SHELTER", "FREE", ""]) {
    hr.appendChild(el("th", null, t));
  }
  thead.appendChild(hr);
  tbl.appendChild(thead);

  const tb = el("tbody");
  const base = Object.assign({ label: "Current network" }, resp.baseline);
  tb.appendChild(scenarioTableRow(base, null, resp.baseline));
  resp.scenarios.forEach((s, i) => tb.appendChild(scenarioTableRow(s, specs[i], resp.baseline)));
  tbl.appendChild(tb);
  $("scenTableWrap").hidden = false;
}

/** Apply one scenario to the live network (Phase 1 logic) — the only step that changes it. */
async function applyScenario(spec) {
  const summary = $("scenSummary");
  try {
    const resp = await apiPost("/scenarios/apply", spec);
    summary.hidden = false;
    summary.innerHTML = "";
    summary.appendChild(el("p", null, "Applied \u201c" + resp.label + "\u201d \u2014 " + resp.description + ". "
      + resp.blocked + " road(s) now blocked, " + resp.highRisk + " high risk, " + resp.damaged + " damaged."));
    summary.appendChild(el("p", null, resp.recommendedAction + " Routing already excludes the closed roads \u2014 use Reset network to restore the original roads."));
    state.scenarioPreview = null;
    applyReroute(resp.reroute);
    setIncidentFromResponse(resp);
    clearCompare();
    await refreshData();
    renderScenRows();
    await buildCurve();
    autoRefreshRecommendations();
    await runScenarios();       // re-measure: the applied scenario is now the live network
    await refreshMapState();
  } catch (err) {
    summary.hidden = false;
    summary.innerHTML = "";
    summary.appendChild(el("p", null, err.message));
  }
}

/* ---------------- Leaflet real map (OSM street + Esri satellite) ---------------- */
function initLeaflet() {
  if (typeof L === "undefined" || state.geoMap) return;
  state.geoMap = L.map($("leafletMap"), {
    center: [22.0790, 82.1500],
    zoom: 14,
    zoomControl: true,
    attributionControl: true,
  });
  state.geoStreet = L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  });
  state.geoSat = L.tileLayer("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}", {
    maxZoom: 19,
    attribution: "Tiles &copy; Esri &mdash; Source: Esri, Maxar, Earthstar Geographics",
  });
  state.geoMarkers = L.layerGroup().addTo(state.geoMap);   // locations + shelters
  state.geoLines = L.layerGroup().addTo(state.geoMap);     // roads
  state.geoRoadIcons = L.layerGroup().addTo(state.geoMap); // status symbols
  state.geoIncidents = L.layerGroup().addTo(state.geoMap); // incidents / previews
  state.geoRouteLayer = L.layerGroup().addTo(state.geoMap); // routes
}

function setGeoView(mode) {
  const frame = document.querySelector(".map-frame");
  for (const [id, m] of [["viewSchematic", "schematic"], ["viewStreet", "street"], ["viewSatellite", "satellite"]]) {
    $(id).classList.toggle("active", m === mode);
  }
  if (mode === "schematic") {
    frame.classList.remove("leaflet-active");
    map.fitted = false;
    renderMap();
    return;
  }
  frame.classList.add("leaflet-active");   // unhide BEFORE init: L.map/fitBounds need a real size
  initLeaflet();
  syncLeaflet();   // first activation: the map now exists, fill it
  const tilePane = state.geoMap.getPane("tilePane");
  tilePane.classList.remove("satellite");
  if (mode === "street") {
    state.geoStreet.addTo(state.geoMap);
    state.geoSat.remove();
  } else {
    state.geoSat.addTo(state.geoMap);
    state.geoStreet.remove();
    tilePane.classList.add("satellite");
  }
  drawGeoRoute();
  state.geoMap.invalidateSize();
  setTimeout(() => {
    state.geoMap.invalidateSize();
    if (state.geoNeedsFit) { state.geoNeedsFit = false; fitGeo(); }
  }, 80);
}

function openGeoCard(html, latlng) {
  L.popup({ className: "resq-pop", maxWidth: 330, autoPan: true })
    .setLatLng(latlng)
    .setContent(html)
    .openOn(state.geoMap);
}

function syncLeaflet() {
  if (!state.geoMap) return;
  state.geoMarkers.clearLayers();
  state.geoRoadIcons.clearLayers();
  state.geoIncidents.clearLayers();
  const showShelters = !state.layers || state.layers.shelters !== false;

  // locations — shelters get their live state, capacity colour and recommendation
  for (const l of state.locations) {
    const info = showShelters ? shelterState(l.name) : null;
    // SHELTERS layer off → hide shelter markers entirely (keep other waypoints)
    if (!showShelters && isShelterLoc(l.name)) continue;
    if (info) {
      const star = info.rec && info.rec.recommended;
      const html = '<div class="resq-shelter' + (star ? " rec" : "") + '" style="--sc:' + info.colour + '">' +
        svgIcon("shelter", info.colour, 14) +
        (star ? '<span class="resq-star">' + svgIcon("star", C.safe, 10) + "</span>" : "") + "</div>";
      L.marker([l.lat, l.lng], { icon: L.divIcon({ className: "resq-marker", html, iconSize: [26, 26], iconAnchor: [13, 13] }), keyboard: false })
        .bindTooltip(l.name.toUpperCase() + " \u00b7 " + info.label, { className: "resq-tip", direction: "top", offset: [0, -12] })
        .bindPopup(() => shelterCardHtml(l.name), { className: "resq-pop", maxWidth: 330 })
        .addTo(state.geoMarkers);
      continue;
    }
    const icon = L.divIcon({
      className: "resq-marker",
      html: '<div class="resq-pin loc"></div>',
      iconSize: [16, 16], iconAnchor: [8, 8],
    });
    L.marker([l.lat, l.lng], { icon, keyboard: false })
      .bindTooltip(l.name.toUpperCase(), { className: "resq-tip", direction: "top", offset: [0, -10] })
      .bindPopup(() => locationCardHtml(l.name), { className: "resq-pop", maxWidth: 330 })
      .addTo(state.geoMarkers);
  }

  // roads + the status symbols that must stay attached to them
  state.geoLines.clearLayers();
  const seen = new Set();
  const showRoadIcons = !state.layers || state.layers.roads !== false;
  const styleMap = {
    OPEN: { color: "#8b9489", weight: 2 },
    HIGH_RISK: { color: "#e8a13c", weight: 2.5 },
    DAMAGED: { color: "#cd5c34", weight: 2.5, dashArray: "6 6" },
    BLOCKED: { color: "#e44b3c", weight: 3, dashArray: "6 6" },
  };
  for (const r of state.roads) {
    const key = r.fromId < r.toId ? r.fromId + "|" + r.toId : r.toId + "|" + r.fromId;
    if (!seen.add(key)) continue;
    const a = state.geoById.get(r.fromId), b = state.geoById.get(r.toId);
    if (!a || !b) continue;
    L.polyline([[a.lat, a.lng], [b.lat, b.lng]],
      Object.assign({ opacity: 0.9 }, styleMap[r.status] || styleMap.OPEN))
      .on("click", (e) => openGeoCard(roadCardHtml(r), e.latlng))
      .addTo(state.geoLines);
    const iconKey = ROAD_ICON[r.status];
    if (iconKey && showRoadIcons) {
      const size = r.status === "BLOCKED" ? 24 : 20;
      const html = '<div class="resq-roadico" style="--rc:' + ROAD_ICON_COLOR[r.status] + '">' +
        svgIcon(iconKey, ROAD_ICON_COLOR[r.status], r.status === "BLOCKED" ? 16 : 13) + "</div>";
      L.marker([(a.lat + b.lat) / 2, (a.lng + b.lng) / 2],
        { icon: L.divIcon({ className: "resq-marker", html, iconSize: [size, size], iconAnchor: [size / 2, size / 2] }), keyboard: false })
        .bindTooltip(r.from + " \u2192 " + r.to + " \u00b7 " + r.status.replace("_", " "), { className: "resq-tip", direction: "top", offset: [0, -8] })
        .bindPopup(() => roadCardHtml(r), { className: "resq-pop", maxWidth: 330 })
        .addTo(state.geoRoadIcons);
    }
  }

  // incident (or the temporary scenario preview) at its epicentre
  const inc = state.scenarioPreview || state.incident;
  if (inc && (!state.layers || state.layers.incidents !== false) && inc.epicenterId) {
    const loc = state.geoById.get(inc.epicenterId);
    if (loc) {
      const col = SEVERITY_COLOR[inc.severity] || C.warn;
      const html = '<div class="resq-incident' + (inc.severity === "CRITICAL" ? " crit" : "") +
        (inc.label ? " prev" : "") + '" style="--ic:' + col + '">' + svgIcon(DISASTER_ICON[inc.type] || "warn", C.bone, 15) + "</div>";
      L.marker([loc.lat, loc.lng], { icon: L.divIcon({ className: "resq-marker", html, iconSize: [32, 32], iconAnchor: [16, 16] }), keyboard: false })
        .bindTooltip((inc.label ? "PREVIEW \u00b7 " : "") + (inc.typeDisplay || inc.type) + " \u00b7 " + inc.severity,
          { className: "resq-tip", direction: "top", offset: [0, -14] })
        .bindPopup(() => incidentCardHtml(inc), { className: "resq-pop", maxWidth: 330 })
        .addTo(state.geoIncidents);
    }
  }

  drawGeoRoute();
  fitGeo();
}

function geoPoints(names) {
  if (!names || names.length < 2) return null;
  const pts = names.map((n) => state.byName.get(n)).filter(Boolean).map((l) => [l.lat, l.lng]);
  return pts.length < 2 ? null : pts;
}

function drawGeoRoute() {
  if (!state.geoMap) return;
  state.geoRouteLayer.clearLayers();
  if (state.layers && state.layers.routes === false) return;
  // previous plan (phase 1) and the temporary scenario route (phase 4)
  const prevPts = geoPoints(state.previousPath);
  if (prevPts && (state.previousPath || []).join(">") !== (state.routePath || []).join(">")) {
    L.polyline(prevPts, { className: "resq-route-prev", interactive: false }).addTo(state.geoRouteLayer);
  }
  const prevScenarioPts = geoPoints(state.scenarioPreview && state.scenarioPreview.path);
  if (prevScenarioPts) {
    L.polyline(prevScenarioPts, { className: "resq-route-preview", interactive: false }).addTo(state.geoRouteLayer);
  }
  const shortPts = geoPoints(state.shortestPath);
  if (shortPts) L.polyline(shortPts, { className: "resq-route-short", interactive: false }).addTo(state.geoRouteLayer);
  const pts = geoPoints(state.routePath);
  if (!pts) return;
  L.polyline(pts, { className: "resq-route-glow", interactive: false }).addTo(state.geoRouteLayer);
  L.polyline(pts, { className: "resq-route", interactive: false }).addTo(state.geoRouteLayer);
  L.polyline(pts, { className: "resq-route-dash", interactive: false }).addTo(state.geoRouteLayer);
}

/** Fit the real map to the active route (falls back to every location). */
function fitGeoRoute() {
  if (!state.geoMap) return;
  const names = state.routePath && state.routePath.length ? state.routePath : state.locations.map((l) => l.name);
  const pts = names.map((n) => state.byName.get(n)).filter(Boolean).map((l) => [l.lat, l.lng]);
  if (pts.length) state.geoMap.fitBounds(L.latLngBounds(pts).pad(0.3));
}

function fitGeo() {
  if (!state.geoMap || !state.locations.length) return;
  const el = state.geoMap.getContainer();
  if (!el.offsetWidth || !el.offsetHeight) { state.geoNeedsFit = true; return; } // hidden: refit once shown
  state.geoMap.fitBounds(L.latLngBounds(state.locations.map((l) => [l.lat, l.lng])).pad(0.25));
}

/* ---------------- boot ---------------- */
const DISASTER_TYPES = ["FLOOD", "EARTHQUAKE", "CYCLONE", "TSUNAMI", "LANDSLIDE", "AVALANCHE",
  "DROUGHT", "THUNDERSTORM", "TORNADO", "INDUSTRIAL_FIRE", "CHEMICAL_LEAK", "GAS_LEAK",
  "NUCLEAR_EMERGENCY", "BUILDING_COLLAPSE", "DAM_FAILURE"];

async function boot() {
  splitAll();
  initReveals();
  initScrollDriver();
  initGlow();
  initWipe();
  initCurve();
  initForms();
  initMap();

  fillSelect($("disasterType"), DISASTER_TYPES.map((t) => ({ v: t, t: t.replace("_", " ") })), "v", "t");
  $("routeForm").addEventListener("submit", findRoute);
  $("nearestBtn").addEventListener("click", findNearest);
  $("compareBtn").addEventListener("click", runCompare);
  $("scenForm").addEventListener("submit", runScenarios);
  $("scenReset").addEventListener("click", resetNetwork);
  $("disasterForm").addEventListener("submit", applyDisaster);
  $("disasterReset").addEventListener("click", resetNetwork);
  $("resetNetwork").addEventListener("click", resetNetwork);
  $("checkinForm").addEventListener("submit", checkIn);
  $("recForm").addEventListener("submit", runRecommend);
  $("roadForm").addEventListener("submit", updateRoad);
  $("bfsForm").addEventListener("submit", (e) => { e.preventDefault(); runTraversal("bfs"); });
  $("dfsBtn").addEventListener("click", (e) => { e.preventDefault(); runTraversal("dfs"); });
  $("minRoadsForm").addEventListener("submit", (e) => { e.preventDefault(); runMinRoads(); });
  $("connBtn").addEventListener("click", (e) => { e.preventDefault(); runConnectivity(); });
  $("compBtn").addEventListener("click", (e) => { e.preventDefault(); runComponents(); });
  $("mstBtn").addEventListener("click", (e) => { e.preventDefault(); runMST(); });
  $("viewSchematic").addEventListener("click", () => setGeoView("schematic"));
  $("viewStreet").addEventListener("click", () => setGeoView("street"));
  $("viewSatellite").addEventListener("click", () => setGeoView("satellite"));

  // map controls: view, layer toggles and the legend
  $("mapReset").addEventListener("click", () => { mapResetView(); fitGeo(); });
  $("mapFitRoute").addEventListener("click", () => { mapFitRoute(); fitGeoRoute(); });
  for (const [id, key] of [["tglIncidents", "incidents"], ["tglShelters", "shelters"], ["tglRoutes", "routes"]]) {
    $(id).addEventListener("click", () => {
      state.layers[key] = state.layers[key] === false;
      syncMapChips();
      syncLeaflet();
    });
  }
  $("legendToggle").addEventListener("click", () => {
    const lg = $("mapLegend");
    lg.hidden = !lg.hidden;
    $("legendToggle").classList.toggle("active", !lg.hidden);
  });
  $("mapStatus").addEventListener("click", () => {
    state.focus = { kind: "route" };   // route card in the detail panel
    const box = $("mapDetail");
    box.hidden = false;
    box.innerHTML = routeCardHtml();
  });

  renderWatch(null);
  renderLegend();
  syncMapChips();
  try {
    await refreshData();
    await buildCurve();
    await refreshMapState();
  } catch (err) {
    $("footApiState").textContent = "API OFFLINE — " + err.message;
  }
}

boot();
