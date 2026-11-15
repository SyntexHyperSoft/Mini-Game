const fs = require('fs');

const html = fs.readFileSync('app/src/main/assets/game/index.html', 'utf-8');

// Build a simulated browser environment
class MockElement {
  constructor(tag, id = '') {
    this.tagName = tag.toUpperCase();
    this.id = id;
    this.classList = {
      _classes: new Set(),
      add: function(...cls) { cls.forEach(c => this._classes.add(c)); },
      remove: function(...cls) { cls.forEach(c => this._classes.delete(c)); },
      contains: function(c) { return this._classes.has(c); },
      toggle: function(c, val) {
        if (val !== undefined) {
          if (val) this._classes.add(c); else this._classes.delete(c);
          return val;
        }
        if (this._classes.has(c)) { this._classes.delete(c); return false; }
        else { this._classes.add(c); return true; }
      }
    };
    this.style = {};
    this.children = [];
    this._innerHTML = '';
    this._textContent = '';
    this.attributes = {};
  }
  set innerHTML(val) {
    this._innerHTML = val;
  }
  get innerHTML() {
    return this._innerHTML;
  }
  set textContent(val) {
    this._textContent = val;
  }
  get textContent() {
    return this._textContent;
  }
  set innerText(val) {
    this._textContent = val;
  }
  get innerText() {
    return this._textContent;
  }
  appendChild(child) {
    this.children.push(child);
    return child;
  }
  querySelector(sel) {
    return new MockElement('div');
  }
  querySelectorAll(sel) {
    return [new MockElement('div')];
  }
  getAttribute(attr) {
    return this.attributes[attr] || null;
  }
  setAttribute(attr, val) {
    this.attributes[attr] = val;
  }
  closest() {
    return null;
  }
  addEventListener() {}
  remove() {}
}

const elementsById = new Map();

// Parse all IDs from HTML
const idRegex = /id=["']([a-zA-Z0-9\-_]+)["']/g;
let m;
while ((m = idRegex.exec(html)) !== null) {
  elementsById.set(m[1], new MockElement('div', m[1]));
}

const mockCanvas = new MockElement('canvas', 'gameCanvas');
const mockCtx = {
  save() {}, restore() {}, translate() {}, scale() {}, rotate() {},
  beginPath() {}, closePath() {}, moveTo() {}, lineTo() {}, arc() {}, ellipse() {},
  fill() {}, stroke() {}, fillRect() {}, strokeRect() {}, fillText() {},
  setLineDash() {}, setTransform() {}, resetTransform() {},
  createLinearGradient() { return { addColorStop() {} }; },
  createRadialGradient() { return { addColorStop() {} }; },
  drawImage() {},
  set fillStyle(val) {},
  set strokeStyle(val) {},
  set lineWidth(val) {},
  set globalAlpha(val) {},
  set shadowBlur(val) {},
  set shadowColor(val) {},
  set font(val) {},
  set textAlign(val) {}
};
mockCanvas.getContext = () => mockCtx;
elementsById.set('gameCanvas', mockCanvas);
elementsById.set('menu-canvas', mockCanvas);
elementsById.set('builder-canvas', mockCanvas);

global.window = {
  innerWidth: 400,
  innerHeight: 800,
  devicePixelRatio: 2,
  addEventListener() {},
  removeEventListener() {},
  requestAnimationFrame(cb) { return setTimeout(cb, 16); },
  cancelAnimationFrame(id) { clearTimeout(id); },
  localStorage: {
    _data: {},
    getItem(k) { return this._data[k] || null; },
    setItem(k, v) { this._data[k] = String(v); },
    removeItem(k) { delete this._data[k]; }
  },
  setInterval() {},
  setTimeout: setTimeout,
  clearTimeout: clearTimeout,
  haptic() {},
  location: { reload() {} }
};

global.document = {
  documentElement: new MockElement('html'),
  body: new MockElement('body'),
  getElementById(id) {
    if (!elementsById.has(id)) {
      elementsById.set(id, new MockElement('div', id));
    }
    return elementsById.get(id);
  },
  createElement(tag) {
    const el = new MockElement(tag);
    if (tag === 'canvas') {
      el.getContext = () => mockCtx;
    }
    return el;
  },
  querySelectorAll() {
    return [new MockElement('div')];
  },
  addEventListener() {}
};

global.navigator = { userAgent: 'Android' };
global.localStorage = global.window.localStorage;
global.requestAnimationFrame = global.window.requestAnimationFrame;

// Extract script
const scriptMatch = html.match(/<script[\s\S]*?>([\s\S]*?)<\/script>/i);
const scriptCode = scriptMatch[1];

console.log('--- EXECUTING SCRIPT ---');
try {
  eval(scriptCode);
  console.log('Script evaluated successfully!');

  // Now test key game lifecycle functions
  console.log('Testing loadSettings...');
  if (typeof loadSettings === 'function') loadSettings();

  console.log('Testing loadGame...');
  if (typeof loadGame === 'function') loadGame();

  console.log('Testing initSprites...');
  if (typeof initSprites === 'function') initSprites();

  console.log('Testing bindAllEvents...');
  if (typeof bindAllEvents === 'function') bindAllEvents();

  console.log('Testing updateUI...');
  if (typeof updateUI === 'function') updateUI();

  console.log('Testing updateAvailableIndicators...');
  if (typeof updateAvailableIndicators === 'function') updateAvailableIndicators();

  console.log('Testing openIndex & tabs...');
  if (typeof openIndex === 'function') {
    openIndex();
    if (typeof switchIndexTab === 'function') {
      switchIndexTab('weapons');
      switchIndexTab('weapon-upgrade');
      switchIndexTab('abilities');
      switchIndexTab('ability-upgrade');
      switchIndexTab('enemies');
      switchIndexTab('builder');
      switchIndexTab('stats');
    }
  }

  console.log('Testing openStarEnchantModal...');
  if (typeof openStarEnchantModal === 'function') {
    openStarEnchantModal('weapon', 'blaster');
    openStarEnchantModal('ability', 'rocketStrike');
  }

  console.log('Testing showGameDialog...');
  if (typeof showGameDialog === 'function') {
    showGameDialog({ title: 'Test', message: 'Hello' });
  }

  console.log('Testing openQuests...');
  if (typeof openQuests === 'function') openQuests();

  console.log('Testing openUpgrades...');
  if (typeof openUpgrades === 'function') openUpgrades();

  console.log('Testing openSettings...');
  if (typeof openSettings === 'function') openSettings();

  console.log('Testing openProfile...');
  if (typeof openProfile === 'function') openProfile();

  console.log('Testing startGame...');
  if (typeof startGame === 'function') startGame();

  console.log('Testing shoot...');
  if (typeof shoot === 'function') shoot();

  console.log('Testing castPhantomClone...');
  if (typeof castPhantomClone === 'function') castPhantomClone();

  console.log('Testing useAbility...');
  if (typeof useAbility === 'function') {
    useAbility(0);
    useAbility(1);
  }

  console.log('Testing drawPhantomClone...');
  if (typeof drawPhantomClone === 'function') drawPhantomClone(mockCtx);

  console.log('Testing draw...');
  if (typeof draw === 'function') draw();

  console.log('ALL LIFECYCLE TESTS PASSED WITH 0 ERRORS!');
} catch (err) {
  console.error('RUNTIME TEST FAILED:', err);
  process.exit(1);
}
