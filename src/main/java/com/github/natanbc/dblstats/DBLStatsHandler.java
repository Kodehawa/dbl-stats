package com.github.natanbc.dblstats;

import co.cask.http.AbstractHttpHandler;
import co.cask.http.HttpResponder;
import com.github.natanbc.discordbotsapi.BotInfo;
import com.github.natanbc.luaeval.CycleLimitExceededException;
import io.netty.handler.codec.http.CombinedHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.luaj.vm2.LuaError;
import redis.clients.jedis.Jedis;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DBLStatsHandler extends AbstractHttpHandler {
    @Path("/")
    @GET
    public void index(HttpRequest request, HttpResponder responder) throws IOException {
        responder.sendString(HttpResponseStatus.OK, new String(
                Files.readAllBytes(Paths.get("./index.html")),
                StandardCharsets.UTF_8
        ).replace("%BOT_INFO_FIELDS%", genFieldsHtml()), new CombinedHttpHeaders(false).add(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8"));
    }

    @Path("/chart")
    @GET
    public void graph(HttpRequest request, HttpResponder responder, @QueryParam("amount") int amount) throws IOException {
        if(System.getProperty("enable_chart", null) == null) {
            responder.sendStatus(HttpResponseStatus.NOT_FOUND);
            return;
        }
        responder.sendString(HttpResponseStatus.OK, new String(
                Files.readAllBytes(Paths.get("./chart.html")),
                StandardCharsets.UTF_8
        ).replace("%AMOUNT%", Integer.toString(amount)), new CombinedHttpHeaders(false).add(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8"));
    }

    @Path("/chartdata")
    @GET
    public void graphdata(HttpRequest request, HttpResponder responder, @QueryParam("amount") int amount) {
        if(System.getProperty("enable_chart", null) == null) {
            responder.sendStatus(HttpResponseStatus.NOT_FOUND);
            return;
        }
        //at least 5 samples
        if(amount < 5) amount = 5;
        String[] names;
        int[][] colors;
        int[][] data;
        try(Jedis jedis = Main.getPool().getResource()) {
            Set<String> whitelist = jedis.smembers("chart-whitelist");
            names = new String[whitelist.size()];
            colors = new int[whitelist.size()][];
            data = new int[whitelist.size()][];
            int i = 0;
            for(String s : whitelist) {
                long idLong = Long.parseUnsignedLong(s);
                names[i] = Main.getBots().stream().filter(b->b.getId() == idLong).findFirst().map(BotInfo::getUsername).orElse(null);
                long len = jedis.llen("guilds-" + s);
                if(amount > len) {
                    responder.sendString(HttpResponseStatus.BAD_REQUEST, "Not enough samples");
                    return;
                }
                if(jedis.llen("colors-" + s) < 2) {
                    colors[i] = new int[2];
                } else {
                    colors[i] = jedis.lrange("colors-" + s, -2, -1).stream().mapToInt(Integer::parseUnsignedInt).toArray();
                }
                data[i] = jedis.lrange("guilds-" + s, -amount, -1).stream().mapToInt(Integer::parseUnsignedInt).toArray();
                i++;
            }
        }
        /*
        data = new int[][]{
                {1000, 1150, 1200, 1280},
                {3000, 3100, 3110, 3200}
        };
        names = new String[]{
                "Test A",
                "Test B"
        };
        // */
        JSONArray dataWithExpectedGrowth = new JSONArray();
        for(int i = 0; i < data.length; i++) {
            JSONArray a = new JSONArray();
            int[] color = colors[i];
            int[] array = data[i];
            String name = names[i];
            for(int j : array) a.put(j);
            int now = array[amount-1];
            int first = array[0];
            double growthRate = Math.pow(((double)now/first), 1d/(amount)) - 1;
            for(int j = 1; j < amount; j++) {
                a.put(Math.floor(now + now * growthRate * j));
            }
            dataWithExpectedGrowth.put(new JSONObject()
                    .put("label", name)
                    .put("data", a)
                    .put("backgroundColor", color[0] == 0 ? null : "#" + Integer.toHexString(color[0]))
                    .put("borderColor", color[1] == 0 ? null : "#" + Integer.toHexString(color[1]))
            );
        }
        List<String> l = new LinkedList<>();
        for(int i = amount; i > 0; i--) {
            if(i == amount) {
                l.add(0, "Today");
            } else if(i == amount - 1) {
                l.add(0, "Yesterday");
            } else {
                l.add(0, (amount - i) + " days ago");
            }
        }
        for(int i = 0; i < amount-1; i++) {
            if(i == 0) {
                l.add("Tomorrow");
            } else {
                l.add(i + 1 + " days from now");
            }
        }
        JSONArray labels = new JSONArray();
        l.forEach(labels::put);
        responder.sendJson(HttpResponseStatus.OK, new JSONObject()
                .put("labels", labels)
                .put("datasets", dataWithExpectedGrowth)
                .toString()
        );
    }

    @Path("/bots")
    @GET
    public void bots(HttpRequest request, HttpResponder responder) {
        responder.sendJson(HttpResponseStatus.OK, Main.getData().toString(4));
    }

    @Path("/query")
    @GET
    public void query(HttpRequest request, HttpResponder responder,
                       @QueryParam("script") String script, @QueryParam("limit") long limit, @QueryParam("skip") long skip,
                       @QueryParam("filter") List<String> filters, @QueryParam("select") List<String> select) {
        if(limit < 1) limit = Long.MAX_VALUE;
        if(skip < 0) skip = 0;
        try {
            //no need to waste cpu if no result needed
            if(select.isEmpty()) {
                responder.sendJson(HttpResponseStatus.OK, new JSONObject()
                        .put("results", new JSONArray())
                        .put("updated", Main.getLastUpdated().toString())
                        .toString(4)
                );
                return;
            }
            if(script != null) {
                try {
                    JSONArray a = new JSONArray();
                    LuaQuery.query(Main.getBots(), script).stream().skip(skip).limit(limit).map(b->{
                        JSONObject j = new JSONObject();
                        select.forEach(e->j.put(e, getField(b, e)));
                        return j;
                    }).forEach(a::put);
                    responder.sendJson(HttpResponseStatus.OK, new JSONObject()
                            .put("results", a)
                            .put("updated", Main.getLastUpdated().toString())
                            .toString(4)
                    );
                } catch(CycleLimitExceededException e) {
                    responder.sendJson(HttpResponseStatus.BAD_REQUEST, new JSONObject()
                            .put("error", "cycle limit exceeded")
                            .toString(4)
                    );
                } catch(LuaError|LuaQuery.InvalidReturnException e) {
                    responder.sendJson(HttpResponseStatus.BAD_REQUEST, new JSONObject()
                            .put("error", e.getMessage())
                            .toString(4)
                    );
                } catch(Exception e) {
                    e.printStackTrace();
                    responder.sendJson(HttpResponseStatus.INTERNAL_SERVER_ERROR, new JSONObject()
                            .put("error", e.toString())
                            .toString(4)
                    );
                }
                return;
            }
            Map<String, String> filter = filters.stream().map(f->{
                String[] parts = new String[2];
                parts[0] = f.substring(0, f.indexOf('='));
                parts[1] = f.substring(f.indexOf('=') + 1);
                return parts;
            }).collect(Collectors.toMap(a->a[0], a->a[1]));
            List<JSONObject> l = Main.getBots().stream().skip(skip).limit(limit).filter(b->
                    filter.entrySet().stream()
                            .allMatch(e->{
                                Object v = getField(b, e.getKey());
                                Object expected = getExpected(e.getKey(), e.getValue());
                                return v == null || equals(v, expected);
                            })

            ).map(b->{
                JSONObject j = new JSONObject();
                select.forEach(e->j.put(e, getField(b, e)));
                return j;
            }).collect(Collectors.toList());
            JSONArray a = new JSONArray();
            l.forEach(a::put);
            responder.sendJson(HttpResponseStatus.OK, new JSONObject()
                    .put("results", a)
                    .put("updated", Main.getLastUpdated().toString())
                    .toString(4)
            );
        } catch(JSONException e) {
            responder.sendJson(HttpResponseStatus.BAD_REQUEST, new JSONObject()
                    .put("error", "malformed json: " + e.getMessage())
                    .toString(4)
            );
        } catch(Throwable t) {
            t.printStackTrace();
            responder.sendJson(HttpResponseStatus.INTERNAL_SERVER_ERROR, new JSONObject()
                    .put("error", "internal server error")
                    .toString(4)
            );
        }
    }

    //too lazy to write them to the html file directly
    private static String genFieldsHtml() {
        return Arrays.stream(BotInfo.class.getDeclaredFields())
                .map(f->"<li>" + f.getName() + ": " + f.getType().getSimpleName() + "</li>")
                .collect(Collectors.joining("\n"));
    }

    //yes i know this is inefficient but i couldn't care less
    private static Object getField(BotInfo b, String key) {
        try {
            Field f = BotInfo.class.getDeclaredField(key);
            f.setAccessible(true);
            return f.get(b);
        } catch(Exception e) {
            return null;
        }
    }

    private static Object getExpected(String key, String value) {
        switch(key) {
            case "owners":
                return Arrays.stream(value.split(",")).map(String::trim).mapToLong(Long::parseLong).toArray();
            case "shards":
                return Arrays.stream(value.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
            case "certified":
                return value.equals("true");
            default:
                return value;
        }
    }

    private static boolean equals(Object a, Object b) {
        if(a instanceof String) {
            return a.equals(b);
        }
        if(a instanceof Number) {
            return a.toString().equals(b);
        }
        if(a instanceof long[]) {
            return b instanceof long[] && Arrays.equals((long[])a, (long[])b);
        }
        if(a instanceof int[]) {
            return b instanceof int[] && Arrays.equals((int[])a, (int[])b);
        }
        return Objects.equals(a, b);
    }
}
