interface MarkedApi {
  (markdown: string, options?: object): string;
  parse(markdown: string, options?: object): string;
  use(...extensions: object[]): void;
}

interface DomPurifyApi {
  sanitize(dirty: string, config?: object): string;
  addHook(name: string, hook: (node: Element, data?: unknown) => void): void;
}

interface HighlightApi {
  highlight(code: string, options: { language: string; ignoreIllegals?: boolean }): { value: string };
  getLanguage(name: string): unknown;
  highlightElement(element: Element): void;
}

type CcMethod = (payload?: unknown) => void;

interface CcHost {
  [method: string]: CcMethod | undefined;
}

type Child = Node | string | number | boolean | null | undefined | Child[];

interface HProps {
  [key: string]: unknown;
}

interface PickItem {
  value: string;
  label: string;
  checked?: boolean;
}

interface PickMenu {
  menu: HTMLElement;
  sync(): void;
  toggle(): void;
  close(): void;
}

interface DurationMenuOptions {
  anchor: HTMLElement;
  home: HTMLElement;
  label?: string;
  watch?: () => Node | null;
  onPick: (value: string) => void;
}

interface PickMenuOptions extends DurationMenuOptions {
  items?: PickItem[];
  checkable?: boolean;
  checkedOf?: (value: string) => boolean;
  menuClass?: string;
  itemClass?: string;
}

interface CcElements {
  app: HTMLElement | null;
  conversation: HTMLElement | null;
  permissions: HTMLElement | null;
  composer: HTMLElement | null;
  palette: HTMLElement | null;
  a11yStatus: HTMLElement | null;
}

interface DiagramAction {
  label: string;
  onClick: () => void;
}

interface DiagramNode {
  id?: string | number | null;
  label?: unknown;
  meta?: unknown;
  action?: DiagramAction | null;
  children?: (DiagramNode | null | undefined)[] | null;
  kind?: string | null;
  status?: string | null;
  selected?: boolean;
  title?: string | null;
  name?: string | null;
  onPick?: (ev: MouseEvent) => void;
  running?: boolean;
}

interface PanView extends HTMLDivElement {
  __fit?: () => void;
}

interface TranscriptEntry {
  id?: string | number | null;
  speaker?: string;
  text?: string | null;
  meta?: string | null;
  state?: string | null;
  toolUseId?: string | null;
  open?: boolean;
  command?: string | null;
  filePath?: string | null;
  title?: string | null;
  message?: string | null;
  elapsed?: number | null;
  reviewable?: boolean;
  parent?: string | null;
  order?: number | null;
  blockedRule?: string | null;
  bypassedRule?: string | null;
  bypassAction?: string | null;
}

interface BodyEl extends HTMLElement {
  __rawText?: string;
}

interface FlashEl extends HTMLElement {
  __ccFlashLabel?: string | null;
  __ccFlashTimer?: ReturnType<typeof setTimeout> | null;
}

interface RowEl extends HTMLElement {
  __outNode?: HTMLElement | null;
  __toolUseId?: string | null;
  __isAgentCard?: boolean;
  __cmdNode?: HTMLElement | null;
  __msgNode?: HTMLElement | null;
  __nameNode?: HTMLElement | null;
  __childrenNode?: HTMLElement | null;
  __elapsedNode?: HTMLElement | null;
  __filePath?: string | null;
  __diffBtn?: HTMLElement | null;
  __restoreBtn?: HTMLElement | null;
  __label?: HTMLElement | null;
  __order?: number | null;
  __autoOpenedOnError?: boolean;
}

interface RowRec {
  el: RowEl;
  bodyNode: BodyEl | null;
  kind: string;
  outNode?: HTMLElement | null;
  speaker?: string;
  toolUseId?: string | null;
  text?: string | null;
  meta?: string | null;
  state?: string | null;
}

interface LinkHit {
  token?: unknown;
  path?: unknown;
  line?: unknown;
}

