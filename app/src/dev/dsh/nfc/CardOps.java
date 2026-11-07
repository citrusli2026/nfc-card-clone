package dev.dsh.nfc;

import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 所有和卡打交道的逻辑：鉴定、试密钥、读全卡、写克隆。 */
public class CardOps {

    public interface Log {
        void line(String s);

        void line(int resId, Object... args);
    }

    public static class Info {
        public String uid = "";
        public int typeRes;
        public String atqa = "?";
        public String sak = "?";
        public String[] techs = new String[0];
    }

    /** 常见门禁卡密钥：默认密钥 + 常见厂商密钥。 */
    public static final List<byte[]> DEFAULT_KEYS = new ArrayList<>();

    static {
        String[] keys = {
                "FFFFFFFFFFFF", "000000000000", "A0A1A2A3A4A5", "D3F7D3F7D3F7",
                "AABBCCDDEEFF", "B0B1B2B3B4B5", "4D3A99C351DD", "1A982C7E459A",
                "F0E1D2C3B4A5", "123456789ABC", "010203040506", "ABCDEF123456",
                "A1B2C3D4E5F6", "533CB6C723F6", "8FD0A4F256E9", "587EE5F9350F",
                "A0478CC39091", "714C5C886E97", "B5FF67CBA951", "A39FB0AE5D9B",
                "1A2B3C4D5E6F", "000000000001", "000000000002", "FFFFFF000000"
        };
        for (String k : keys) {
            byte[] b = Dump.unhex(k);
            if (b != null && b.length == 6) DEFAULT_KEYS.add(b);
        }
    }

    public static Info identify(Tag tag) {
        Info i = new Info();
        byte[] id = tag.getId();
        i.uid = Dump.hex(id);
        i.techs = tag.getTechList();
        int sak = -1, atqa = -1;
        NfcA na = NfcA.get(tag);
        if (na != null) {
            try {
                sak = na.getSak() & 0xff;
                byte[] a = na.getAtqa();
                if (a != null && a.length >= 2) {
                    atqa = ((a[0] & 0xff) << 8) | (a[1] & 0xff);
                }
            } catch (Exception ignored) {
            }
        }
        i.sak = sak < 0 ? "?" : String.format(Locale.US, "%02X", sak);
        i.atqa = atqa < 0 ? "?" : String.format(Locale.US, "%04X", atqa);
        i.typeRes = classify(tag, id, sak);
        return i;
    }

    private static int classify(Tag tag, byte[] id, int sak) {
        if (MifareClassic.get(tag) != null) {
            if (sak == 0x18 || sak == 0x38) return R.string.type_classic_4k;
            if (sak == 0x09) return R.string.type_classic_mini;
            return R.string.type_classic_1k;
        }
        if (id.length == 7 && sak == 0x00) return R.string.type_ultralight;
        if (sak == 0x20) return R.string.type_desfire;
        if (sak == 0x28) return R.string.type_cpu;
        if (id.length == 7) return R.string.type_iso_dep;
        if (id.length == 10) return R.string.type_iso15693;
        return R.string.type_unknown;
    }

