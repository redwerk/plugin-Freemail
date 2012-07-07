/*
 * IMAPCopyTest.java
 * This file is part of Freemail, copyright (C) 2012
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.freenetproject.freemail.imap;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class IMAPCopyTest extends IMAPTestWithMessages {
	public void testCopySingleMessageToSameFolder() throws IOException {
		List<String> commands = new LinkedList<String>();
		commands.add("0001 LOGIN " + IMAP_USERNAME + " test");
		commands.add("0002 SELECT INBOX");
		commands.add("0003 SEARCH ALL");
		commands.add("0004 COPY 1 INBOX");
		commands.add("0005 SEARCH ALL");

		List<String> expectedResponse = new LinkedList<String>();
		expectedResponse.addAll(INITIAL_RESPONSES);
		expectedResponse.add("* SEARCH 1 2 3 4 5 6 7 8 9");
		expectedResponse.add("0003 OK Search completed");
		expectedResponse.add("0004 OK COPY completed");
		expectedResponse.add("* SEARCH 1 2 3 4 5 6 7 8 9 10");
		expectedResponse.add("0005 OK Search completed");

		runSimpleTest(commands, expectedResponse);
	}

	public void testCopyWithMessageId0() throws IOException {
		List<String> commands = new LinkedList<String>();
		commands.add("0001 LOGIN " + IMAP_USERNAME + " test");
		commands.add("0002 SELECT INBOX");
		commands.add("0003 COPY 0 INBOX");

		List<String> expectedResponse = new LinkedList<String>();
		expectedResponse.addAll(INITIAL_RESPONSES);
		expectedResponse.add("0003 NO Invalid message ID");

		runSimpleTest(commands, expectedResponse);
	}

	public void testCopyToNonexistentMailbox() throws IOException {
		List<String> commands = new LinkedList<String>();
		commands.add("0001 LOGIN " + IMAP_USERNAME + " test");
		commands.add("0002 SELECT INBOX");
		commands.add("0003 COPY 1 INBOX.abc");

		List<String> expectedResponse = new LinkedList<String>();
		expectedResponse.addAll(INITIAL_RESPONSES);
		expectedResponse.add("0003 NO [TRYCREATE] No such mailbox.");

		runSimpleTest(commands, expectedResponse);
	}

	public void testCopySetsRecentFlag() throws IOException {
		List<String> commands = new LinkedList<String>();
		commands.add("0001 LOGIN " + IMAP_USERNAME + " test");
		commands.add("0002 SELECT INBOX");
		commands.add("0003 COPY 1 INBOX");
		commands.add("0004 FETCH * FLAGS");

		List<String> expectedResponse = new LinkedList<String>();
		expectedResponse.addAll(INITIAL_RESPONSES);
		expectedResponse.add("0003 OK COPY completed");
		expectedResponse.add("* 10 FETCH (FLAGS (\\Recent))");
		expectedResponse.add("0004 OK Fetch completed");

		runSimpleTest(commands, expectedResponse);
	}

	public void testCopyDoesntCreateMailbox() throws IOException {
		List<String> commands = new LinkedList<String>();
		commands.add("0001 LOGIN " + IMAP_USERNAME + " test");
		commands.add("0002 SELECT INBOX");
		commands.add("0003 COPY 1 INBOX.abc");
		commands.add("0004 SELECT INBOX.abc");

		List<String> expectedResponse = new LinkedList<String>();
		expectedResponse.addAll(INITIAL_RESPONSES);
		expectedResponse.add("0003 NO [TRYCREATE] No such mailbox.");
		expectedResponse.add("0004 NO No such mailbox");

		runSimpleTest(commands, expectedResponse);
	}

	public void testUidAfterCopy() throws IOException {
		List<String> commands = new LinkedList<String>();
		commands.add("0001 LOGIN " + IMAP_USERNAME + " test");
		commands.add("0002 SELECT INBOX");
		commands.add("0003 UID SEARCH ALL");
		commands.add("0004 COPY 1 INBOX");
		commands.add("0005 UID SEARCH ALL");
		commands.add("0005 FETCH 10 (UID)");

		List<String> expectedResponse = new LinkedList<String>();
		expectedResponse.addAll(INITIAL_RESPONSES);
		expectedResponse.add("* SEARCH 1 2 3 4 6 7 8 9 10");
		expectedResponse.add("0003 OK Search completed");
		expectedResponse.add("0004 OK COPY completed");
		expectedResponse.add("* SEARCH 1 2 3 4 6 7 8 9 10 11");
		expectedResponse.add("0005 OK Search completed");
		expectedResponse.add("* 10 FETCH (UID 11)");
		expectedResponse.add("0005 OK Fetch completed");

		runSimpleTest(commands, expectedResponse);
	}
}
