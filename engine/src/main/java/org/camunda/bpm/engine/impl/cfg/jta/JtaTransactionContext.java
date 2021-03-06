/*
 * Copyright © 2012 - 2018 camunda services GmbH and various authors (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.impl.cfg.jta;

import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.camunda.bpm.engine.impl.ProcessEngineLogger;
import org.camunda.bpm.engine.impl.cfg.TransactionContext;
import org.camunda.bpm.engine.impl.cfg.TransactionListener;
import org.camunda.bpm.engine.impl.cfg.TransactionLogger;
import org.camunda.bpm.engine.impl.cfg.TransactionState;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;

/**
 * @author Daniel Meyer
 */
public class JtaTransactionContext implements TransactionContext {

  public final static TransactionLogger LOG = ProcessEngineLogger.TX_LOGGER;

  protected final TransactionManager transactionManager;

  public JtaTransactionContext(TransactionManager transactionManager) {
    this.transactionManager = transactionManager;
  }

  public void commit() {
    // managed transaction, ignore
  }

  public void rollback() {
    // managed transaction, mark rollback-only if not done so already.
    try {
      Transaction transaction = getTransaction();
      int status = transaction.getStatus();
      if (status != Status.STATUS_NO_TRANSACTION && status != Status.STATUS_ROLLEDBACK) {
        transaction.setRollbackOnly();
      }
    }
    catch (Exception e) {
      throw LOG.exceptionWhileInteractingWithTransaction("setting transaction rollback only", e);
    }
  }

  protected Transaction getTransaction() {
    try {
      return transactionManager.getTransaction();
    }
    catch (Exception e) {
      throw LOG.exceptionWhileInteractingWithTransaction("getting transaction", e);
    }
  }

  public void addTransactionListener(TransactionState transactionState, final TransactionListener transactionListener) {
    Transaction transaction = getTransaction();
    CommandContext commandContext = Context.getCommandContext();
    try {
      transaction.registerSynchronization(new TransactionStateSynchronization(transactionState, transactionListener, commandContext));
    }
    catch (Exception e) {
      throw LOG.exceptionWhileInteractingWithTransaction("registering synchronization", e);
    }
  }

  public static class TransactionStateSynchronization implements Synchronization {

    protected final TransactionListener transactionListener;
    protected final TransactionState transactionState;
    private final CommandContext commandContext;

    public TransactionStateSynchronization(TransactionState transactionState, TransactionListener transactionListener, CommandContext commandContext) {
      this.transactionState = transactionState;
      this.transactionListener = transactionListener;
      this.commandContext = commandContext;
    }

    public void beforeCompletion() {
      if(TransactionState.COMMITTING.equals(transactionState)
         || TransactionState.ROLLINGBACK.equals(transactionState)) {
        transactionListener.execute(commandContext);
      }
    }

    public void afterCompletion(int status) {
      if(Status.STATUS_ROLLEDBACK == status && TransactionState.ROLLED_BACK.equals(transactionState)) {
        transactionListener.execute(commandContext);
      } else if(Status.STATUS_COMMITTED == status && TransactionState.COMMITTED.equals(transactionState)) {
        transactionListener.execute(commandContext);
      }
    }

  }

  public boolean isTransactionActive() {
    try {
      return transactionManager.getStatus() != Status.STATUS_MARKED_ROLLBACK && transactionManager.getStatus() != Status.STATUS_NO_TRANSACTION;
    } catch (SystemException e) {
      throw LOG.exceptionWhileInteractingWithTransaction("getting transaction state", e);
    }
  }

}
