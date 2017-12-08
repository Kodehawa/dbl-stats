package main

import (
	"github.com/go-redis/redis"
	"fmt"
	"flag"
)

/**
This program serves a simple purpose: to be a simple replacement of this same program in java, to avoid starting the JVM.
This basically adds or removes the specified value from the chart-whitelist set on the redis database.
 */
func main() {
	//Set the flags
	ptrPort := flag.String("port", "6379", "The port to bind redis to.")
	ptrTypeof := flag.String("type", "", "What operation (add/remove).")
	ptrValue := flag.String("value", "", "What to add/remove.")
	//Parse the flags for usage.
	flag.Parse()

	//Dereference the pointer so I can actually use it without using * on every single usage
	port := *ptrPort
	typeOf := *ptrTypeof
	value := *ptrValue

	//Open a quick redis connection, no password.
	client := redis.NewClient(&redis.Options{
		Addr:     "localhost:" + port,
		Password: "",
		DB:       0,
	})

	//Adds or removes the specified value from the chart-whitelist set on the redis database.
	if typeOf == "add" {
		fmt.Printf("Added %s to chart-whitelist\n", value)
		client.SAdd("chart-whitelist", value)
	} else if typeOf == "remove" {
		fmt.Printf("Removed %s to chart-whitelist\n", value)
		client.SRem("chart-whitelist", value)
	}

	//Close the redis connection.
	client.Close()
}
