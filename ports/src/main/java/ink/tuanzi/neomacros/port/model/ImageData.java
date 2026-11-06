package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Neutral copy of one raster image: dimensions plus PNG-encoded bytes.
 *
 * @param width  image width in pixels
 * @param height image height in pixels
 * @param bytes  PNG-encoded image content
 */
public record ImageData(int width, int height, byte[] bytes) {

    public ImageData {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
