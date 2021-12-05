package com.mazh.nx.zookeeper.nxbe12;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;

public class ZKDemo {

    // TODO_MA 马中华 注释： ZK 的服务器的地址
    private static final String CONNECT_INFO = "bigdata02:2181,bigdata03:2181,bigdata04:2181";
    // TODO_MA 马中华 注释： 会话的超时时间
    private static final int TIME_OUT = 4000;

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {

        // TODO_MA 马中华 注释： 1、获取链接
        ZooKeeper zooKeeper = new ZooKeeper(CONNECT_INFO, TIME_OUT, null);


        // TODO_MA 马中华 注释： 2、业务操作：增删改查 ZNode 节点
        String path = zooKeeper.create(
                // TODO_MA 马中华 注释： 节点路径
                "/nxbe12_01",
                // TODO_MA 马中华 注释： 节点数据
                "ABCD".getBytes(),
                // TODO_MA 马中华 注释： ACL 权限信息
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                // TODO_MA 马中华 注释： znode 节点的类型
                CreateMode.PERSISTENT
        );

        System.out.println(path);
    }
}
