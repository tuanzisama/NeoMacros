package ink.tuanzi.neomacros.fabric.web.pair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The pairing state store over {@code web-pairing.json}: one live token at
 * a time (minted by the chat link, delivered to the browser through the
 * deep link) plus the loopback-trust flag. The state persists across game
 * restarts, so yesterday's chat link stays clickable while the configured
 * lifetime covers it. A token is the pairing credential itself — single
 * use, one token pairs exactly one device, and minting a fresh token
 * invalidates the previous one. A corrupt file is set aside as
 * {@code .corrupted} and the store falls back to empty, following the
 * options.json recovery idiom.
 */
public final class PairingService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path file;
    private final LongSupplier nowMillis;
    private final LongSupplier ttlMillis;
    private final Consumer<String> warn;

    private String token;
    private long tokenExpiresAt;
    private boolean trustLoopback;

    /**
     * Creates the store over one storage file. The lifetime supplier is
     * read per mint, so a config change applies without recreating the
     * service; already-persisted tokens keep their mint-time expiry.
     */
    public PairingService(Path file, LongSupplier nowMillis, LongSupplier ttlMillis,
                          Consumer<String> warn) {
        this.file = file;
        this.nowMillis = nowMillis;
        this.ttlMillis = ttlMillis;
        this.warn = warn;
        load();
    }

    /**
     * Mints a fresh pairing token, invalidating the previous one, and
     * returns it (base64url, 32 bytes of entropy) with the expiry persisted.
     */
    public synchronized String newToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokenExpiresAt = nowMillis.getAsLong() + ttlMillis.getAsLong();
        save();
        return token;
    }

    /** Whether the given token is the live one and still inside its lifetime. */
    public synchronized boolean validate(String candidate) {
        return token != null && token.equals(candidate)
                && nowMillis.getAsLong() < tokenExpiresAt;
    }

    /** Burns the live token (single use) after a successful pairing. */
    public synchronized void consume(String candidate) {
        if (validate(candidate)) {
            token = null;
            save();
        }
    }

    /** Whether tokenless pairing from loopback sources is auto-approved. */
    public synchronized boolean trustLoopback() {
        return trustLoopback;
    }

    /** Toggles the loopback trust and persists the flag immediately. */
    public synchronized void setTrustLoopback(boolean value) {
        if (trustLoopback != value) {
            trustLoopback = value;
            save();
        }
    }

    /** Writes the pairing state to disk. */
    private synchronized void save() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("token", token);
        root.addProperty("tokenExpiresAt", tokenExpiresAt);
        root.addProperty("trustLoopback", trustLoopback);
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(file, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            warn.accept("cannot write " + file.getFileName() + ": " + e.getMessage());
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warn.accept("cannot read " + file.getFileName() + ": " + e.getMessage());
            return;
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(content).getAsJsonObject();
        } catch (RuntimeException e) {
            quarantine(e);
            return;
        }
        JsonElement tokenElement = root.get("token");
        JsonElement expiryElement = root.get("tokenExpiresAt");
        if (tokenElement != null && tokenElement.isJsonPrimitive()
                && expiryElement != null && expiryElement.isJsonPrimitive()) {
            token = tokenElement.getAsString();
            if (token.isEmpty()) {
                token = null;
            }
            try {
                tokenExpiresAt = expiryElement.getAsLong();
            } catch (RuntimeException e) {
                token = null;
                warn.accept("skipping malformed token expiry: " + e.getMessage());
            }
        }
        JsonElement trustElement = root.get("trustLoopback");
        trustLoopback = trustElement != null && trustElement.isJsonPrimitive()
                && trustElement.getAsBoolean();
    }

    private void quarantine(Exception cause) {
        warn.accept(file.getFileName() + " is corrupt (" + cause.getMessage() + "); starting empty");
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".corrupted"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            warn.accept("cannot quarantine corrupt pairing file: " + e.getMessage());
        }
    }
}
