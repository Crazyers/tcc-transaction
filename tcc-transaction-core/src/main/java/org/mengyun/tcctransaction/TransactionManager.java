/*
 * ====================================================================
 * 龙果学院： www.roncoo.com （微信公众号：RonCoo_com）
 * 超级教程系列：《微服务架构的分布式事务解决方案》视频教程
 * 讲师：吴水成（水到渠成），840765167@qq.com
 * 课程地址：http://www.roncoo.com/course/view/7ae3d7eddc4742f78b0548aa8bd9ccdb
 * ====================================================================
 */
package org.mengyun.tcctransaction;

import org.apache.log4j.Logger;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.api.TransactionXid;
import org.mengyun.tcctransaction.common.TransactionType;
import org.mengyun.tcctransaction.support.TransactionConfigurator;

/**
 * 事务管理器.
 * Created by changmingxie on 10/26/15.
 */
public class TransactionManager {

    static final Logger LOG = Logger.getLogger(TransactionManager.class.getSimpleName());

    /**
	 * 事务配置器
	 */
    private TransactionConfigurator transactionConfigurator;

    public TransactionManager(TransactionConfigurator transactionConfigurator) {
        this.transactionConfigurator = transactionConfigurator;
    }

    /**
     * 定义当前线程的事务局部变量.
     */
    private ThreadLocal<Transaction> threadLocalTransaction = new ThreadLocal<Transaction>();

    /**
     * 事务开始（创建事务日志记录，并将该事务日志记录存入当前线程的事务局部变量中）
     */
    public void begin() {
    	LOG.debug("-->begin()");
        Transaction transaction = new Transaction(TransactionType.ROOT); // 事务类型为ROOT:1
        LOG.debug("-->TransactionType:" + transaction.getTransactionType().toString() + ", Transaction Status:" + transaction.getStatus().toString());
        TransactionRepository transactionRepository = transactionConfigurator.getTransactionRepository();
        transactionRepository.create(transaction); // 创建事务记录,写入事务日志库
        threadLocalTransaction.set(transaction); // 将该事务日志记录存入当前线程的事务局部变量中
    }

    /**
     * 基于全局事务ID扩展创建新的分支事务，并存于当前线程的事务局部变量中.
     * @param transactionContext
     */
    public void propagationNewBegin(TransactionContext transactionContext) {

        Transaction transaction = new Transaction(transactionContext);
        LOG.debug("-->propagationNewBegin TransactionXid：" + TransactionXid.byteArrayToUUID(transaction.getXid().getGlobalTransactionId()).toString()
        		+ "|" + TransactionXid.byteArrayToUUID(transaction.getXid().getBranchQualifier()).toString());
        
        transactionConfigurator.getTransactionRepository().create(transaction);

        threadLocalTransaction.set(transaction);
    }

    /**
     * 找出存在的事务并处理.
     * @param transactionContext
     * @throws NoExistedTransactionException
     */
    public void propagationExistBegin(TransactionContext transactionContext) throws NoExistedTransactionException {
        TransactionRepository transactionRepository = transactionConfigurator.getTransactionRepository();
        Transaction transaction = transactionRepository.findByXid(transactionContext.getXid());

        if (transaction != null) {
        	
        	LOG.debug("-->propagationExistBegin TransactionXid：" + TransactionXid.byteArrayToUUID(transaction.getXid().getGlobalTransactionId()).toString()
            		+ "|" + TransactionXid.byteArrayToUUID(transaction.getXid().getBranchQualifier()).toString());
        	
            transaction.changeStatus(TransactionStatus.valueOf(transactionContext.getStatus()));
            threadLocalTransaction.set(transaction);
        } else {
            throw new NoExistedTransactionException();
        }
    }

    /**
     * 提交.
     */
    public void commit() {
    	LOG.debug("-->TransactionManager commit()");
        Transaction transaction = getCurrentTransaction();

        transaction.changeStatus(TransactionStatus.CONFIRMING);
        LOG.debug("-->update transaction status to CONFIRMING");
        transactionConfigurator.getTransactionRepository().update(transaction);

        try {
        	LOG.info("-->transaction begin commit()");
            transaction.commit();
            transactionConfigurator.getTransactionRepository().delete(transaction);
        } catch (Throwable commitException) {
            LOG.error("compensable transaction confirm failed.", commitException);
            throw new ConfirmingException(commitException);
        }
    }

    /**
     * 获取当前事务.
     * @return
     */
    public Transaction getCurrentTransaction() {
        return threadLocalTransaction.get();
    }

    /**
     * 回滚事务.
     */
    public void rollback() {

        Transaction transaction = getCurrentTransaction();
        transaction.changeStatus(TransactionStatus.CANCELLING);

        transactionConfigurator.getTransactionRepository().update(transaction);
        
        try {
        	LOG.info("-->transaction begin rollback()");
            transaction.rollback();
            transactionConfigurator.getTransactionRepository().delete(transaction);
        } catch (Throwable rollbackException) {
            LOG.error("compensable transaction rollback failed.", rollbackException);
            throw new CancellingException(rollbackException);
        }
    }
}
