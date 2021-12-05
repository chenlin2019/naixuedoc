package com.mazh.nx.zookeeper.app2.命名服务;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

/*************************************************
 * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
 *  注释： 命名服务
 */
public class NameService {

    // zookeeper服务器地址
    private static final String CONNECT_INFO = "bigdata02:2181,bigdata03:2181,bigdata04:2181";
    private static final int TIME_OUT = 4000;

    // 备用的父子节点
    private static final String PARENT_NODE = "/nameservice";

    // 会话对象
    private static ZooKeeper zookeeper = null;

    private static CountDownLatch countDownLatch = new CountDownLatch(1);

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        // 请开始你的表演！

        /*************************************************
         * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
         *  注释： 获取 ZooKeeper 链接
         */
        zookeeper = new ZooKeeper(CONNECT_INFO, TIME_OUT, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                // 确保链接建立
                if(countDownLatch.getCount() > 0 && event.getState() == Event.KeeperState.SyncConnected) {
                    countDownLatch.countDown();
                    System.out.println("创建会话链接成功");
                }
            }
        });
        countDownLatch.await();

        /*************************************************
         * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
         *  注释： 确保父节点存在
         */
        ArrayList<ACL> acls = ZooDefs.Ids.OPEN_ACL_UNSAFE;
        CreateMode mode = CreateMode.PERSISTENT;
        // 判断父节点是否存在
        Stat exists_parent = zookeeper.exists(PARENT_NODE, false);
        if(exists_parent == null) {
            zookeeper.create(PARENT_NODE, PARENT_NODE.getBytes(), acls, mode);
        }

        /*************************************************
         * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
         *  注释： 通过创建带顺序编号的子节点来实现命名
         */
        String node = zookeeper.create(PARENT_NODE + "/childNS", "".getBytes(), acls, CreateMode.PERSISTENT_SEQUENTIAL);

        /*************************************************
         * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
         *  注释： 获取命名结果
         */
        System.out.println(node);

        Thread.sleep(1000000);

        // TODO_MA 注释： 关闭客户端
        zookeeper.close();
    }
}
