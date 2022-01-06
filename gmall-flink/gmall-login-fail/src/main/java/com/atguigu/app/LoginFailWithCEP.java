package com.atguigu.app;

import com.atguigu.bean.LoginEvent;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;


public class LoginFailWithCEP {
    public static void main(String[] args) throws Exception {
//1.获取执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

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

        //3.按照用户id分组
        KeyedStream<LoginEvent, Long> keyedStream = loginEventDS.keyBy(LoginEvent::getUserId);

        //4.定义模式连续两次失败
//        Pattern<LoginEvent, LoginEvent> parttern = Pattern.<LoginEvent>begin("start").where(new SimpleCondition<LoginEvent>() {
//            @Override
//            public boolean filter(LoginEvent value) throws Exception {
//                return "fail".equals(value.getEventType());
//            }
//        }).next("next").where(new SimpleCondition<LoginEvent>() {
//            @Override
//            public boolean filter(LoginEvent value) throws Exception {
//                return "fail".equals(value.getEventType());
//            }
//        }).within(Time.seconds(2));

        //用循环模式
        Pattern<LoginEvent, LoginEvent> pattern = Pattern.<LoginEvent>begin("start").where(new SimpleCondition<LoginEvent>() {
            @Override
            public boolean filter(LoginEvent value) throws Exception {
                return "fail".equals(value.getEventType());
            }
        }).within(Time.seconds(5))
                .times(5)
                .consecutive();


        //5.将模式作用到流上
        PatternStream<LoginEvent> patternStream = CEP.pattern(keyedStream, pattern);

        //6.提取时间
        SingleOutputStreamOperator<String> result = patternStream.select(new MyPatternSelectFunc());

        //7.打印
        result.print();

        //8.执行
        env.execute();
    }

    public static class MyPatternSelectFunc implements PatternSelectFunction<LoginEvent, String> {

        @Override
        public String select(Map<String, List<LoginEvent>> pattern) throws Exception {
            //提取事件
//            LoginEvent start = pattern.get("start").get(0);
//            LoginEvent next = pattern.get("next").get(0);

            List<LoginEvent> events = pattern.get("start");
            LoginEvent start = events.get(0);
            LoginEvent next = events.get(events.size()-1);

            long startTimestamp = start.getTimestamp() * 1000L;
            long nextTimestamp = next.getTimestamp() * 1000L;
            return start.getUserId() + "在" + new Timestamp(startTimestamp) + "到" + new Timestamp(nextTimestamp) +
                    "之间连续登陆失败" +events.size() + "次" ;
        }
    }
}
