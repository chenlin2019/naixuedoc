package com.mazh.nx.zookeeper.app2.集群管理;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;

/*************************************************
 * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
 *  注释： 模拟服务器上下线
 *  启动代表上线，程序停止代表服务器宕机下线
 */
public class DataNode {

    private static int index = 1205;

    private static ZooKeeper zk = null;

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {

        // TODO_MA 马中华 注释： 获取 ZK 链接
        zk = new ZooKeeper(Constant.ConnectStr, Constant.TimeOut, null);

        String path = Constant.ChildNode+ index;

        // TODO_MA 马中华 注释： 服务器上线
        // path = /servers/childNode107
        String str = zk.create(Constant.ChildNode + index, "服务器".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL);
        System.out.println(str + "上线成功");

        // TODO_MA 马中华 注释： 模拟服务器一直运行
        Thread.sleep(Long.MAX_VALUE);

        // TODO_MA 马中华 注释： 服务器下线
        System.out.println(str + "下线成功");
        zk.close();
    }
}
