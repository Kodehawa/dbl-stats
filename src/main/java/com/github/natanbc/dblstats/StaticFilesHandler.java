package com.github.natanbc.dblstats;

import co.cask.http.AbstractHttpHandler;
import co.cask.http.HttpResponder;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class StaticFilesHandler extends AbstractHttpHandler {
    @Path("/static/**")
    @GET
    public void serve(HttpRequest request, HttpResponder responder) throws IOException {
        File expectedParent = new File(".");
        File file = new File(expectedParent, request.uri());
        File f = file;
        do {
            if(f.equals(expectedParent)) break;
            f = f.getParentFile();
            if(f == null) {
                responder.sendStatus(HttpResponseStatus.NOT_FOUND);
                return;
            }
        } while(true);
        if(!file.exists()) {
            responder.sendStatus(HttpResponseStatus.NOT_FOUND);
            return;
        }
        responder.sendString(HttpResponseStatus.OK, new String(
                Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8
        ), new DefaultHttpHeaders(false).add("Content-Type", contentTypeFor(file)));
    }

    private static String contentTypeFor(File file) {
        String name = file.getName();
        name = name.substring(name.lastIndexOf('.') + 1);
        switch(name) {
            case "js": return "application/javascript";
            case "html": return "text/html";
            case "css": return "text/css";
            default: return "application/x-octet-stream";
        }
    }
}
