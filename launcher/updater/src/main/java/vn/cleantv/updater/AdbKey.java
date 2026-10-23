package vn.cleantv.updater;

import android.util.Base64;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * This app's own ADB key, as a PC's ~/.android/adbkey: TVs whose adbd asks for a key (ro.adb.secure=1, the onn
 * 4K box) show "Allow USB debugging?" for it once; with "Always allow" ticked later connections need no tap.
 * Kept in the app's private files, so reinstalling the app over itself keeps the TV's approval.
 */
final class AdbKey {
    private static final String NAME = " CapNhatMayChieu@tv";
    /** DER DigestInfo header for SHA-1: adbd verifies the token signature as a SHA-1 PKCS#1 v1.5 signature. */
    private static final byte[] SHA1_INFO = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a,
            0x05, 0x00, 0x04, 0x14};

    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;

    private AdbKey(PrivateKey privateKey, RSAPublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    static AdbKey load(File dir) throws IOException, GeneralSecurityException {
        File priv = new File(dir, "adbkey"), pub = new File(dir, "adbkey.pub");
        KeyFactory rsa = KeyFactory.getInstance("RSA");
        if (priv.isFile() && pub.isFile()) {
            return new AdbKey(rsa.generatePrivate(new PKCS8EncodedKeySpec(readAll(priv))),
                    (RSAPublicKey) rsa.generatePublic(new X509EncodedKeySpec(readAll(pub))));
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        writeAll(priv, pair.getPrivate().getEncoded());
        writeAll(pub, pair.getPublic().getEncoded());
        return new AdbKey(pair.getPrivate(), (RSAPublicKey) pair.getPublic());
    }

    private static byte[] readAll(File f) throws IOException {
        byte[] data = new byte[(int) f.length()];
        try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
            in.readFully(data);
        }
        return data;
    }

    private static void writeAll(File f, byte[] data) throws IOException {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(data);
        }
    }

    /** Signs adbd's 20-byte AUTH token. */
    byte[] sign(byte[] token) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        c.init(Cipher.ENCRYPT_MODE, privateKey);   // private-key "encryption" with type 1 padding = a raw signature
        c.update(SHA1_INFO);
        return c.doFinal(token);
    }

    /**
     * The public key in adbd's format: base64 of {words, n0inv, modulus, R^2 mod n, exponent}, little endian,
     * then a name and a NUL.
     */
    byte[] publicKeyPayload() {
        BigInteger n = publicKey.getModulus();
        int words = 2048 / 32;
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32)).mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(2048 * 2).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words);
        b.putInt(n0inv.intValue());
        putWords(b, n, words);
        putWords(b, rr, words);
        b.putInt(publicKey.getPublicExponent().intValue());
        String text = Base64.encodeToString(b.array(), Base64.NO_WRAP) + NAME + "\0";
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void putWords(ByteBuffer b, BigInteger v, int words) {
        BigInteger mask = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE);
        for (int i = 0; i < words; i++) b.putInt(v.shiftRight(32 * i).and(mask).intValue());
    }
}
