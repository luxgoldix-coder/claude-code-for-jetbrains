(function () {
  'use strict';

  const CC = (window.CC = window.CC || ({} as CcShared));
  const CX = (CC.composer = CC.composer || ({} as ComposerNs));

  const HAIR_DOWN =
    '<path d="M3.2 8.6c0-4.1 2.1-6.4 4.8-6.4s4.8 2.3 4.8 6.4v6.2l-1.7-1.3V9.3H4.9v4.2l-1.7 1.3z" fill="currentColor"/>';

  const HAIR_UP =
    '<path d="M3.2 9.4 2.3 5.2l2.1 1.3L4.9 2l1.7 2.4L8 .9l1.4 3.5L11.1 2l.5 4.5 2.1-1.3-.9 4.2v5.4l-1.7-1.3V9.3H4.9v4.2l-1.7 1.3z" ' +
    'fill="currentColor"/>';

  const AURA =
    '<path d="M1.4 8.2l1.3-.4M1.9 4.6l1.2.7M14.6 8.2l-1.3-.4M14.1 4.6l-1.2.7M4.1 1.8l.7 1.1M11.9 1.8l-.7 1.1" ' +
    'stroke="currentColor" stroke-width=".8" stroke-linecap="round" opacity=".75"/>';

  const FACE =
    '<circle class="robot-face" cx="8" cy="9.2" r="3.7" stroke="currentColor" stroke-width=".9"/>' +
    '<path d="M4.5 8.1c.5-2.3 2-3.4 4.1-3.3 1.6.1 2.7.8 3.2 2.1-1.5-.6-2.8-.5-4.1.4-.8-.1-1.9.1-3.2.8z" fill="currentColor"/>' +
    '<circle cx="6.7" cy="9.7" r=".85" fill="currentColor"/><circle cx="9.5" cy="9.7" r=".85" fill="currentColor"/>' +
    '<path d="M7.3 11.4q.7.6 1.4 0" stroke="currentColor" stroke-width=".7" stroke-linecap="round"/>';

  const ANTENNA =
    '<path d="M8 2.3V1.3" stroke="currentColor" stroke-width=".9" stroke-linecap="round"/>' +
    '<circle cx="8" cy=".9" r=".7" fill="currentColor"/>';

  CX.robotGlyph = function (powered: boolean): string {
    const body = powered ? AURA + HAIR_UP + FACE : HAIR_DOWN + FACE + ANTENNA;
    return '<svg viewBox="0 0 16 16" fill="none" aria-hidden="true">' + body + '</svg>';
  };
})();
