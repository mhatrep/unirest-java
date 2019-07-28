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
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpRequest;

import java.io.Closeable;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class ApacheClient extends BaseApacheClient implements Client {
    private final CloseableHttpClient client;
    private final Config config;
    private final SecurityConfig security;
    private final PoolingHttpClientConnectionManager manager;
    private final SyncIdleConnectionMonitorThread syncMonitor;
    private boolean hookset;

    public ApacheClient(Config config) {
        this.config = config;
        security = new SecurityConfig(config);
        manager = security.createManager();
        syncMonitor = new SyncIdleConnectionMonitorThread(manager);
        syncMonitor.start();

        HttpClientBuilder cb = HttpClients.custom()
                .setDefaultRequestConfig(RequestOptions.toRequestConfig(config))
                .setDefaultCredentialsProvider(toApacheCreds(config.getProxy()))
                .setConnectionManager(manager)
                .useSystemProperties();

        setOptions(cb);
        client = cb.build();
    }


    public ApacheClient(CloseableHttpClient httpClient, Config config){
        this.client = httpClient;
        this.config = config;
        this.security = new SecurityConfig(config);
        this.manager = null;
        this.syncMonitor = null;
    }

    @Deprecated
    public ApacheClient(CloseableHttpClient httpClient, Config config, PoolingHttpClientConnectionManager clientManager, SyncIdleConnectionMonitorThread connMonitor) {
        this.client = httpClient;
        this.security = new SecurityConfig(config);
        this.config = config;
        this.manager = clientManager;
        this.syncMonitor = connMonitor;
    }


    private void setOptions(HttpClientBuilder cb) {
        security.configureSecurity(cb);
        if (!config.isAutomaticRetries()) {
            cb.disableAutomaticRetries();
        }
        if (!config.isRequestCompressionOn()) {
            cb.disableContentCompression();
        }
        if (config.useSystemProperties()) {
            cb.useSystemProperties();
        }
        if (!config.getFollowRedirects()) {
            cb.disableRedirectHandling();
        }
        if (!config.getEnabledCookieManagement()) {
            cb.disableCookieManagement();
        }
        config.getInterceptors().stream().forEach(cb::addRequestInterceptorFirst);
        if (config.shouldAddShutdownHook()) {
            registerShutdownHook();
        }
    }

    @Override
    public void registerShutdownHook() {
        if(!hookset) {
            hookset = true;
            Runtime.getRuntime().addShutdownHook(new Thread(this::close, "Unirest Apache Client Shutdown Hook"));
        }
    }


    @Override
    public <T> HttpResponse<T> request(HttpRequest request, Function<RawResponse, HttpResponse<T>> transformer) {

        ClassicHttpRequest requestObj = new RequestPrep(request, config, false).prepare(configFactory);
        MetricContext metric = config.getMetric().begin(request.toSummary());
        try(CloseableHttpResponse execute = client.execute(requestObj)) {
            ApacheResponse t = new ApacheResponse(execute, config);
            metric.complete(t.toSummary(), null);
            HttpResponse<T> httpResponse = transformBody(transformer, t);
            return httpResponse;
        } catch (Exception e) {
            metric.complete(null, e);
            throw new UnirestException(e);
        }
    }

    @Override
    public HttpClient getClient() {
        return client;
    }

    public PoolingHttpClientConnectionManager getManager() {
        return manager;
    }

    public SyncIdleConnectionMonitorThread getSyncMonitor() {
        return syncMonitor;
    }

    @Override
    public Stream<Exception> close() {
        return Util.collectExceptions(Util.tryCast(client, CloseableHttpClient.class)
                        .map(c -> Util.tryDo(c, Closeable::close))
                        .filter(Optional::isPresent)
                        .map(Optional::get),
                Util.tryDo(manager, m -> m.close()),
                Util.tryDo(syncMonitor, i -> i.interrupt())
        );
    }

    public static Builder builder(CloseableHttpClient baseClient) {
        return new Builder(baseClient);
    }

    public static class Builder implements Function<Config, Client> {

        private CloseableHttpClient baseClient;
        private RequestConfigFactory configFactory;

        public Builder(CloseableHttpClient baseClient) {
            this.baseClient = baseClient;
        }

        @Override
        public Client apply(Config config) {
            ApacheClient apacheClient = new ApacheClient(baseClient, config);
            if(configFactory != null){
                apacheClient.setConfigFactory(configFactory);
            }
            return apacheClient;
        }

        public Builder withRequestConfig(RequestConfigFactory factory) {
            Objects.requireNonNull(factory);
            this.configFactory = factory;
            return this;
        }
    }

}
