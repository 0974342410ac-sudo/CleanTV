import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Adler32;
import java.util.zip.CRC32;

/**
 * Reads and rebuilds a partition an MStar USB bin writes with "mmc unlzo" (XGIMI H2, msd838), for build-firmware-h2-vi.ps1.
 * Such a partition is a row of pieces, each an lzop file of 256 KB blocks. A block whose stored size equals its real size
 * is not compressed, which the original bins already use, so a changed block is stored that way. Every other block, and
 * every other piece, stays byte for byte. A changed piece goes after the original data and the script points at it.
 *
 *   unpack bin part out.img           the partition's raw image (checks each block's Adler32)
 *   pack   bin part new.img out.bin   new bin with new.img as the partition
 *   verify bin part image             the bin's partition equals the image
 *   compare a.bin b.bin               the original data is unchanged; lists the script lines that differ
 */
public class FirmwareLzo {
    private static final int SCRIPT = 0x4000, FOOTER = 32, ALIGN = 4096, ADLER_D = 1, ADLER_C = 2, CRC_D = 0x100, CRC_C = 0x200;
    private static final long MAX_PIECE = 0xA000000L;   // the largest piece in the original H2 bins
    private static final String END_OF_SCRIPT = "% <- this is end of script symbol";

    public static void main(String[] a) throws Exception {
        switch (a[0]) {
            case "unpack": unpack(a[1], a[2], a[3]); break;
            case "pack": pack(a[1], a[2], a[3], a[4]); break;
            case "verify": verify(a[1], a[2], a[3]); break;
            case "compare": compare(a[1], a[2]); break;
            default: throw new IllegalArgumentException(a[0]);
        }
    }

    private static final class Piece {
        long off, size;
        int start, end;   // the two script lines: filepartload and mmc unlzo
    }

    private static String script(RandomAccessFile f) throws IOException {
        byte[] head = new byte[SCRIPT];
        f.seek(0);
        f.readFully(head);
        return new String(head, StandardCharsets.ISO_8859_1);
    }

    private static List<Piece> pieces(String script, String part) throws IOException {
        int at = script.indexOf("# File Partition: " + part + "\n");
        if (at < 0) throw new IOException("no partition " + part);
        int stop = script.indexOf("# File Partition: ", at + 1);
        if (stop < 0) stop = script.length();
        List<Piece> out = new ArrayList<>();
        Matcher m = Pattern.compile("filepartload (\\S+) \\$\\(UpgradeImage\\) 0x([0-9a-fA-F]+) 0x([0-9a-fA-F]+)\\n"
                + "mmc (unlzo(?:\\.cont)?) \\1 0x([0-9a-fA-F]+) " + Pattern.quote(part) + " 1\\n")
                .matcher(script).region(at, stop);
        while (m.find()) {
            Piece p = new Piece();
            p.off = Long.parseLong(m.group(2), 16);
            p.size = Long.parseLong(m.group(3), 16);
            if (p.size != Long.parseLong(m.group(5), 16)) throw new IOException("sizes differ in script at " + m.group(2));
            p.start = m.start();
            p.end = m.end();
            out.add(p);
        }
        if (out.isEmpty()) throw new IOException("no unlzo pieces for " + part);
        return out;
    }

    private static byte[] read(RandomAccessFile f, Piece p) throws IOException {
        byte[] d = new byte[(int) p.size];
        f.seek(p.off);
        f.readFully(d);
        return d;
    }

    /** Where the first block starts, after the lzop header. */
    private static int headerEnd(ByteBuffer b, int[] flagsOut) throws IOException {
        if (b.getInt(0) != 0x894C5A4F) throw new IOException("not lzop");
        b.position(9);
        int version = b.getShort() & 0xffff;
        b.getShort();
        if (version >= 0x940) b.getShort();
        b.get();
        if (version >= 0x940) b.get();
        int flags = b.getInt();
        if ((flags & 0x800) != 0) b.getInt();
        b.getInt();
        b.getInt();
        if (version >= 0x940) b.getInt();
        int name = b.get() & 0xff;
        b.position(b.position() + name + 4);
        if ((flags & 0x40) != 0) {
            int n = b.getInt();
            b.position(b.position() + n + 4);
        }
        if ((flags & (CRC_D | CRC_C)) != 0) throw new IOException("CRC32 blocks not handled");
        flagsOut[0] = flags;
        return b.position();
    }