interface TranscriptNs {
  el(tag: string, props?: HProps | null): HTMLElement;
  safeSend(obj: unknown): void;
  conversationEl(): HTMLElement | null;
  rows: Map<unknown, RowRec>;
  toolCards: Map<string, RowEl>;
  setBody(rec: RowRec, text: unknown): void;
  createRow(entry: TranscriptEntry, cards?: Map<string, RowEl>): RowRec;
  updateRow(rec: RowRec, entry: TranscriptEntry, links?: boolean): void;
  builderFor(speaker: string | undefined, entry: TranscriptEntry): RowRec;
  buildBlockNotice(rule: unknown, command: unknown): RowRec;
  buildBypassNotice(entry: TranscriptEntry): RowRec;
  buildTool(entry: TranscriptEntry | null | undefined): RowRec;
  toolIconSvg(meta: unknown): string;
  applyToolElapsed(node: RowEl, state: string | null | undefined, elapsedSecs: unknown): void;
  applyToolState(node: RowEl, state: string | null | undefined, meta: string | null | undefined): void;
  routeToolOutput(entry: TranscriptEntry, cards?: Map<string, RowEl>): boolean;
  renderCommandBlock(cmdNode: HTMLElement | null | undefined, commandText: unknown): void;
  jbHref(relPath: unknown, line?: unknown): string;
  renderToolLabel(nameEl: HTMLElement | null, text: unknown, filePath: unknown): void;
  requestLinks(rec: RowRec, entry: TranscriptEntry): void;
  runSearch(q: string | null | undefined, silent: boolean): void;
  refreshSearch(): void;
  resetSearch(): void;
  findNext(): void;
  findPrev(): void;
  hitCount(): number;
  activeHit(): number;
  updateFindCount(): void;
  resetFindBar(): void;
  scheduleScroll(stick: boolean): void;
  stickToBottom(): boolean;
  [name: string]: unknown;
}

interface PillOption {
  id?: string;
  value?: string | null;
  wire?: string;
  on?: boolean;
  label?: unknown;
  selected?: boolean;
  group?: string;
}

interface PillField {
  label?: unknown;
  id?: unknown;
  options?: PillOption[];
}

interface PillDef {
  key: string;
  field: string;
  idKey: string;
  msg(o: PillOption): unknown;
}

interface Pill {
  el: HTMLElement;
  label: HTMLElement;
  def: PillDef;
  icon: HTMLElement | null;
}

interface UsageWindow {
  key?: string;
  label?: string;
  pct?: number | null;
  resetsAt?: string | null;
}

interface ComposerState {
  running?: boolean;
  starting?: boolean;
  resuming?: boolean;
  binaryMissing?: boolean;
  needsLogin?: boolean;
  turnActive?: boolean;
  interrupting?: boolean;
  guardOn?: boolean;
  remoteControlOn?: boolean;
  remoteControlError?: string | null;
  queue?: unknown[];
  suggestion?: unknown;
  thinkingStatus?: string | null;
  context?: { pct?: number } | null;
  tokensOut?: number;
  reasoningTokens?: number;
  costUsd?: number;
  usage?: UsageWindow[];
  [field: string]: unknown;
}

interface ComposerEls {
  card: HTMLElement;
  input: HTMLTextAreaElement;
  send: HTMLElement;
  pills: Record<string, Pill>;
  queue: HTMLElement;
  ghost: HTMLElement;
  readout: HTMLElement;
  usageBars: HTMLElement;
  attachments: HTMLElement;
  attachBtn: HTMLElement;
}

interface OverflowMetrics {
  available: number;
  overflowing: boolean;
  ends: number[];
  reserved: number;
  toggle: number;
}

interface OverflowPlan {
  visible: number;
  toggle: boolean;
}

interface OverflowOptions {
  row: HTMLElement;
  label: string;
  items(): HTMLElement[];
  reserved?(): HTMLElement[];
  place(btn: HTMLElement): void;
  activate?(el: HTMLElement, anchor: HTMLElement): boolean;
}

interface OverflowApi {
  update(force?: boolean): void;
  close(returnFocus: boolean): void;
  toggle: HTMLElement;
  collected(): HTMLElement[];
}

interface OverflowRow extends HTMLElement {
  __ccOverflow?: OverflowApi;
}

interface OpenMenu {
  el: HTMLElement;
  pill: string;
  anchor: HTMLElement;
  sig?: string;
}

interface Roving {
  set(row: HTMLElement | null | undefined): void;
  focus(row: HTMLElement | null | undefined): void;
  step(delta: number): void;
}

interface RecentFile {
  name?: string;
  path?: string;
  ext?: string;
}

interface AttachData {
  recent: RecentFile[];
  hasSelection: boolean;
  hasFile: boolean;
}

interface TreeEntry {
  name?: string;
  path: string;
  directory?: boolean;
}

interface TreeDir {
  entries: TreeEntry[] | null;
  pending: boolean;
  truncated: boolean;
}

interface TreeState {
  mode: string;
  multi: boolean;
  query: string;
  dirs: Record<string, TreeDir>;
  open: Record<string, boolean>;
  sel: Record<string, boolean>;
  exp: Record<string, string[]>;
  capped: Record<string, boolean>;
}

interface TreeRow extends HTMLElement {
  __ccPath?: string;
  __ccDir?: boolean;
}

interface Attachment {
  id?: unknown;
  kind?: unknown;
  label?: unknown;
}

