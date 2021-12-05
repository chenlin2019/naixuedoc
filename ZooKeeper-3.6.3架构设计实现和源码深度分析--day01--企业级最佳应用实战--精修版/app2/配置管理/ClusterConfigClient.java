package com.mazh.nx.zookeeper.app2.配置管理;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;

/*************************************************
 * TODO_MA 马中华 https://blog.csdn.net/zhongqi2513
 *  注释： 这个程序模拟配置的改变：
 *  1、增加一个配置
 *  2、修改一个配置
 *  3、删除一个配置
 *  做配置修改的客户端程序  client
 */
public class ClusterConfigClient {

	// 获取zookeeper连接时所需要的服务器连接信息，格式为主机名：端口号
	private static final String ConnectString = "bigdata02:2181,bigdata03:2181,bigdata04:2181";

	// 请求链接的会话超时时长
	private static final int SessionTimeout = 5000;

	private static ZooKeeper zk = null;
	private static final String PARENT_NODE = "/config";

	public static void main(String[] args) throws Exception {

		// TODO_MA 注释： 获取 ZooKeeper
		zk = new ZooKeeper(ConnectString, SessionTimeout, new Watcher() {

			@Override
			public void process(WatchedEvent event) {
				System.out.println(event.getPath() + "\t-----" + event.getType());
			}
		});

		// TODO_MA 注释： 创建父节点
		if (null == zk.exists(PARENT_NODE, false)) {
			zk.create(PARENT_NODE, "cluster-config".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
		}

		// TODO_MA 注释： 利用 创建 znode 节点模拟 新增配置
//		zk.create(PARENT_NODE + "/hadoop", "hadoop".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
//		zk.create(PARENT_NODE+"/hive", "hive".getBytes(),Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
//		zk.create(PARENT_NODE+"/mysql", "mysql".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
//		zk.create(PARENT_NODE+"/redis3", "redis".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

		// TODO_MA 注释： 利用 删除 znode 节点模拟 删除配置
//		zk.delete(PARENT_NODE+"/hadoop", -1);
//		zk.delete(PARENT_NODE+"/hive", -1);
//		zk.delete(PARENT_NODE+"/mysql", -1);
		zk.delete(PARENT_NODE+"/redis2", -1);

		// TODO_MA 注释： 利用 修改 znode 节点数据模拟 修改配置
//		zk.setData(PARENT_NODE+"/hadoop", "aaaa2222222".getBytes(), -1);
//		zk.setData(PARENT_NODE+"/hive", "8899aaaa".getBytes(), -1);
//		zk.setData(PARENT_NODE+"/mysql", "64646456464456".getBytes(), -1);
//		zk.setData(PARENT_NODE+"/redis1", "lsdkflsadfa".getBytes(), -1);

		// TODO_MA 注释： 关闭 ZK 客户端
		zk.close();
	}
}
