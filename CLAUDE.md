# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

聪聪学堂 / ChinaStudyFree：免费开源的小学全科（语文/数学/英语/科学）学习平台。npm workspaces monorepo，一套 Next.js 静态站同时服务 Web、Docker、Android/Android TV（WebView 内嵌）；iOS 为独立 SwiftUI 实现。

## 常用命令

```sh
npm ci                                              # 根目录安装（workspaces）
npm run dev                                         # build:data + next dev → http://localhost:3000
npm run build                                       # build:data + next build → apps/web/out/（纯静态）
npm run type-check                                  # 所有 workspace 的 tsc --noEmit
npm test                                            # packages/core 的 vitest
npm test --workspace=@cstf/core -- src/__tests__/grade.test.ts   # 单个测试文件
npm test --workspace=@cstf/core -- -t "用例名"                     # 按用例名过滤
npm run lint --workspace=china-study-free           # next lint
```

- `build:data` 经 `apps/web/scripts/run-python.ts` 调用 `scripts/tts/web_audio.py`：按 `PYTHON` 环境变量 → Windows `py -3`/`python`/`python3`，其它系统 `python3`/`python` 的顺序探测 Python 3.9+，并追加 `-X utf8`。npm script 中不要直接写 `python3`。只需要数据/页面时可在 `apps/web` 下直接 `npx tsx scripts/build-data.ts` 再 `npx next build`，跳过 MP3 转码。
- 资源（音频/插画/课本页/`public/data`/`data/`）不在 Git 中，由 Release 下载：完整包 `python scripts/install-release.py --tag v1.2.0-assets`（约 2 GB，需 FFmpeg/libmp3lame 生成 Web MP3）；只要课程 JSON 用 `python scripts/install-web-data.py`（约 5 MB）。Android CI 用 `install-web-data.py --dest <dir>` 下载到别处，再 `build-data.ts --prebuilt <dir>` 从 `output/` 生成课程，只借用 Release 包里的课文/故事与预录音频索引。
- 行为回归脚本（不在 `npm test` 中，需手动跑）：
  - `npx tsx scripts/checks/intro-narration.ts`、`npx tsx scripts/checks/tts-legacy-audio.ts`、`node scripts/checks/service-worker.mjs`
  - `python scripts/checks/web-audio-compat.py [--self-test]`、`python scripts/checks/docker-site.py --origin http://localhost:8080`
- Android（`apps/android`，JDK 17，AGP 8.7.3 / Gradle 8.9）：先导出 Web（`apps/web/out` 必须存在），再 `./gradlew testDebugUnitTest lintDebug assembleDebug`。本机未装 Android SDK，构建与 Lint 依赖 GitHub Actions `android.yml`。
- Docker：`docker compose up -d --build`（多阶段：下载资源 → `npm run build` → nginx 托管 `out/`）。

## 架构要点

**数据流**：`output/<subject>/{outlines,quizzes}/`（Python pipeline 产物，入 Git）+ `data/{passages,stories}/`（Release 下载）→ `apps/web/scripts/build-data.ts` → `apps/web/public/data/`（前端 fetch 的 JSON）。TTS 音频路径由文本 hash 推导：`trim + 折叠空白 → sha1 → /audio/<hash[0:2]>/<hash>.opus`，必须与 `scripts/tts/collect_texts.py` 保持一致；仅在音频文件存在时才注入路径。Web 实际播放的是 `web_audio.py` 转出的 MP3。
- bookId 兼容规则：math 用 `g1up` 这类无前缀旧格式（兼容老用户 localStorage 进度），其它学科带前缀 `chinese-g1up` 等。不要“统一”它。

**packages/core（`@cstf/core`）**：纯 TS 域逻辑（SRS、判分、经济/宝石、宝箱、成就、任务、联赛、备份等），无 DOM/Next 依赖，源码直接导出（`main: src/index.ts`，Next 通过 `transpilePackages` 编译）。`apps/web/src/lib/` 中的同名文件多为 `export * from "@cstf/core/..."` 薄封装——改逻辑去 core 改。
- `packages/core/spec/golden-vectors.json` 是 Web 与 iOS（`apps/mobile/.../Domain/*.swift` 逐文件对译 core）共享的黄金向量；经济口径变化必须先改 spec 再同步两端实现。
- `quizContent.test.ts` 直接读取 `output/*/quizzes/*.json` 校验判分，题库内容修改会影响测试。

