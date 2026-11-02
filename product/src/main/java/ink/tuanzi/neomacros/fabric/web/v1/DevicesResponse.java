package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;

import java.util.List;

/** The paired-device listing. */
public record DevicesResponse(
        @NmDesc("All paired devices, oldest first") List<DeviceView> devices) {
}
