import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing (PBKDF2 with a random salt).
 *
 * A stored hash looks like:  pbkdf2$120000$<salt>$<hash>
 *
 * Passwords that are still stored as plain text (like the first "admin"
 * row) are accepted once; LoginServlet then replaces them with a hash.
 *
 * To make a hash by hand (to paste into the users table):
 *     java -cp WEB-INF/classes Passwords yourPasswordHere
 */
public final class Passwords {

    private static final String PREFIX     = "pbkdf2$";
    private static final int    ITERATIONS = 120000;
    private static final int    SALT_BYTES = 16;
    private static final int    HASH_BITS  = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() { }

    public static String hash(String password) {

        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);

        byte[] hash = pbkdf2(password, salt, ITERATIONS);

        return PREFIX + ITERATIONS + "$" +
               Base64.getEncoder().encodeToString(salt) + "$" +
               Base64.getEncoder().encodeToString(hash);
    }

    /** True when the stored value is a hash (not old plain text). */
    public static boolean isHashed(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    /**
     * Checks a typed password against what is stored. The comparison
     * takes the same time however many characters match.
     */
    public static boolean matches(String stored, String typed) {

        if (stored == null || typed == null) {
            // spend about the same time as a real check
            pbkdf2("x", new byte[SALT_BYTES], ITERATIONS);
            return false;
        }

        if (!isHashed(stored)) {
            // legacy plain-text row
            return MessageDigest.isEqual(
                stored.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                typed.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        }

        try {
            String[] parts = stored.split("\\$");

            if (parts.length != 4) {
                return false;
            }

            int iterations = Integer.parseInt(parts[1]);
            byte[] salt    = Base64.getDecoder().decode(parts[2]);
            byte[] want    = Base64.getDecoder().decode(parts[3]);

            byte[] got = pbkdf2(typed, salt, iterations);

            return MessageDigest.isEqual(want, got);

        } catch (IllegalArgumentException e) {
            return false;   // malformed number or base64
        }
    }

    private static byte[] pbkdf2(
            String password, byte[] salt, int iterations) {

        try {
            PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), salt, iterations, HASH_BITS
            );

            return SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("usage: java Passwords <password>");
            return;
        }

        System.out.println(hash(args[0]));
    }
}
