/*
 * Copyright (C) 2013 Burton Alexander
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 * 
 */
package asia.stampy.common.mina.raw;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.common.StampyLibrary;
import java.net.URI;
import asia.stampy.common.gateway.MessageListenerHaltException;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StompMessageType;
import asia.stampy.common.mina.StampyMinaHandler;
import asia.stampy.common.parsing.StompMessageParser.ReadableByteArray;
import asia.stampy.common.parsing.UnparseableException;

/**
 * This class uses its own message parsing to piece together STOMP messages. In
 * non-Stampy STOMP environments subclasses are to be used. While tested
 * successfully in simple cases it has not (yet) been battle-tested. Use at your
 * own risk.
 */
@StampyLibrary(libraryName = "stampy-MINA-client-server-RI")
public abstract class StampyRawStringHandler extends StampyMinaHandler {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Map<URI, byte[]> messageParts = new ConcurrentHashMap<URI, byte[]>();

  /*
   * (non-Javadoc)
   * 
   * @see
   * asia.stampy.common.mina.StampyMinaHandler#messageReceived(org.apache.mina
   * .core.session.IoSession, java.lang.Object)
   */
  @Override
  public void messageReceived(IoSession session, Object message) throws Exception {
    final URI uri = new URI("stomp","",((InetSocketAddress) session.getRemoteAddress()).getHostName(),((InetSocketAddress) session.getRemoteAddress()).getPort(),"","","");
    log.trace("Received raw message {} from {}", message, uri);

    helper.resetHeartbeat(uri);

    if (!(message instanceof byte[])) {
      log.error("Object {} is not a valid STOMP message, closing connection {}", message, uri);
      illegalAccess(session);
      return;
    }

    final byte[] msg = (byte[]) message;

    Runnable runnable = new Runnable() {

      @Override
      public void run() {
        asyncProcessing(uri, msg);
      }
    };

    getExecutor().execute(runnable);
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.mina.StampyMinaHandler#getFactory(int)
   */
  @Override
  public ProtocolCodecFactory getFactory(int maxMessageSize) {
    return new StringCodecFactory(maxMessageSize);
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * asia.stampy.common.mina.StampyMinaHandler#asyncProcessing(org.apache.mina
   * .core.session.IoSession, asia.stampy.common.HostPort, java.lang.String)
   */
  @Override
  protected void asyncProcessing(URI uri, byte[] msg) {
    try {
      byte[] existing = messageParts.get(uri);
      if (existing.length == 0) {
        processNewMessage(uri, msg);
      } else {
        ByteArrayOutputStream concat = new ByteArrayOutputStream( );
        concat.write(existing);
        concat.write(msg);
        processMessage(concat.toByteArray(), uri);
      }
    } catch (UnparseableException e) {
      helper.handleUnparseableMessage(uri, new String(msg), e);
    } catch (MessageListenerHaltException e) {
      // halting
    } catch (Exception e) {
      helper.handleUnexpectedError(uri, new String(msg), null, e);
    }
  }

  private void processNewMessage(URI uri, byte[] msg) throws Exception, UnparseableException, IOException {
    if (helper.isHeartbeat(msg)) {
      log.trace("Received heartbeat");
      return;
    } else if (isStompMessage(msg)) {
      processMessage(msg, uri);
    } else {
      helper.handleUnparseableMessage(uri, new String(msg), null);
    }
  }

  private void processMessage(byte[] msg, URI uri) throws Exception {
    int length = msg.length;
    int idx = indexOfEOM(msg);

    //TODO this is not correct, a single message may contain \0 if the content-length is set.
    if (idx == length - 1) {
      log.trace("Creating StampyMessage from {}", msg);
      processStompMessage(msg, uri);
    } else if (idx > 0) {
      log.trace("Multiple messages detected, parsing {}", msg);
      processMultiMessages(msg, uri);
    } else {
      messageParts.put(uri, msg);
      log.trace("Message part {} stored for {}", msg, uri);
    }
  }

  private void processMultiMessages(byte[] msg, URI uri) throws Exception {
    int idx = indexOfEOM(msg);
    byte[] fullMessage = Arrays.copyOfRange(msg, 0, idx +1);
    byte[] partMessage = Arrays.copyOfRange(msg, idx, msg.length-1);
    if (partMessage[0] == '\0') {
      partMessage = Arrays.copyOfRange(partMessage, 1, partMessage.length-1);
    }

    processStompMessage(fullMessage, uri);

    processMessage(partMessage, uri);
  }

  private void processStompMessage(byte[] msg, URI uri) throws MessageListenerHaltException {
    messageParts.remove(uri);
    StampyMessage<?> sm = null;
    try {
      sm = getParser().parseMessage(msg);
      getGateway().notifyMessageListeners(sm, uri);
    } catch (MessageListenerHaltException e) {
      throw e;
    } catch (Exception e) {
      helper.handleUnexpectedError(uri, new String(msg), sm, e);
    }
  }

  private boolean isStompMessage(byte[] msg) throws Exception {
    ReadableByteArray r = new ReadableByteArray(msg);
    byte[] line = r.toNextNewLine();
    StompMessageType type = StompMessageType.valueOf(new String(line));
    return type != null;
  }
  
  private int indexOfEOM(byte[] msg) {
    for (int i = 0; i < msg.length; i++) {
      if(msg[i] == '\0') {
        return i;
      }
    }
    return -1;
  }

}
