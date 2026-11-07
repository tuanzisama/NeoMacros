let fired = false;
neo.events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
    results['now'] = typeof neo.performance.now() === 'number' && neo.performance.now() >= 0;
    results['timeOrigin'] = typeof neo.performance.timeOrigin === 'number'
      && neo.performance.timeOrigin > 0
      && neo.performance.timeOrigin <= Date.now() + 50;

    neo.performance.mark('phase-a');
    const span = neo.performance.measure('phase-a-to-now', 'phase-a');
    results['measureFromMark'] = span.entryType === 'measure'
      && span.name === 'phase-a-to-now'
      && span.startTime >= 0 && span.duration >= 0;

    const raw = neo.performance.measure('raw-bounds', 1.5, 3.5);
    results['rawBounds'] = raw.startTime === 1.5 && raw.duration === 2;

    results['entries'] = neo.performance.entries.length === 2
      && neo.performance.entriesByName('phase-a', 'mark').length === 1
      && neo.performance.entriesByType('measure').length === 1;

    const json = JSON.stringify(neo.performance.entriesByName('phase-a')[0]);
    results['jsonShape'] = json.includes('"entryType":"mark"')
      && json.includes('"startTime"') && json.includes('"duration"');

    neo.performance.clearMarks();
    neo.performance.clearMeasures();
    results['clears'] = neo.performance.entries.length === 0;
  } catch (e) {
    neo.chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] ' + key + '=' + value);
  }
  neo.chat.log('[E2E] s1v2-performance: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
