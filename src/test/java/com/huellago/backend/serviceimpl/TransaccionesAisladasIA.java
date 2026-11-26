package com.huellago.backend.serviceimpl;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;

/** Gestor de recursos ficticios para ejercitar proxies Spring; no prueba bloqueos ni rollback SQL. */
final class TransaccionesAisladasIA extends AbstractPlatformTransactionManager {
    private final ThreadLocal<Estado> recurso = ThreadLocal.withInitial(Estado::new);
    int comienzos, confirmaciones, reversiones;
    private static class Estado { boolean activo; }
    @Override protected Object doGetTransaction() { return recurso.get(); }
    @Override protected boolean isExistingTransaction(Object tx) { return ((Estado)tx).activo; }
    @Override protected void doBegin(Object tx, TransactionDefinition d) { ((Estado)tx).activo=true; comienzos++; }
    @Override protected void doCommit(DefaultTransactionStatus s) { confirmaciones++; }
    @Override protected void doRollback(DefaultTransactionStatus s) { reversiones++; }
    @Override protected void doSetRollbackOnly(DefaultTransactionStatus s) { }
    @Override protected void doCleanupAfterCompletion(Object tx) { recurso.remove(); }
}