**Web（`apps/web`）**：Next 15 App Router，`output: "export"` + `trailingSlash: true`，无任何后端；所有学习进度在 Zustand `persist`（`src/store/progress.ts`，key `csf-progress-v1`，带 version/migrate——改状态结构要加迁移，不能破坏旧用户数据）。`public/sw.js` 手写 Service Worker（壳层网络优先、`/data` 与 `/audio` SWR、`/_next/static` 缓存优先），改缓存策略需同步 bump `VERSION` 并跑 `service-worker.mjs` 检查；音频 Range 请求需绕过缓存。

**TV 模式**：`src/lib/tvMode.ts` 通过 UA（Android 壳会追加 `ChinaStudyFreeTV`）、宽屏无 hover 视口或 `?tv=1/0` 开启；`tvNavigation.ts` / `useTvQuestionFocus.ts` 负责遥控器方向键空间焦点导航。

**Android（`apps/android`，Java）**：WebView 壳，从 `https://appassets.androidplatform.net/` 加载内置站点。Gradle `prepareEmbeddedWeb` 把 `apps/web/out` 同步进 `assets/www`，并将 `_next/**` 改放到 `next-static/`（Android assets 不能读以 `_` 开头的目录），运行时由 `EmbeddedAssetPath` 映射回来。`-PwebAppUrl=` 可切远程模式（仅 HTTPS）。`OriginPolicy` 控制可信源；`MediaArchiveImporter`/`MediaLibrary` 实现 U 盘导入官方媒体包（SHA-256 校验、拒绝路径穿越/符号链接）。预生成 MP3 缺失时经受限 JS 接口回退系统 TTS。

**iOS（`apps/mobile`）**：XcodeGen（`project.yml`）+ SwiftUI，需 macOS；与 Web 无代码共享，只共享 core 的语义与黄金向量。

**内容生成 Pipeline（`scripts/quiz|stories|passages|tts`）**：Python，从教材 PDF 经 LLM 生成大纲/题库/故事/配图/TTS，需 `.env` 中的 API Key；日常开发通常不需要运行。

## 文档

`docs/android.md`（Android/TV/U 盘导入）、`docs/docker.md`、`docs/aws-web-deployment.md`（S3 + CloudFront 静态部署，`scripts/deploy/`）、`docs/web-audio-compatibility.md`。

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **ChinaTextbookStudyFree** (8350 symbols, 29755 relationships, 657 execution flows).

> Index stale? Run `node .gitnexus/run.cjs analyze --index-only` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? Bootstrap with `npx`, `bunx`, or `pnpm dlx` — e.g. `bunx gitnexus@latest analyze` (npm 11 npx crash; #1939).

## Always Do

- **MUST run impact before editing.** Use `impact({target: "symbolName", direction: "upstream"})` or `node .gitnexus/run.cjs impact "symbolName" --direction upstream --repo .`; report callers, processes, and risk. Never substitute grep for graph analysis.
- **MUST analyze graph changes before committing.** Use `detect_changes({scope: "all"})` (MCP) or `node .gitnexus/run.cjs detect-changes --scope all --repo .` (CLI fallback). `partial: true` or `truncated: true` is not a clean check — a zero means unseen, not unaffected; re-run it. For regression review: `detect_changes({scope: "compare", base_ref: "main"})` or `node .gitnexus/run.cjs detect-changes --scope compare --base-ref "main" --repo .`.
- MUST warn on HIGH/CRITICAL `risk` pre-edit; never use `riskSharedAxes` to waive a HIGH/CRITICAL `risk` warning. Compare File/symbol: MCP File omits axes; Graph-RAG expands File.
- **MUST treat `risk: UNKNOWN` as unresolved, not as low.** An empty caller set is not evidence the symbol is unused — it can also mean the callers are not resolvable by the index (plain-object property access, dynamic dispatch, cross-language calls). `impact` pairs `UNKNOWN` with a `riskNote` saying so. Confirm with a text search before treating the symbol as safe to change or delete; do not proceed on the strength of a zero.
- **MUST use `query({search_query: "concept"})` for concepts/flows, `context({name: "symbolName"})` for a named symbol, or `impact` for blast radius, on read-only callers, dependencies, imports, or execution flow.** Graph first; text search only for empty/`UNKNOWN`/literals.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER edit a function, class, or method before MCP/CLI impact analysis.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis, and never read `UNKNOWN` as an all-clear — it means the walk could not answer, which is the one verdict that requires confirming by other means.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit before MCP/CLI graph change analysis.

## Resources

| Resource | Use for |
| --- | --- |
| `gitnexus://repo/ChinaTextbookStudyFree/context` | Codebase overview, check index freshness |
| `gitnexus://repo/ChinaTextbookStudyFree/clusters` | All functional areas |
| `gitnexus://repo/ChinaTextbookStudyFree/processes` | All execution flows |
| `gitnexus://repo/ChinaTextbookStudyFree/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
| --- | --- |
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
