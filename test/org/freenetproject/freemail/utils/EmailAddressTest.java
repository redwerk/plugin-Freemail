package org.freenetproject.freemail.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EmailAddressTest {

    /* spec.pdf#2.1
     * A Freemail address comprises an arbitrary text string, followed by an
     * ’@’ character. Following this is the base32 encoded representation of the
     * hash of the public key (the first part of an SSK is this value base64 en-
     * coded). The URI must be base 32 encoded in order to make the address
     * case insensitive to maintain compatibility with traditional email clients. The
     * string ’.freemail’ is appended to the whole address. For example, the identity
     * D3MrAR-AVMqKJRjXnpKW2guW9z1mw5GZ9BB15mYVkVc has the Freemail address
     * <anything>@b5zswai7ybkmvcrfddlz5euw3ifzn5z5m3bzdgpucb26mzqvsflq.freemail */
    @Test
    public void getWoTIdFromEmailTest() {
        EmailAddress email = new EmailAddress("oleh@5lkdxgggtbletiv6iti2awiqwlnvrzoiiu6icgbqgebca7legaha.freemail");
        assertEquals("6tQ7mMaYVkmivkTRoFkQsttY5chFPIEYMDECIH1kMA4", email.getIdentity());
    }

    /* spec.pdf#2.2
     * Once the full key for an identity has been obtained the mailpage can be
     * located at USK@<key of the identity>/mailsite/<edition>/mailpage. */
    @Test
    public void getFullKeyForWoTIdentityTest() {
        EmailAddress email = new EmailAddress("oleh@5lkdxgggtbletiv6iti2awiqwlnvrzoiiu6icgbqgebca7legaha.freemail");
        String decryptionKey = "";
        String encryptionSettings = "";
        assertEquals("USK@6tQ7mMaYVkmivkTRoFkQsttY5chFPIEYMDECIH1kMA4," +
                        "kxTdQK6tB1graMn3Q21zT-JH3slGnzEV9lZlqFXzlBA,AQACAAE/WebOfTrust/27",
                "USK@" + email.getIdentity() + "," + decryptionKey + "," + encryptionSettings + "/WebOfTrust/27");
    }
}
