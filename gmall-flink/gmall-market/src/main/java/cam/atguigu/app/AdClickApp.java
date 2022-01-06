package cam.atguigu.app;

import cam.atguigu.bean.AdClickEvent;
import cam.atguigu.bean.AdCountByProvince;
import cam.atguigu.bean.BlackListWarning;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple;
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
import org.apache.flink.util.OutputTag;

import java.sql.Timestamp;

public class AdClickApp {
    public static void main(String[] args) throws Exception {
        //1.获取执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        //2.读取文本数据，转换为JavaBean
        SingleOutputStreamOperator<AdClickEvent> adClickEventDS = env.readTextFile("input/AdClickLog.csv")
                .map(line -> {
                    String[] fields = line.split(",");
                    return new AdClickEvent(Long.parseLong(fields[0]),
                            Long.parseLong(fields[1]),
                            fields[2],
                            fields[3],
                            Long.parseLong(fields[4]));
                })
                .assignTimestampsAndWatermarks(new AscendingTimestampExtractor<AdClickEvent>() {
                    @Override
                    public long extractAscendingTimestamp(AdClickEvent element) {
                        return element.getTimestamp() * 1000L;
                    }
                });

        //添加过滤逻辑,单日某个人点击某个广告达到100次,加入黑名单(今天后续不再统计该用户的点击信息)
        KeyedStream<AdClickEvent, Tuple> adClickEventTupleKeyedStream = adClickEventDS.keyBy("userId", "adId");
        SingleOutputStreamOperator<AdClickEvent> filterDS =
                adClickEventTupleKeyedStream.process(new BlankListProcessFunc(100));


        //3.按照省份分组
        KeyedStream<AdClickEvent, String> keyedStream = filterDS.keyBy(AdClickEvent::getProvince);

        //4.开窗
        WindowedStream<AdClickEvent, String, TimeWindow> windowedStream = keyedStream.timeWindow(Time.hours(1), Time.seconds(5));

        //5.用Aggregate滚动聚合
        SingleOutputStreamOperator<AdCountByProvince> result = windowedStream.aggregate(new adCountAddFunc(), new adCountWindowFunc());

        //6.打印
        result.print("result");

        filterDS.getSideOutput(new OutputTag<BlackListWarning>("BlackList") {
        }).print("side");

        //7.执行
        env.execute();
    }

    public static class adCountAddFunc implements AggregateFunction<AdClickEvent, Long, Long> {

        @Override
        public Long createAccumulator() {
            return 0L;
        }

        @Override
        public Long add(AdClickEvent value, Long accumulator) {
            return accumulator + 1;
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

    public static class adCountWindowFunc implements WindowFunction<Long, AdCountByProvince, String, TimeWindow> {

        @Override
        public void apply(String province, TimeWindow window, Iterable<Long> input, Collector<AdCountByProvince> out) throws Exception {
            out.collect(new AdCountByProvince(province,
                    new Timestamp(window.getEnd()).toString(),
                    input.iterator().next()));
        }
    }

    public static class BlankListProcessFunc extends KeyedProcessFunction<Tuple, AdClickEvent, AdClickEvent> {

        //定义属性
        private long maxClick;

        public BlankListProcessFunc(long maxClick) {
            this.maxClick = maxClick;
        }

        //定义状态
        private ValueState<Long> countState;
        private ValueState<Boolean> isSendState;

        @Override
        public void open(Configuration parameters) throws Exception {
            countState = getRuntimeContext().getState(new ValueStateDescriptor<Long>("count-state", Long.class));
            isSendState = getRuntimeContext().getState(new ValueStateDescriptor<Boolean>("issend-state", Boolean.class));
        }

        @Override
        public void processElement(AdClickEvent value, Context ctx, Collector<AdClickEvent> out) throws Exception {

            //获取状态
            Long count = countState.value();
            Boolean isSend = isSendState.value();

            //判断为第一条数据
            if (count == null) {
                countState.update(1L);

                //注册定时器，时间为第二天零点
                long ts = (value.getTimestamp() / (60 * 60 * 24) + 1) * (24 * 60 * 60 * 1000L) - 8 * 60 * 60 * 1000L;
                ctx.timerService().registerEventTimeTimer(ts);
            } else {
                //更新状态
                countState.update(count + 1);
                if (count + 1 >= maxClick) {
                    //判断数据是否输出到过测输出流
                    if (isSend == null) {
                        //输出到测输出流
                        ctx.output(new OutputTag<BlackListWarning>("BlackList") {
                        }, new BlackListWarning(value.getUserId(), value.getAdId(), "拉入黑名单！！！"));
                        //更新状态
                        isSendState.update(true);
                    }
                    return;
                }
            }
            //输出数据
            out.collect(value);
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<AdClickEvent> out) throws Exception {
            countState.clear();
            isSendState.clear();
        }
    }

}
