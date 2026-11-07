// Service script: startup() runs once on activation, then the async loop
// keeps the service alive for the whole session. Prints the uptime every
// 5 minutes.
// Stop it with /neomacros service stop uptime, or from the panel.
export async function startup() {
    const started = Date.now();
    while (true) {
        await neo.thread.sleep(300_000); // 5 minutes
        const minutes = Math.floor((Date.now() - started) / 60_000);
        neo.chat.log('[uptime] this session has run for ' + minutes + ' minutes');
    }
}
