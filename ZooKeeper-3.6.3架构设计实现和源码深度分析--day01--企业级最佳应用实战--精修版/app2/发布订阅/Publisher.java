package com.mazh.nx.zookeeper.app2.发布订阅;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/*************************************************
 * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
 *  注释： 发布者程序
 *  实现思路： Publisher程序只负责发布消息
 *  -
 *  发布者的逻辑：
 *  发布一个消息到 ZK 中。把消息存储在 ZK 中的一个 znode 上
 *  订阅者，因为订阅了 znode 节点的父节点的 NodeChildrenChanged 事件，所以 订阅者能收到通知。
 *  收到了通知，再去查询这个 znode 上的数据，就知道了发布的 消息到底是什么了。
 **/
public class Publisher {

    // zookeeper服务器地址
    private static final String CONNECT_INFO = "bigdata02:2181,bigdata03:2181,bigdata04:2181";
    private static final int TIME_OUT = 4000;

    // 备用的父子节点
    private static final String PARENT_NODE = "/publish_parent";

    // TODO_MA 注释： 发布者程序发布消息： 通过 key-value 来存储， 发布消息就是创建一个 znode 节点
    // TODO_MA 注释： key 就是 znode 的 path， value = 消息的值
    // TODO_MA 注释： key = SUB_NODE， value = PUBLISH_INFO

    // TODO_MA 注释： 节点路径
    private static final String SUB_NODE_NAME = "publish_info";
    private static final String SUB_NODE = PARENT_NODE + "/" + SUB_NODE_NAME;

    // TODO_MA 注释： 待发布的消息
//    private static final String PUBLISH_INFO = "bigdata01,7899,com.mazh.nx.Service01,getSum,[1,2,3]";
//    private static final String PUBLISH_INFO = "bigdata02,6655,com.mazh.nx.Service02,hello,huangbo";
//    private static final String PUBLISH_INFO = "bigdata066,9522,com.mazh.nx.Service03,getDate,2020-02-02";
    private static final String PUBLISH_INFO = "bigdata9876,9527,com.mazh.nx.Service9876,getName,xuzheng";

    // 会话对象
    private static ZooKeeper zookeeper = null;

    // latch就相当于一个对象锁，当latch.await()方法执行时，方法所在的线程会等待
    // 当latch的count减为0时，将会唤醒等待的线程
    private static CountDownLatch countDownLatch = new CountDownLatch(1);

    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
        // 请开始你的表演！！！

        /*************************************************
         * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
         *  注释： 获取会话
         */
        zookeeper = new ZooKeeper(CONNECT_INFO, TIME_OUT, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                // 确保链接建立
                if (countDownLatch.getCount() > 0 && event.getState() == Event.KeeperState.SyncConnected) {
                    System.out.println("创建会话链接成功");
                    countDownLatch.countDown();
                }

                // 发布者不用干点啥
            }
        });

        /*************************************************
         * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
         *  注释： 先确保父节点存在
         */
        ArrayList<ACL> acls = ZooDefs.Ids.OPEN_ACL_UNSAFE;
        CreateMode mode = CreateMode.PERSISTENT;
        // 判断父节点是否存在
        Stat exists_parent = zookeeper.exists(PARENT_NODE, false);
        if (exists_parent == null) {
            zookeeper.create(PARENT_NODE, PARENT_NODE.getBytes(), acls, mode);
        }

        /*************************************************
         * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
         *  注释： 发布消息
         */
        List<String> children = zookeeper.getChildren(PARENT_NODE, false);
        String[] strings = children.toArray(new String[]{});
        Arrays.sort(strings);
        String max = strings[strings.length - 1];
//        String index = max.substring(SUB_NODE_NAME.length() - 2);
        String index = "100111";
        CreateMode serviceMode = CreateMode.PERSISTENT;
        String s = zookeeper.create(SUB_NODE + index, PUBLISH_INFO.getBytes(), acls, serviceMode);
        System.out.println(s);

        /*************************************************
         * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
         *  注释： 关闭会话链接
         */
        zookeeper.close();
    }
}