interface AttachNs {
  data: AttachData;
  view: string;
  tree: TreeState | null;
  attIconGlyph(kind: string): string;
  folderGlyph(): string;
  fileIconGlyph(ext: string | undefined): string;
  extOf(name: unknown): string;
  isText(v: unknown): v is string;
  menuEl(): HTMLElement | null;
  bodyEl(): HTMLElement | null;
  reposition(): void;
  renderMenu(menu: HTMLElement, from?: string | null, focusSearch?: boolean): void;
  buildRootView(body: HTMLElement): void;
  enterTree(mode: string): void;
  leaveTree(): void;
  requestChildren(path: string): void;
  matchesQuery(entry: TreeEntry): boolean;
  hasMatch(path: string): boolean;
  openFor(path: string): boolean;
  visibleEntry(entry: TreeEntry): boolean;
  resetMatches(): void;
  buildTreeView(body: HTMLElement): void;
  doneButton(): HTMLElement;
  setMulti(on: boolean): void;
  renderTree(): void;
  treeEl(): HTMLElement | null;
  selectedCount(): number;
  confirmSelection(): void;
  dirState(path: string): string;
  applyRowState(row: HTMLElement, entry: TreeEntry): void;
  syncSelection(): void;
  markPaths(paths: string[], on: boolean): void;
  onRowPress(entry: TreeEntry, e: MouseEvent): void;
  setOpen(path: string, on: boolean): void;
  announceCap(entry: TreeEntry): void;
  rowByPath(path: string): TreeRow | null;
  visibleRows(): TreeRow[];
  rows: Roving;
  onMenuKey(e: KeyboardEvent): void;
  attachImageFile(file: File | null | undefined): void;
  isImageFile(f: File | null | undefined): boolean;
}

interface PaletteCommand {
  name?: unknown;
  description?: unknown;
}

interface PaletteItem {
  name: string;
  description: string;
  score: number;
}

interface PaletteState {
  items: PaletteItem[];
  active: number;
  navigated: boolean;
}

interface PaletteEl extends HTMLElement {
  __built?: boolean;
  __list?: HTMLElement;
}

interface PaletteNs {
  state: PaletteState;
  queryOf(value: unknown): string | null;
  setCommands(list: PaletteCommand[]): void;
  rank(q: string): PaletteItem[];
  composerInput(): HTMLTextAreaElement | null;
  paletteEl(): PaletteEl | null;
  isOpen(): boolean;
  ensureBuilt(): PaletteEl | null;
  linkInput(open: boolean): void;
  syncActiveDescendant(): void;
  renderList(onPick: (idx: number) => void): void;
  updateActiveClass(): void;
}

interface SettingItem {
  key: unknown;
  label?: unknown;
  group?: unknown;
  sub?: unknown;
  type?: unknown;
  on?: boolean;
  hostOwned?: boolean;
  deferred?: boolean;
}

interface SettingsPanel {
  group: string;
  title: string;
  path: string;
  rows: SettingItem[];
  subs: SettingsSub[];
  list: SettingItem[];
}

interface SettingsSub {
  name: string;
  list: SettingItem[];
}

interface SettingsGroup extends SettingsSub {
  direct: SettingItem[];
  subs: SettingsSub[];
}

interface SettingsRow extends HTMLElement {
  __ccKey?: string;
  __ccGroup?: string;
  __ccFocusId?: string;
}

interface SettingsNs {
  SEP: string;
  payload: { items?: unknown } | null;
  view: string | null;
  items(): SettingItem[];
  labelOf(it: SettingItem): string;
  groupOf(it: SettingItem): string;
  isRadio(it: SettingItem): boolean;
  groups(): SettingsGroup[];
  panelFor(path: string): SettingsPanel | null;
  structureSig(): string;
  allRows(): SettingsRow[];
  applyState(row: HTMLElement, on: boolean): void;
  enterGroup(path: string): void;
  leaveGroup(): void;
  close(returnFocus: boolean): void;
  buildBody(): DocumentFragment;
}

interface InstallMethod {
  id: string;
  label?: string;
  shell?: string;
  display: string;
}

interface AuthCard extends HTMLElement {
  __url?: string | null;
}

interface WiredEl extends HTMLElement {
  __wired?: boolean;
}

interface SessionPayload {
  model?: unknown;
  cwd?: unknown;
  home?: unknown;
  account?: { email?: unknown; org?: unknown; plan?: unknown; provider?: unknown } | null;
  [name: string]: unknown;
}

interface DashNs {
  lastSession?(): SessionPayload | null;
  [name: string]: unknown;
}

