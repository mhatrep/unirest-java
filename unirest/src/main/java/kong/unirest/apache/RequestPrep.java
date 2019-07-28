/**
 * The MIT License
 *
 * Copyright for portions of unirest-java are held by Kong Inc (c) 2013.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package kong.unirest.apache;

import kong.unirest.*;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.message.BasicHeader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

class RequestPrep {
    private static final String CONTENT_TYPE = "content-type";
    private static final String ACCEPT_ENCODING_HEADER = "accept-encoding";
    private static final String USER_AGENT_HEADER = "user-agent";
    private static final String USER_AGENT = "unirest-java/3.0.00";
    private static final Map<HttpMethod, Function<String, ClassicHttpRequest>> FACTORIES;
    private final HttpRequest request;
    private Config config;
    private final boolean async;

    static {
        FACTORIES = new HashMap<>();
        FACTORIES.put(HttpMethod.GET, HttpGet::new);
        FACTORIES.put(HttpMethod.POST, HttpPost::new);
        FACTORIES.put(HttpMethod.PUT, HttpPut::new);
        FACTORIES.put(HttpMethod.DELETE, ApacheDeleteWithBody::new);
        FACTORIES.put(HttpMethod.PATCH, ApachePatchWithBody::new);
        FACTORIES.put(HttpMethod.OPTIONS, HttpOptions::new);
        FACTORIES.put(HttpMethod.HEAD, HttpHead::new);
    }

    RequestPrep(HttpRequest request, Config config, boolean async) {
        this.request = request;
        this.config = config;
        this.async = async;
    }

    ClassicHttpRequest prepare(RequestConfigFactory configFactory) {
        ClassicHttpRequest reqObj = getHttpRequestBase(configFactory);

        setBody(reqObj);

        return reqObj;
    }

    private ClassicHttpRequest getHttpRequestBase(RequestConfigFactory configFactory) {
        if (!request.getHeaders().containsKey(USER_AGENT_HEADER)) {
            request.header(USER_AGENT_HEADER, USER_AGENT);
        }
        if (!request.getHeaders().containsKey(ACCEPT_ENCODING_HEADER) && config.isRequestCompressionOn()) {
            request.header(ACCEPT_ENCODING_HEADER, "gzip");
        }

        try {
            String url = request.getUrl();
            ClassicHttpRequest reqObj = FACTORIES.computeIfAbsent(request.getHttpMethod(), this::register).apply(url);
            request.getHeaders().all().stream().map(this::toEntries).forEach(reqObj::addHeader);
            //reqObj.setConfig(configFactory.apply(config, request));
            return reqObj;
        } catch (RuntimeException e) {
            throw new UnirestException(e);
        }
    }

    private Function<String, ClassicHttpRequest> register(HttpMethod method) {
        return u -> new ApacheRequestWithBody(method, u);
    }

    private BasicHeader toEntries(kong.unirest.Header k) {
        return new BasicHeader(k.getName(), k.getValue());
    }

    private void setBody(ClassicHttpRequest reqObj) {
        if (request.getBody().isPresent()) {
            ApacheBodyMapper mapper = new ApacheBodyMapper(request);
            HttpEntity entity = mapper.apply();
            if (async) {
                if (reqObj.getHeaders(CONTENT_TYPE) == null || reqObj.getHeaders(CONTENT_TYPE).length == 0) {
                    reqObj.setHeader("Content-Type", entity.getContentType());
                }
                try {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    entity.writeTo(output);
//                    NByteArrayEntity en = new NByteArrayEntity(output.toByteArray());
//                    ((HttpEntityEnclosingRequestBase) reqObj).setEntity(en);
                } catch (IOException e) {
                    throw new UnirestException(e);
                }
            } else {
               // ((HttpEntityEnclosingRequestBase) reqObj).setEntity(entity);
            }
        }
    }

    public SimpleHttpRequest prepareSimple(RequestConfigFactory configFactory) {
        SimpleHttpRequest r = new SimpleHttpRequest(request.getHttpMethod().name(), request.getUrl());
        request.getHeaders().all().stream().map(this::toEntries).forEach(r::addHeader);
        return r;
    }
}
