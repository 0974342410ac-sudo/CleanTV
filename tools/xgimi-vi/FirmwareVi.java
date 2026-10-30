import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * Reads and rebuilds an MStar/MediaTek USB upgrade bin (m7642), for build-firmware-vi.ps1.
 * File layout: a 0x4000-byte mboot script, the pieces it loads with filepartload, 0xFF up to 4 KB, then a 32-byte footer:
 * "12345678", CRC32 of the script, CRC32 of the whole file up to there, the file's first 16 bytes.
 * Partitions like vendor are written piece by piece with sparse_write, so one more sparse piece holding only the changed
 * blocks changes the partition while every original piece stays byte for byte.
 *
 *   unpack  bin dir part...      raw image of each sparse partition
 *   footer  bin                  checks the footer's two CRCs
 *   delta   old.img new.img out  sparse image of the blocks that differ
 *   pack    bin delta part out   new bin with delta added to part's pieces
 *   compare a.bin b.bin          every byte outside the script, the added pieces and the footer must match
 */
public class FirmwareVi {
    private static final int SCRIPT = 0x4000, FOOTER = 32, ALIGN = 4096, SPARSE_MAGIC = 0xED26FF3A;
    private static final int CHUNK_RAW = 0xCAC1, CHUNK_FILL = 0xCAC2, CHUNK_SKIP = 0xCAC3, CHUNK_CRC = 0xCAC4;
    private static final int MAX_PIECE = 0x6400000;      // what the original pieces stay under (the load buffer)
    private static final String END_OF_SCRIPT = "% <- this is end of script symbol";
    private static final byte[] BUF = new byte[1 << 20];

    public static void main(String[] a) throws Exception {
        switch (a[0]) {
            case "unpack": unpack(a); break;
            case "footer": footer(a[1]); break;
            case "delta": delta(a[1], a[2], a[3]); break;
            case "pack": pack(a[1], a[2], a[3], a[4]); break;
            case "compare": compare(a[1], a[2]); break;
            default: throw new IllegalArgumentException(a[0]);
        }
    }

    private static String script(RandomAccessFile f) throws IOException {
        byte[] head = new byte[SCRIPT];
        f.seek(0);
        f.readFully(head);
        return new String(head, StandardCharsets.ISO_8859_1);
    }

    /** Partition name -> its {offset, size} pieces, in script order. */
    private static Map<String, List<long[]>> pieces(String script) {
        Map<String, List<long[]>> parts = new LinkedHashMap<>();
        String current = null;
        Matcher m = Pattern.compile("# File Partition: (\\S+)|filepartload \\S+ \\S+ 0x([0-9a-fA-F]+) 0x([0-9a-fA-F]+)").matcher(script);
        while (m.find()) {
            if (m.group(1) != null) {
                current = m.group(1);
                parts.putIfAbsent(current, new ArrayList<>());
            } else {
                parts.get(current).add(new long[]{Long.parseLong(m.group(2), 16), Long.parseLong(m.group(3), 16)});
            }
        }
        return parts;
    }

