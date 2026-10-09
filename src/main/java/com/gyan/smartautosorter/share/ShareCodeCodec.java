package com.gyan.smartautosorter.share;

import com.gyan.smartautosorter.core.ItemRule;
import com.gyan.smartautosorter.core.Layout;
import com.gyan.smartautosorter.core.ManualPlacementPolicy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.CRC32;

/**
 * Spec section 7: encodes a Layout as a compact text share code and
 * decodes it back, with corruption/size/format validation.
 *
 * Format (all integers big-endian, written through DataOutputStream):
 *   magic:    4 bytes "SAS1"
 *   name:     UTF string (max 64 chars enforced)
 *   color:    UTF string (hex, e.g. "#43A047")
 *   policy:   1 byte (ManualPlacementPolicy ordinal)
 *   ruleCount:2 bytes (unsigned short, max MAX_RULES)
 *   rules[]:  { itemId: UTF string, slot: short, priority: byte, lockOnPlace: byte }
 *   crc32:    8 bytes (checksum of everything above)
 * -> Base64 (URL-safe, no padding) with a human-readable "SAS1-" prefix.
 *
 * There is deliberately no executable content whatsoever in a share
 * code — it is pure structured data (strings, shorts, bytes), parsed
 * field-by-field with explicit bounds/size checks. There is nothing here
 * that evaluates, reflects, deserializes-as-Java-objects, or loads
 * anything from the string, which is what "prevent arbitrary code
 * execution or file access through imported layouts" requires.
 */
public final class ShareCodeCodec {

    private static final byte[] MAGIC = "SAS1".getBytes(StandardCharsets.US_ASCII);
    private static final String PREFIX = "SAS1-";
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_RULES = 200;
    /** Hard cap on decoded payload size — anything bigger is rejected outright as oversized/corrupt. */
    private static final int MAX_DECODED_BYTES = 16 * 1024;

    private ShareCodeCodec() {}

    public static String encode(Layout layout) {
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(body);

            out.write(MAGIC);
            out.writeUTF(truncate(layout.name(), MAX_NAME_LENGTH));
            out.writeUTF(layout.colorHex());
            out.writeByte(layout.manualPlacementPolicy().ordinal());

            var rules = layout.allRules();
            int ruleCount = Math.min(rules.size(), MAX_RULES);
            out.writeShort(ruleCount);
            for (int i = 0; i < ruleCount; i++) {
                ItemRule r = rules.get(i);
                out.writeUTF(r.itemId());
                out.writeShort(r.preferredSlot());
                out.writeByte(r.priority());
                out.writeByte(r.lockOnPlace() ? 1 : 0);
            }

            byte[] payload = body.toByteArray();
            CRC32 crc = new CRC32();
            crc.update(payload);

            ByteArrayOutputStream withChecksum = new ByteArrayOutputStream();
            DataOutputStream finalOut = new DataOutputStream(withChecksum);
            finalOut.write(payload);
            finalOut.writeLong(crc.getValue());

            String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(withChecksum.toByteArray());
            return PREFIX + b64;
        } catch (IOException e) {
            // writes to an in-memory stream never actually fail; keep the API checked-exception-free for callers.
            throw new IllegalStateException("Failed to encode share code", e);
        }
    }

    public sealed interface DecodeResult permits Success, Failure {}
    public record Success(Layout layout) implements DecodeResult {}
    public record Failure(String reasonKey) implements DecodeResult {}

    public static DecodeResult decode(String code) {
        if (code == null) return new Failure("null");
        String trimmed = code.strip();
        if (trimmed.length() > 24000) return new Failure("oversized_input");
        if (!trimmed.startsWith(PREFIX)) return new Failure("bad_prefix");

        byte[] raw;
        try {
            raw = Base64.getUrlDecoder().decode(trimmed.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return new Failure("bad_base64");
        }

        if (raw.length < MAGIC.length + 8 || raw.length > MAX_DECODED_BYTES) {
            return new Failure("bad_length");
        }

        byte[] payload = java.util.Arrays.copyOfRange(raw, 0, raw.length - 8);
        long storedCrc = java.nio.ByteBuffer.wrap(raw, raw.length - 8, 8).getLong();
        CRC32 crc = new CRC32();
        crc.update(payload);
        if (crc.getValue() != storedCrc) {
            return new Failure("checksum_mismatch");
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            byte[] magic = new byte[MAGIC.length];
            in.readFully(magic);
            if (!java.util.Arrays.equals(magic, MAGIC)) return new Failure("bad_magic");

            String name = in.readUTF();
            if (name.isBlank() || name.length() > MAX_NAME_LENGTH) return new Failure("bad_name");

            String color = in.readUTF();
            if (!color.matches("^#[0-9A-Fa-f]{6}$")) return new Failure("bad_color");

            int policyOrdinal = in.readUnsignedByte();
            ManualPlacementPolicy[] policies = ManualPlacementPolicy.values();
            if (policyOrdinal < 0 || policyOrdinal >= policies.length) return new Failure("bad_policy");

            int ruleCount = in.readUnsignedShort();
            if (ruleCount > MAX_RULES) return new Failure("too_many_rules");

            Layout layout = Layout.newCustom(name, color);
            layout.setManualPlacementPolicy(policies[policyOrdinal]);

            for (int i = 0; i < ruleCount; i++) {
                String itemId = in.readUTF();
                if (!itemId.matches("^[a-z0-9_.-]+:[a-z0-9_./]+$")) return new Failure("bad_item_id");
                short slot = in.readShort();
                byte priority = in.readByte();
                boolean lock = in.readByte() != 0;
                layout.putRule(new ItemRule(itemId, slot, priority, lock));
            }

            return new Success(layout);
        } catch (EOFException e) {
            return new Failure("truncated");
        } catch (IOException e) {
            return new Failure("unreadable");
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
