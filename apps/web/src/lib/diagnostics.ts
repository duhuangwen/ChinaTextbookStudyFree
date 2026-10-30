declare global {
  interface Window {
    /** Android shell bridge; it keeps the last steps so a hung page can still be diagnosed natively. */
    CstfDiag?: { mark(step: string): void };
  }
}

export function diagMark(step: string): void {
  try {
    window.CstfDiag?.mark(step.slice(0, 120));
  } catch {
    // Diagnostics must never break the page.
  }
}

export function describeElement(element: Element | null): string {
  if (!element) return "none";
  const label = element.getAttribute("aria-label") ?? element.textContent ?? "";
  return `${element.tagName.toLowerCase()}:${label.replace(/\s+/g, "").slice(0, 16)}`;
}

let errorsInstalled = false;

export function installErrorMarks(): void {
  if (errorsInstalled) return;
  errorsInstalled = true;
  window.addEventListener("error", event => diagMark(`error:${event.message}`));
  window.addEventListener("unhandledrejection", event => diagMark(`rejection:${String(event.reason)}`));
}
