package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;

import java.util.List;

/** The full panel first-screen snapshot: every discovered script plus server info. */
public record StateSnapshot(
        @NmDesc("All discovered scripts") List<MacroView> macros,
        @NmDesc("The web service status") ServerInfo server,
        @NmDesc("The absolute macros root path on this machine;"
                + " join editor-relative paths onto it") String macrosRoot) {
}
