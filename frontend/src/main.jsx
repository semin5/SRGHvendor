import React, { useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { Eye, Pencil, Plus, Printer, Save, Search, Trash2, X } from "lucide-react";
import "./style.css";

const API = "/vendor/api";
const NURI_VOICE_VENDOR = "누리보이스(전화기)";
const DICLE_VENDOR = "디클";
const FINETECH_VENDOR = "파인텍";
const TABS = [["sites", "업무사이트"], ["vendors", "업체 정보"], ["maintenance", "유지보수업체 리스트"], ["video", "영상정보월간점검일지"]];
const COLUMNS = {
  sites: [["work_name", "업무명칭"], ["site_url", "사이트"], ["login_id", "ID"], ["password", "PW"], ["vendor_name", "업체"], ["contact_person", "담당자"], ["contact_phone", "연락처"], ["remarks", "비고"]],
  vendors: [["vendor_name", "업체명"], ["product", "제품"], ["contact_person", "담당자"], ["phone_number", "전화번호"], ["office_phone", "사무실 전화"], ["address", "주소"], ["website", "홈페이지"], ["courier_service", "택배이용"]],
  maintenance: [["vendor_name", "유지보수업체"], ["phone_number", "전화번호"]],
  video: [["inspection_year", "연도"], ["inspection_month", "월"], ["change_details", "변경 내용"]],
};
const VISIBLE = {
  sites: COLUMNS.sites.filter(([key]) => !["login_id", "password", "contact_person"].includes(key)),
  vendors: COLUMNS.vendors.filter(([key]) => !["contact_person", "office_phone"].includes(key)),
  maintenance: COLUMNS.maintenance,
  video: COLUMNS.video.filter(([key]) => key !== "inspection_year"),
};
const SENSITIVE = new Set(["password"]);
const NUMERIC = new Set(["inspection_year", "inspection_month"]);
const LONG_TEXT = new Set(["remarks", "change_details"]);
const display = (value) => String(value ?? "").trim() || "—";
const cellDisplay = (key, value) => key === "inspection_month" && value != null ? `${value}월` : display(value);

function useEscape(onClose) {
  useEffect(() => {
    const handler = (event) => { if (event.key === "Escape") onClose(); };
    addEventListener("keydown", handler);
    return () => removeEventListener("keydown", handler);
  }, [onClose]);
}

function EntryModal({ tab, row, saving, onClose, onSubmit }) {
  useEscape(onClose);
  const submit = (event) => {
    event.preventDefault();
    onSubmit(Object.fromEntries(new FormData(event.currentTarget).entries()));
  };
  return <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <form className="modal entry-modal" onSubmit={submit} autoComplete="off">
      <div className="modal-header"><h2>{row ? "수정" : "추가"}</h2><button type="button" onClick={onClose} aria-label="닫기"><X size={18} /></button></div>
      <div className="entry-grid">{COLUMNS[tab].map(([key, label]) =>
        <label key={key}>{label}
          {LONG_TEXT.has(key)
            ? <textarea name={key} defaultValue={row?.[key] ?? ""} />
            : <input name={key} type={NUMERIC.has(key) ? "number" : "text"}
                defaultValue={row?.[key] ?? ""} autoFocus={key === COLUMNS[tab][0][0]}
                autoComplete={SENSITIVE.has(key) ? "new-password" : "off"} />}
        </label>)}</div>
      <div className="modal-footer"><button type="button" className="cancel" onClick={onClose}>취소</button>
        <button type="submit" className="save" disabled={saving}><Save size={15} />{saving ? "저장 중" : "확인"}</button></div>
    </form>
  </div>;
}

function DetailModal({ tab, row, onClose }) {
  useEscape(onClose);
  const title = TABS.find(([key]) => key === tab)?.[1] || "상세";
  return <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <div className="modal hardware-detail-modal" role="dialog" aria-modal="true" aria-label={title + " 상세"}>
      <div className="modal-header"><h2>{title} 상세</h2><button type="button" onClick={onClose} aria-label="닫기"><X size={18} /></button></div>
      <dl className="detail-grid">{COLUMNS[tab].map(([key, label]) => <div key={key}>
        <dt>{label}</dt><dd>{cellDisplay(key, row[key])}</dd>
      </div>)}</dl>
      <div className="modal-footer">
        <button type="button" className="save" onClick={onClose}>확인</button>
      </div>
    </div>
  </div>;
}

function AsRequestModal({ row, onClose }) {
  useEscape(onClose);
  const isDicle = row.vendor_name?.trim() === DICLE_VENDOR;
  const isFinetech = row.vendor_name?.trim() === FINETECH_VENDOR;
  const printFrameRef = useRef(null);
  const pendingPrintRef = useRef(false);
  const [deviceName, setDeviceName] = useState("");
  const [mac, setMac] = useState("");
  const [symptoms, setSymptoms] = useState("");
  const [quantityOne, setQuantityOne] = useState("");
  const [quantityTwo, setQuantityTwo] = useState("");
  const [printPages, setPrintPages] = useState([]);
  const [printReady, setPrintReady] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!printReady || !pendingPrintRef.current) return;
    pendingPrintRef.current = false;
    const frameWindow = printFrameRef.current?.contentWindow;
    if (frameWindow) { frameWindow.focus(); frameWindow.print(); }
    else setError("인쇄 화면을 준비하지 못했습니다. 다시 시도해 주세요.");
  }, [printReady]);
  const invalidate = (setter) => (event) => { setter(event.target.value); setPrintPages([]); setPrintReady(false); setError(""); };
  const generatePdf = async () => {
    setGenerating(true); setError(""); setPrintPages([]); setPrintReady(false);
    try {
      let token = null;
      if (!isFinetech) {
        const identity = await fetch("/it/api/auth/audit-token", { credentials: "same-origin" });
        if (!identity.ok) throw new Error(identity.status === 401
          ? "IT에 로그인한 뒤 신청서를 출력해 주세요." : "IT 로그인 상태를 확인하지 못했습니다.");
        token = (await identity.json()).token;
      }
      const response = await fetch(`${API}/vendors/${row.source_row}/${isFinetech ? "finetech-request" : "as-request"}`, {
        method: "POST", headers: { "Content-Type": "application/json", ...(token ? { "X-IT-Audit-Token": token } : {}) },
        body: JSON.stringify(isFinetech ? { quantityOne, quantityTwo } : isDicle ? { mac, symptoms } : { deviceName, mac, symptoms }),
      });
      if (!response.ok || !response.headers.get("Content-Type")?.includes("application/pdf"))
        throw new Error(response.status === 401 ? "IT에 로그인한 뒤 신청서를 출력해 주세요." : "PDF를 만들지 못했습니다. 입력 내용과 서버 연결을 확인해 주세요.");
      const pdf = await response.blob();
      const [{ getDocument, GlobalWorkerOptions }, { default: workerSrc }] = await Promise.all([
        import("pdfjs-dist"), import("pdfjs-dist/build/pdf.worker.min.mjs?url"),
      ]);
      GlobalWorkerOptions.workerSrc = workerSrc;
      const loadingTask = getDocument({ data: new Uint8Array(await pdf.arrayBuffer()) });
      const document = await loadingTask.promise;
      try {
        const pages = [];
        for (let number = 1; number <= document.numPages; number++) {
          const page = await document.getPage(number);
          const viewport = page.getViewport({ scale: 3 });
          const canvas = window.document.createElement("canvas");
          canvas.width = Math.ceil(viewport.width);
          canvas.height = Math.ceil(viewport.height);
          await page.render({ canvas, viewport, intent: "print" }).promise;
          pages.push(canvas.toDataURL("image/png"));
          canvas.width = canvas.height = 0;
        }
        setPrintPages(pages);
      } finally { await loadingTask.destroy(); }
    } catch (error) {
      pendingPrintRef.current = false;
      setError(error.message || "PDF를 만들지 못했습니다. 입력 내용과 서버 연결을 확인해 주세요.");
    }
    finally { setGenerating(false); }
  };
  const print = (event) => {
    event.preventDefault();
    const frameWindow = printFrameRef.current?.contentWindow;
    if (printReady && frameWindow) { frameWindow.focus(); frameWindow.print(); }
    else { pendingPrintRef.current = true; generatePdf(); }
  };
  const printHtml = printPages.length ? `<!doctype html><html><head><meta charset="utf-8"><style>
    @page{size:A4;margin:0}html,body{margin:0;padding:0}.page{break-after:page}
    .page:last-child{break-after:auto}img{display:block;width:100%;height:auto}
    </style></head><body>${printPages.map((src) => `<div class="page"><img src="${src}" alt=""></div>`).join("")}</body></html>` : "";
  return <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <form className="modal as-request-modal" onSubmit={print}>
      <div className="modal-header"><h2>{isFinetech ? "파인텍 양식" : isDicle ? "디클 접수증" : "제품 A/S 신청서"} · {row.vendor_name || "업체 정보"}</h2><button type="button" onClick={onClose} aria-label="닫기"><X size={18} /></button></div>
      <div className="as-request-fields">
        {isFinetech ? <>
          <label>AS USB One B″ Foot Switch 수량<input type="text" inputMode="numeric" pattern="[0-9]{0,4}" value={quantityOne} onChange={(event) => { setQuantityOne(event.target.value.replace(/[^0-9]/g, "").slice(0, 4)); setPrintPages([]); setPrintReady(false); setError(""); }} maxLength={4} /></label>
          <label>AS USB Two B″ Foot Switch 수량<input type="text" inputMode="numeric" pattern="[0-9]{0,4}" value={quantityTwo} onChange={(event) => { setQuantityTwo(event.target.value.replace(/[^0-9]/g, "").slice(0, 4)); setPrintPages([]); setPrintReady(false); setError(""); }} maxLength={4} /></label>
        </> : <>
          {!isDicle && <label>기기명<input value={deviceName} onChange={invalidate(setDeviceName)} maxLength={80} required /></label>}
          <label className={isDicle ? "wide" : undefined}>{isDicle ? "시리얼 번호(S/N)" : "MAC"}<input value={mac} onChange={invalidate(setMac)} maxLength={isDicle ? 100 : 80} required /></label>
          <label className="wide">{isDicle ? "증상 및 점검요청" : "증상"}<textarea value={symptoms} onChange={invalidate(setSymptoms)} maxLength={isDicle ? 800 : 600} rows={3} required /></label>
        </>}
      </div>
      {error && <div className="as-request-error" role="alert">{error}</div>}
      {printPages.length > 0 && <iframe ref={printFrameRef} className="as-request-print-frame" srcDoc={printHtml} title="신청서 인쇄용 화면" onLoad={() => setPrintReady(true)} />}
      <div className="modal-footer"><button type="button" className="cancel" onClick={onClose}>닫기</button>
        <button type="submit" className="save" disabled={generating}><Printer size={15} />{generating ? "준비 중" : "확인"}</button></div>
    </form>
  </div>;
}

