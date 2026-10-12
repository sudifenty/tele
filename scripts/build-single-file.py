#!/usr/bin/env python3
"""Build the single-file Vercel deliverable.

Takes the parent dashboard (web/index.html) and adds:
  - a "Project source" section, and
  - an embedded, read-only copy of every git-tracked text file in the project
    (except web/index.html itself, which is the page).

Usage (from the repo root):
    python3 scripts/build-single-file.py [output.html]

Default output: ../tele-vercel/index.html (relative to the repo root).
Only files that git would commit are embedded (git ls-files --cached --others
--exclude-standard), so build outputs, local.properties, and google-services.json
are never included.
"""
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "web" / "index.html"
DEFAULT_OUT = ROOT.parent / "tele-vercel" / "index.html"
OUT = pathlib.Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else DEFAULT_OUT

EXCLUDE = {"web/index.html", "index.html"}

EXTRA_CSS = """
  details.source { margin-top:24px; }
  details.source > summary { cursor:pointer; font-weight:600; padding:12px 0; color:var(--teal); }
  .srcwrap { display:grid; grid-template-columns: 260px 1fr; gap:12px; margin-top:12px; }
  .srclist { display:flex; flex-direction:column; gap:4px; max-height:560px; overflow:auto; }
  .srclist button { background:#e8eeee; color:var(--ink); text-align:left; font-size:12px; font-family:ui-monospace, Menlo, monospace; word-break:break-all; }
  .srclist button.active { background:var(--teal); color:#fff; }
  pre.srccode { background:#0f1718; color:#e6f2f1; padding:14px; border-radius:8px; overflow:auto; max-height:560px; font-size:12px; line-height:1.45; margin:0; white-space:pre; }
  @media (max-width:760px) { .srcwrap { grid-template-columns:1fr; } }
"""

SOURCE_SECTION = """
<section id="projectSource" style="max-width:980px;margin:0 auto;padding:0 20px 40px;">
  <details class="source">
    <summary>Project source: Android child app, Firestore rules, Firebase config, README (for developers)</summary>
    <div class="card">
      <p class="muted">Every text file in the Tele project is embedded in this page: the Android child app (Kotlin), the Firestore security rules, Firebase config, and the README. Secrets such as google-services.json and local.properties are not included.</p>
      <div class="row" style="margin-bottom:8px">
        <button id="btnDownloadSource" class="secondary">Download all source as one text file</button>
      </div>
      <div class="srcwrap">
        <div class="srclist" id="srcList"></div>
        <pre class="srccode" id="srcCode"></pre>
      </div>
    </div>
  </details>
</section>
"""

VIEWER_JS_TEMPLATE = """
/* ---------- embedded project source (read-only copy of the project) ---------- */
const PROJECT_FILES = __PROJECT_JSON__;
function showSource(path) {
  document.querySelectorAll(".srclist button").forEach(b => b.classList.toggle("active", b.dataset.path === path));
  $("#srcCode").textContent = PROJECT_FILES[path] ?? "";
}
function renderSourceList() {
  const paths = Object.keys(PROJECT_FILES).sort();
  $("#srcList").innerHTML = paths.map(p => `<button type="button" data-path="${esc(p)}">${esc(p)}</button>`).join("");
  showSource("README.md");
}
$("#srcList").addEventListener("click", e => {
  if (e.target.dataset && e.target.dataset.path) showSource(e.target.dataset.path);
});
$("#btnDownloadSource").addEventListener("click", () => {
  const text = Object.keys(PROJECT_FILES).sort()
    .map(p => `===== FILE: ${p} =====\\n${PROJECT_FILES[p]}\\n`).join("\\n");
  const url = URL.createObjectURL(new Blob([text], { type: "text/plain" }));
  const a = document.createElement("a");
  a.href = url; a.download = "tele-project-source.txt";
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
});
renderSourceList();

"""


def tracked_text_files():
    out = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
        cwd=ROOT, check=True, capture_output=True, text=True,
    ).stdout.splitlines()
    files = {}
    for rel in sorted(out):
        if rel in EXCLUDE:
            continue
        p = ROOT / rel
        if not p.is_file():
            continue
        try:
            files[rel] = p.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue  # skip binaries such as gradle-wrapper.jar
    return files


def build():
    html = SRC.read_text(encoding="utf-8")
    files = tracked_text_files()

    # JSON is safe inside <script> only if "</" cannot close the tag.
    project_json = json.dumps(files, ensure_ascii=False).replace("</", "<\\/")
    viewer_js = VIEWER_JS_TEMPLATE.replace("__PROJECT_JSON__", project_json)

    def insert_before(text, marker, addition):
        if text.count(marker) != 1:
            raise SystemExit(f"marker not found exactly once: {marker!r}")
        return text.replace(marker, addition + marker)

    html = insert_before(html, "</style>", EXTRA_CSS)                    # styles
    if html.count("</main>") != 1:
        raise SystemExit("expected exactly one </main>")
    html = html.replace("</main>", "</main>\n" + SOURCE_SECTION)  # source section after </main>
    html = insert_before(html, "/* ---------- startup ---------- */", viewer_js)  # viewer JS before startup

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(html, encoding="utf-8")
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes, {len(files)} embedded files)")


if __name__ == "__main__":
    build()
