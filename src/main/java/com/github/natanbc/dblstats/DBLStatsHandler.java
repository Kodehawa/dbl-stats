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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    @Path("/bots")
    @GET
    public void bots(HttpRequest request, HttpResponder responder) {
        responder.sendJson(HttpResponseStatus.OK, Main.getData().toString(4));
    }

    @Path("/query")
    @GET
    public void query(HttpRequest request, HttpResponder responder,
                       @QueryParam("script") String script, @QueryParam("limit") long limit,
                       @QueryParam("filter") List<String> filters, @QueryParam("select") List<String> select) {
        if(limit < 1) limit = Long.MAX_VALUE;
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
                    LuaQuery.query(Main.getBots(), script).stream().limit(limit).map(b->{
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
            List<JSONObject> l = Main.getBots().stream().limit(limit).filter(b->
                    filter.entrySet().stream()
                            .allMatch(e->{
                                Object v = getField(b, e.getKey());
                                return v == null || equals(v, e.getValue());
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

    //yes i know this is innefficient but i couldn't care less
    private static Object getField(BotInfo b, String key) {
        try {
            Field f = BotInfo.class.getDeclaredField(key);
            f.setAccessible(true);
            Object o = f.get(b);
            if(o instanceof long[]) {
                JSONArray a = new JSONArray();
                for(long l : (long[])o) a.put(l);
                return a;
            }
            if(o instanceof int[]) {
                JSONArray a = new JSONArray();
                for(int i : (int[])o) a.put(i);
                return a;
            }
            return o;
        } catch(Exception e) {
            return null;
        }
    }

    private static boolean equals(Object a, String b) {
        if(a instanceof String) {
            return a.equals(b);
        }
        if(a instanceof Number) {
            return a.toString().equals(b);
        }
        return Objects.equals(a, b);
    }
}
