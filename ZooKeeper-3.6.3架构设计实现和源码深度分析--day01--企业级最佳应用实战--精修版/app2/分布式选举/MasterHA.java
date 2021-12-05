package com.mazh.nx.zookeeper.app2.分布式选举;

import org.apache.zookeeper.*;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;

/*************************************************
 * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
 *  注释： 这就是服务器  主节点
 *  核心业务：
 *  如果A是第一个上线的master, 它会自动成为active状态
 *  如果B是第二个上线的master, 它会自动成为standby状态
 *  如果C是第三个上线的master, 它也会自动成为standby状态
 *  如果A宕机了之后, B和C去竞选谁成为active状态
 *  然后A再上线，应该要自动成为standby
 *  然后如果standby中的任何一个节点宕机，剩下的节点的active和standby的状态不用改变
 *  然后如果active中的任何一个节点宕机，那么剩下的standby节点就要去竞选active状态
 *  -
 *  HFDS ZKFC = MasterHA
 *  这个程序就是运行在 namenode 当中，用来给 anmendoe 去竞选 active 的
 */
public class MasterHA {

    private static ZooKeeper zk = null;
    private static final String CONNECT_STRING = "bigdata02:2181,bigdata03:2181,bigdata04:2181";
    private static final int Session_TimeOut = 4000;

    private static final String PARENT = "/cluster_ha";
    private static final String ACTIVE = PARENT + "/ActiveMaster";
    private static final String STANDBY = PARENT + "/StandbyMaster";
    private static final String LOCK = PARENT + "/ElectorLock";

    /*************************************************
     * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
     *  注释： 测试顺序：
     *  1、先启动 bigdata02, 成为 active
     *  2、再启动 bigdata03, 成为 standby
     *  3、再启动 bigdata04, 成为 standby
     *  4、再启动 bigdata05, 成为 standby
     *  5、停止 active 节点 bigdata02, 那么 bigdata03, bigdata04, bigdata05 会发生竞选，胜者为 active, 假如为 bigdata03
     *     那么 bigdata04, bigdata05 依然是 standby
     *  6、再干掉 active 节点 bigdata03, 那么 bigdata04, bigdata05 会发生竞选，假如 bigdata04 胜出，那么 bigdata05 依然是 standby
     *  7、然后上线 bigdata02, 当然自动成为 standby
     *  8、再干掉 bigdata04, 那么 bigdata02 和 bigdata05 竞选
     *  ........
     */
    private static final String HOSTNAME = "bigdata04";

    private static final String activeMasterPath = ACTIVE + "/" + HOSTNAME;
    private static final String standByMasterPath = STANDBY + "/" + HOSTNAME;

    private static final CreateMode CME = CreateMode.EPHEMERAL;
    private static final CreateMode CMP = CreateMode.PERSISTENT;

    public static void main(String[] args) throws Exception {

        // TODO_MA 注释： 代码执行顺序标识：1
        zk = new ZooKeeper(CONNECT_STRING, Session_TimeOut, new Watcher() {

            @Override
            public void process(WatchedEvent event) {

                String path = event.getPath();
                EventType type = event.getType();

                if (path.equals(ACTIVE) && type == EventType.NodeChildrenChanged) {

                    // TODO_MA 注释： 代码执行顺序标识：4
                    // 如果发现active节点下的active master节点被删除了之后，就应该自己去竞选active
                    if (getChildrenNumber(ACTIVE) == 0) {

                        // 先注册一把独占锁,多个standby角色，谁注册成功，谁就应该切换成为active状态
                        try {
                            zk.exists(LOCK, true);
                        } catch (KeeperException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        // 创建节点, 数据存储为自己的信息，以方便到时候判断
                        createZNode(LOCK, HOSTNAME, CME, "lock");
                    } else {
                        // getChildrenNumber(ACTIVE) == 1， 表示刚刚有active节点生成， 不用做任何操作
                    }

                    // 做到循环监听
                    try {
                        zk.getChildren(ACTIVE, true);
                    } catch (KeeperException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                } else if (path.equals(LOCK) && type == EventType.NodeCreated) {

                    // TODO_MA 注释： 代码执行顺序标识：5
                    // 获取节点数据
                    String trueData = null;
                    try {
                        byte[] data = zk.getData(LOCK, false, null);
                        trueData = new String(data);
                    } catch (Exception e) {
                    }

                    // 判断是不是当前节点创建的，如果是，就切换自己的状态为active，否则不做任何操作
                    if (trueData.equals(HOSTNAME)) {
                        // 是自己
                        createZNode(activeMasterPath, HOSTNAME, CME);

                        if (exists(standByMasterPath)) {
                            System.out.println(HOSTNAME + " 成功切换自己的状态为active");
                            deleteZNode(standByMasterPath);
                        } else {
                            System.out.println(HOSTNAME + " 竞选成为active状态");
                        }
                    } else {
                        // 不是自己
                    }

                }
            }
        });

        // TODO_MA 注释： 代码执行顺序标识：2
        // 保证PARENT一定存在
        if (!exists(PARENT)) {
            createZNode(PARENT, PARENT, CMP);
        }
        // 保证ACTIVE一定存在
        if (!exists(ACTIVE)) {
            createZNode(ACTIVE, ACTIVE, CMP);
        }
        // 保证STANDBY一定存在
        if (!exists(STANDBY)) {
            createZNode(STANDBY, STANDBY, CMP);
        }

        // TODO_MA 注释： 代码执行顺序标识：3
        // 首先判断 ACTIVE 节点下是否有子节点， 如果有的话， 必定是有active的节点存在
        // 如果没有，那么就先去注册一把锁， 让自己去竞选active
        if (getChildrenNumber(ACTIVE) == 0) {

            // TODO_MA 注释： 代码执行顺序标识：3-1
            // 注册监听
            zk.exists(LOCK, true);

            // 创建争抢锁
            createZNode(LOCK, HOSTNAME, CME);

        } else {

            // TODO_MA 注释： 代码执行顺序标识：3-2
            // 自己自动成为  standby 状态
            createZNode(standByMasterPath, HOSTNAME, CME);
            System.out.println(HOSTNAME + " 发现active存在，所以自动成为standby");

            // 注册监听， 监听active下子节点个数变化
            zk.getChildren(ACTIVE, true);
        }

        // 让程序一直运行
        Thread.sleep(Long.MAX_VALUE);
    }

    private static void deleteZNode(String standbymasterpath) {
        try {
            zk.delete(standbymasterpath, -1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static int getChildrenNumber(String path) {
        int number = 0;
        try {
            number = zk.getChildren(path, null).size();
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return number;
    }

    public static boolean exists(String path) {
        Stat exists = null;
        try {
            exists = zk.exists(path, null);
        } catch (KeeperException e) {
        } catch (InterruptedException e) {
        }
        if (exists == null) {
            return false;
        } else {
            return true;
        }
    }

    public static void createZNode(String path, String data, CreateMode cm) {
        try {
            zk.create(path, data.getBytes(), Ids.OPEN_ACL_UNSAFE, cm);
        } catch (Exception e) {
            System.out.println("创建节点失败 或者 节点已经存在");
        }
    }

    public static void createZNode(String path, String data, CreateMode cm, String message) {
        try {
            zk.create(path, data.getBytes(), Ids.OPEN_ACL_UNSAFE, cm);
        } catch (Exception e) {
            if (message.equals("lock")) {
                System.out.println("我没有抢到锁，等下一波");
            }
        }
    }
}
