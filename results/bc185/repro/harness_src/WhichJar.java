import org.bouncycastle.pqc.crypto.mldsa.MLDSASigner;
public final class WhichJar {
    public static void main(String[] a) throws Exception {
        System.out.println("MLDSASigner from : " + MLDSASigner.class.getProtectionDomain().getCodeSource().getLocation());
        // 1.85 added the private field 'queuePacked' to KeccakDigest; 1.84 has no such field.
        boolean hasQueuePacked = false;
        for (java.lang.reflect.Field f : Class.forName("org.bouncycastle.crypto.digests.KeccakDigest").getDeclaredFields()) {
            if (f.getName().equals("queuePacked")) hasQueuePacked = true;
        }
        System.out.println("KeccakDigest.queuePacked present : " + hasQueuePacked + "   => " + (hasQueuePacked ? "1.85 lazy-pack build" : "1.84 eager-pack build"));
    }
}
