package com.atguigu.app;

import com.atguigu.bean.ItemCount;
import com.atguigu.bean.UserBehavior;
import org.apache.commons.compress.utils.Lists;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.datastream.WindowedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;


public class HotItemApp {
    public static void main(String[] args) throws Exception {
        //1.获取执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        //2.读取文本，转换为JavaBean,过滤,提取时间戳生成watermark
        SingleOutputStreamOperator<UserBehavior> userBehaviorDS = env.readTextFile("input/UserBehavior.csv")
                .map(new MapFunction<String, UserBehavior>() {
                    @Override
                    public UserBehavior map(String value) throws Exception {
                        String[] fields = value.split(",");
                        return new UserBehavior(Long.parseLong(fields[0]),
                                Long.parseLong(fields[1]),
                                Integer.parseInt(fields[2]),
                                fields[3],
                                Long.parseLong(fields[4]));
                    }
                }).filter(data -> "pv".equals(data.getBehavior()))
                .assignTimestampsAndWatermarks(new AscendingTimestampExtractor<UserBehavior>() {
                    @Override
                    public long extractAscendingTimestamp(UserBehavior element) {
                        return element.getTimestamp() * 1000;
                    }
                });

        //3.商品id分组
        KeyedStream<UserBehavior, Long> keyedStream = userBehaviorDS.keyBy(UserBehavior::getItemId);

        //4.开窗
        WindowedStream<UserBehavior, Long, TimeWindow> windowedStream = keyedStream.timeWindow(Time.hours(1), Time.minutes(5));

        //5.聚合，逐条计算每个商品点击次数,获取窗口时间
        SingleOutputStreamOperator<ItemCount> itemCountWindowEndDS = windowedStream.aggregate(new ItemCountFunction(), new ItemCountWinFunction());

        //6.按照窗口时间分组
        KeyedStream<ItemCount, Long> itemCountLongKeyedStream = itemCountWindowEndDS.keyBy(ItemCount::getWindowEnd);

        //7.使用ProcessFunction实现收集每个窗口中的数据做排序输出(状态编程--ListState  定时器)
        SingleOutputStreamOperator<String> result = itemCountLongKeyedStream.process(new ItemKeydeProcessFunction(5));

        //8.打印
        result.print();

        //9.执行
        env.execute();

    }

    //逐条计算,效率更高
    public static class ItemCountFunction implements AggregateFunction<UserBehavior, Long, Long> {

        @Override
        public Long createAccumulator() {
            return 0L;
        }

        @Override
        public Long add(UserBehavior value, Long accumulator) {
            return accumulator + 1L;
        }

        @Override
        public Long getResult(Long accumulator) {
            return accumulator;
        }

        @Override
        public Long merge(Long a, Long b) {
            return a + b;
        }
    }

    //获取窗口时间
    public static class ItemCountWinFunction implements WindowFunction<Long, ItemCount, Long, TimeWindow> {

        @Override
        public void apply(Long itemId, TimeWindow window, Iterable<Long> input, Collector<ItemCount> out) throws Exception {
            //获取itemId,窗口关闭时间,当前窗口当前商品的点击次数
            out.collect(new ItemCount(itemId, window.getEnd(), input.iterator().next()));
        }
    }

    public static class ItemKeydeProcessFunction extends KeyedProcessFunction<Long, ItemCount, String> {

        //定义属性
        private int topN;

        public ItemKeydeProcessFunction() {

        }

        public ItemKeydeProcessFunction(int topN) {
            this.topN = topN;
        }

        //声明集合状态
        private ListState<ItemCount> listState;

        @Override
        public void open(Configuration parameters) throws Exception {
            //状态初始化
            listState = getRuntimeContext().getListState(new ListStateDescriptor<ItemCount>("list-state",
                    ItemCount.class));
        }

        @Override
        public void processElement(ItemCount value, Context ctx, Collector<String> out) throws Exception {
            //来的每条数据加入状态
            listState.add(value);
            //注册定时器
            ctx.timerService().registerEventTimeTimer(value.getWindowEnd() + 1000L);

        }

        //定时器
        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
            //提取状态中的数据
            Iterator<ItemCount> iterator = listState.get().iterator();
            ArrayList<ItemCount> itemCounts = Lists.newArrayList(iterator);

            //排序
            itemCounts.sort(new Comparator<ItemCount>() {
                @Override
                public int compare(ItemCount o1, ItemCount o2) {
                    if (o1.getCount() > o2.getCount()) {
                        return -1;
                    } else if (o1.getCount() < o2.getCount()) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            });

            //输出TopN数据
            StringBuilder sb = new StringBuilder();

            sb.append("=======================")
                    .append(new Timestamp(timestamp - 1000))
                    .append("=======================")
                    .append("\n");

            //遍历排序之后的结果
            for (int i = 0; i < Math.min(topN, itemCounts.size()); i++) {
                //1.提取数据
                ItemCount itemCount = itemCounts.get(i);

                //2.封装top数据
                sb.append("Top").append(i + 1);
                sb.append(" ItemId").append(itemCount.getItemId());
                sb.append(" Count").append(itemCount.getCount());
                sb.append("\n");

            }

            //休息
            Thread.sleep(2000);

            //清空状态
            listState.clear();

            //输出数据
            out.collect(sb.toString());
        }
    }
}
