const { loadFrontend } = require('../helpers/load');

function state(extra = {}) {
  return {
    turnActive: false,
    interrupting: false,
    running: true,
    guardOn: true,
    ideIntegrationOn: false,
    ideRulesOn: 0,
    provider: { id: 'anthropic', label: 'Anthropic', options: [{ id: 'anthropic', label: 'Anthropic' }] },
    model: { label: 'Opus 5', options: [{ value: 'opus[1m]', label: 'Opus 5', selected: true }] },
    mode: { wire: 'default', label: 'Default', options: [{ wire: 'default', label: 'Default' }] },
    effort: { label: 'High', options: [{ value: 'high', label: 'High', selected: true }] },
    thinking: { on: true, label: 'Thinking on', options: [{ on: false, label: 'Off' }] },
    queue: [],
    ...extra,
  };
}

function mount(extra) {
  const win = loadFrontend(['app-composer.js']);
  const sent = [];
  win.CC.send = (m) => sent.push(m);
  win.CC.composer.send = (m) => sent.push(m);
  win.cc.state(state(extra));
  const robot = document.querySelector('.bar-right [aria-label="IDE integration"]');
  return { win, sent, robot };
}

describe('the robot says whether Claude works through the IDE', () => {
  it('stands right of the Remote Control phone', () => {
    const { robot } = mount();
    expect(robot).toBeTruthy();
    expect(robot.tagName).toBe('BUTTON');
    expect(robot.type).toBe('button');
    expect(robot.previousElementSibling.getAttribute('aria-label')).toBe('Remote Control');
  });

  it('is drawn inline, from nothing fetched', () => {
    const { robot } = mount();
    const svg = robot.querySelector('svg');
    expect(svg).toBeTruthy();
    expect(svg.getAttribute('aria-hidden')).toBe('true');
    expect(robot.innerHTML).not.toMatch(/url\(|<image|href/);
  });

  it('is grey and says so while no IDE server is on', () => {
    const { robot } = mount();
    expect(robot.classList.contains('active')).toBe(false);
    expect(robot.title).toContain('off');
  });

  it('powers up from the host, and counts the rules in the tooltip', () => {
    const { win, robot } = mount();
    const idle = robot.innerHTML;

    win.cc.state(state({ ideIntegrationOn: true, ideRulesOn: 3 }));

    expect(robot.classList.contains('active')).toBe(true);
    expect(robot.title).toContain('3 IDE rules');
    expect(robot.innerHTML).not.toBe(idle);

    win.cc.state(state({ ideIntegrationOn: true, ideRulesOn: 1 }));
    expect(robot.title).toContain('1 IDE rule active');

    win.cc.state(state({ ideIntegrationOn: false }));
    expect(robot.classList.contains('active')).toBe(false);
    expect(robot.innerHTML).toBe(idle);
  });

  it('a click opens the ⚙ menu on the IDE rules, not a menu of its own', () => {
    const { win, robot } = mount();
    win.cc.settingsMenu({
      items: [
        { key: 'model:opus[1m]', group: 'Model', label: 'Opus 5', on: true, type: 'radio' },
        {
          key: 'iderule:index.read',
          group: 'IDE rules',
          sub: 'IDE Index MCP Server',
          label: 'Read',
          on: false,
        },
      ],
    });

    robot.dispatchEvent(new win.MouseEvent('click', { bubbles: true }));

    const menu = document.querySelector('.settings-menu');
    expect(menu).toBeTruthy();
    expect(menu.querySelector('.attach-title').textContent).toBe('IDE rules');
    expect(document.querySelectorAll('.settings-menu').length).toBe(1);
  });
});
