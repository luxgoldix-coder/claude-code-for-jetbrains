const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');

const JCEF = path.resolve(__dirname, '../../../main/resources/jcef');
const EMIT = path.resolve(__dirname, '../../../../build/web/jcef');

function appDir(name) {
  return fs.existsSync(path.join(EMIT, name)) ? EMIT : JCEF;
}

function appPath(name) {
  return path.join(appDir(name), name);
}

function readApp(name) {
  return fs.readFileSync(appPath(name), 'utf8');
}

function shellBody() {
  const html = fs.readFileSync(path.join(JCEF, 'shell.html'), 'utf8');
  const parsed = new JSDOM(html);
  const body = parsed.window.document.body;
  if (!body) throw new Error('helpers/load: could not find <body> in shell.html');
  body.querySelectorAll('script').forEach((node) => node.remove());
  return body.innerHTML;
}

const VENDOR = ['purify.min.js', 'marked.min.js', 'highlight.min.js'];

function pageAssemblySource() {
  return fs.readFileSync(
    path.resolve(__dirname, '../../../main/kotlin/dev/lain/claudejb/ui/jcef/PageAssembly.kt'),
    'utf8'
  );
}

function declaredList(name, entry) {
  const source = pageAssemblySource();
  const block = source.slice(source.indexOf(`val ${name} = listOf(`)).replace(/\/\/[^\n]*/g, '');
  const found = block.slice(0, block.indexOf(')')).match(entry);
  if (!found) throw new Error(`helpers/load: could not read ${name} from PageAssembly.kt`);
  return found.map((quoted) => quoted.replace(/"/g, ''));
}

function appModules() {
  return declaredList('appNames', /"([\w-]+(?:\/[\w-]+)*\.js)"/g);
}

function cssParts() {
  return declaredList('CSS_PARTS', /"([\w-]+\.css)"/g);
}

const LEGACY_FAMILY = { session: 'dashboard' };

function familyOf(name) {
  const legacy = /^app-([a-z]+)/.exec(name);
  if (legacy) return LEGACY_FAMILY[legacy[1]] || legacy[1];
  return name.includes('/') ? name.slice(0, name.indexOf('/')) : name;
}

function loadFrontend(files = [], { vendor = true } = {}) {
  document.documentElement.innerHTML = `<head></head><body>${shellBody()}</body>`;
  const wanted = new Set(['core', ...files.map(familyOf)]);
  const seq = [...(vendor ? VENDOR : []), ...appModules().filter((f) => wanted.has(familyOf(f)))];
  for (const f of seq) {
    window.eval(readApp(f));
  }
  return window;
}

const SOURCES = path.resolve(__dirname, '../../../main/ts/jcef');

function modulesUnder(root, prefix = '') {
  if (!fs.existsSync(root)) return [];
  return fs.readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const relative = prefix + entry.name;
    if (entry.isDirectory())
      return entry.name === 'types' ? [] : modulesUnder(path.join(root, entry.name), relative + '/');
    return entry.name.endsWith('.ts') && !entry.name.endsWith('.d.ts')
      ? [relative.replace(/\.ts$/, '.js')]
      : [];
  });
}

function appJsFiles() {
  return modulesUnder(SOURCES).sort();
}

function readCss() {
  return cssParts()
    .map((part) => fs.readFileSync(path.join(JCEF, 'css', part), 'utf8'))
    .join('\n');
}

module.exports = { loadFrontend, readApp, appPath, appJsFiles, appModules, cssParts, readCss, JCEF, EMIT };
