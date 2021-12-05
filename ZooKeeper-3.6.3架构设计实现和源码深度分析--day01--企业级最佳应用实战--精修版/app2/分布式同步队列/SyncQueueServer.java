package com.mazh.nx.zookeeper.app2.分布式同步队列;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.util.List;

/*************************************************
 * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
 *  注释：
 *  Author： 马中华 奈学教育 https://blog.csdn.net/zhongqi2513
 *  DateTime： 2017/4/25 15:29
 *  Description： 同步队列 、 分布式栅栏
 *  -
 *  作用：我们执行业务方法的业务类，监听PARENT_NODE下的子节点的数目
 *  因为我们是实现同步队列，要求是达到一定的队列成员时，我们的业务方法才可以执行
 **/
public class SyncQueueServer {

    private static final String CONNECT_STRING = "bigdata02:2181,bigdata03:2181,bigdata04:2181";
    private static final int sessionTimeout = 4000;
    private static final String PARENT_NODE = "/syncQueue";
    private static final int NEED_QUEUE = 3;
    static ZooKeeper zk = null;

    static int count = 0;

    public static void main(String[] args) throws Exception {

        /*************************************************
         * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
         *  注释： 1、获取zookeeper链接
         */
        zk = new ZooKeeper(CONNECT_STRING, sessionTimeout, new Watcher() {

            @Override
            public void process(WatchedEvent event) {
                String path = event.getPath();
                EventType et = event.getType();

                // 第一次判断监听的节点和事件是否满足要求
                if (path.equals(PARENT_NODE) && et == EventType.NodeChildrenChanged) {
                    // 第二次要判断队列的成员是否都达到，如果是，才能执行业务方法

                    try {
                        List<String> children = zk.getChildren(PARENT_NODE, true);
                        int queueNumber = children.size();
                        if (queueNumber == NEED_QUEUE) {
                            System.out.println("所有兄弟到齐！2222222");
                            handleBusiness(true);
                        } else if (queueNumber < NEED_QUEUE) {
                            if (count == NEED_QUEUE) {
                                System.out.println("所有兄弟到齐！1111111");
                                handleBusiness(false);
                            } else {
                                System.out.println("正等待其他兄弟上线，现在到位的有：" + queueNumber + ", 还缺：" + (NEED_QUEUE - queueNumber));
                            }
                        }
                        count = queueNumber;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
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
            System.out.println("测试日志：   父节点：" + PARENT_NODE + "  已存在，不用我创建");
        }

        /*************************************************
         * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
         *  注释： 3、给该程序应该要监听的znode加监听
         */
        zk.getChildren(PARENT_NODE, true);

        Thread.sleep(Long.MAX_VALUE);

        zk.close();
    }

    /*************************************************
     * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
     *  注释：
     *  首先，我们的这个程序会一直监听PARENT_NODE，等待所监听的节点下的队列成员是否全部到达 如果满足条件，该业务方法才会执行
     *  这个条件是：
     *  1、该业务程序收到的某一个节点的某一个事件要满足我们监听的该节点
     *  2、队列的所有成员都达到
     */
    public static void handleBusiness(boolean flag) throws Exception {
        if (flag) {
            System.out.println("正在调用业务方法........");
        } else {
            System.out.println("已停止处理业务..........");
        }
    }
}
