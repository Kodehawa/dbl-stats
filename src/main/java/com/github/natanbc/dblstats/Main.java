package com.github.natanbc.dblstats;

import co.cask.http.NettyHttpService;
import com.github.natanbc.discordbotsapi.BotInfo;
import com.github.natanbc.discordbotsapi.DiscordBotsAPI;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger("DBL-Stats");
    private static final DiscordBotsAPI API = new DiscordBotsAPI();
    private static JedisPool pool;
    private static JSONObject data;
    private static List<BotInfo> bots;
    private static OffsetDateTime lastUpdated;

    public static void main(String[] args) throws Exception {
        pool = new JedisPool("localhost", Integer.getInteger("redis_port", 6379));

        update();
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(Main::update, 1, 1, TimeUnit.HOURS);

        NettyHttpService service = NettyHttpService.builder("API")
                .setPort(Integer.getInteger("port", 5346))
                .setHttpHandlers(new DBLStatsHandler(), new StaticFilesHandler())
                .setHost("localhost")
                .build();

        service.start();
    }

    private static void update() {
        try {
            List<BotInfo> list = System.getProperty("debug", null) == null ?
                    API.index().stream().collect(Collectors.toList()) :
                    API.index().stream().limit(40).collect(Collectors.toList());

            if(System.getProperty("enable_chart", null) != null) {
                try(Jedis jedis = pool.getResource()) {
                    for(String s : jedis.smembers("chart-whitelist")) {
                        long idLong = Long.parseUnsignedLong(s);
                        list.stream().filter(b->b.getId() == idLong).findFirst().ifPresent(b->
                                jedis.rpush("guilds-" + s, Integer.toString(b.getServerCount()))
                        );
                    }
                }
            }

            OffsetDateTime updated = OffsetDateTime.now();

            JSONObject json = new JSONObject();

            json.put("total_bots", list.size());
            json.put("stats", genStats(list));
            json.put("updated", updated.toString());

            lastUpdated = updated;
            bots = list;
            data = json;
            LOGGER.info("Updated data");
        } catch(Exception e) {
            LOGGER.error("Error updating data", e);
        }
    }

    public static JedisPool getPool() {
        return pool;
    }

    public static JSONObject getData() {
        return data;
    }

    public static List<BotInfo> getBots() {
        return bots;
    }

    public static OffsetDateTime getLastUpdated() {
        return lastUpdated;
    }

    private static JSONObject genStats(List<BotInfo> list) {
        JSONObject obj = new JSONObject();

        obj.put("total", total(list));
        obj.put("certified", certified(list));
        obj.put("points", upvotes(list));

        return obj;
    }

    private static JSONObject total(List<BotInfo> list) {
        Map<String, Integer> map = list.stream().map(BotInfo::getLib).collect(Collectors.groupingBy(
                Function.identity(),
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
        ));
        JSONObject o = new JSONObject();
        map.forEach(o::put);
        return o;
    }

    private static JSONObject certified(List<BotInfo> list) {
        Map<String, Integer> map = list.stream().filter(BotInfo::isCertified).map(BotInfo::getLib).collect(Collectors.groupingBy(
                Function.identity(),
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
        ));
        JSONObject o = new JSONObject();
        map.forEach(o::put);
        return o;
    }

    private static JSONObject upvotes(List<BotInfo> list) {
        Map<String, Integer> map = list.stream().collect(Collectors.groupingBy(
                BotInfo::getLib,
                Collectors.summingInt(BotInfo::getPoints)
        ));
        JSONObject o = new JSONObject();
        map.forEach(o::put);
        return o;
    }
}
