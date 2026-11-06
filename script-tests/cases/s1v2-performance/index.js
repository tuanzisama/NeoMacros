import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';
import { performance } from '@neomacros/performance';

let fired = false;
events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
    results['now'] = typeof performance.now() === 'number' && performance.now() >= 0;
    results['timeOrigin'] = typeof performance.timeOrigin === 'number'
      && performance.timeOrigin > 0
      && performance.timeOrigin <= Date.now() + 50;

    performance.mark('phase-a');
    const span = performance.measure('phase-a-to-now', 'phase-a');
    results['measureFromMark'] = span.entryType === 'measure'
      && span.name === 'phase-a-to-now'
      && span.startTime >= 0 && span.duration >= 0;

    const raw = performance.measure('raw-bounds', 1.5, 3.5);
    results['rawBounds'] = raw.startTime === 1.5 && raw.duration === 2;

    results['entries'] = performance.entries.length === 2
      && performance.entriesByName('phase-a', 'mark').length === 1
      && performance.entriesByType('measure').length === 1;

    const json = JSON.stringify(performance.entriesByName('phase-a')[0]);
    results['jsonShape'] = json.includes('"entryType":"mark"')
      && json.includes('"startTime"') && json.includes('"duration"');

    performance.clearMarks();
    performance.clearMeasures();
    results['clears'] = performance.entries.length === 0;
  } catch (e) {
    chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    chat.log('[E2E] ' + key + '=' + value);
  }
  chat.log('[E2E] s1v2-performance: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
