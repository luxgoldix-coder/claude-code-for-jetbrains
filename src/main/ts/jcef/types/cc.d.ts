interface MarkedApi {
  parse(markdown: string, options?: object): string;
  use(...extensions: object[]): void;
}

interface DomPurifyApi {
  sanitize(dirty: string, config?: object): string;
  addHook(name: string, hook: (node: Element, data?: unknown) => void): void;
}

interface HighlightApi {
  highlight(code: string, options: { language: string }): { value: string };
  getLanguage(name: string): unknown;
  highlightElement(element: Element): void;
}

type CcMethod = (payload?: unknown) => void;

interface CcHost {
  [method: string]: CcMethod | undefined;
}

interface CcShared {
  [name: string]: unknown;
}

declare var marked: MarkedApi;
declare var DOMPurify: DomPurifyApi;
declare var hljs: HighlightApi;
declare var cc: CcHost;
declare var CC: CcShared;

interface Window {
  cc: CcHost;
  CC: CcShared;
  __ccSend?: (json: string) => void;
}
