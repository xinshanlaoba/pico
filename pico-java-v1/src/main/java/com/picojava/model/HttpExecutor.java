package com.picojava.model;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@FunctionalInterface
interface HttpExecutor {
    HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
}
