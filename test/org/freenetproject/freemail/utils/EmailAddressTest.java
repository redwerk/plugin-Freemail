package org.freenetproject.freemail.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EmailAddressTest {

    @Test
    public void getWoTIdFromEmailTest() {
        EmailAddress email = new EmailAddress("oleh@5lkdxgggtbletiv6iti2awiqwlnvrzoiiu6icgbqgebca7legaha.freemail");
        assertEquals("6tQ7mMaYVkmivkTRoFkQsttY5chFPIEYMDECIH1kMA4", email.getIdentity());
    }
}
