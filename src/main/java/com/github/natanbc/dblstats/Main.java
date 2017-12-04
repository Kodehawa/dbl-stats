package com.github.natanbc.dblstats;

import co.cask.http.NettyHttpService;
import com.github.natanbc.discordbotsapi.BotInfo;
import com.github.natanbc.discordbotsapi.DiscordBotsAPI;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
    private static final DiscordBotsAPI api = new DiscordBotsAPI();
    private static JSONObject data;
    private static List<BotInfo> bots;
    private static OffsetDateTime lastUpdated;

    public static void main(String[] args) throws Exception {
        update();
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(Main::update, 1, 1, TimeUnit.HOURS);

        NettyHttpService service = NettyHttpService.builder("API")
                .setPort(Integer.getInteger("port", 5346))
                .setHttpHandlers(new DBLStatsHandler())
                .setHost("localhost")
                .build();

        service.start();
    }

    private static void update() {
        List<BotInfo> list = System.getProperty("debug", null) == null ?
                api.index().stream().collect(Collectors.toList()) :
                api.index().stream().limit(40).collect(Collectors.toList());
        OffsetDateTime updated = OffsetDateTime.now();

        JSONObject json = new JSONObject();

        json.put("total_bots", list.size());
        json.put("stats", genStats(list));
        json.put("updated", updated.toString());

        lastUpdated = updated;
        bots = list;
        data = json;
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
