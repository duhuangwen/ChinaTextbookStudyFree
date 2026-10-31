"use client";

/**
 * Pinyin over Han characters for grade 1–2 Chinese, where children cannot read most characters yet.
 * pinyin-pro (~1 MB of dictionaries) is loaded on demand; text renders plain until it arrives.
 */

import { createContext, Fragment, useContext, useEffect, useState, type ReactNode } from "react";
import type { Question } from "@/types";

const SETTING_KEY = "csf-pinyin-ruby";
const SETTING_EVENT = "csf-pinyin-ruby-change";
const HAN = /[㐀-鿿]+/g;
const PINYIN_LETTER = /[a-zA-Zāáǎàōóǒòēéěèīíǐìūúǔùǖǘǚǜüɑɡ]/;
const PRONUNCIATION_TOPIC = /拼音|读音|音节|声母|韵母|声调|注音|多音|整体认读|三拼|两拼|拼读/;

const RubyEnabled = createContext(false);

type PinyinFn = typeof import("pinyin-pro").pinyin;
let pinyinFn: PinyinFn | null = null;
let loading: Promise<PinyinFn> | null = null;

function loadPinyin(): Promise<PinyinFn> {
  loading ??= import("pinyin-pro").then(m => (pinyinFn = m.pinyin));
  return loading;
}

function readSetting(): boolean {
  try {
    return window.localStorage.getItem(SETTING_KEY) !== "off";
  } catch {
    return true;
  }
}

export function usePinyinRubySetting(): [boolean, (on: boolean) => void] {
  const [on, setOn] = useState(true);
  useEffect(() => {
    setOn(readSetting());
    const sync = () => setOn(readSetting());
    window.addEventListener(SETTING_EVENT, sync);
    return () => window.removeEventListener(SETTING_EVENT, sync);
  }, []);
  const update = (next: boolean) => {
    try {
      window.localStorage.setItem(SETTING_KEY, next ? "on" : "off");
    } catch {}
    window.dispatchEvent(new Event(SETTING_EVENT));
  };
  return [on, update];
}

/** Grades 1–2 of every Chinese-language subject get pinyin: `chinese-g1up…`, math `g1up…`, `science-g1up…`. */
export function isLowerGradePinyin(id: string | undefined): boolean {
  return !!id && /^(chinese-|science-)?g[12](up|down)\b/.test(id);
}

export function isChineseContent(id: string | undefined): boolean {
  return !!id && id.startsWith("chinese-");
}

/** Annotating a question that tests pronunciation would print its answer. */
export function revealsPronunciation(question: Question): boolean {
  if (PRONUNCIATION_TOPIC.test(question.knowledge_point ?? "")) return true;
  return [question.question, ...(question.options ?? [])].some(text => PINYIN_LETTER.test(text));
}

export function PinyinRubyScope({ contentId, children }: { contentId: string | undefined; children: ReactNode }) {
  const [setting] = usePinyinRubySetting();
  return <RubyEnabled.Provider value={setting && isLowerGradePinyin(contentId)}>{children}</RubyEnabled.Provider>;
}

/**
 * Chinese content: school-style ɑ/ɡ glyphs for Latin letters (.zh-content), plus pinyin for grades 1–2.
 * Lower-grade math and science get the pinyin only; their Latin letters are units and variables.
 * `display: contents` keeps the wrapper out of the page layout.
 */
export function ChineseContent({ contentId, children }: { contentId: string | undefined; children: ReactNode }) {
  if (!isChineseContent(contentId)) return <PinyinRubyScope contentId={contentId}>{children}</PinyinRubyScope>;
  return (
    <div className="zh-content" style={{ display: "contents" }}>
      <PinyinRubyScope contentId={contentId}>{children}</PinyinRubyScope>
    </div>
  );
}

export function PinyinRubyOff({ off, children }: { off: boolean; children: ReactNode }) {
  const enabled = useContext(RubyEnabled);
  return <RubyEnabled.Provider value={enabled && !off}>{children}</RubyEnabled.Provider>;
}

export function usePinyinRubyEnabled(): boolean {
  return useContext(RubyEnabled);
}

/** Plain text that gains pinyin only inside an enabled scope. */
export function PinyinText({ text }: { text: string }) {
  return useContext(RubyEnabled) ? <RubyText text={text} /> : <>{text}</>;
}

export function RubyText({ text }: { text: string }) {
  const [fn, setFn] = useState<PinyinFn | null>(pinyinFn);
  useEffect(() => {
    if (!fn) void loadPinyin().then(f => setFn(() => f));
  }, [fn]);
  if (!fn) return <>{text}</>;

  const nodes: ReactNode[] = [];
  let last = 0;
  for (const match of text.matchAll(HAN)) {
    const start = match.index ?? 0;
    if (start > last) nodes.push(text.slice(last, start));
    // Whole runs keep word context, which settles most polyphonic characters.
    const readings = fn(match[0], { type: "array", toneType: "symbol" });
    [...match[0]].forEach((ch, i) =>
      nodes.push(
        <ruby key={`${start}-${i}`} className="pinyin-ruby">
          {ch}
          <rt>{readings[i]}</rt>
        </ruby>,
      ),
    );
    last = start + match[0].length;
  }
  if (last < text.length) nodes.push(text.slice(last));
  return <>{nodes.map((n, i) => <Fragment key={i}>{n}</Fragment>)}</>;
}
