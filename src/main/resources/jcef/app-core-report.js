(function () {
  'use strict';

  var CC = window.CC || (window.CC = {});

  document.addEventListener(
    'click',
    function (ev) {
      var node = ev.target;
      while (node && node !== document) {
        if (node.tagName === 'A' && node.hasAttribute('href')) {
          var url = node.getAttribute('href');
          if (url && url !== '#') {
            ev.preventDefault();
            ev.stopPropagation();
            CC.send({ type: 'open', url: url });
          }
          return;
        }
        node = node.parentNode;
      }
    },
    true
  );

  function copyTargetText(copyEl) {
    var pre = copyEl.closest ? copyEl.closest('pre') : null;
    if (!pre) {
      var n = copyEl.parentNode;
      while (n && n.tagName !== 'PRE') n = n.parentNode;
      pre = n;
    }
    var code = pre ? pre.querySelector('code') : null;
    return code ? code.textContent : '';
  }
  function flashCopied(copyEl) {
    var prev = copyEl.textContent;
    copyEl.textContent = 'Copied';
    copyEl.classList.add('copied');
    setTimeout(function () {
      copyEl.textContent = prev;
      copyEl.classList.remove('copied');
    }, 1200);
  }
  CC.flashCopied = flashCopied;
  function handleCopyFromCodeHead(ev, copyEl) {
    var text = copyTargetText(copyEl);
    if (!text) return;
    ev.preventDefault();
    ev.stopPropagation();
    CC.send({ type: 'copy', text: text });
    flashCopied(copyEl);
  }
  document.addEventListener(
    'click',
    function (ev) {
      var node = ev.target;
      while (node && node !== document) {
        if (
          node.className &&
          ('' + node.className).indexOf('copy') >= 0 &&
          node.parentNode &&
          ('' + (node.parentNode.className || '')).indexOf('code-head') >= 0
        ) {
          handleCopyFromCodeHead(ev, node);
          return;
        }
        node = node.parentNode;
      }
    },
    true
  );
  document.addEventListener(
    'keydown',
    function (ev) {
      if (ev.key !== 'Enter' && ev.key !== ' ' && ev.key !== 'Spacebar') return;
      var node = ev.target;
      if (
        node &&
        node.className &&
        ('' + node.className).indexOf('copy') >= 0 &&
        node.parentNode &&
        ('' + (node.parentNode.className || '')).indexOf('code-head') >= 0
      ) {
        handleCopyFromCodeHead(ev, node);
      }
    },
    true
  );

  var reported = Object.create(null);
  var reportedCount = 0;
  var MAX_REPORTED = 20;
  function reportUncaught(what, error) {
    var text = error && error.stack ? String(error.stack) : String(error);
    var key = what + '|' + text.split('\n')[0];
    if (reported[key] || reportedCount >= MAX_REPORTED) return;
    reported[key] = true;
    reportedCount++;
    CC.send({ type: 'diagnostics', report: 'uncaught ' + what + ': ' + text });
  }
  window.addEventListener('error', function (ev) {
    reportUncaught('error', ev.error || ev.message);
  });
  window.addEventListener('unhandledrejection', function (ev) {
    reportUncaught('rejection', ev.reason);
  });
  window.addEventListener('securitypolicyviolation', function (ev) {
    reportUncaught(
      'csp',
      ev.violatedDirective +
        ' blocked ' +
        (ev.blockedURI || 'inline') +
        ' (' +
        ev.sourceFile +
        ':' +
        ev.lineNumber +
        ')'
    );
  });

  CC.selfCheck = function () {
    var expected = ['batch', 'clear', 'state', 'permissions', 'session', 'tabs', 'theme', 'settingsMenu'];
    var missing = [];
    for (var i = 0; i < expected.length; i++) {
      if (typeof (window.cc || {})[expected[i]] !== 'function') missing.push('cc.' + expected[i]);
    }
    if (missing.length) CC.send({ type: 'diagnostics', report: 'uncaught missing: ' + missing.join(', ') });
  };
})();
