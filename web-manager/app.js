import { initializeApp } from "https://www.gstatic.com/firebasejs/10.13.2/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut,
} from "https://www.gstatic.com/firebasejs/10.13.2/firebase-auth.js";
import {
  collection,
  doc,
  getFirestore,
  onSnapshot,
} from "https://www.gstatic.com/firebasejs/10.13.2/firebase-firestore.js";

const firebaseConfig = {
  apiKey: "AIzaSyDhnjjTS1Da99p76VrjMGSKQxgib3b23zk",
  authDomain: "pos0210-17ce4.firebaseapp.com",
  projectId: "pos0210-17ce4",
  storageBucket: "pos0210-17ce4.firebasestorage.app",
  messagingSenderId: "734292261602",
  appId: "1:734292261602:android:c60646ce0c9ed4c3f67cbb",
};
const STORE_OWNER_UID = "X9ln8CP7UtbOLOHfipYMCjfbb1p1";
const STORE_ID = "0210";
const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);
const root = ["users", STORE_OWNER_UID, "stores", STORE_ID];
const state = { dashboard: {}, tables: [], sessions: [], batches: [], items: [], unsubs: [] };

const $ = (selector) => document.querySelector(selector);
const money = (value) => `${new Intl.NumberFormat("vi-VN").format(Number(value || 0))}đ`;
const dateTime = (value) => value ? new Intl.DateTimeFormat("vi-VN", { hour: "2-digit", minute: "2-digit", day: "2-digit", month: "2-digit" }).format(new Date(value)) : "—";
const elapsed = (value) => {
  if (!value) return "Chưa có giờ gọi";
  const minutes = Math.max(0, Math.floor((Date.now() - value) / 60000));
  return minutes < 60 ? `${minutes} phút` : `${Math.floor(minutes / 60)}g ${minutes % 60}p`;
};
const statusText = (statuses) => {
  const status = statuses.includes("SERVED") || statuses.includes("DELIVERED") ? "Đã phục vụ" : statuses.includes("SENT") ? "Đã gửi bếp" : "Đang gọi món";
  return status;
};
const rows = (snapshot) => snapshot.docs.map((item) => ({ id: item.id, ...item.data() }));

function currentTable(table) {
  const session = state.sessions.find((row) => row.tableId === table.id && row.status === "OPEN");
  const batches = session ? state.batches.filter((row) => row.sessionId === session.id) : [];
  const batchIds = new Set(batches.map((row) => row.id));
  const items = state.items.filter((row) => batchIds.has(row.batchId));
  return { table, session, batches, items, total: items.reduce((sum, item) => sum + Number(item.unitPrice || 0) * Number(item.qty || 0), 0) };
}

function renderSummary() {
  const dashboard = state.dashboard;
  const cards = [
    ["Bàn đang phục vụ", dashboard.openTables || 0],
    ["Doanh thu hôm nay", money(dashboard.revenueToday)],
    ["Bill hôm nay", dashboard.paidBillsToday || 0],
    ["Doanh thu tháng", money(dashboard.monthRevenue)],
    ["Lợi nhuận vận hành", dashboard.operatingProfit == null ? "Chưa có" : money(dashboard.operatingProfit)],
    ["Tiền cuối kỳ ước tính", money(dashboard.closingCash)],
    ["Tổng vốn đầu tư", money(dashboard.initialInvestment)],
    ["Đã thu hồi vốn", money(dashboard.recoveredCapital)],
    ["Tỷ lệ hoàn vốn", `${(Number(dashboard.paybackBasisPoints || 0) / 100).toLocaleString("vi-VN", { maximumFractionDigits: 1 })}%`],
  ];
  const host = $("#summary");
  host.replaceChildren(...cards.map(([label, value]) => {
    const node = $("#metric-template").content.firstElementChild.cloneNode(true);
    node.querySelector("p").textContent = label;
    node.querySelector("strong").textContent = value;
    return node;
  }));
  $("#sync-status").textContent = dashboard.lastUpdatedAt ? `Cập nhật ${dateTime(dashboard.lastUpdatedAt)}` : "Chờ POS gửi dữ liệu lần đầu…";
}