    private interface Block {
        void at(int dst, int src, int headerAt, int dataAt, long check) throws IOException;
    }

    private static void blocks(byte[] piece, Block each) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(piece);
        int[] flags = new int[1];
        headerEnd(b, flags);
        while (true) {
            int headerAt = b.position();
            int dst = b.getInt();
            if (dst == 0) break;
            int src = b.getInt();
            long check = (flags[0] & ADLER_D) != 0 ? b.getInt() & 0xffffffffL : -1;
            if (src < dst && (flags[0] & ADLER_C) != 0) b.getInt();
            each.at(dst, src, headerAt, b.position(), check);
            b.position(b.position() + src);
        }
        if (b.hasRemaining()) throw new IOException("data after the end of an lzop piece");
    }

    private static byte[] decode(byte[] piece, int dst, int src, int dataAt, long check, byte[] work) throws IOException {
        byte[] out;
        if (src < dst) {
            if (lzo1x(piece, dataAt, work) != dst) throw new IOException("block size");
            out = work;
        } else {
            System.arraycopy(piece, dataAt, work, 0, dst);
            out = work;
        }
        if (check >= 0 && adler(out, dst) != check) throw new IOException("Adler32 differs");
        return out;
    }

    private static long adler(byte[] d, int n) {
        Adler32 a = new Adler32();
        a.update(d, 0, n);
        return a.getValue();
    }

    private static void unpack(String bin, String part, String to) throws Exception {
        try (RandomAccessFile f = new RandomAccessFile(bin, "r");
             BufferedOutputStream o = new BufferedOutputStream(new FileOutputStream(to), 1 << 20)) {
            byte[] work = new byte[1 << 22];
            long[] total = {0};
            for (Piece p : pieces(script(f), part)) {
                byte[] d = read(f, p);
                blocks(d, (dst, src, h, at, check) -> {
                    o.write(decode(d, dst, src, at, check, work), 0, dst);
                    total[0] += dst;
                });
            }
            System.out.printf("%s: %d bytes%n", part, total[0]);
        }
    }

    private static void verify(String bin, String part, String image) throws Exception {
        try (RandomAccessFile f = new RandomAccessFile(bin, "r"); RandomAccessFile im = new RandomAccessFile(image, "r")) {
            byte[] work = new byte[1 << 22], want = new byte[1 << 22];
            long[] total = {0};
            for (Piece p : pieces(script(f), part)) {
                byte[] d = read(f, p);
                blocks(d, (dst, src, h, at, check) -> {
                    byte[] got = decode(d, dst, src, at, check, work);
                    im.readFully(want, 0, dst);
                    if (!Arrays.equals(got, 0, dst, want, 0, dst)) throw new IOException("differs at " + total[0]);
                    total[0] += dst;
                });
            }
            if (total[0] != im.length()) throw new IOException("image is " + im.length() + ", partition " + total[0]);
            System.out.printf("%s in the bin equals %s (%d bytes)%n", part, image, total[0]);
        }
    }

    private static void pack(String bin, String part, String image, String out) throws Exception {
        if (new File(out).getCanonicalPath().equals(new File(bin).getCanonicalPath())) throw new IOException("out is the input");
        try (RandomAccessFile f = new RandomAccessFile(bin, "r"); RandomAccessFile im = new RandomAccessFile(image, "r");
             RandomAccessFile o = new RandomAccessFile(out, "rw")) {
            o.setLength(0);
            String s = script(f);
            long body = f.length() - FOOTER;
            if (body % ALIGN != 0) throw new IOException("body not 4 KB aligned");
            List<Piece> list = pieces(s, part);
            byte[] work = new byte[1 << 22], want = new byte[1 << 22];

            // Script and original data first; the script is written again at the end
            o.write(new byte[SCRIPT]);
            copy(f, o, SCRIPT, body - SCRIPT);
            long tail = body;
            StringBuilder patched = new StringBuilder();
            int last = 0, changedPieces = 0;
            int[] changedBlocks = {0};
            long[] total = {0};
            for (Piece p : list) {
                byte[] d = read(f, p);
                ByteArrayOutputStream fresh = new ByteArrayOutputStream(d.length + (1 << 20));
                ByteBuffer hb = ByteBuffer.wrap(d);
                int[] flags = new int[1];
                int first = headerEnd(hb, flags);
                fresh.write(d, 0, first);
                boolean[] changed = {false};
                int[] blocksIn = {0};
                blocks(d, (dst, src, h, at, check) -> {
                    byte[] got = decode(d, dst, src, at, check, work);
                    im.readFully(want, 0, dst);
                    total[0] += dst;
                    blocksIn[0]++;
                    if (Arrays.equals(got, 0, dst, want, 0, dst)) {
                        fresh.write(d, h, at + src - h);
                        return;
                    }
                    changed[0] = true;
                    changedBlocks[0]++;
                    ByteBuffer nh = ByteBuffer.allocate(12);
                    nh.putInt(dst).putInt(dst);
                    if ((flags[0] & ADLER_D) != 0) nh.putInt((int) adler(want, dst));
                    fresh.write(nh.array(), 0, nh.position());
                    fresh.write(want, 0, dst);
                });
                if (!changed[0]) continue;
                fresh.write(new byte[4]);
                if (fresh.size() > MAX_PIECE) throw new IOException("piece over the load buffer: " + fresh.size());
                changedPieces++;
                o.seek(tail);
                o.write(fresh.toByteArray());
                long pos = o.getFilePointer();
                byte[] pad = new byte[(int) ((ALIGN - pos % ALIGN) % ALIGN)];
                Arrays.fill(pad, (byte) 0xFF);
                o.write(pad);
                String old = s.substring(p.start, p.end);
                String line = old
                        .replaceFirst("0x[0-9a-fA-F]+ 0x[0-9a-fA-F]+\\n", String.format("0x%x 0x%x\n", tail, fresh.size()))
                        .replaceFirst("(mmc unlzo(?:\\.cont)? \\S+) 0x[0-9a-fA-F]+", String.format("$1 0x%X", fresh.size()));
                patched.append(s, last, p.start).append(line);
                last = p.end;
                System.out.printf("piece at 0x%x: %d of %d blocks changed, now at 0x%x size 0x%x (was 0x%x)%n",
                        p.off, changedBlocks[0], blocksIn[0], tail, fresh.size(), p.size);
                tail = o.getFilePointer();
                changedBlocks[0] = 0;
            }
            if (changedPieces == 0) throw new IOException("nothing changed");
            patched.append(s, last, list.get(list.size() - 1).end);
            last = list.get(list.size() - 1).end;
            long grown = im.length() - total[0];
            if (grown < 0 || grown % 512 != 0) throw new IOException("image is " + im.length() + ", partition " + total[0]);
            if (grown > 0) {
                // New files lie past the end of the original image: one more piece, written on after the others
                byte[] d = read(f, list.get(0));
                ByteBuffer hb = ByteBuffer.wrap(d);
                int[] flags = new int[1];
                int first = headerEnd(hb, flags);
                ByteArrayOutputStream fresh = new ByteArrayOutputStream();
                fresh.write(d, 0, first);
                for (long left = grown; left > 0; ) {
                    int dst = (int) Math.min(left, 1 << 18);
                    im.readFully(want, 0, dst);
                    ByteBuffer nh = ByteBuffer.allocate(12);
                    nh.putInt(dst).putInt(dst);
                    if ((flags[0] & ADLER_D) != 0) nh.putInt((int) adler(want, dst));
                    fresh.write(nh.array(), 0, nh.position());
                    fresh.write(want, 0, dst);
                    left -= dst;
                }
                fresh.write(new byte[4]);
                o.seek(tail);
                o.write(fresh.toByteArray());
                long pos = o.getFilePointer();
                byte[] pad = new byte[(int) ((ALIGN - pos % ALIGN) % ALIGN)];
                Arrays.fill(pad, (byte) 0xFF);
                o.write(pad);
                String addr = s.substring(list.get(0).start).split(" ")[1];
                patched.append(String.format("filepartload %s $(UpgradeImage) 0x%x 0x%x\nmmc unlzo.cont %s 0x%X %s 1\n",
                        addr, tail, fresh.size(), addr, fresh.size(), part));
                System.out.printf("%d bytes past the original end: new piece at 0x%x size 0x%x%n", grown, tail, fresh.size());
                tail = o.getFilePointer();
            }
            patched.append(s.substring(last));

            // Same 0x4000 bytes: the padding after the end symbol takes up the difference
            String t = patched.toString();
            int end = t.indexOf(END_OF_SCRIPT) + END_OF_SCRIPT.length();
            if (end >= SCRIPT) throw new IOException("script full");
            byte fill = (byte) s.charAt(SCRIPT - 1);
            byte[] head = new byte[SCRIPT];
            Arrays.fill(head, fill);
            byte[] all = t.getBytes(StandardCharsets.ISO_8859_1);
            System.arraycopy(all, 0, head, 0, Math.min(all.length, SCRIPT));
            o.seek(0);
            o.write(head);

            o.seek(tail);
            o.setLength(tail);
            ByteArrayOutputStream foot = new ByteArrayOutputStream();
            foot.write("12345678".getBytes(StandardCharsets.US_ASCII));
            CRC32 c = new CRC32();
            c.update(head);
            foot.write(leBytes(c.getValue()));
            o.write(foot.toByteArray());
            long fileCrc = crc(o, o.length());
            o.seek(o.length());
            o.write(leBytes(fileCrc));
            o.write(head, 0, 16);
            System.out.printf("packed %s: %d bytes, %d pieces rewritten%n", out, o.length(), changedPieces);
        }
    }

    private static void copy(RandomAccessFile f, RandomAccessFile o, long from, long n) throws IOException {
        byte[] b = new byte[1 << 20];
        f.seek(from);
        for (long left = n; left > 0; ) {
            int r = f.read(b, 0, (int) Math.min(left, b.length));
            if (r < 0) throw new IOException("short file");
            o.write(b, 0, r);
            left -= r;
        }
    }

    private static long crc(RandomAccessFile f, long to) throws IOException {
        CRC32 c = new CRC32();
        byte[] b = new byte[1 << 20];
        f.seek(0);
        for (long left = to; left > 0; ) {
            int r = f.read(b, 0, (int) Math.min(left, b.length));
            c.update(b, 0, r);
            left -= r;
        }
        return c.getValue();
    }

    private static byte[] leBytes(long v) {
        return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    private static void compare(String a, String b) throws Exception {
        try (RandomAccessFile x = new RandomAccessFile(a, "r"); RandomAccessFile y = new RandomAccessFile(b, "r")) {
            long end = x.length() - FOOTER;
            byte[] p = new byte[1 << 20], q = new byte[1 << 20];
            x.seek(SCRIPT);
            y.seek(SCRIPT);
            for (long left = end - SCRIPT; left > 0; ) {
                int n = (int) Math.min(left, p.length);
                x.readFully(p, 0, n);
                y.readFully(q, 0, n);
                if (!Arrays.equals(p, 0, n, q, 0, n)) throw new IOException("data differs near " + (end - left));
                left -= n;
            }
            System.out.printf("all %d data bytes of the original are unchanged%n", end - SCRIPT);
            String[] la = cut(script(x)).split("\n"), lb = cut(script(y)).split("\n");
            String ours = "(filepartload|mmc unlzo).*";
            int diff = 0, added = 0, i = 0, j = 0;
            while (i < la.length || j < lb.length) {
                if (i < la.length && j < lb.length && la[i].equals(lb[j])) {
                    i++;
                    j++;
                } else if (j < lb.length && lb[j].matches(ours) && i < la.length && la[i].matches(ours)) {
                    System.out.println("  - " + la[i++] + "\n  + " + lb[j++]);
                    diff++;
                } else if (j < lb.length && lb[j].matches(ours)) {
                    System.out.println("  + " + lb[j++]);
                    added++;
                } else {
                    throw new IOException("other script line differs: " + (i < la.length ? la[i] : "(end)"));
                }
            }
            System.out.println("script lines changed: " + diff + ", added: " + added);
        }
    }

    private static String cut(String s) {
        return s.substring(0, s.indexOf(END_OF_SCRIPT));
    }

    /** LZO1X decompression from s at p into d; returns the size written. */
    private static int lzo1x(byte[] s, int p, byte[] d) throws IOException {
        int op = 0, t = 0, pos;
        final int LOOP = 0, FIRST = 1, MATCH = 2, DONE = 3, NEXT = 4;
        int state = LOOP;
        if ((s[p] & 0xff) > 17) {
            t = (s[p++] & 0xff) - 17;
            if (t < 4) state = NEXT;
            else {
                System.arraycopy(s, p, d, op, t);
                p += t;
                op += t;
                state = FIRST;
            }
        }
        while (true) {
            switch (state) {
                case LOOP:
                    t = s[p++] & 0xff;
                    if (t >= 16) { state = MATCH; break; }
                    if (t == 0) {
                        while (s[p] == 0) { t += 255; p++; }
                        t += 15 + (s[p++] & 0xff);
                    }
                    System.arraycopy(s, p, d, op, t + 3);
                    p += t + 3;
                    op += t + 3;
                    state = FIRST;
                    break;
                case FIRST:
                    t = s[p++] & 0xff;
                    if (t >= 16) { state = MATCH; break; }
                    pos = op - (1 + 0x0800) - (t >> 2) - ((s[p++] & 0xff) << 2);
                    d[op++] = d[pos++];
                    d[op++] = d[pos++];
                    d[op++] = d[pos];
                    state = DONE;
                    break;
                case MATCH:
                    if (t >= 64) {
                        pos = op - 1 - ((t >> 2) & 7) - ((s[p++] & 0xff) << 3);
                        t = (t >> 5) - 1;
                    } else if (t >= 32) {
                        t &= 31;
                        if (t == 0) {
                            while (s[p] == 0) { t += 255; p++; }
                            t += 31 + (s[p++] & 0xff);
                        }
                        pos = op - 1 - ((s[p] & 0xff) >> 2) - ((s[p + 1] & 0xff) << 6);
                        p += 2;
                    } else if (t >= 16) {
                        pos = op - ((t & 8) << 11);
                        t &= 7;
                        if (t == 0) {
                            while (s[p] == 0) { t += 255; p++; }
                            t += 7 + (s[p++] & 0xff);
                        }
                        pos -= ((s[p] & 0xff) >> 2) + ((s[p + 1] & 0xff) << 6);
                        p += 2;
                        if (pos == op) return op;
                        pos -= 0x4000;
                    } else {
                        pos = op - 1 - (t >> 2) - ((s[p++] & 0xff) << 2);
                        d[op++] = d[pos++];
                        d[op++] = d[pos];
                        state = DONE;
                        break;
                    }
                    for (int i = 0; i < t + 2; i++) d[op++] = d[pos++];
                    state = DONE;
                    break;
                case DONE:
                    t = s[p - 2] & 3;
                    state = t == 0 ? LOOP : NEXT;
                    break;
                case NEXT:
                    for (int i = 0; i < t; i++) d[op++] = s[p++];
                    t = s[p++] & 0xff;
                    state = MATCH;
                    break;
                default:
                    throw new IOException("state");
            }
        }
    }
}
