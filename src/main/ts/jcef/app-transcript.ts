(function () {
  'use strict';

  const cc = (window.cc = window.cc || {});
  const CC = (window.CC = window.CC || ({} as CcShared));
  const TX = (CC.transcript = CC.transcript || ({} as TranscriptNs));

  const conversationEl = TX.conversationEl;
  const rows = TX.rows;
  const toolCards = TX.toolCards;
  const setBody = TX.setBody;

  function emptyEl(): HTMLElement | null {
    return document.getElementById('empty');
  }

  function createRow(entry: TranscriptEntry, cards?: Map<string, RowEl>): RowRec {
    const known = cards || toolCards;
    const rec = TX.builderFor(entry.speaker, entry);
    rec.speaker = entry.speaker;
    rec.toolUseId = entry.toolUseId || null;
    if (entry.speaker === 'TOOL' && entry.toolUseId) {
      rec.outNode = rec.el.__outNode || rec.el.querySelector<HTMLElement>('.tool-out');
      rec.el.__toolUseId = entry.toolUseId;
      known.set(entry.toolUseId, rec.el);
      if (entry.meta === 'Task' || entry.meta === 'Agent') {
        rec.el.__isAgentCard = true;
        rec.el.classList.add('agent-link');
      }
      if (entry.open) {
        rec.el.classList.add('open');
      }
    }
    if (entry.speaker === 'TOOL') {
      const icNode = rec.el.querySelector('.ic');
      if (icNode) {
        icNode.innerHTML = TX.toolIconSvg(entry.meta);
      }
      if (entry.command) {
        TX.renderCommandBlock(rec.el.__cmdNode, entry.command);
        rec.el.classList.add('cmd-tool');
      }
      rec.el.__filePath = entry.filePath || null;
    }
    return rec;
  }

  function updateRow(rec: RowRec, entry: TranscriptEntry, links?: boolean): void {
    if (rec.speaker === 'TOOL' && entry.title) {
      setBody(rec, entry.title);
    } else if (rec.speaker === 'TOOL' && entry.command) {
      setBody(rec, entry.meta || entry.text);
    } else if (rec.speaker === 'TOOL' && entry.filePath) {
      TX.renderToolLabel(rec.bodyNode, entry.text, entry.filePath);
    } else {
      setBody(rec, entry.text);
      if (links !== false && rec.speaker === 'ASSISTANT' && entry.state !== 'RUNNING') {
        TX.requestLinks(rec, entry);
      }
    }
    rec.text = entry.text;
    rec.meta = entry.meta;
    rec.state = entry.state;
    if (rec.speaker === 'TOOL') {
      TX.applyToolState(rec.el, entry.state, entry.meta);
      TX.applyToolElapsed(rec.el, entry.state, entry.elapsed);
      if (rec.el.__diffBtn) {
        rec.el.__diffBtn.hidden = !entry.reviewable;
      }
      if (rec.el.__restoreBtn) {
        rec.el.__restoreBtn.hidden = !entry.reviewable;
      }
    }
    if (rec.speaker === 'MEMORY' && rec.el.__label) {
      const title = entry.meta && String(entry.meta).trim() ? String(entry.meta) : '🧠 Recalled memories';
      rec.el.__label.textContent = title;
    }
  }

  TX.createRow = createRow;
  TX.updateRow = updateRow;

  function upsert(entry: TranscriptEntry | null | undefined): RowRec | null {
    if (entry == null || entry.id == null) {
      return null;
    }

    if (entry.speaker === 'TOOL_OUTPUT') {
      if (TX.routeToolOutput(entry)) {
        return rows.get(entry.id) || null;
      }
    }

    let rec = rows.get(entry.id) || null;
    if (rec && rec.speaker !== entry.speaker) {
      if (rec.el && rec.el.parentNode) {
        rec.el.parentNode.removeChild(rec.el);
      }
      if (rec.toolUseId) {
        toolCards.delete(rec.toolUseId);
      }
      rows.delete(entry.id);
      rec = null;
    }
    if (!rec) {
      rec = createRow(entry);
      rows.set(entry.id, rec);
    }
    updateRow(rec, entry);
    return rec;
  }

  function containerFor(entry: TranscriptEntry): HTMLElement | null {
    if (entry.parent) {
      const parentCard = toolCards.get(entry.parent);
      if (parentCard) {
        return (
          parentCard.__childrenNode ||
          parentCard.querySelector<HTMLElement>('.tool-children') ||
          conversationEl()
        );
      }
    }
    return conversationEl();
  }

  function reposition(entry: TranscriptEntry): void {
    const rec = rows.get(entry.id);
    if (!rec || !rec.el) {
      return;
    }
    const order = entry.order;
    rec.el.__order = typeof order === 'number' && order >= 0 ? order : null;
    const container = containerFor(entry);
    if (!container) {
      return;
    }

    let ref: RowEl | null = null;
    if (rec.el.__order != null) {
      const kids = container.children;
      for (let i = 0; i < kids.length; i++) {
        const k = kids[i] as RowEl;
        if (k === rec.el) {
          continue;
        }
        if (k.__order == null) {
          continue;
        }
        if (k.__order > rec.el.__order) {
          ref = k;
          break;
        }
      }
    }
    if (rec.el.parentNode === container && rec.el.nextSibling === ref) {
      return;
    }
    if (ref) {
      container.insertBefore(rec.el, ref);
    } else {
      container.appendChild(rec.el);
    }
  }

  function showEmptyState(show: boolean): void {
    const empty = emptyEl();
    if (empty) {
      empty.hidden = !show;
    }
  }

  cc.batch = function (input?: unknown): void {
    if (!input) {
      return;
    }
    let entries: TranscriptEntry[];
    if (Array.isArray(input)) {
      entries = input as TranscriptEntry[];
    } else {
      const wrapped = input as { entries?: unknown };
      entries = Array.isArray(wrapped.entries)
        ? (wrapped.entries as TranscriptEntry[])
        : [input as TranscriptEntry];
    }
    const c = conversationEl();
    const stick = TX.stickToBottom();

    for (let i = 0; i < entries.length; i++) {
      upsert(entries[i]);
    }
    for (let j = 0; j < entries.length; j++) {
      const e = entries[j];
      if (e && e.id != null && e.speaker !== 'TOOL_OUTPUT') {
        reposition(e);
      } else if (e && e.id != null && e.speaker === 'TOOL_OUTPUT' && rows.has(e.id)) {
        reposition(e);
      }
    }

    if (rows.size > 0 || (c && c.children.length > 0)) {
      showEmptyState(false);
    }

    TX.refreshSearch();

    TX.scheduleScroll(stick);
  };

  cc.clear = function (): void {
    rows.clear();
    toolCards.clear();
    const c = conversationEl();
    if (c) {
      const kids = Array.prototype.slice.call(c.children) as Element[];
      for (let i = 0; i < kids.length; i++) {
        if (kids[i].id === 'empty') {
          continue;
        }
        c.removeChild(kids[i]);
      }
    }
    TX.resetSearch();
    showEmptyState(true);
  };
})();
