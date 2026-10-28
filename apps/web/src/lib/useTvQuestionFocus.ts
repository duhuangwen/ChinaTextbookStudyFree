"use client";

import { useEffect, useRef } from "react";

/**
 * When a question mounts, put D-pad focus on its first answer control.
 * Desktop and touch layouts keep their existing focus behavior.
 */
export function useTvQuestionFocus<T extends HTMLElement>(questionId: string | number) {
  const ref = useRef<T>(null);

  useEffect(() => {
    if (!document.documentElement.classList.contains("tv-mode")) return;
    const frame = requestAnimationFrame(() => {
      const element = ref.current;
      if (element?.isConnected && !element.hasAttribute("disabled")) {
        element.focus({ preventScroll: true });
      }
    });
    return () => cancelAnimationFrame(frame);
  }, [questionId]);

  return ref;
}
