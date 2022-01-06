package com.atguigu.app;

import com.atguigu.bean.OrderEvent;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.PatternTimeoutFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.OutputTag;

import java.util.List;
import java.util.Map;

public class OrderPayWithCepApp {
    public static void main(String[] args) throws Exception {
        //1.获取执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        //2读取文本数据，转换为JavaBean并提取时间戳生成watermark
        SingleOutputStreamOperator<OrderEvent> orderEventDS = env.readTextFile("input/OrderLog.csv")
                .map(new MapFunction<String, OrderEvent>() {
                    @Override
                    public OrderEvent map(String value) throws Exception {
                        String[] feilds = value.split(",");
                        return new OrderEvent(Long.parseLong(feilds[0]),
                                feilds[1],
                                feilds[2],
                                Long.parseLong(feilds[3]));
                    }
                })
                .assignTimestampsAndWatermarks(new AscendingTimestampExtractor<OrderEvent>() {
                    @Override
                    public long extractAscendingTimestamp(OrderEvent element) {
                        return element.getEventTime() * 1000L;
                    }
                });

        //3.按照ID分组
        KeyedStream<OrderEvent, Long> keyedStream = orderEventDS.keyBy(OrderEvent::getOrderId);

        //4.定义模式序列
        Pattern<OrderEvent, OrderEvent> pattern = Pattern.<OrderEvent>begin("start").where(new SimpleCondition<OrderEvent>() {
            @Override
            public boolean filter(OrderEvent value) throws Exception {
                return "create".equals(value.getEventType());
            }
        })
                .followedBy("follow").where(new SimpleCondition<OrderEvent>() {
                    @Override
                    public boolean filter(OrderEvent value) throws Exception {
                        return "pay".equals(value.getEventType());
                    }
                })
                .within(Time.seconds(15));

        //5.将模式序列作用到流中
        PatternStream<OrderEvent> patternStream = CEP.pattern(keyedStream, pattern);

        //6.提取时间，匹配上的和超时的都需要
        SingleOutputStreamOperator<String> result = patternStream.select(new OutputTag<String>("timeOut") {
                                                                         },
                new MyTimeOutSelectFunc(),
                new MySelectFunc());

        //7.打印数据
        result.print("Result");
        result.getSideOutput(new OutputTag<String>("timeOut"){
        }).print("TimeOut");

        //8.执行
        env.execute();
    }

    public static class MySelectFunc implements PatternSelectFunction<OrderEvent, String> {

        @Override
        public String select(Map<String, List<OrderEvent>> pattern) throws Exception {
            OrderEvent start = pattern.get("start").get(0);
            OrderEvent follow = pattern.get("follow").get(0);

            return start.getOrderId() + " " + "create at" + start.getEventTime() + ",payed at" + follow.getEventTime();
        }
    }

    public static class MyTimeOutSelectFunc implements PatternTimeoutFunction<OrderEvent, String> {

        @Override
        public String timeout(Map<String, List<OrderEvent>> pattern, long l) throws Exception {
            OrderEvent start = pattern.get("start").get(0);

            return start.getOrderId() + "超时！！";
        }
    }
}

