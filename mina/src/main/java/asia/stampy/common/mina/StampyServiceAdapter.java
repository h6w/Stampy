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
package asia.stampy.common.mina;

import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;

import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.common.StampyLibrary;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * This class keeps track of all connections and disconnections and is the
 * interface for sending messages to remote hosts.
 */
@Resource
@StampyLibrary(libraryName = "stampy-MINA-client-server-RI")
public class StampyServiceAdapter extends MinaServiceAdapter {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Map<URI, IoSession> sessions = new ConcurrentHashMap<URI, IoSession>();

  private boolean autoShutdown;

  private AbstractStampyMinaMessageGateway gateway;

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.apache.mina.core.service.IoServiceListener#sessionCreated(org.apache
   * .mina.core.session.IoSession)
   */
  @Override
  public void sessionCreated(IoSession session) throws Exception {
    URI uri = createURI(session);
    log.info("Stampy MINA session created for {}", uri);

    sessions.put(uri, session);
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.apache.mina.core.service.IoServiceListener#sessionDestroyed(org.apache
   * .mina.core.session.IoSession)
   */
  @Override
  public void sessionDestroyed(IoSession session) throws Exception {
    URI uri = createURI(session);
    log.info("Stampy MINA session destroyed for {}", uri);

    sessions.remove(uri);

    if (sessions.isEmpty() && isAutoShutdown()) {
      log.info("No more sessions and auto shutdown is true, shutting down gateway");
      gateway.shutdown();
    }
  }

  public void closeAllSessions() {
    for (IoSession session : sessions.values()) {
      CloseFuture cf = session.close(true);
      cf.awaitUninterruptibly();
    }

    sessions.clear();
  }

  public void closeSession(URI uri) {
    IoSession session = sessions.get(uri);
    if (session != null) {
      session.close(false);
    }
  }

  private URI createURI(IoSession session) {
    try {
	    return new URI("stomp","",((InetSocketAddress) session.getRemoteAddress()).getHostName(),((InetSocketAddress) session.getRemoteAddress()).getPort(),"","","");
    }
    catch (URISyntaxException e) {
	    System.err.println(e);
    }
    return null;
  }

  /**
   * Returns true if the specified {@link HostPort} has an active session.
   * 
   * @param uri
   *          the host port
   * @return true, if successful
   */
  public boolean hasSession(URI uri) {
    return sessions.containsKey(uri);
  }

  /**
   * Gets the session.
   * 
   * @param uri
   *          the host port
   * @return the session
   */
  public IoSession getSession(URI uri) {
    IoSession session = sessions.get(uri);

    if (session == null) throw new IllegalArgumentException(uri.toString() + " has no current session");

    return session;
  }

  /**
   * Gets the host ports.
   * 
   * @return the host ports
   */
  public Set<URI> getURIs() {
    return Collections.unmodifiableSet(sessions.keySet());
  }

  /**
   * Send message.
   * 
   * @param stompMessage
   *          the stomp message
   * @param uri
   *          the host port
   */
  public void sendMessage(byte[] stompMessage, URI uri) {
    if (!hasSession(uri)) {
      log.error("No session for {}, cannot send message {}", uri, stompMessage);
      return;
    }

    IoSession session = getSession(uri);
    if (session.isConnected() && !session.isClosing()) {
      session.write(stompMessage);
      log.trace("Sent message {} to {}", stompMessage, uri);
    } else {
      log.error("Session is not active for {}, cannot send message {}", uri, stompMessage);
    }
  }

  /**
   * Checks if is auto shutdown.
   * 
   * @return true, if is auto shutdown
   */
  public boolean isAutoShutdown() {
    return autoShutdown;
  }

  /**
   * Sets the auto shutdown.
   * 
   * @param autoClose
   *          the new auto shutdown
   */
  public void setAutoShutdown(boolean autoClose) {
    this.autoShutdown = autoClose;
  }

  /**
   * Gets the gateway.
   * 
   * @return the gateway
   */
  public AbstractStampyMinaMessageGateway getGateway() {
    return gateway;
  }

  /**
   * Sets the gateway.
   * 
   * @param gateway
   *          the new gateway
   */
  public void setGateway(AbstractStampyMinaMessageGateway gateway) {
    this.gateway = gateway;
  }

}
