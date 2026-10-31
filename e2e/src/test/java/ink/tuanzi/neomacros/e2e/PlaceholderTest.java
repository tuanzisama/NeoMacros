package ink.tuanzi.neomacros.e2e;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 占位冒烟测试：证明 e2e 模块的测试基础设施（JUnit + Java 17 工具链）可用。
 * 真正的 E2E-A harness（E2ERuntime/StubAdapter/TestBridge）由 T-040 落地后移除本类。
 */
class PlaceholderTest {
    @Test
    void testInfraWorks() {
        assertTrue(true);
    }
}
