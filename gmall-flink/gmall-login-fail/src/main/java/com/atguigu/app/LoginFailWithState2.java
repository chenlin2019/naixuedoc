package com.atguigu.app;

import com.atguigu.bean.LoginEvent;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

public class LoginFailWithState2 {
    public static void main(String[] args) throws Exception {
        //1.获取执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.setParallelism(1);

        //2.读取文本数据转换为JavaBean并提取时间戳生成Watermark
        SingleOutputStreamOperator<LoginEvent> loginEventDS = env.readTextFile("input/LoginLog.csv")
                .map(new MapFunction<String, LoginEvent>() {
                    @Override
                    public LoginEvent map(String value) throws Exception {
                        String[] fields = value.split(",");
                        return new LoginEvent(Long.parseLong(fields[0]),
                                fields[1],
                                fields[2],
                                Long.parseLong(fields[3]));
                    }
                })
                .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor<LoginEvent>(Time.seconds(5)) {
                    @Override
                    public long extractTimestamp(LoginEvent element) {
                        return element.getTimestamp() * 1000L;
                    }
                });

        //3.按照用户分组
        KeyedStream<LoginEvent, Long> loginEventLongKeyedStream = loginEventDS.keyBy(LoginEvent::getUserId);

        //4.使用ProcessFunction处理数据
        SingleOutputStreamOperator<String> result = loginEventLongKeyedStream.process(new LoginFailProcessFunc(2));

        //5.打印
        result.print();

        //6.执行
        env.execute();
    }
    public static class LoginFailProcessFunc extends KeyedProcessFunction<Long,LoginEvent,String> {

        //定义属性
        private int interval;

        public LoginFailProcessFunc(int interval) {
            this.interval = interval;
        }

        //定义状态
        private ValueState<LoginEvent> failEventState;

        @Override
        public void open(Configuration parameters) throws Exception {
            //状态初始化
            failEventState = getRuntimeContext().getState(new ValueStateDescriptor<LoginEvent>("event-state",
                    LoginEvent.class));
        }

        @Override
        public void processElement(LoginEvent value, Context ctx, Collector<String> out) throws Exception {

            //判断当前数据为失败数据
            if ("fail".equals(value.getEventType())) {
                //取出状态数据
                LoginEvent lastFail = failEventState.value();
                failEventState.update(value);

                //非第一条数据，比较时间间隔
                if (lastFail != null && Math.abs(value.getTimestamp()-lastFail.getTimestamp()) <= interval) {

                    //输出报警信息
                    out.collect(value.getUserId() + "连续登陆失败" + interval + "次");
                }
            } else {
                //清空状态
                failEventState.clear();
            }

        }
    }
}
