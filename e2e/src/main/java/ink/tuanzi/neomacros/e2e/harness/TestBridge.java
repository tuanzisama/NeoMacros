package ink.tuanzi.neomacros.e2e.harness;

import ink.tuanzi.neomacros.doc.api.ApiModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code __test} binding injected into every scenario script: scripts
 * self-report case outcomes, and the harness asserts that every declared case
 * reported PASS. Reports carry the case id, a pass flag, and free-form detail.
 */
@ApiModule(name = "__test", category = "harness")
public final class TestBridge {

    /** One reported case. */
    public record Report(String id, boolean pass, String detail) {
    }

    private final Map<String, Report> reports = new LinkedHashMap<>();

    /** Script entry: report a case outcome. */
    public void report(String id, boolean pass, String detail) {
        reports.put(id, new Report(id, pass, detail == null ? "" : detail));
    }

    /** All reports in report order. */
    public List<Report> all() {
        return new ArrayList<>(reports.values());
    }

    /** The report for one case id. */
    public Report get(String id) {
        return reports.get(id);
    }
}
