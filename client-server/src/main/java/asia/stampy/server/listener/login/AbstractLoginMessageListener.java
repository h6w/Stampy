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
package asia.stampy.server.listener.login;

import java.lang.invoke.MethodHandles;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.client.message.connect.ConnectHeader;
import asia.stampy.client.message.connect.ConnectMessage;
import asia.stampy.client.message.stomp.StompMessage;
import asia.stampy.common.StampyLibrary;
import asia.stampy.common.gateway.AbstractStampyMessageGateway;
import java.net.URI;
import asia.stampy.common.gateway.MessageListenerHaltException;
import asia.stampy.common.gateway.StampyMessageListener;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StompMessageType;
import asia.stampy.common.message.interceptor.InterceptException;
import asia.stampy.server.message.error.ErrorMessage;

/**
 * This class enforces login functionality via the implementation of a.
 * 
 * {@link StampyLoginHandler}. Should the login handler throw a
 * {@link TerminateSessionException} this class will send an error to the
 * client, close the session and throw a {@link MessageListenerHaltException} to
 * prevent downstream processing of the message by the remaining
 * {@link StampyMessageListener}s.
 */
@StampyLibrary(libraryName = "stampy-client-server")
public abstract class AbstractLoginMessageListener<SVR extends AbstractStampyMessageGateway> implements
    StampyMessageListener {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static StompMessageType[] TYPES = StompMessageType.values();

  /** The logged in connections. */
  protected Queue<URI> loggedInConnections = new ConcurrentLinkedQueue<URI>();

  private StampyLoginHandler loginHandler;
  private SVR gateway;

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.gateway.StampyMessageListener#getMessageTypes()
   */
  @Override
  public StompMessageType[] getMessageTypes() {
    return TYPES;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * asia.stampy.common.gateway.StampyMessageListener#isForMessage(asia.stampy
   * .common.message.StampyMessage)
   */
  @Override
  public boolean isForMessage(StampyMessage<?> message) {
    return true;
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.gateway.StampyMessageListener#messageReceived(asia.
   * stampy.common.message.StampyMessage, asia.stampy.common.HostPort)
   */
  @Override
  public void messageReceived(StampyMessage<?> message, URI uri) throws Exception {
    switch (message.getMessageType()) {
    case ABORT:
    case ACK:
    case BEGIN:
    case COMMIT:
    case NACK:
    case SEND:
    case SUBSCRIBE:
    case UNSUBSCRIBE:
      loggedInCheck(message, uri);
      break;
    case CONNECT:
      logIn(uri, ((ConnectMessage) message).getHeader());
      break;
    case STOMP:
      logIn(uri, ((StompMessage) message).getHeader());
      break;
    case DISCONNECT:
      loggedInConnections.remove(uri);
      break;
    default:
      String error = "Unexpected message type " + message.getMessageType();
      log.error(error);
      throw new IllegalArgumentException(error);

    }
  }

  private void loggedInCheck(StampyMessage<?> message, URI uri) throws NotLoggedInException {
    if (loggedInConnections.contains(uri)) return;

    log.error("{} attempted to send a {} message without logging in", uri, message.getMessageType());
    throw new NotLoggedInException("Not logged in");
  }

  private void logIn(URI uri, ConnectHeader header) throws AlreadyLoggedInException, NotLoggedInException,
      MessageListenerHaltException {
    if (loggedInConnections.contains(uri)) throw new AlreadyLoggedInException(uri + " is already logged in");

    if (!isForHeader(header)) throw new NotLoggedInException("login and passcode not specified, cannot log in");

    try {
      getLoginHandler().login(header.getLogin(), header.getPasscode());
      loggedInConnections.add(uri);
    } catch (TerminateSessionException e) {
      log.error("Login handler has terminated the session", e);
      sendErrorMessage(e.getMessage(), uri);
      gateway.closeConnection(uri);
      throw new MessageListenerHaltException();
    }
  }

  private void sendErrorMessage(String message, URI uri) {
    ErrorMessage error = new ErrorMessage("n/a");
    error.getHeader().setMessageHeader(message);

    try {
      getGateway().sendMessage(error, uri);
    } catch (InterceptException e) {
      log.error("Sending of login error message failed", e);
    }
  }

  private boolean isForHeader(ConnectHeader header) {
    return StringUtils.isNotEmpty(header.getLogin()) && StringUtils.isNotEmpty(header.getPasscode());
  }

  /**
   * Gets the login handler.
   * 
   * @return the login handler
   */
  public StampyLoginHandler getLoginHandler() {
    return loginHandler;
  }

  /**
   * Inject the implementation of {@link StampyLoginHandler} on system startup.
   * 
   * @param loginHandler
   *          the new login handler
   */
  public void setLoginHandler(StampyLoginHandler loginHandler) {
    this.loginHandler = loginHandler;
  }

  /**
   * Gets the gateway.
   * 
   * @return the gateway
   */
  public SVR getGateway() {
    return gateway;
  }

  /**
   * Inject the {@link AbstractStampyMessageGateway} on system startup.
   * 
   * @param gateway
   *          the new gateway
   */
  public void setGateway(SVR gateway) {
    this.gateway = gateway;
    ensureCleanup();
  }

  /**
   * Configure the gateway to clean up the queue of logged in connections on
   * session termination.
   */
  protected abstract void ensureCleanup();

}
