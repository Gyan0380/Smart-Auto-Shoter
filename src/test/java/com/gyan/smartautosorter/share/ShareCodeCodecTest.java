package com.gyan.smartautosorter.share;

import com.gyan.smartautosorter.core.ItemRule;
import com.gyan.smartautosorter.core.Layout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShareCodeCodecTest {

    @Test
    void roundTripsACustomLayout() {
        Layout layout = Layout.newCustom("My PvP Setup", "#AA00FF");
        layout.putRule(new ItemRule("minecraft:netherite_sword", 0, 5, true));
        layout.putRule(new ItemRule("minecraft:shield", 1, 0, false));

        String code = ShareCodeCodec.encode(layout);
        assertTrue(code.startsWith("SAS1-"));

        ShareCodeCodec.DecodeResult result = ShareCodeCodec.decode(code);
        assertInstanceOf(ShareCodeCodec.Success.class, result);
        Layout decoded = ((ShareCodeCodec.Success) result).layout();

        assertEquals("My PvP Setup", decoded.name());
        assertEquals("#AA00FF", decoded.colorHex());
        assertEquals(2, decoded.allRules().size());
        assertEquals(0, decoded.ruleFor("minecraft:netherite_sword").preferredSlot());
    }

    @Test
    void rejectsGarbageInput() {
        assertInstanceOf(ShareCodeCodec.Failure.class, ShareCodeCodec.decode("not a share code"));
        assertInstanceOf(ShareCodeCodec.Failure.class, ShareCodeCodec.decode("SAS1-not-valid-base64-!!!"));
        assertInstanceOf(ShareCodeCodec.Failure.class, ShareCodeCodec.decode(null));
    }

    @Test
    void rejectsTamperedChecksum() {
        Layout layout = Layout.newCustom("Tamper Test", "#112233");
        String code = ShareCodeCodec.encode(layout);
        // flip a character in the middle of the payload to simulate corruption/tampering
        char[] chars = code.toCharArray();
        int mid = chars.length / 2;
        chars[mid] = chars[mid] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        ShareCodeCodec.DecodeResult result = ShareCodeCodec.decode(tampered);
        assertInstanceOf(ShareCodeCodec.Failure.class, result);
    }

    @Test
    void rejectsOversizedInput() {
        String huge = "SAS1-" + "A".repeat(50_000);
        assertInstanceOf(ShareCodeCodec.Failure.class, ShareCodeCodec.decode(huge));
    }
}
