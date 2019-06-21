/*
 * SingleAccountWatcher.java
 * This file is part of Freemail
 * Copyright (C) 2006,2007,2008 Dave Baker
 * Copyright (C) 2007,2008 Alexander Lehmann
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

package org.freenetproject.freemail;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.InterruptedException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import freenet.support.api.Bucket;
import freenet.support.io.ArrayBucket;
import freenet.support.io.BucketTools;
import org.freenetproject.freemail.fcp.ConnectionTerminatedException;
import org.freenetproject.freemail.utils.Logger;
import org.freenetproject.freemail.utils.Timer;
import org.freenetproject.freemail.wot.Identity;
import org.freenetproject.freemail.wot.IdentityMatcher;
import org.freenetproject.freemail.wot.WoTConnection;
import org.freenetproject.freemail.wot.WoTProperties;

import freenet.pluginmanager.PluginNotFoundException;

public class SingleAccountWatcher implements Runnable {
	/**
	 * Whether the thread this service runs in should stop.
	 */
	protected volatile boolean stopping = false;

	public static final String RTS_DIR = "rts";
	private static final int MIN_POLL_DURATION = 5 * 60 * 1000; // in milliseconds
	private static final int MAILSITE_UPLOAD_INTERVAL = 60 * 60 * 1000;
	private static final int SEND_FREEMAIL_RETRY_INTERVAL = 60 * 60 * 1000;
	private final RTSFetcher rtsf;
	private long mailsite_last_upload;
	private long sendFreemailLastRetry;
	private final FreemailAccount account;
	private final Freemail freemail;
	private final File rtsdir;
	private boolean hasSetWoTContext;

	SingleAccountWatcher(FreemailAccount acc, Freemail freemail) {
		this.account = acc;
		this.freemail = freemail;
		this.mailsite_last_upload = 0;

		rtsdir = new File(account.getAccountDir(), RTS_DIR);

		String rtskey=account.getProps().get("rtskey");

		if(rtskey==null) {
			Logger.error(this, "Your accprops file is missing the rtskey entry. This means it is broken, you will not be able to receive new contact requests.");
		}

		this.rtsf = new RTSFetcher("KSK@"+rtskey+"-", rtsdir, account);

		//this.mf = new MailFetcher(this.mb, inbound_dir, Freemail.getFCPConnection());

		// temporary info message until there's a nicer UI :)
		String freemailDomain=account.getDomain();
		if(freemailDomain!=null) {
			Logger.normal(this, "Secure Freemail address: <anything>@"+freemailDomain);
		} else {
			Logger.error(this, "You do not have a freemail address USK. This account is really broken.");
		}
	}

	@Override
	public void run() {
		while(!stopping) {
			try {
				long start = System.currentTimeMillis();
				WoTConnection wotConnection = freemail.getWotConnection();

				insertMailsite(wotConnection);
				setWoTContext(wotConnection);

				retrySendFreemail(wotConnection);

				if(stopping) {
					break;
				}
				Logger.debug(this, "polling rts");
				this.rtsf.poll();
				if(stopping) {
					break;
				}

				long runtime = System.currentTimeMillis() - start;

				if(MIN_POLL_DURATION - runtime > 0) {
					Thread.sleep(MIN_POLL_DURATION - runtime);
				}
			} catch (ConnectionTerminatedException cte) {

			} catch (InterruptedException ie) {
				Logger.debug(this, "SingleAccountWatcher interrupted, stopping");
				kill();
				break;
			}
		}
	}

	private void insertMailsite(WoTConnection wotConnection) throws InterruptedException {
		// is it time we inserted the mailsite?
		if(System.currentTimeMillis() > this.mailsite_last_upload + MAILSITE_UPLOAD_INTERVAL) {
			int editionHint = 1;

			//Try to get the edition from WoT
			if(wotConnection != null) {
				Timer propertyRead = Timer.start();
				try {
					String hint = wotConnection.getProperty(
							account.getIdentity(), WoTProperties.MAILSITE_EDITION);
					editionHint = Integer.parseInt(hint);
				} catch (PluginNotFoundException e) {
					//Only means that we can't get the hint from WoT so ignore it
				} catch (NumberFormatException e) {
					//Same as above, so ignore this too
				}
				propertyRead.log(this, 1, TimeUnit.HOURS, "Time spent getting mailsite property");
			}

			//And from the account file
			try {
				int slot = Integer.parseInt(account.getProps().get("mailsite.slot"));
				if(slot > editionHint) {
					editionHint = slot;
				}
			} catch (NumberFormatException e) {
				//Same as for the WoT approach
			}

			MailSite ms = new MailSite(account.getProps());
			Timer mailsiteInsert = Timer.start();
			int edition = ms.publish(editionHint);
			mailsiteInsert.log(this, 1, TimeUnit.HOURS, "Time spent inserting mailsite");
			if(edition >= 0) {
				this.mailsite_last_upload = System.currentTimeMillis();
				if(wotConnection != null) {
					Timer propertyUpdate = Timer.start();
					try {
						wotConnection.setProperty(account.getIdentity(), WoTProperties.MAILSITE_EDITION, "" + edition);
					} catch(PluginNotFoundException e) {
						//In most cases this doesn't matter since the edition doesn't
						//change very often anyway
						Logger.normal(this, "WoT plugin not loaded, can't save mailsite edition");
					}
					propertyUpdate.log(this, 1, TimeUnit.HOURS, "Time spent setting mailsite property");
				}
			}
		}
	}

	private void setWoTContext(WoTConnection wotConnection) {
		if(hasSetWoTContext) {
			return;
		}
		if(wotConnection == null) {
			return;
		}

		Timer contextWrite = Timer.start();
		try {
			if(!wotConnection.setContext(account.getIdentity(), WoTProperties.CONTEXT)) {
				Logger.error(this, "Setting WoT context failed");
			} else {
				hasSetWoTContext = true;
			}
		} catch (PluginNotFoundException e) {
			Logger.normal(this, "WoT plugin not loaded, can't set Freemail context");
		}
		contextWrite.log(this, 1, TimeUnit.HOURS, "Time spent adding WoT context");
	}

	private void retrySendFreemail(WoTConnection wotConnection) {
		if(System.currentTimeMillis() < sendFreemailLastRetry + SEND_FREEMAIL_RETRY_INTERVAL)
			return;

		MessageBank messageBank = account.getMessageBank().makeSubFolder(MailPendingMessage.SEND_PENDING_FOLDER);
		if(messageBank == null)
			return;


		IdentityMatcher messageSender = new IdentityMatcher(wotConnection);
		for (MailPendingMessage pendingMessage : messageBank.listPendingMessages()) {
			Map<String, List<Identity>> matches;
			try {
				matches = messageSender.matchIdentities(
						new HashSet<>(pendingMessage.getPendingRecipients()),
						account.getIdentity(),
						EnumSet.allOf(IdentityMatcher.MatchMethod.class));
			} catch (PluginNotFoundException e) {
				Logger.warning(this, "WoT not loaded");
				return;
			}

			List<String> failedRecipients = new ArrayList<>();
			List<String> knownRecipientsKeys = new ArrayList<>();
			List<Identity> knownRecipients = new ArrayList<>();
			for(Map.Entry<String, List<Identity>> entry : matches.entrySet()) {
				if(entry.getValue().size() == 1) {
					knownRecipientsKeys.add(entry.getKey());
					knownRecipients.add(entry.getValue().get(0));
				}
				else
					failedRecipients.add(entry.getKey());
			}

			if (knownRecipients.isEmpty())
				continue;

			if (failedRecipients.isEmpty())
				messageBank.delete(pendingMessage.getUID());
			else {
				List<String> remainingRecipients = pendingMessage.getPendingRecipients();
				remainingRecipients.removeAll(knownRecipientsKeys);
				pendingMessage.setPendingRecipients(remainingRecipients);
				// save pendingMessage?
			}

			try (BufferedReader body = pendingMessage.getBodyReader()) {
				Bucket messageHeader = new ArrayBucket(pendingMessage.getAllHeadersAsString().getBytes(StandardCharsets.UTF_8));

				char[] charArray = new char[8 * 1024];
				StringBuilder builder = new StringBuilder();
				int numCharsRead;
				while ((numCharsRead = body.read(charArray, 0, charArray.length)) != -1)
					builder.append(charArray, 0, numCharsRead);
				Bucket messageText = new ArrayBucket(builder.toString().getBytes(StandardCharsets.UTF_8));

				Bucket message = new ArrayBucket();
				OutputStream messageOutputStream = message.getOutputStream();
				BucketTools.copyTo(messageHeader, messageOutputStream, -1);
				BucketTools.copyTo(messageText, new MailMessage.EncodingOutputStream(messageOutputStream), -1);
				messageOutputStream.close();

				// TODO: if the sent folder does not contain this message
//				copyMessageToSentFolder(message, account.getMessageBank());

				account.getMessageHandler().sendMessage(knownRecipients, message);
				message.free();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Terminate the run method
	 */
	public void kill() {
		stopping = true;
	}
}
