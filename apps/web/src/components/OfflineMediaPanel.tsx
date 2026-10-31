"use client";

import { useEffect, useState } from "react";
import { BookOpen, Lightning } from "@/components/icons";
import { playSfx } from "@/lib/sfx";

declare global {
  interface Window {
    MediaImport?: {
      open(): boolean;
      status(): string;
      clear(): boolean;
    };
  }
}

interface MediaStatus {
  audio: boolean;
  images: boolean;
  pages: boolean;
  complete: boolean;
}

const EMPTY: MediaStatus = {
  audio: false,
  images: false,
  pages: false,
  complete: false,
};

function readStatus(): MediaStatus {
  try {
    const value = window.MediaImport?.status();
    return value ? { ...EMPTY, ...JSON.parse(value) } : EMPTY;
  } catch {
    return EMPTY;
  }
}

const IMPORT_BUTTON_ID = "offline-media-import";

function useOfflineMediaStatus() {
  const [available, setAvailable] = useState(false);
  const [status, setStatus] = useState<MediaStatus>(EMPTY);

  useEffect(() => {
    const bridge = window.MediaImport;
    if (!bridge) return;
    setAvailable(true);
    setStatus(readStatus());
    const timer = window.setInterval(() => setStatus(readStatus()), 2000);
    return () => window.clearInterval(timer);
  }, []);

  return { available, status };
}

/** One-line reminder at the top of the profile; the full panel lives with the other settings. */
export function OfflineMediaHint() {
  const { available, status } = useOfflineMediaStatus();
  if (!available || status.complete) return null;
  const missing = [
    !status.audio && "语音",
    !status.pages && "课本页",
    !status.images && "故事图",
  ].filter(Boolean).join("、");

  return (
    <div className="flex items-center gap-3 rounded-2xl border-2 border-secondary/35 bg-secondary/5 px-4 py-2.5">
      <BookOpen className="w-5 h-5 text-secondary shrink-0" />
      <div className="flex-1 min-w-0 text-sm font-bold text-ink-light">还没导入{missing}</div>
      <button
        type="button"
        data-tv-autofocus="true"
        onClick={() => {
          playSfx("tap");
          const target = document.getElementById(IMPORT_BUTTON_ID);
          target?.scrollIntoView({ block: "center" });
          target?.focus({ preventScroll: true });
        }}
        className="h-9 px-4 shrink-0 rounded-2xl text-sm font-extrabold text-secondary-dark border-2 border-secondary/40 bg-white"
      >
        去导入
      </button>
    </div>
  );
}

export function OfflineMediaPanel({ className = "" }: { className?: string }) {
  const { available, status } = useOfflineMediaStatus();

  if (!available) return null;

  return (
    <section
      className={`rounded-3xl border-2 border-secondary/35 bg-white p-5 ${className}`}
      style={{ boxShadow: "0 4px 0 0 var(--shadow-card-color)" }}
      aria-label="离线媒体资源"
    >
      <div className="flex items-start gap-4">
        <div className="w-12 h-12 shrink-0 rounded-2xl bg-secondary/15 text-secondary flex items-center justify-center">
          <BookOpen className="w-7 h-7" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-lg font-extrabold text-ink">离线课本与语音</div>
          <div className="mt-1 text-sm text-ink-light leading-relaxed">
            从 U 盘选择官方音频、课本页和故事图压缩包。导入后可拔掉 U 盘。
          </div>
          <div className="mt-3 flex flex-wrap gap-2 text-xs font-extrabold">
            <span className={status.audio ? "text-primary" : "text-ink-softer"}>
              {status.audio ? "✓ 语音" : "未导入语音"}
            </span>
            <span className={status.pages ? "text-primary" : "text-ink-softer"}>
              {status.pages ? "✓ 课本页" : "未导入课本页"}
            </span>
            <span className={status.images ? "text-primary" : "text-ink-softer"}>
              {status.images ? "✓ 故事图" : "未导入故事图"}
            </span>
          </div>
        </div>
        <Lightning className="w-6 h-6 text-warning shrink-0" />
      </div>
      <div className="mt-4 flex flex-wrap gap-3">
        <button
          id={IMPORT_BUTTON_ID}
          type="button"
          onClick={() => {
            playSfx("tap");
            window.MediaImport?.open();
          }}
          className="btn-chunky-secondary px-5"
        >
          {status.audio || status.pages || status.images ? "继续导入" : "从 U 盘导入"}
        </button>
        {(status.audio || status.pages || status.images) && (
          <button
            type="button"
            onClick={() => {
              playSfx("tap");
              window.MediaImport?.clear();
            }}
            className="btn-chunky-ghost px-5"
          >
            删除离线资源
          </button>
        )}
      </div>
    </section>
  );
}
