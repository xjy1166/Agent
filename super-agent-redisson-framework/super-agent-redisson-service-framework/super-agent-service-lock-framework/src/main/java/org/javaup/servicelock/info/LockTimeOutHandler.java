package org.javaup.servicelock.info;

/**

 * @description: 分布式锁 处理失败抽象

 **/
public interface LockTimeOutHandler {

    void handler(String lockName);
}
