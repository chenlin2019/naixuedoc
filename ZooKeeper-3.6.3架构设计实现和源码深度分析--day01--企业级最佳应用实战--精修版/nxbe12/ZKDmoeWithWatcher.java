package com.mazh.nx.zookeeper.nxbe12;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;

public class ZKDmoeWithWatcher {

    // TODO_MA 马中华 注释： ZK 的服务器的地址
    private static final String CONNECT_INFO = "bigdata02:2181,bigdata03:2181,bigdata04:2181";
    // TODO_MA 马中华 注释： 会话的超时时间
    private static final int TIME_OUT = 4000;

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {

        // TODO_MA 马中华 注释： 获取链接
        ZooKeeper zooKeeper = new ZooKeeper(CONNECT_INFO, TIME_OUT, null);

        // TODO_MA 马中华 注释： 注册监听！
        // TODO_MA 马中华 注释： 1、通过 getData 给 /nxbe12_01 节点注册了一个监听
        // TODO_MA 马中华 注释： 2、如果确实 /nxbe12_01 节点发送了数据改变，则 ZK 系统会通知当前客户端一个事件
        // TODO_MA 马中华 注释： 3、客户端会解析这个事件，然后回调 Watcher 对象的额process() 方法
        zooKeeper.getData("/nxbe12_01",  new MyWatcher(), null);
        System.out.println("已经注册了一个监听！");
        // TODO_MA 马中华 注释： 注册监听，其实就相当于是在  订阅

        Thread.sleep(2423094284L);
    }

    static class MyWatcher implements Watcher{
        // TODO_MA 马中华 注释： 回调方法
        @Override
        public void process(WatchedEvent event) {
            Event.KeeperState state = event.getState();
            String path = event.getPath();
            Event.EventType type = event.getType();
            System.out.println("ABCD:===" + state + "," + path + "," + type);
        }
    }
}
