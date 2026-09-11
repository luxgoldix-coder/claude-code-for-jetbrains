(function () {
  'use strict';

  const CC = (window.CC = window.CC || ({} as CcShared));
  const TX = (CC.transcript = CC.transcript || ({} as TranscriptNs));

  const el = TX.el;

  type Row = Record<string, unknown>;

  const POSITION = ['line', 'column'];

  function isRow(value: unknown): value is Row {
    return !!value && typeof value === 'object' && !Array.isArray(value);
  }

  function uniformKeys(list: unknown[]): string[] | null {
    if (list.length === 0 || !list.every(isRow)) return null;
    const keys = Object.keys(list[0] as Row);
    for (let i = 1; i < list.length; i++) {
      const own = Object.keys(list[i] as Row);
      if (own.length !== keys.length || own.some((k, at) => k !== keys[at])) return null;
    }
    return keys;
  }

  function scalar(value: unknown): string {
    if (value === true) return '✓';
    if (value === false) return '—';
    if (value == null) return '';
    return String(value);
  }

  function fileLink(file: string, line: unknown): HTMLElement {
    const text = line ? file + ':' + line : file;
    const a = el('a', { class: 'jb-link', text: text, attrs: { href: TX.jbHref(file, line), title: 'Open ' + text } });
    a.addEventListener('click', function (e) {
      e.stopPropagation();
    });
    return a;
  }

  function cell(row: Row, key: string, tag: string): HTMLElement {
    const value = row[key];
    const node = el(tag, { class: 'toon-' + key });
    if (key === 'file' && typeof value === 'string' && value) {
      node.appendChild(fileLink(value, row.line));
    } else if (Array.isArray(value) || isRow(value)) {
      node.appendChild(render(value));
    } else {
      node.textContent = scalar(value);
    }
    return node;
  }

  function columns(keys: string[], rows: Row[]): string[] {
    const merged = keys.indexOf('file') >= 0;
    return keys.filter(function (key) {
      if (merged && POSITION.indexOf(key) >= 0) return false;
      return rows.some(function (row) {
        return scalar(row[key]) !== '';
      });
    });
  }

  function table(keys: string[], rows: Row[]): HTMLElement {
    const shown = columns(keys, rows);
    const head = el('tr', {});
    shown.forEach(function (key) {
      head.appendChild(el('th', { text: key }));
    });
    const body = el('tbody', {});
    rows.forEach(function (row) {
      const tr = el('tr', {});
      shown.forEach(function (key) {
        tr.appendChild(cell(row, key, 'td'));
      });
      body.appendChild(tr);
    });
    const node = el('table', { class: 'toon-table' });
    node.appendChild(el('thead', {})).appendChild(head);
    node.appendChild(body);
    return node;
  }

  function list(items: unknown[]): HTMLElement {
    const node = el('ul', { class: 'toon-list' });
    items.forEach(function (item) {
      const li = el('li', {});
      if (Array.isArray(item) || isRow(item)) li.appendChild(render(item));
      else li.textContent = scalar(item);
      node.appendChild(li);
    });
    return node;
  }

  function fields(row: Row): HTMLElement {
    const node = el('div', { class: 'toon-fields' });
    Object.keys(row).forEach(function (key) {
      const value = row[key];
      const field = el('div', { class: 'toon-field' + (Array.isArray(value) || isRow(value) ? ' toon-nested' : '') });
      field.appendChild(el('span', { class: 'toon-key', text: key }));
      field.appendChild(cell(row, key, 'span'));
      node.appendChild(field);
    });
    return node;
  }

  function render(value: unknown): HTMLElement {
    if (Array.isArray(value)) {
      const keys = uniformKeys(value);
      return keys ? table(keys, value as Row[]) : list(value);
    }
    if (isRow(value)) return fields(value);
    return el('span', { class: 'toon-scalar', text: scalar(value) });
  }

  TX.renderToon = function (root: HTMLElement, json: string): void {
    root.innerHTML = '';
    let value: unknown;
    try {
      value = JSON.parse(json);
    } catch (e) {
      root.appendChild(el('pre', { text: json }));
      return;
    }
    root.appendChild(render(value));
  };
})();
