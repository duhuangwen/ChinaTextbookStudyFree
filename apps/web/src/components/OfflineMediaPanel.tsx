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

export function OfflineMediaPanel() {
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

  if (!available) return null;

  return (
    <section
      className="mb-6 rounded-3xl border-2 border-secondary/35 bg-white p-5"
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
          type="button"
          data-tv-autofocus="true"
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
