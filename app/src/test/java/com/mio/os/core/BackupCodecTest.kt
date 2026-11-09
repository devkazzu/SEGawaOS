package com.mio.os.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BackupCodecTest {

    @Test
    fun packUnpackRoundTrip() {
        val parts = mapOf("prefs" to "{\"a\":1}", "meta" to "segawa-backup test")
        val unpacked = BackupCodec.unpack(BackupCodec.pack(parts))
        assertEquals(parts, unpacked)
    }

    @Test
    fun unpackRejectsGarbage() {
        assertNull(BackupCodec.unpack(""))
        assertNull(BackupCodec.unpack("not json"))
        assertNull(BackupCodec.unpack("{}"))
        assertNull(BackupCodec.unpack("{\"v\":99,\"parts\":{}}"))
        assertNull(BackupCodec.unpack("{\"v\":2}"))
    }

    @Test
    fun unpackAcceptsV1AndV2() {
        assertNotNull(BackupCodec.unpack("{\"v\":1,\"parts\":{\"a\":\"b\"}}"))
        assertNotNull(BackupCodec.unpack("{\"v\":2,\"parts\":{}}"))
    }

    @Test
    fun encryptDecryptRoundTrip() {
        val plain = BackupCodec.pack(mapOf("prefs" to "secret-data"))
        val packed = BackupCodec.encrypt("correct horse", plain)
        assertEquals(plain, BackupCodec.decrypt("correct horse", packed))
    }

    @Test
    fun decryptWrongPasswordReturnsNull() {
        val packed = BackupCodec.encrypt("one", "data")
        assertNull(BackupCodec.decrypt("two", packed))
    }

    @Test
    fun decryptGarbageReturnsNull() {
        assertNull(BackupCodec.decrypt("pw", "!!!not-base64!!!"))
        assertNull(BackupCodec.decrypt("pw", "c2hvcnQ="))
    }

    @Test
    fun encryptIsRandomized() {
        val a = BackupCodec.encrypt("pw", "same")
        val b = BackupCodec.encrypt("pw", "same")
        assertNotEquals(a, b)
        assertEquals("same", BackupCodec.decrypt("pw", a))
        assertEquals("same", BackupCodec.decrypt("pw", b))
    }
}
