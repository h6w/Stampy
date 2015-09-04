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
package asia.stampy.server.listener.transaction;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.client.message.abort.AbortMessage;
import asia.stampy.client.message.begin.BeginMessage;
import asia.stampy.client.message.commit.CommitMessage;
import asia.stampy.common.StampyLibrary;
import asia.stampy.common.gateway.AbstractStampyMessageGateway;
import java.net.URI;
import asia.stampy.common.gateway.StampyMessageListener;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StompMessageType;

/**
 * This class manages transactional boundaries, ensuring that a transaction has
 * been started prior to an {@link StompMessageType#ABORT} or.
 * 
 * {@link StompMessageType#COMMIT} and that a transaction is began only once.
 */
@StampyLibrary(libraryName = "stampy-client-server")
public abstract class AbstractTransactionListener<SVR extends AbstractStampyMessageGateway> implements StampyMessageListener {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /** The active transactions. */
  protected Map<URI, Queue<String>> activeTransactions = new ConcurrentHashMap<URI, Queue<String>>();
  private SVR gateway;

  private static StompMessageType[] TYPES = { StompMessageType.ABORT, StompMessageType.BEGIN, StompMessageType.COMMIT,
      StompMessageType.DISCONNECT };

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
      abort(uri, ((AbortMessage) message).getHeader().getTransaction());
      break;
    case BEGIN:
      begin(uri, ((BeginMessage) message).getHeader().getTransaction());
      break;
    case COMMIT:
      commit(uri, ((CommitMessage) message).getHeader().getTransaction());
      break;
    case DISCONNECT:
      logOutstandingTransactions(uri);
      break;
    default:
      break;

    }

  }

  private void logOutstandingTransactions(URI uri) {
    Queue<String> q = getTransactions(uri);
    if (q.isEmpty()) return;

    for (String transaction : q) {
      log.warn("Disconnect received, discarding outstanding transaction {}", transaction);
    }
  }

  private void commit(URI uri, String transaction) throws TransactionNotStartedException {
    removeActiveTransaction(uri, transaction, "committed");
  }

  private void abort(URI uri, String transaction) throws TransactionNotStartedException {
    removeActiveTransaction(uri, transaction, "aborted");
  }

  private void begin(URI uri, String transaction) throws TransactionAlreadyStartedException {
    if (isNoTransaction(uri, transaction)) {
      log.info("Starting transaction {} for {}", transaction, uri);
      Queue<String> q = getTransactions(uri);
      q.add(transaction);
    }
  }

  private boolean isNoTransaction(URI uri, String transaction) throws TransactionAlreadyStartedException {
    Queue<String> q = getTransactions(uri);
    if (q.contains(transaction)) {
      String error = "Transaction already started";
      throw new TransactionAlreadyStartedException(error);
    }

    return true;
  }

  private void removeActiveTransaction(URI uri, String transaction, String function)
      throws TransactionNotStartedException {
    if (isTransactionStarted(uri, transaction)) {
      Object[] parms = { transaction, uri, function };
      log.info("Transaction id {} for {} {}", parms);
      Queue<String> q = getTransactions(uri);
      q.remove(transaction);
    }
  }

  private Queue<String> getTransactions(URI uri) {
    Queue<String> transactions = activeTransactions.get(uri);
    if (transactions == null) {
      transactions = new ConcurrentLinkedQueue<String>();
      activeTransactions.put(uri, transactions);
    }

    return transactions;
  }

  private boolean isTransactionStarted(URI uri, String transaction) throws TransactionNotStartedException {
    Queue<String> q = getTransactions(uri);
    if (!q.contains(transaction)) {
      String error = "Transaction not started";
      log.error(error);
      throw new TransactionNotStartedException(error);
    }

    return true;
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
  }

  /**
   * Configure the gateway to clean up the map of active transactions on session
   * termination.
   */
  protected abstract void ensureCleanup();

}
