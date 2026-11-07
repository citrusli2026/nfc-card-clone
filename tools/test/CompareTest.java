import dev.dsh.nfc.CardOps;
import dev.dsh.nfc.Dump;

public class CompareTest {
    static int fail = 0;

    static Dump make(int blocks, String uid) {
        Dump d = new Dump();
        d.raw = new byte[blocks * 16];
        d.sectors = blocks / 4;
        d.uidHex = uid;
        d.keyA = new byte[Dump.MAX_SECTORS][];
        d.keyB = new byte[Dump.MAX_SECTORS][];
        d.usedKey = new byte[Dump.MAX_SECTORS][];
        d.access = new byte[Dump.MAX_SECTORS][];
        d.ok = new boolean[Dump.MAX_SECTORS];
        for (int s = 0; s < d.sectors; s++) {
            d.ok[s] = true;
            d.usedKey[s] = Dump.unhex("FFFFFFFFFFFF");
        }
        return d;
    }

    static void check(String name, boolean cond) {
        System.out.println((cond ? "PASS  " : "FAIL  ") + name);
        if (!cond) fail++;
    }

    public static void main(String[] a) {
        Dump base = make(64, "A1B2C3D4");
        Dump copy = make(64, "A1B2C3D4");

        CardOps.CompareResult r = CardOps.compare(base, copy);
        check("完全相同 -> identical()", r.identical());
        check("块数 64", r.blocks == 64);
        check("0 字节差异", r.byteDiff == 0);

        copy.raw[5] ^= 0x01;
        r = CardOps.compare(base, copy);
        check("改 1 字节 -> 不通过", !r.identical());
        check("byteDiff=1", r.byteDiff == 1);
        check("blockDiff=1", r.blockDiff == 1);
        copy.raw[5] ^= 0x01;

        copy.usedKey[3] = Dump.unhex("000000000000");
        r = CardOps.compare(base, copy);
        check("密钥不同 -> 不通过", !r.identical());
        check("keyDiff=1", r.keyDiff == 1);
        check("提示带扇区 3", r.details.size() == 1 && Integer.valueOf(3).equals(r.details.get(0).args[0]));
        copy.usedKey[3] = Dump.unhex("FFFFFFFFFFFF");

        Dump other = make(64, "11223344");
        r = CardOps.compare(base, other);
        check("UID 不同 -> 不通过", !r.identical() && !r.uidSame);

        Dump small = make(16, "A1B2C3D4");
        r = CardOps.compare(base, small);
        check("容量不同 -> 不通过", !r.identical());

        System.out.println(fail == 0 ? "\n全部通过" : "\n失败 " + fail + " 项");
        System.exit(fail == 0 ? 0 : 1);
    }
}