    /** 连接卡，逐扇区试密钥并读出全部块。 */
    public static Dump read(Tag tag, Log log) {
        MifareClassic mc = MifareClassic.get(tag);
        if (mc == null) {
            log.line(R.string.log_not_classic);
            log.line(R.string.log_cpu_hint);
            return null;
        }
        try {
            mc.connect();
            Dump d = new Dump();
            d.uidHex = Dump.hex(tag.getId());
            NfcA na = NfcA.get(tag);
            if (na != null) {
                try {
                    d.sak = na.getSak() & 0xff;
                    byte[] a = na.getAtqa();
                    if (a != null && a.length >= 2) {
                        d.atqa = ((a[0] & 0xff) << 8) | (a[1] & 0xff);
                    }
                } catch (Exception ignored) {
                }
            }
            d.techs = tag.getTechList();
            d.sectors = mc.getSectorCount();
            int blocks = 0;
            for (int s = 0; s < d.sectors; s++) blocks += mc.getBlockCountInSector(s);
            d.raw = new byte[blocks * 16];
            log.line(R.string.log_geometry, d.sectors, blocks);

            List<byte[]> dict = new ArrayList<>(DEFAULT_KEYS);
            int opened = 0;
            for (int s = 0; s < d.sectors; s++) {
                boolean[] usedA = new boolean[1];
                byte[] key = findKey(mc, s, dict, usedA);
                if (key == null) {
                    log.line(R.string.log_sector_locked, s);
                    continue;
                }
                d.usedKey[s] = key;
                if (usedA[0]) d.keyA[s] = key;
                boolean okAll = readSector(mc, d, s);
                d.ok[s] = okAll;
                if (okAll) {
                    opened++;
                    byte[] kb = d.keyB[s];
                    if (kb != null && !isZero(kb)) addKey(dict, kb);
                    log.line(R.string.log_sector_ok, s, usedA[0] ? "A" : "B", Dump.hex(key));
                } else {
                    log.line(R.string.log_sector_partial, s);
                }
            }
            log.line(R.string.log_opened, opened, d.sectors);
            return d;
        } catch (IOException e) {
            log.line(R.string.log_io_error, String.valueOf(e.getMessage()));
            return null;
        } finally {
            try {
                mc.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean readSector(MifareClassic mc, Dump d, int s) {
        int first = mc.sectorToBlock(s);
        int n = mc.getBlockCountInSector(s);
        boolean all = true;
        for (int i = 0; i < n; i++) {
            int b = first + i;
            try {
                byte[] data = mc.readBlock(b);
                System.arraycopy(data, 0, d.raw, b * 16, 16);
            } catch (IOException e) {
                all = false;
            }
        }
        int trailer = first + n - 1;
        int off = trailer * 16;
        d.access[s] = Arrays.copyOfRange(d.raw, off + 6, off + 10);
        d.keyB[s] = Arrays.copyOfRange(d.raw, off + 10, off + 16);
        if (d.keyB[s] != null && isZero(d.keyB[s])) d.keyB[s] = null;
        return all;
    }

    /** 把 dump 写进目标卡。先写块 0（UID），其余块按扇区写，扇区尾块最后写。 */
    public static void write(Tag tag, Dump d, Log log) {
        MifareClassic mc = MifareClassic.get(tag);
        if (mc == null) {
            log.line(R.string.log_target_not_classic);
            return;
        }
        if (d == null || d.raw.length < 16) {
            log.line(R.string.log_source_empty);
            return;
        }
        try {
            mc.connect();
            int tSectors = mc.getSectorCount();
            int tBlocks = 0;
            for (int s = 0; s < tSectors; s++) tBlocks += mc.getBlockCountInSector(s);
            int srcBlocks = d.raw.length / 16;
            if (srcBlocks > tBlocks) {
                log.line(R.string.log_capacity, srcBlocks, tBlocks);
                return;
            }
            log.line(R.string.log_write_start, srcBlocks);

            byte[] block0 = Arrays.copyOfRange(d.raw, 0, 16);
            boolean uidOk = writeBlock0(tag, mc, block0, d, log);
            log.line(uidOk ? R.string.log_uid_ok : R.string.log_uid_locked);

            int done = 0, fail = 0;
            for (int s = 0; s < d.sectors && s < tSectors; s++) {
                if (!d.ok[s]) {
                    log.line(R.string.log_skip_sector, s);
                    continue;
                }
                byte[] key = null;
                boolean keyIsA = false;
                for (byte[] cand : candidates(d, s)) {
                    if (auth(mc, s, true, cand)) {
                        key = cand;
                        keyIsA = true;
                        break;
                    }
                    if (auth(mc, s, false, cand)) {
                        key = cand;
                        keyIsA = false;
                        break;
                    }
                }
                if (key == null) {
                    log.line(R.string.log_auth_failed, s);
                    continue;
                }
                int first = mc.sectorToBlock(s);
                int n = mc.getBlockCountInSector(s);
                int trailerIdx = first + n - 1;
                int start = (s == 0) ? 1 : 0;
                for (int i = start; i < n; i++) {
                    int b = first + i;
                    byte[] data = Arrays.copyOfRange(d.raw, b * 16, b * 16 + 16);
                    if (b == trailerIdx) {
                        // dump 里 Key A 读不到，永远是 6 个 0；直接写会把卡锁成 0 密钥。
                        // 用源卡已知的 Key A 补回去，Key B 同理。
                        if (d.keyA[s] != null && d.keyA[s].length == 6) {
                            System.arraycopy(d.keyA[s], 0, data, 0, 6);
                        } else if (keyIsA) {
                            System.arraycopy(key, 0, data, 0, 6);
                        }
                        if (d.keyB[s] != null && d.keyB[s].length == 6) {
                            System.arraycopy(d.keyB[s], 0, data, 10, 6);
                        }
                    }
                    try {
                        mc.writeBlock(b, data);
                        done++;
                    } catch (IOException e) {
                        fail++;
                    }
                }
            }
            log.line(R.string.log_write_done, done, fail);

            int bad = 0;
            for (int s = 0; s < d.sectors && s < tSectors; s++) {
                if (!d.ok[s]) continue;
                byte[] key = null;
                for (byte[] cand : candidates(d, s)) {
                    if (auth(mc, s, true, cand) || auth(mc, s, false, cand)) {
                        key = cand;
                        break;
                    }
                }
                if (key == null) {
                    bad++;
                    continue;
                }
                int first = mc.sectorToBlock(s);
                int n = mc.getBlockCountInSector(s);
                for (int i = 0; i < n; i++) {
                    int b = first + i;
                    try {
                        byte[] got = mc.readBlock(b);
                        byte[] want = Arrays.copyOfRange(d.raw, b * 16, b * 16 + 16);
                        if (!Arrays.equals(got, want)) bad++;
                    } catch (IOException e) {
                        bad++;
                    }
                }
            }
            log.line(bad == 0 ? R.string.log_verify_ok : R.string.log_verify_bad, bad);
        } catch (IOException e) {
            log.line(R.string.log_write_error, String.valueOf(e.getMessage()));
        } finally {
            try {
                mc.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean writeBlock0(Tag tag, MifareClassic mc, byte[] block0, Dump d, Log log) {
        for (byte[] cand : candidates(d, 0)) {
            if (auth(mc, 0, true, cand) || auth(mc, 0, false, cand)) {
                try {
                    mc.writeBlock(0, block0);
                    return true;
                } catch (IOException ignored) {
                }
            }
        }
        NfcA na = NfcA.get(tag);
        if (na != null) {
            try {
                na.transceive(new byte[]{0x40});
                na.transceive(new byte[]{0x43});
                mc.writeBlock(0, block0);
                return true;
            } catch (Exception e) {
                log.line(R.string.log_gen1a);
            }
        }
        return false;
    }

    private static List<byte[]> candidates(Dump d, int s) {
        List<byte[]> out = new ArrayList<>();
        if (d.usedKey[s] != null) out.add(d.usedKey[s]);
        if (d.keyA[s] != null && !isZero(d.keyA[s])) out.add(d.keyA[s]);
        if (d.keyB[s] != null && !isZero(d.keyB[s])) out.add(d.keyB[s]);
        out.addAll(DEFAULT_KEYS);
        return out;
    }

    private static byte[] findKey(MifareClassic mc, int s, List<byte[]> dict, boolean[] usedA) {
        for (byte[] k : dict) {
            if (auth(mc, s, true, k)) {
                usedA[0] = true;
                return k;
            }
        }
        for (byte[] k : dict) {
            if (auth(mc, s, false, k)) {
                usedA[0] = false;
                return k;
            }
        }
        return null;
    }

    private static boolean auth(MifareClassic mc, int s, boolean keyA, byte[] key) {
        if (key == null || key.length != 6) return false;
        try {
            return keyA ? mc.authenticateSectorWithKeyA(s, key)
                    : mc.authenticateSectorWithKeyB(s, key);
        } catch (IOException e) {
            return false;
        }
    }

    private static void addKey(List<byte[]> dict, byte[] k) {
        for (byte[] x : dict) if (Arrays.equals(x, k)) return;
        dict.add(0, Arrays.copyOf(k, 6));
    }

    private static boolean isZero(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    /** 一条对比差异，文案在界面层用资源拼。 */
    public static class Detail {
        public final int resId;
        public final Object[] args;

        public Detail(int resId, Object... args) {
            this.resId = resId;
            this.args = args;
        }
    }

    /** 基准卡 vs 复制卡 的对比结果。 */
    public static class CompareResult {
        public int blocks;
        public int byteDiff;
        public int blockDiff;
        public int keyDiff;
        public boolean uidSame;
        public boolean sizeSame = true;
        public String uidA = "";
        public String uidB = "";
        public final List<Detail> details = new ArrayList<>();

        public boolean identical() {
            return blocks > 0 && byteDiff == 0 && blockDiff == 0 && keyDiff == 0
                    && uidSame && sizeSame;
        }
    }

    public static CompareResult compare(Dump a, Dump b) {
        CompareResult r = new CompareResult();
        r.uidA = a.uidHex;
        r.uidB = b.uidHex;
        r.uidSame = a.uidHex.equalsIgnoreCase(b.uidHex);
        int n = Math.min(a.raw.length, b.raw.length);
        r.blocks = n / 16;
        for (int blk = 0; blk < r.blocks; blk++) {
            boolean same = true;
            for (int j = 0; j < 16; j++) {
                if (a.raw[blk * 16 + j] != b.raw[blk * 16 + j]) {
                    same = false;
                    r.byteDiff++;
                }
            }
            if (!same) {
                r.blockDiff++;
                if (r.details.size() < 8) r.details.add(new Detail(R.string.cmp_block_diff, blk));
            }
        }
        int secs = Math.min(a.sectors, b.sectors);
        for (int s = 0; s < secs; s++) {
            if (!sameBytes(a.usedKey[s], b.usedKey[s])) {
                r.keyDiff++;
                if (r.details.size() < 10) {
                    r.details.add(new Detail(R.string.cmp_key_diff, s,
                            a.usedKey[s] == null ? null : Dump.hex(a.usedKey[s]),
                            b.usedKey[s] == null ? null : Dump.hex(b.usedKey[s])));
                }
            }
        }
        if (a.raw.length != b.raw.length) {
            r.sizeSame = false;
            r.details.add(new Detail(R.string.cmp_capacity,
                    a.raw.length / 16, b.raw.length / 16));
        }
        return r;
    }

    private static boolean sameBytes(byte[] x, byte[] y) {
        if (x == null && y == null) return true;
        if (x == null || y == null) return false;
        return Arrays.equals(x, y);
    }
}
