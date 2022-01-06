package com.atguigu.appexer;

import com.atguigu.bean.ApacheLog;
import com.atguigu.bean.UrlCount;
import org.apache.commons.compress.utils.Lists;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.datastream.WindowedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;


public class HotUrlAppExer {
    public static void main(String[] args) throws Exception {
        //1.获取执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        //2.读取文本数据，转换为JavaBean，过滤，提取时间戳生成watermark
        SingleOutputStreamOperator<ApacheLog> apacheLogDS = env.readTextFile("input/apache.log")
                .map(new MapFunction<String, ApacheLog>() {
                    @Override
                    public ApacheLog map(String value) throws Exception {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy:HH:mm:ss");
                        String[] fields = value.split(" ");
                        return new ApacheLog(fields[1],
                                fields[1],
                                sdf.parse(fields[3]).getTime(),
                                fields[3],
                                fields[4]);
                    }

                })
                .filter(new FilterFunction<ApacheLog>() {
                    @Override
                    public boolean filter(ApacheLog value) throws Exception {
                        return "GET".equals(value.getMethod());
                    }
                })
                .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor<ApacheLog>(Time.seconds(2)) {
                    @Override
                    public long extractTimestamp(ApacheLog element) {
                        return element.getEventTime() * 1000L;
                    }
                });

        //3.按照URL分组
        KeyedStream<ApacheLog, String> apacheLogKeyedStream = apacheLogDS.keyBy(ApacheLog::getUrl);
        //4.开窗，窗口大小10分钟,滑动步长5秒钟,允许一分钟的迟到数据
        WindowedStream<ApacheLog, String, TimeWindow> windowedStream = apacheLogKeyedStream.timeWindow(Time.minutes(10), Time.seconds(5))
                .allowedLateness(Time.minutes(1));

        //5.计算每个窗口每个URL的访问次数,获取窗口信息
        SingleOutputStreamOperator<UrlCount> aggWindowDS = windowedStream.aggregate(new UrlCountAggFunc(), new UrlCountWindowFunc());

        //6.按照窗口信息分组
        KeyedStream<UrlCount, Long> urlCountLongKeyedStream = aggWindowDS.keyBy(UrlCount::getWindowEnd);

        //7.使用ProcessFunction处理排序,状态编程  定时器
        SingleOutputStreamOperator<String> result = urlCountLongKeyedStream.process(new UrlCountProcessFunc(5));

        //8.打印
        result.print();

        //9.执行
        env.execute();
    }
    public static class UrlCountAggFunc implements AggregateFunction<ApacheLog,Long,Long> {

        @Override
        public Long createAccumulator() {
            return 0L;
        }

        @Override
        public Long add(ApacheLog value, Long accumulator) {
            return accumulator+1;
        }

        @Override
        public Long getResult(Long accumulator) {
            return accumulator;
        }

        @Override
        public Long merge(Long a, Long b) {
            return a+b;
        }
    }

    public static class UrlCountWindowFunc implements WindowFunction<Long, UrlCount,String,TimeWindow> {
        @Override
        public void apply(String url, TimeWindow window, Iterable<Long> input, Collector<UrlCount> out) throws Exception {
            out.collect(new UrlCount(url,window.getEnd(),input.iterator().next()));
        }
    }
    public static class UrlCountProcessFunc extends KeyedProcessFunction<Long,UrlCount,String> {

        //定义属性
        private int TopN;

        public UrlCountProcessFunc() {
        }

        public UrlCountProcessFunc(int topN) {
            TopN = topN;
        }

        //声明集合状态
        private MapState<String,UrlCount> mapState;

        @Override
        public void open(Configuration parameters) throws Exception {
            //状态初始化
            mapState = getRuntimeContext().getMapState(new MapStateDescriptor<String, UrlCount>("map-state",
                    String.class,UrlCount.class));
        }

        @Override
        public void processElement(UrlCount value, Context ctx, Collector<String> out) throws Exception {
            //将数据放入状态中
            mapState.put(value.getUrl(),value);

            //注册1毫秒的定时器
            ctx.timerService().registerEventTimeTimer(value.getWindowEnd()+1);
            //注册1分钟后的定时器
            ctx.timerService().registerEventTimeTimer(value.getWindowEnd()+60000L);
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
            if (timestamp==ctx.getCurrentKey()+60000L) {
                //清空状态
                mapState.clear();
                //返回
                return;
            }

            //取出状态数据
            Iterator<Map.Entry<String, UrlCount>> iterator = mapState.entries().iterator();
            ArrayList<Map.Entry<String, UrlCount>> entries = Lists.newArrayList(iterator);
            //排序
            entries.sort(new Comparator<Map.Entry<String, UrlCount>>() {
                @Override
                public int compare(Map.Entry<String, UrlCount> o1, Map.Entry<String, UrlCount> o2) {
                    UrlCount urlCount1 = o1.getValue();
                    UrlCount urlCount2 = o2.getValue();

                    return urlCount2.getCount().compareTo(urlCount1.getCount());
                }
            });
            //准备输出数据
            StringBuilder sb = new StringBuilder();
            sb.append("===========").append(new Timestamp(timestamp - 1)).append("===========").append("\n");


            //遍历数据取出TopN
            for (int i = 0; i < Math.min(TopN, entries.size()); i++) {

                //取出单条数据
                UrlCount urlCount = entries.get(i).getValue();

                sb.append("Top ").append(i + 1);
                sb.append(" URL:").append(urlCount.getUrl());
                sb.append(" Count:").append(urlCount.getCount());
                sb.append("\n");
            }

            //休息
            Thread.sleep(100);

            //输出数据
            out.collect(sb.toString());
        }
    }
}
