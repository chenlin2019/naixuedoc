package com.mazh.nx.zookeeper.app2.集群管理;

import org.apache.zookeeper.*;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs.Ids;

import java.util.ArrayList;
import java.util.List;

/*************************************************
 * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
 *  注释：
 *  作用：用来感知上线 或者 下线的服务器 怎么模拟？
 *  namenode 去监控 /servers 节点下面的子节点个数
 *  如果个数增加，表示上线一个 datanode
 *  如果个数减少，表示下线一个 datanode
 *	-
 *  测试前提：当 nameonde 上线了之后，会去监听：/servers
 *  zk.getChildren("/servers", watcher)  ===>  NodeChildrendChanged
 */
public class Namenode {

    private static ZooKeeper zk = null;
    private static List<String> currentNodes = new ArrayList<>();

    public static void main(String[] args) throws Exception {

        // TODO_MA 马中华 注释： 第一步：拿zookeeper连接
        zk = new ZooKeeper(Constant.ConnectStr, Constant.TimeOut, new Watcher() {
            @Override
            public void process(WatchedEvent event) {

                // 直接打印event的时候，第一次打印的结果是：Type:Node, Null
                String outPath = event.getPath();
                EventType type = event.getType();
                System.out.println(outPath + "  -- " + type.toString());

                /*************************************************
                 * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
                 *  注释： 如果是 /servers 节点发生了  NodeChildrenChanged 事件，就证明有服务器上线或者下线！
                 */
                if (type == EventType.NodeChildrenChanged && event.getPath().equals(Constant.ParentNode)) {

                    try {
                        // 存储上一次的节点集合
                        List<String> lastNodes = currentNodes;
                        // 获取新的节点集合
                        currentNodes = getDatanodeList(zk);
                        // 求两个集合的差
                        String resultNode = null;
                        if (lastNodes.size() > currentNodes.size()) {
                            for (String node : lastNodes) {
                                if (!currentNodes.contains(node)) {
                                    resultNode = node;
                                    break;
                                }
                            }
                            System.out.println(resultNode + " 下线成功");
                        }else{
                            for (String node : currentNodes) {
                                if (!lastNodes.contains(node)) {
                                    resultNode = node;
                                    break;
                                }
                            }
                            System.out.println(resultNode + " 上线成功");
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (type == EventType.NodeDataChanged && event.getPath().equals(Constant.ParentNode)) {
                    System.out.println("节点数据发生变化");

                    try {
                        byte[] data = zk.getData(Constant.ParentNode, null, null);
                        System.out.println(new String(data));

                        zk.getData(Constant.ParentNode, true, null);
                    } catch (KeeperException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        // TODO_MA 马中华 注释： 第二步：检查/servers节点存在与否，如果不存在，则创建
        if (zk.exists(Constant.ParentNode, null) == null) {
            zk.create(Constant.ParentNode, "huangbo".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } else {
            System.out.println(Constant.ParentNode + " 已存在");
        }

        // TODO_MA 马中华 注释： 第三步：给/servers节点加监听
        zk.getChildren(Constant.ParentNode, true);
        zk.getData(Constant.ParentNode, true, null);

        Thread.sleep(Integer.MAX_VALUE);

        // TODO_MA 马中华 注释： 关闭zk连接
        zk.close();
    }

    /*************************************************
     * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
     *  注释： 获取子节点内容
     */
    public static List<String> getDatanodeList(ZooKeeper zk) throws Exception {
        // TODO_MA 马中华 注释： 获取父节点下面的子节点列表。也就是datanode列表
        // TODO_MA 马中华 注释： 同时也继续注册监听
        List<String> children = zk.getChildren(Constant.ParentNode, true);
        return children;
    }
}