    private static void unpack(String[] a) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(a[1], "r")) {
            Map<String, List<long[]>> parts = pieces(script(f));
            for (String name : Arrays.asList(a).subList(3, a.length)) {
                File to = new File(a[2], name + ".img");
                unsparse(f, parts.get(name), to);
                System.out.printf("%s: %d bytes from %d pieces%n", to.getName(), to.length(), parts.get(name).size());
            }
        }
    }

    private static void unsparse(RandomAccessFile in, List<long[]> pieces, File to) throws IOException {
        try (RandomAccessFile o = new RandomAccessFile(to, "rw")) {
            o.setLength(0);
            long end = 0;
            for (long[] piece : pieces) {
                in.seek(piece[0]);
                ByteBuffer h = read(in, 28);
                if (h.getInt() != SPARSE_MAGIC) throw new IOException("not sparse at " + piece[0]);
                h.getInt();
                int fileHdr = h.getShort() & 0xffff, chunkHdr = h.getShort() & 0xffff, blk = h.getInt();
                h.getInt();
                int chunks = h.getInt();
                in.seek(piece[0] + fileHdr);
                long at = 0;
                for (int c = 0; c < chunks; c++) {
                    ByteBuffer ch = read(in, chunkHdr);
                    int type = ch.getShort() & 0xffff;
                    ch.getShort();
                    long blocks = ch.getInt() & 0xffffffffL, data = (ch.getInt() & 0xffffffffL) - chunkHdr;
                    if (type == CHUNK_RAW) {
                        o.seek(at);
                        for (long left = data; left > 0; ) {
                            int n = in.read(BUF, 0, (int) Math.min(left, BUF.length));
                            if (n < 0) throw new EOFException();
                            o.write(BUF, 0, n);
                            left -= n;
                        }
                    } else if (type == CHUNK_FILL) {
                        byte[] v = new byte[4];
                        in.readFully(v);
                        if (v[0] != 0 || v[1] != 0 || v[2] != 0 || v[3] != 0) {
                            byte[] fill = new byte[blk];
                            for (int i = 0; i < blk; i++) fill[i] = v[i % 4];
                            o.seek(at);
                            for (long b = 0; b < blocks; b++) o.write(fill);
                        }
                    } else if (type == CHUNK_CRC) {
                        in.skipBytes((int) data);
                        continue;
                    } else if (type != CHUNK_SKIP) {
                        throw new IOException("chunk type " + Integer.toHexString(type));
                    }
                    at += blocks * blk;
                }
                end = Math.max(end, at);
            }
            if (o.length() < end) o.setLength(end);
        }
    }

    private static void footer(String bin) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(bin, "r")) {
            long len = f.length();
            byte[] t = new byte[FOOTER];
            f.seek(len - FOOTER);
            f.readFully(t);
            byte[] head = new byte[SCRIPT];
            f.seek(0);
            f.readFully(head);
            CRC32 c = new CRC32();
            c.update(head);
            long scriptCrc = c.getValue();
            long fileCrc = crc(f, 0, len - FOOTER + 12);
            boolean first16 = Arrays.equals(Arrays.copyOfRange(t, 16, 32), Arrays.copyOf(head, 16));
            System.out.printf("magic %s, script crc %08x/%08x, file crc %08x/%08x, first bytes %s%n", new String(t, 0, 8),
                    le(t, 8), scriptCrc, le(t, 12), fileCrc, first16);
            if (le(t, 8) != scriptCrc || le(t, 12) != fileCrc || !first16) throw new IOException("footer does not match");
        }
    }

    private static void delta(String oldImg, String newImg, String out) throws IOException {
        int blk = 4096;
        List<long[]> runs = new ArrayList<>();
        long total;
        try (RandomAccessFile a = new RandomAccessFile(oldImg, "r"); RandomAccessFile b = new RandomAccessFile(newImg, "r")) {
            if (a.length() != b.length() || a.length() % blk != 0) throw new IOException("image sizes differ");
            total = a.length() / blk;
            byte[] x = new byte[blk], y = new byte[blk];
            long start = -1;
            for (long i = 0; i < total; i++) {
                a.readFully(x);
                b.readFully(y);
                boolean same = Arrays.equals(x, y);
                if (!same && start < 0) start = i;
                if (same && start >= 0) {
                    runs.add(new long[]{start, i - start});
                    start = -1;
                }
            }
            if (start >= 0) runs.add(new long[]{start, total - start});
            int chunks = 0;
            long at = 0, rawBlocks = 0;
            for (long[] r : runs) {
                if (r[0] > at) chunks++;
                chunks++;
                at = r[0] + r[1];
                rawBlocks += r[1];
            }
            if (at < total) chunks++;
            long size = 28 + 12L * chunks + rawBlocks * blk;
            if (size > MAX_PIECE) throw new IOException("delta too large for one piece: " + size);
            try (FileOutputStream o = new FileOutputStream(out)) {
                ByteBuffer h = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
                h.putInt(SPARSE_MAGIC).putShort((short) 1).putShort((short) 0).putShort((short) 28).putShort((short) 12);
                h.putInt(blk).putInt((int) total).putInt(chunks).putInt(0);
                o.write(h.array());
                at = 0;
                for (long[] r : runs) {
                    if (r[0] > at) writeChunk(o, CHUNK_SKIP, r[0] - at, 12);
                    writeChunk(o, CHUNK_RAW, r[1], 12 + r[1] * blk);
                    b.seek(r[0] * blk);
                    for (long n = 0; n < r[1]; n++) {
                        b.readFully(y);
                        o.write(y);
                    }
                    at = r[0] + r[1];
                }
                if (at < total) writeChunk(o, CHUNK_SKIP, total - at, 12);
            }
            System.out.printf("delta: %d changed blocks in %d runs, %d chunks, %d bytes%n", rawBlocks, runs.size(), chunks, size);
        }
    }

    private static void writeChunk(FileOutputStream o, int type, long blocks, long bytes) throws IOException {
        ByteBuffer c = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        c.putShort((short) type).putShort((short) 0).putInt((int) blocks).putInt((int) bytes);
        o.write(c.array());
    }

    private static void pack(String bin, String delta, String part, String out) throws IOException {
        if (new File(out).getCanonicalPath().equals(new File(bin).getCanonicalPath())) throw new IOException("out is the input");
        try (RandomAccessFile f = new RandomAccessFile(bin, "r"); RandomAccessFile o = new RandomAccessFile(out, "rw")) {
            o.setLength(0);
            String s = script(f);
            long body = f.length() - FOOTER;
            long deltaSize = new File(delta).length();
            if (body % ALIGN != 0) throw new IOException("body not 4 KB aligned");
            if (body + deltaSize + ALIGN + FOOTER >= 0xFFFFFFFFL) throw new IOException("file over 4 GB");

            // The new lines go right after the partition's last sparse_write
            String write = "sparse_write mmc 0x55000000 " + part + " $(filesize)\n";
            int at = s.lastIndexOf(write);
            if (at < 0) throw new IOException("no sparse_write for " + part);
            at += write.length();
            String add = String.format("filepartload 0x55000000 $(UpgradeImage) 0x%x 0x%x\n", body, deltaSize) + write;
            int end = s.indexOf(END_OF_SCRIPT) + END_OF_SCRIPT.length();
            if (end + add.length() >= SCRIPT) throw new IOException("script full");
            // Same 0x4000 bytes: the padding after the end symbol gets shorter by what was added
            String patched = s.substring(0, at) + add + s.substring(at, SCRIPT - add.length());
            byte[] head = patched.getBytes(StandardCharsets.ISO_8859_1);
            if (head.length != SCRIPT) throw new IOException("script size");
            o.write(head);

            f.seek(SCRIPT);
            for (long left = body - SCRIPT; left > 0; ) {
                int n = f.read(BUF, 0, (int) Math.min(left, BUF.length));
                o.write(BUF, 0, n);
                left -= n;
            }
            try (RandomAccessFile d = new RandomAccessFile(delta, "r")) {
                for (int n; (n = d.read(BUF)) > 0; ) o.write(BUF, 0, n);
            }
            long pos = o.getFilePointer();
            byte[] pad = new byte[(int) ((ALIGN - pos % ALIGN) % ALIGN)];
            Arrays.fill(pad, (byte) 0xFF);
            o.write(pad);

            ByteArrayOutputStream foot = new ByteArrayOutputStream();
            foot.write("12345678".getBytes(StandardCharsets.US_ASCII));
            CRC32 c = new CRC32();
            c.update(head);
            foot.write(leBytes(c.getValue()));
            o.write(foot.toByteArray());
            long fileCrc = crc(o, 0, o.length());
            o.seek(o.length());
            o.write(leBytes(fileCrc));
            o.write(head, 0, 16);
            System.out.printf("packed %s: %d bytes, added piece at 0x%x size 0x%x%n", out, o.length(), body, deltaSize);
        }
    }

    private static void compare(String a, String b) throws IOException {
        try (RandomAccessFile x = new RandomAccessFile(a, "r"); RandomAccessFile y = new RandomAccessFile(b, "r")) {
            long end = x.length() - FOOTER;
            byte[] p = new byte[1 << 20], q = new byte[1 << 20];
            x.seek(SCRIPT);
            y.seek(SCRIPT);
            for (long left = end - SCRIPT; left > 0; ) {
                int n = (int) Math.min(left, p.length);
                x.readFully(p, 0, n);
                y.readFully(q, 0, n);
                if (!Arrays.equals(p, q)) throw new IOException("data differs near " + (end - left));
                left -= n;
            }
            System.out.printf("all %d data bytes of the original are unchanged%n", end - SCRIPT);
            String[] la = script(x).split("\n"), lb = script(y).split("\n");
            StringBuilder added = new StringBuilder();
            int i = 0, j = 0;
            for (; i < la.length && !la[i].startsWith(END_OF_SCRIPT); j++) {
                if (la[i].equals(lb[j])) i++;
                else added.append(lb[j]).append('\n');
            }
            if (!lb[j].startsWith(END_OF_SCRIPT)) throw new IOException("script differs by more than added lines");
            System.out.print("script differs only by:\n" + added);
        }
    }

    private static long crc(RandomAccessFile f, long from, long to) throws IOException {
        CRC32 c = new CRC32();
        f.seek(from);
        for (long left = to - from; left > 0; ) {
            int n = f.read(BUF, 0, (int) Math.min(left, BUF.length));
            c.update(BUF, 0, n);
            left -= n;
        }
        return c.getValue();
    }

    private static ByteBuffer read(RandomAccessFile f, int n) throws IOException {
        byte[] b = new byte[n];
        f.readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static long le(byte[] b, int i) {
        return (b[i] & 255L) | (b[i + 1] & 255L) << 8 | (b[i + 2] & 255L) << 16 | (b[i + 3] & 255L) << 24;
    }

    private static byte[] leBytes(long v) {
        return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }
}