function renderTables() {
  const host = $("#tables");
  const records = state.tables.filter((table) => table.active !== false).map(currentTable);
  records.sort((a, b) => String(a.table.name).localeCompare(String(b.table.name), "vi"));
  host.replaceChildren(...records.map((record) => {
    const node = $("#table-template").content.firstElementChild.cloneNode(true);
    const occupied = Boolean(record.session || record.table.occupied);
    node.classList.toggle("occupied", occupied);
    node.querySelector(".table-state").textContent = occupied ? "ĐANG PHỤC VỤ" : "TRỐNG";
    node.querySelector("strong").textContent = record.table.name || "Bàn";
    node.querySelector("small").textContent = occupied ? `${elapsed(record.session?.openedAt || record.table.openedAt)} · ${statusText(record.batches.map((batch) => batch.status))}` : "Sẵn sàng nhận khách";
    node.querySelector("b").textContent = occupied ? money(record.total) : "";
    node.disabled = !occupied;
    node.addEventListener("click", () => showDetail(record));
    return node;
  }));
}

function showDetail(record) {
  $("#detail-title").textContent = record.table.name || "Bàn";
  $("#detail-meta").textContent = `Mở bàn: ${dateTime(record.session?.openedAt || record.table.openedAt)} · ${statusText(record.batches.map((batch) => batch.status))}`;
  const list = $("#detail-items");
  list.replaceChildren(...record.items.map((item) => {
    const row = document.createElement("li");
    const note = item.note ? ` · ${item.note}` : "";
    row.innerHTML = `<span>${item.qty}× ${escapeHtml(item.itemName || "Món")}${escapeHtml(note)}</span><b>${money(Number(item.qty || 0) * Number(item.unitPrice || 0))}</b>`;
    return row;
  }));
  if (!record.items.length) list.innerHTML = "<li><span>Chưa có món đồng bộ</span></li>";
  $("#detail-total").textContent = `Tạm tính ${money(record.total)}`;
  $("#table-detail").showModal();
}

function escapeHtml(value) {
  return String(value).replace(/[&<>'"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[char]);
}

function render() { renderSummary(); renderTables(); }
function clearListeners() { state.unsubs.splice(0).forEach((unsubscribe) => unsubscribe()); }
function subscribe() {
  clearListeners();
  const watch = (path, key, transform = rows) => state.unsubs.push(onSnapshot(path, (snapshot) => { state[key] = transform(snapshot); render(); }, (error) => { $("#sync-status").textContent = `Không đọc được dữ liệu: ${error.message}`; }));
  watch(doc(db, ...root, "dashboard", "current"), "dashboard", (snapshot) => snapshot.exists() ? snapshot.data() : {});
  watch(collection(db, ...root, "tableStatus"), "tables");
  watch(collection(db, ...root, "sessions"), "sessions");
  watch(collection(db, ...root, "orderBatches"), "batches");
  watch(collection(db, ...root, "orderItems"), "items");
}

$("#login-button").addEventListener("click", async () => {
  const email = $("#email").value.trim();
  const password = $("#password").value;
  $("#login-error").textContent = "";
  try { await signInWithEmailAndPassword(auth, email, password); }
  catch (error) { $("#login-error").textContent = "Không đăng nhập được. Kiểm tra email hoặc mật khẩu."; }
});
$("#password").addEventListener("keydown", (event) => { if (event.key === "Enter") $("#login-button").click(); });
$("#logout-button").addEventListener("click", () => signOut(auth));
$("#close-detail").addEventListener("click", () => $("#table-detail").close());
onAuthStateChanged(auth, (user) => {
  $("#login-view").hidden = Boolean(user);
  $("#dashboard-view").hidden = !user;
  if (user) subscribe(); else clearListeners();
});
