package de.joelneumann.lojinha.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordHasherTest {

    @Test
    fun testKnownSha256Vectors() {
        // Standard NIST test vectors
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            PasswordHasher.hash("")
        )
        assertEquals(
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            PasswordHasher.hash("a")
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            PasswordHasher.hash("abc")
        )
        // Known hash for "admin"
        assertEquals(
            "8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918",
            PasswordHasher.hash("admin")
        )
    }

    @Test
    fun testVariablePinLengths() {
        // 4-digit PIN
        val pin4 = "1234"
        val hash4 = PasswordHasher.hash(pin4)
        assertEquals(64, hash4.length)
        assertTrue(PasswordHasher.verify(pin4, hash4))
        assertFalse(PasswordHasher.verify("0000", hash4))

        // Longer PIN (e.g. 8 digits or letters)
        val longPin = "9876543210"
        val hashLong = PasswordHasher.hash(longPin)
        assertEquals(64, hashLong.length)
        assertTrue(PasswordHasher.verify(longPin, hashLong))
        assertFalse(PasswordHasher.verify("987654321", hashLong))

        // Alphanumeric passcode
        val passcode = "MySecretPass#2026"
        val hashPass = PasswordHasher.hash(passcode)
        assertTrue(PasswordHasher.verify(passcode, hashPass))
    }

    @Test
    fun testIsHash() {
        assertTrue(PasswordHasher.isHash("8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918"))
        assertTrue(PasswordHasher.isHash("8C6976E5B5410415BDE908BD4DEE15DFB167A9C873FC4BB8A81F6F2AB448A918"))
        assertFalse(PasswordHasher.isHash("admin"))
        assertFalse(PasswordHasher.isHash("1234"))
        assertFalse(PasswordHasher.isHash(""))
        assertFalse(PasswordHasher.isHash("8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a91")) // 63 chars
        assertFalse(PasswordHasher.isHash("8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918z")) // 65 chars
        assertFalse(PasswordHasher.isHash("8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a91g")) // non-hex
    }

    @Test
    fun testLegacyPlainTextFallback() {
        // Plain text "admin" stored in database
        assertTrue(PasswordHasher.verify("admin", "admin"))
        assertFalse(PasswordHasher.verify("wrong", "admin"))

        // Plain text user PIN stored in database
        assertTrue(PasswordHasher.verify("1234", "1234"))
        assertFalse(PasswordHasher.verify("9999", "1234"))
    }

    @Test
    fun testVerifyAdminBypass() {
        val adminHash = PasswordHasher.hash("adminSecret")
        // Matching admin pass bypasses
        assertTrue(PasswordHasher.verifyAdminBypass("adminSecret", adminHash))
        // Wrong password does not bypass
        assertFalse(PasswordHasher.verifyAdminBypass("wrong", adminHash))
        // "admin" does not bypass if admin password changed
        assertFalse(PasswordHasher.verifyAdminBypass("admin", adminHash))

        // Default admin password bypass
        val defaultHash = PasswordHasher.hash("admin")
        assertTrue(PasswordHasher.verifyAdminBypass("admin", defaultHash))
        assertTrue(PasswordHasher.verifyAdminBypass("admin", "admin"))
        assertTrue(PasswordHasher.verifyAdminBypass("admin", ""))
    }
}