function App() {
  const [tab, setTab] = useState("sites");
  const [rows, setRows] = useState([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState(null);
  const [detail, setDetail] = useState(null);
  const [asRequest, setAsRequest] = useState(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const tools = document.querySelector(".sticky-tools");
    if (!tools) return;
    const update = () => document.documentElement.style.setProperty("--list-tools-height", `${tools.offsetHeight}px`);
    const observer = new ResizeObserver(update);
    observer.observe(tools);
    update();
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    fetch(API + "/" + tab, { signal: controller.signal, cache: "no-store" })
      .then((response) => response.ok ? response.json() : Promise.reject(new Error("HTTP " + response.status)))
      .then(setRows)
      .catch((failure) => { if (failure.name !== "AbortError") setError("업체 목록을 불러오지 못했습니다."); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [tab]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase("ko");
    const searchable = tab === "video" ? COLUMNS.video : VISIBLE[tab];
    const matches = needle ? rows.filter((row) => searchable.some(([key]) => display(row[key]).toLocaleLowerCase("ko").includes(needle))) : rows;
    return tab === "video" ? [...matches].sort((a, b) => Number(a.inspection_year) - Number(b.inspection_year)
      || Number(a.inspection_month) - Number(b.inspection_month) || Number(a.source_row) - Number(b.source_row)) : matches;
  }, [rows, query, tab]);
  const yearCounts = useMemo(() => {
    const counts = new Map();
    if (tab === "video") filtered.forEach((row) => counts.set(row.inspection_year, (counts.get(row.inspection_year) || 0) + 1));
    return counts;
  }, [filtered, tab]);

  const changeTab = (next) => {
    setTab(next); setQuery(""); setNotice(""); setEditing(null); setDetail(null); setAsRequest(null); setAdding(false);
  };
  const openRecord = async (row, target) => {
    setNotice("");
    try {
      const response = await fetch(API + "/" + tab + "/" + row.source_row, { cache: "no-store" });
      if (!response.ok) throw new Error();
      const full = await response.json();
      if (target === "edit") setEditing(full); else setDetail(full);
    } catch { setNotice("상세 내용을 불러오지 못했습니다."); }
  };
  const save = async (values) => {
    setSaving(true); setNotice("");
    try {
      const response = await fetch(API + "/" + tab + (editing ? "/" + editing.source_row : ""), {
        method: editing ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(values),
      });
      if (!response.ok) throw new Error("HTTP " + response.status);
      const saved = await response.json();
      setRows((current) => editing
        ? current.map((item) => item.source_row === editing.source_row ? saved : item)
        : [...current, saved].sort((a, b) => a.source_row - b.source_row));
      setAdding(false); setEditing(null);
      setNotice(editing ? "수정했습니다." : "추가했습니다.");
    } catch { setNotice("저장하지 못했습니다. 입력 내용을 확인해 주세요."); }
    finally { setSaving(false); }
  };
  const remove = async (row) => {
    if (!confirm("해당 항목을 삭제하시겠습니까? 삭제한 데이터는 복구할 수 없습니다.")) return;
    setNotice("");
    try {
      const response = await fetch(API + "/" + tab + "/" + row.source_row, { method: "DELETE" });
      if (!response.ok) throw new Error();
      setRows((current) => current.filter((item) => item.source_row !== row.source_row));
      setNotice("삭제했습니다.");
    } catch { setNotice("삭제하지 못했습니다."); }
  };
  return <main className="app">
    <header className="hero"><div className="hero-title"><h1>외부 업체</h1></div></header>
    <section className="sticky-tools"><div className="control-row">
      <nav className="tabs" aria-label="외부 업체 목록 종류">{TABS.map(([key, label]) =>
        <button key={key} className={tab === key ? "active" : ""} onClick={() => changeTab(key)}>{label}</button>)}</nav>
      <label className="search-box"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="검색" aria-label="외부 업체 목록 검색" />
        <button type="button" onClick={() => setQuery("")} aria-label="검색어 지우기">{query && <X size={15} />}</button></label>
      <div className="filter-end"><button className="add-button" onClick={() => setAdding(true)}><Plus size={15} />추가</button></div>
    </div></section>
    {notice && <div className="notice">{notice}</div>}
    {loading && <div className="state">외부 업체 목록을 불러오는 중입니다.</div>}
    {error && <div className="error">{error}</div>}
    {!loading && !error && <section className="table-card" aria-label={TABS.find(([key]) => key === tab)?.[1]}>
      <table className={"table-" + tab}><thead><tr>
        {VISIBLE[tab].map(([key, label]) => <th key={key}>{label}</th>)}
        <th className="manage-heading"><div><span>관리</span><span className="table-count">총 {filtered.length.toLocaleString()}개</span></div></th>
      </tr></thead><tbody>{filtered.map((row, index) => <React.Fragment key={row.source_row}>
        {tab === "video" && (index === 0 || row.inspection_year !== filtered[index - 1].inspection_year) &&
          <tr className="year-group"><th colSpan={VISIBLE[tab].length + 1}>{row.inspection_year == null ? "연도 미정" : `${row.inspection_year}년`}<span>{yearCounts.get(row.inspection_year)}개</span></th></tr>}
        <tr>
        {VISIBLE[tab].map(([key]) => <td key={key} title={cellDisplay(key, row[key])}><span className="cell-text">{cellDisplay(key, row[key])}</span></td>)}
        <td className="manage-cell"><div className="actions">
          <button className="detail-button icon-only" title="자세히" aria-label={row.source_row + "번 행 자세히"} onClick={() => openRecord(row, "detail")}><Eye size={14} /></button>
          {tab === "vendors" && <button className="print-button icon-only" title={[NURI_VOICE_VENDOR, DICLE_VENDOR, FINETECH_VENDOR].includes(row.vendor_name?.trim()) ? "출력" : "출력 양식이 없는 업체"} aria-label={row.source_row + "번 행 A/S 신청서 출력"} disabled={![NURI_VOICE_VENDOR, DICLE_VENDOR, FINETECH_VENDOR].includes(row.vendor_name?.trim())} onClick={() => setAsRequest(row)}><Printer size={15} /></button>}
          <button className="edit icon-only" title="수정" aria-label={row.source_row + "번 행 수정"} onClick={() => openRecord(row, "edit")}><Pencil size={15} /></button>
          <button className="danger icon-only" title="삭제" aria-label={row.source_row + "번 행 삭제"} onClick={() => remove(row)}><Trash2 size={15} /></button>
        </div></td>
      </tr></React.Fragment>)}</tbody></table>
      {!filtered.length && <div className="state">검색 결과가 없습니다.</div>}
    </section>}
    {(adding || editing) && <EntryModal key={tab + (editing?.source_row ?? "new")} tab={tab} row={editing}
      saving={saving} onClose={() => { setAdding(false); setEditing(null); }} onSubmit={save} />}
    {detail && <DetailModal tab={tab} row={detail} onClose={() => setDetail(null)} />}
    {asRequest && <AsRequestModal row={asRequest} onClose={() => setAsRequest(null)} />}
  </main>;
}

createRoot(document.getElementById("root")).render(<App />);
