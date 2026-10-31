"use client";

/**
 * useRecorder — 轻量麦克风录音 hook。
 *
 * - 浏览器用原生 MediaRecorder；Android 壳内改走 NativeMic 桥（VOICE_RECOGNITION 录音源，
 *   电视的蓝牙遥控器麦克风只对这个录音源开放，getUserMedia 打不开）
 * - 不接 ASR、不上传；每次 stop() 返回一个可直接 new Audio(url) 回放的 URL
 * - 刷新页面即丢（不落磁盘），因此不涉及隐私持久化
 *
 * 用法：
 *   const rec = useRecorder();
 *   if (await rec.start()) { const url = await rec.stop(); }
 */
import { useCallback, useEffect, useRef, useState } from "react";

export type RecorderState = "idle" | "requesting" | "recording" | "error";

declare global {
  interface Window {
    NativeMic?: {
      start(): string;
      stop(): string;
    };
  }
}

const NO_MIC = "没有找到可用的麦克风。电视请用带语音键的遥控器或连接 USB / 蓝牙麦克风后再试。";

function nativeError(reason: string): string {
  if (reason === "permission") return "请在弹出的窗口中允许录音，然后再点一次「跟读」。";
  if (reason === "busy") return "麦克风正被其他应用占用，请稍后再试。";
  return NO_MIC;
}

function browserError(e: unknown): string {
  const name = e instanceof DOMException ? e.name : "";
  if (name === "NotAllowedError" || name === "SecurityError") return "没有录音权限，请在系统设置中允许本应用使用麦克风。";
  if (name === "NotFoundError" || name === "NotReadableError" || name === "OverconstrainedError") return NO_MIC;
  return e instanceof Error ? e.message : String(e);
}

export function useRecorder() {
  const [state, setState] = useState<RecorderState>("idle");
  const [error, setError] = useState<string | null>(null);
  const mediaRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const nativeRef = useRef(false);
  const chunksRef = useRef<Blob[]>([]);
  const stopResolverRef = useRef<((url: string | null) => void) | null>(null);

  const cleanup = useCallback(() => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(t => t.stop());
      streamRef.current = null;
    }
    if (nativeRef.current) {
      nativeRef.current = false;
      window.NativeMic?.stop();
    }
    mediaRef.current = null;
    chunksRef.current = [];
  }, []);

  useEffect(() => cleanup, [cleanup]);

  const fail = useCallback((message: string) => {
    setState("error");
    setError(message);
    cleanup();
    return false;
  }, [cleanup]);

  /** 开始录音；返回是否成功（失败原因见 error）。 */
  const start = useCallback(async (): Promise<boolean> => {
    setError(null);
    if (mediaRef.current || nativeRef.current) return true;
    if (typeof window !== "undefined" && window.NativeMic) {
      const result = window.NativeMic.start();
      if (result !== "ok") return fail(nativeError(result));
      nativeRef.current = true;
      setState("recording");
      return true;
    }
    if (typeof navigator === "undefined" || !navigator.mediaDevices?.getUserMedia) {
      return fail("当前浏览器不支持录音");
    }
    setState("requesting");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;
      const mr = new MediaRecorder(stream);
      chunksRef.current = [];
      mr.ondataavailable = e => {
        if (e.data && e.data.size > 0) chunksRef.current.push(e.data);
      };
      mr.onstop = () => {
        const blob = new Blob(chunksRef.current, {
          type: mr.mimeType || "audio/webm",
        });
        const url = blob.size > 0 ? URL.createObjectURL(blob) : null;
        cleanup();
        setState("idle");
        const resolver = stopResolverRef.current;
        stopResolverRef.current = null;
        resolver?.(url);
      };
      mr.start();
      mediaRef.current = mr;
      setState("recording");
      return true;
    } catch (e) {
      return fail(browserError(e));
    }
  }, [cleanup, fail]);

  const stop = useCallback((): Promise<string | null> => {
    if (nativeRef.current) {
      nativeRef.current = false;
      const url = window.NativeMic?.stop() || null;
      setState("idle");
      return Promise.resolve(url);
    }
    return new Promise(resolve => {
      const mr = mediaRef.current;
      if (!mr || mr.state === "inactive") {
        resolve(null);
        return;
      }
      stopResolverRef.current = resolve;
      mr.stop();
    });
  }, []);

  return { state, error, start, stop };
}