interface ComposerNs {
  h(tag: string, props?: HProps | null, ...children: Child[]): HTMLElement;
  send(obj: unknown): void;
  els: ComposerEls | null;
  lastState: ComposerState | null;
  hostClipboard: boolean;
  roving(rows: () => HTMLElement[]): Roving;
  overflowFit(m: OverflowMetrics | null | undefined): OverflowPlan;
  overflowMeasure(
    row: HTMLElement,
    items: HTMLElement[],
    reserved: HTMLElement[],
    toggle: HTMLElement | null
  ): OverflowMetrics;
  overflowLabel(el: HTMLElement): string;
  dotsGlyph(): string;
  createOverflow(opts: OverflowOptions): OverflowApi | null;
  refreshOverflow(force?: boolean): void;
  openMenu: OpenMenu | null;
  menuSig(def: PillDef): string;
  togglePillMenu(def: PillDef, anchorEl: HTMLElement): void;
  positionMenu(menu: HTMLElement, anchor: HTMLElement): void;
  closeMenu(): void;
  PILL_DEFS: PillDef[];
  buildPill(def: PillDef): Pill;
  renderPills(s: ComposerState): void;
  syncOpenMenu(): void;
  attach: AttachNs;
  attachGlyph(): string;
  toggleAttachMenu(anchorEl: HTMLElement): void;
  renderAttachments(): void;
  wireImageDrop(card: HTMLElement | null): void;
  insertAtCursor(input: HTMLInputElement | HTMLTextAreaElement, text: string): void;
  wireImagePaste(input: HTMLTextAreaElement | null): void;
  renderReadout(s: ComposerState): void;
  renderMini(): void;
  palette: PaletteNs;
  openPalette(): void;
  setCommands(list: PaletteCommand[]): void;
  renderBoot(s: ComposerState): void;
  setInstallMethods(methods: InstallMethod[]): void;
  authWanted(s: ComposerState): boolean;
  renderAuth(s: ComposerState): void;
  buildActionRows(): HTMLElement | null;
  viewsRow(): HTMLElement | null;
  mountSettingsButton(): HTMLElement | null;
  settings: SettingsNs;
  ensureBuilt(): boolean;
  autosize(input: HTMLTextAreaElement | HTMLInputElement | null): void;
  setGuardOn(on: boolean | undefined): void;
  setRemoteControlOn(on: boolean | undefined, error: unknown): void;
  buildToggles(barRight: HTMLElement): {
    follow: HTMLElement;
    guard: HTMLElement;
    rc: HTMLElement;
    vibe: HTMLElement;
  };
  applyFollow(): void;
  sendGlyph(): string;
  wireInput(input: HTMLTextAreaElement): void;
  onSendClick(e: Event): void;
  setGhost(text: string): void;
  renderGhost(): void;
  renderQueue(queue: unknown): void;
  renderSendMode(s: ComposerState): void;
  announceTurnState(s: ComposerState): void;
  [name: string]: unknown;
}

interface CcShared {
  send(obj: unknown): void;
  escape(s: unknown): string;
  h(tag: string, props?: HProps | null, ...children: Child[]): HTMLElement;
  resetInShort(iso: string | null | undefined): string | null;
  resetIn(iso: string | null | undefined): string | null;
  on(event: string, fn: (...args: unknown[]) => void): () => void;
  emit(event: string, ...args: unknown[]): void;
  els: CcElements;
  announce(message: unknown): void;
  coverTranscript(owner: string, covered: boolean): void;
  placeMenu(menu: HTMLElement, anchor: HTMLElement): void;
  GUARD_DURATIONS: { token: string; label: string }[];
  durationMenu(opts: DurationMenuOptions): PickMenu;
  pickMenu(opts: PickMenuOptions): PickMenu;
  flashCopied(el: HTMLElement): void;
  selfCheck(): void;
  diagnostics(): void;
  markdown(text: unknown): string;
  decorateOneCodeBlock(code: Element): void;
  languageForPath(path: unknown): string | null;
  diagramLabel(kind: string | null | undefined, depth: number, label: unknown): string;
  diagramShown(kind: string | null | undefined, depth: number, label: unknown): string;
  diagram(roots: unknown): HTMLElement | null;
  panView(canvas: HTMLElement, label?: string | null, key?: string | null): PanView;
  applyTheme(vars: unknown): void;
  __themeVars?: Record<string, string>;
  reducedMotion?: boolean;
  isVibe(): boolean;
  nyanSvg(): string;
  transcript: TranscriptNs;
  composer: ComposerNs;
  dash?: DashNs;
  gitChatActive?(): boolean;
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
  marked?: MarkedApi;
  DOMPurify?: DomPurifyApi;
  hljs?: HighlightApi;
}
