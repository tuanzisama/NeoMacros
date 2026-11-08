package ink.tuanzi.neomacros.core.update;

import ink.tuanzi.neomacros.core.update.ChannelVersions.Channel;
import ink.tuanzi.neomacros.core.update.ChannelVersions.Version;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The channel version scheme: parsing, channel classification and ordering. */
class ChannelVersionsTest {

    @Test
    void parsesEveryChannel() {
        Version release = ChannelVersions.parse("0.1.0");
        assertEquals(Channel.RELEASE, release.channel());
        assertEquals("0.1.0", release.base());
        assertEquals(0, release.build());
        assertTrue(release.isLocal() == false);

        Version dev = ChannelVersions.parse("0.1.0-dev.123+abc1234");
        assertEquals(Channel.DEV, dev.channel());
        assertEquals("0.1.0", dev.base());
        assertEquals(123, dev.build());

        Version canary = ChannelVersions.parse("0.1.0-canary.45+def5678");
        assertEquals(Channel.CANARY, canary.channel());
        assertEquals(45, canary.build());

        Version local = ChannelVersions.parse("0.1.0-local");
        assertEquals(Channel.LOCAL, local.channel());
        assertTrue(local.isLocal());
    }

    @Test
    void rejectsUnusableInput() {
        assertNull(ChannelVersions.parse(null));
        assertNull(ChannelVersions.parse(""));
        assertNull(ChannelVersions.parse("garbage"));
        assertNull(ChannelVersions.parse("0.1"));
        assertNull(ChannelVersions.parse("0.1.0-nightly.7"));
        assertNull(ChannelVersions.parse("1.2.3.4"));
    }

    @Test
    void ordersDevByBuildNumber() {
        Version older = ChannelVersions.parse("0.1.0-dev.122+aaa1111");
        Version newer = ChannelVersions.parse("0.1.0-dev.123+bbb2222");
        assertTrue(ChannelVersions.compareSameChannel(newer, older) > 0);
        assertTrue(ChannelVersions.compareSameChannel(older, newer) < 0);
        assertEquals(0, ChannelVersions.compareSameChannel(older,
                ChannelVersions.parse("0.1.0-dev.122+zzz9999")));
    }

    @Test
    void ordersReleaseBySemver() {
        assertTrue(ChannelVersions.compareSameChannel(
                ChannelVersions.parse("0.2.0"), ChannelVersions.parse("0.1.9")) > 0);
        assertTrue(ChannelVersions.compareSameChannel(
                ChannelVersions.parse("1.0.0"), ChannelVersions.parse("0.99.99")) > 0);
        assertEquals(0, ChannelVersions.compareSameChannel(
                ChannelVersions.parse("0.1.0"), ChannelVersions.parse("0.1.0")));
    }
}
