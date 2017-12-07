package com.github.natanbc.dblstats;

import redis.clients.jedis.Jedis;
import redis.embedded.RedisServer;

public class Test {
    public static void main(String[] args) throws Throwable {
        System.setProperty("redis_port", "6380");
        RedisServer redisServer = new RedisServer(6380);
        redisServer.start();
        Jedis jedis = new Jedis("localhost", 6380);
        jedis.sadd("chart-whitelist", "213466096718708737");
        jedis.rpush("colors-213466096718708737", Integer.toString(0), Integer.toString(0x71368A));
        jedis.rpush("guilds-213466096718708737", "85000", "85500", "86500", "88900", "90000", "91500", "94000", "95000", "96000");
        jedis.close();
        System.setProperty("enable_chart", "");
        Main.main(args);
    }
}
