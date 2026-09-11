(function () {
  'use strict';

  var CC = window.CC || (window.CC = {});

  CC.placeMenu = function (menu, anchor) {
    var margin = 8;
    var r = anchor.getBoundingClientRect();
    menu.style.position = 'fixed';
    menu.style.maxWidth = window.innerWidth - margin * 2 + 'px';
    var mw = menu.offsetWidth;
    var mh = menu.offsetHeight;
    var left = Math.min(Math.round(r.left), window.innerWidth - mw - margin);
    if (left < margin) left = margin;
    var top = r.top - mh - 6;
    if (top < margin) top = r.bottom + 6;
    if (top + mh > window.innerHeight - margin) top = Math.max(margin, window.innerHeight - mh - margin);
    menu.style.left = left + 'px';
    menu.style.top = Math.round(top) + 'px';
  };

  CC.GUARD_DURATIONS = [
    { token: '5m', label: '5 minutes' },
    { token: '15m', label: '15 minutes' },
    { token: '30m', label: '30 minutes' },
    { token: '4h', label: '4 hours' },
    { token: '8h', label: '8 hours' },
    { token: 'ide', label: 'Until IDE closes' },
    { token: 'forever', label: 'Forever' },
  ];

  CC.durationMenu = function (opts) {
    return CC.pickMenu({
      anchor: opts.anchor,
      home: opts.home,
      label: opts.label || 'Disable for',
      watch: opts.watch,
      items: CC.GUARD_DURATIONS.map(function (d) {
        return { value: d.token, label: d.label };
      }),
      onPick: opts.onPick,
    });
  };

  CC.pickMenu = function (opts) {
    var anchor = opts.anchor;
    var home = opts.home;
    var checkable = !!opts.checkable;
    var options = [];
    var isOpen = false;
    var menu = document.createElement('div');
    menu.className = opts.menuClass || 'guard-disable-menu';
    menu.setAttribute('role', 'menu');
    menu.setAttribute('hidden', 'hidden');
    menu.setAttribute('aria-label', opts.label || 'Menu');

    function focusOption(at) {
      var target = options[(at + options.length) % options.length];
      if (target) target.focus({ preventScroll: true });
    }
    function onOutside(e) {
      if (menu.contains(e.target) || anchor.contains(e.target)) return;
      setOpen(false);
    }
    function onEscape(e) {
      if (e.key !== 'Escape') return;
      setOpen(false);
      anchor.focus();
    }
    function onViewChange() {
      setOpen(false);
    }
    var watch =
      window.MutationObserver && opts.watch
        ? new window.MutationObserver(function () {
            if (!anchor.isConnected) setOpen(false);
          })
        : null;

    function setOpen(open) {
      if (open === isOpen) return;
      isOpen = open;
      anchor.setAttribute('aria-expanded', open ? 'true' : 'false');
      if (!open) {
        menu.setAttribute('hidden', 'hidden');
        home.appendChild(menu);
        document.removeEventListener('mousedown', onOutside, true);
        document.removeEventListener('keydown', onEscape, true);
        document.removeEventListener('scroll', onViewChange, true);
        window.removeEventListener('resize', onViewChange);
        if (watch) watch.disconnect();
        return;
      }
      document.body.appendChild(menu);
      menu.removeAttribute('hidden');
      CC.placeMenu(menu, anchor);
      document.addEventListener('mousedown', onOutside, true);
      document.addEventListener('keydown', onEscape, true);
      document.addEventListener('scroll', onViewChange, true);
      window.addEventListener('resize', onViewChange);
      if (watch && opts.watch()) watch.observe(opts.watch(), { childList: true });
      focusOption(0);
    }

    menu.addEventListener('keydown', function (e) {
      if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
      e.preventDefault();
      var step = e.key === 'ArrowDown' ? 1 : -1;
      var at = options.indexOf(document.activeElement);
      focusOption(at < 0 ? (step > 0 ? 0 : options.length - 1) : at + step);
    });

    (opts.items || []).forEach(function (item) {
      var option = document.createElement('button');
      option.className = opts.itemClass || 'guard-disable-option';
      option.type = 'button';
      option.setAttribute('role', checkable ? 'menuitemcheckbox' : 'menuitem');
      option.textContent = item.label;
      if (checkable) option.setAttribute('aria-checked', item.checked ? 'true' : 'false');
      option.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        opts.onPick(item.value);
        if (!checkable) {
          setOpen(false);
          anchor.focus();
          return;
        }
        sync();
      });
      options.push(option);
      menu.appendChild(option);
    });

    function sync() {
      if (!checkable || typeof opts.checkedOf !== 'function') return;
      options.forEach(function (option, index) {
        var item = opts.items[index];
        option.setAttribute('aria-checked', opts.checkedOf(item.value) ? 'true' : 'false');
      });
    }

    home.appendChild(menu);
    return {
      menu: menu,
      sync: sync,
      toggle: function () {
        setOpen(!isOpen);
      },
      close: function () {
        setOpen(false);
      },
    };
  };
})();
