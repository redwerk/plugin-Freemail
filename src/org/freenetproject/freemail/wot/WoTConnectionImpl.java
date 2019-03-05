/*
 * WoTConnectionImpl.java
 * This file is part of Freemail
 * Copyright (C) 2011 Martin Nyhus
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

package org.freenetproject.freemail.wot;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import freenet.clients.fcp.FCPPluginConnection;
import freenet.clients.fcp.FCPPluginMessage;
import freenet.pluginmanager.FredPluginFCPMessageHandler;
import org.freenetproject.freemail.utils.Logger;
import org.freenetproject.freemail.utils.SimpleFieldSetFactory;
import org.freenetproject.freemail.utils.Timer;

import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

class WoTConnectionImpl implements WoTConnection {

	private final FCPPluginConnection fcpPluginConnection;

	private FCPPluginMessage reply = null;
	private final Object replyLock = new Object();

	WoTConnectionImpl(PluginRespirator pr) throws PluginNotFoundException {
		fcpPluginConnection = pr.connectToOtherPlugin(WoTProperties.WOT_PLUGIN_NAME, new FCPMessageHandler());
	}

	@Override
	public List<OwnIdentity> getAllOwnIdentities() {
		Message response = sendBlocking(
				new Message(
						new SimpleFieldSetFactory().put("Message", "GetOwnIdentities").create(),
						null),
				"OwnIdentities");
		if(!"OwnIdentities".equals(response.sfs.get("Message"))) {
			return null;
		}

		final List<OwnIdentity> ownIdentities = new LinkedList<OwnIdentity>();
		for(int count = 0;; count++) {
			String identityID = response.sfs.get("Identity" + count);
			if(identityID == null) {
				//Got all the identities
				break;
			}

			String requestURI = response.sfs.get("RequestURI" + count);
			assert (requestURI != null);

			String insertURI = response.sfs.get("InsertURI" + count);
			assert (insertURI != null);

			String nickname = response.sfs.get("Nickname" + count);

			ownIdentities.add(new OwnIdentity(identityID, requestURI, insertURI, nickname));
		}

		return ownIdentities;
	}

	@Override
	public Set<Identity> getAllTrustedIdentities(String trusterId) {
		return getAllIdentities(trusterId, TrustSelection.TRUSTED);
	}

	@Override
	public Set<Identity> getAllUntrustedIdentities(String trusterId) {
		return getAllIdentities(trusterId, TrustSelection.UNTRUSTED);
	}

	private Set<Identity> getAllIdentities(String trusterId, TrustSelection selection) {
		if(trusterId == null) {
			throw new NullPointerException("Parameter trusterId must not be null");
		}
		if(selection == null) {
			throw new NullPointerException("Parameter selection must not be null");
		}

		SimpleFieldSet sfs = new SimpleFieldSetFactory().create();
		sfs.putOverwrite("Message", "GetIdentitiesByScore");
		sfs.putOverwrite("Truster", trusterId);
		sfs.putOverwrite("Selection", selection.value);
		sfs.put("WantTrustValues", false);

		/*
		 * The Freemail context wasn't added prior to 0.2.1, so for a while after 0.2.1 we need to
		 * support recipients without the Freemail context.
		 * FIXME: Restrict this to Freemail context a few months after 0.2.1 has become mandatory
		 */
		sfs.putOverwrite("Context", WoTProperties.CONTEXT);

		Message response = sendBlocking(new Message(sfs, null), "Identities");
		if(!"Identities".equals(response.sfs.get("Message"))) {
			return null;
		}

		final Set<Identity> identities = new HashSet<Identity>();
		for(int count = 0;; count++) {
			String identityID = response.sfs.get("Identity" + count);
			if(identityID == null) {
				//Got all the identities
				break;
			}

			String requestURI = response.sfs.get("RequestURI" + count);
			assert (requestURI != null);

			String nickname = response.sfs.get("Nickname" + count);

			identities.add(new Identity(identityID, requestURI, nickname));
		}

		return identities;
	}

	@Override
	public Identity getIdentity(String identity, String trusterId) {
		if(identity == null) {
			throw new NullPointerException("Parameter identity must not be null");
		}
		if(trusterId == null) {
			throw new NullPointerException("Parameter trusterId must not be null");
		}

		SimpleFieldSet sfs = new SimpleFieldSetFactory().create();
		sfs.putOverwrite("Message", "GetIdentity");
		sfs.putOverwrite("Identity", identity);
		sfs.putOverwrite("Truster", trusterId);

		Message response = sendBlocking(new Message(sfs, null), "Identity");
		if(!"Identity".equals(response.sfs.get("Message"))) {
			return null;
		}

		String requestURI = response.sfs.get("RequestURI");
		assert(requestURI != null);

		String nickname = response.sfs.get("Nickname");

		return new Identity(identity, requestURI, nickname);
	}

	@Override
	public boolean setProperty(String identity, String key, String value) {
		if(identity == null) {
			throw new NullPointerException("Parameter identity must not be null");
		}
		if(key == null) {
			throw new NullPointerException("Parameter key must not be null");
		}
		if(value == null) {
			throw new NullPointerException("Parameter value must not be null");
		}

		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Message", "SetProperty");
		sfs.putOverwrite("Identity", identity);
		sfs.putOverwrite("Property", key);
		sfs.putOverwrite("Value", value);

		Message response = sendBlocking(new Message(sfs, null), "PropertyAdded");
		return "PropertyAdded".equals(response.sfs.get("Message"));
	}

	@Override
	public String getProperty(String identity, String key) {
		if(identity == null) {
			throw new NullPointerException("Parameter identity must not be null");
		}
		if(key == null) {
			throw new NullPointerException("Parameter key must not be null");
		}

		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Message", "GetProperty");
		sfs.putOverwrite("Identity", identity);
		sfs.putOverwrite("Property", key);

		Set<String> expectedTypes = new HashSet<String>();
		expectedTypes.add("PropertyValue");

		/* Also include Error since WoT returns this if the property doesn't exist for the message */
		/* FIXME: Perhaps check the description and log if it isn't what we expect? */
		expectedTypes.add("Error");

		Message response = sendBlocking(new Message(sfs, null), expectedTypes);

		if("PropertyValue".equals(response.sfs.get("Message"))) {
			return response.sfs.get("Property");
		} else {
			return null;
		}
	}

	@Override
	public boolean setContext(String identity, String context) {
		if(identity == null) {
			throw new NullPointerException("Parameter identity must not be null");
		}
		if(context == null) {
			throw new NullPointerException("Parameter context must not be null");
		}

		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Message", "AddContext");
		sfs.putOverwrite("Identity", identity);
		sfs.putOverwrite("Context", context);

		Message response = sendBlocking(new Message(sfs, null), "ContextAdded");
		return "ContextAdded".equals(response.sfs.get("Message"));
	}

	private Message sendBlocking(final Message msg, String expectedMessageType) {
		return sendBlocking(msg, Collections.singleton(expectedMessageType));
	}

	private Message sendBlocking(final Message msg, Set<String> expectedMessageTypes) {
		assert (msg != null);

		//Log the contents of the message before sending (debug because of private keys etc)
		Iterator<String> msgContentIterator = msg.sfs.keyIterator();
		while(msgContentIterator.hasNext()) {
			String key = msgContentIterator.next();
			Logger.debug(this, key + "=" + msg.sfs.get(key));
		}

		//Synchronize on replyLock so only one message can be sent at a time
		final FCPPluginMessage retValue;
		Timer requestTimer;
		synchronized(replyLock) {
			requestTimer = Timer.start();

			assert (reply == null) : "Reply was " + reply;
			reply = null;

			try {
				fcpPluginConnection.send(FCPPluginMessage.construct(msg.sfs, msg.data));
			} catch (IOException e) {
				Logger.error(this, e.getLocalizedMessage());
				throw new RuntimeException(e);
			}

			while(reply == null) {
				try {
					replyLock.wait();
				} catch (InterruptedException e) {
					//Just check again
				}
			}

			retValue = reply;
			reply = null;
		}
		requestTimer.log(this, "Time spent waiting for WoT request " + msg.sfs.get("Message") + " (reply was "
				+ retValue.params.get("Message") + ")");

		String replyType = retValue.params.get("Message");
		for(String expectedMessageType : expectedMessageTypes) {
			if(expectedMessageType.equals(replyType)) {
				return new Message(retValue);
			}
		}

		Logger.error(this, "Got the wrong message from WoT. Original message was "
				+ retValue.params.get("OriginalMessage") + ", response was "
				+ replyType);

		//Log the contents of the message, but at debug since it might contain private keys etc.
		Iterator<String> keyIterator = retValue.params.keyIterator();
		while(keyIterator.hasNext()) {
			String key = keyIterator.next();
			Logger.debug(this, key + "=" + retValue.params.get(key));
		}

		return new Message(retValue);
	}

	private static class Message {
		private final SimpleFieldSet sfs;
		private final Bucket data;

		private Message(SimpleFieldSet sfs, Bucket data) {
			this.sfs = sfs;
			this.data = data;
		}

		private Message(FCPPluginMessage fcpPluginMessage) {
			sfs = fcpPluginMessage.params;
			data = fcpPluginMessage.data;
		}

		@Override
		public String toString() {
			return "[" + sfs + "] [" + data + "]";
		}
	}

	private class FCPMessageHandler implements FredPluginFCPMessageHandler.ClientSideFCPMessageHandler {
		@Override
		public FCPPluginMessage handlePluginFCPMessage(FCPPluginConnection connection, FCPPluginMessage message) {
			synchronized(replyLock) {
				assert reply == null : "Reply should be null, but was " + reply;

				reply = message;
				replyLock.notify();
			}
			return message;
		}
	}

	private enum TrustSelection {
		TRUSTED("+"),
		ZERO("0"),
		UNTRUSTED("-");

		private final String value;
		TrustSelection(String value) {
			this.value = value;
		}
	}
}
