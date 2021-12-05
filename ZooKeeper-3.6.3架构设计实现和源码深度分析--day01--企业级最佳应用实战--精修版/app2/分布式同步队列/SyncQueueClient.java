package com.mazh.nx.zookeeper.app2.分布式同步队列;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

/*************************************************
 * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
 *  注释：
 *  Author： 马中华 奈学教育 https://blog.csdn.net/zhongqi2513
 *  DateTime： 2017/4/25 15:29
 *  Description： 同步队列 、 分布式栅栏
 *  -
 *  作用：用来记录每一台上线的服务器
 *  具体做法：往 zookeeper 的文件系统里面的某一个znode下面写入我们上线了的服务器的信息
 **/
public class SyncQueueClient {

    private static final String CONNECT_STRING = "bigdata02:2181,bigdata03:2181,bigdata04:2181";
    private static final int sessionTimeout = 4000;
    private static final String PARENT_NODE = "/syncQueue";

    // TODO_MA 注释： 每个成员兄弟的名称，每次运行的时候，请更改一个名字
    private static final String HOSTNAME = "bigdata05";

    public static void main(String[] args) throws Exception {

        /*************************************************
         * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
         *  注释：1、获取zookeeper的链接
         */
        ZooKeeper zk = new ZooKeeper(CONNECT_STRING, sessionTimeout, new Watcher() {

            @Override
            public void process(WatchedEvent event) {
                // TODO Auto-generated method stub
            }
        });

        /*************************************************
         * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
         *  注释： 2、先判断父节点是否存在
         */
        Stat exists = zk.exists(PARENT_NODE, false);
        if (exists == null) {
            zk.create(PARENT_NODE, PARENT_NODE.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } else {
            System.out.println(PARENT_NODE + "  已存在，不用我创建");
        }

        /*************************************************
         * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
         *  注释： 3、往父节点下记录一台刚上线的成员的信息
         *  节点的名字：/syncQueue/hadoop01
         */
        String path = zk.create(PARENT_NODE + "/" + HOSTNAME, HOSTNAME
                .getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        System.out.println("当前上线的成员是：" + HOSTNAME + ", 当前成员注册的子节点是：" + path);

        Thread.sleep(Long.MAX_VALUE);

        zk.close();
    }
}