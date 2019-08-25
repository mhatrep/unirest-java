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

import kong.unirest.Body;
import kong.unirest.BodyPart;
import kong.unirest.HttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;

import java.util.function.Function;

public class AsyncEntityProducerFactory implements Function<HttpRequest, AsyncRequestProducer> {
    @Override
    public AsyncRequestProducer apply(HttpRequest request) {

        BasicClassicHttpRequest r = new BasicClassicHttpRequest(request.getHttpMethod().name(), request.getUrl());
        request.getHeaders().all().forEach(h -> r.addHeader(h.getName(), h.getValue()));

        if (request.getBody().isPresent()) {
            ApacheBodyMapper mapper = new ApacheBodyMapper(request);
            HttpEntity entity = mapper.apply();
            r.setEntity(entity);
        }
        return new BasicRequestProducer(r, getproducer((Body) request.getBody().get()));
    }

    private AsyncEntityProducer getproducer(Body o) {
        if(o.isEntityBody()){
            return mapToUniBody(o);
        }else {
            return null; //mapToMultipart(o);
        }
    }


    private AsyncEntityProducer mapToUniBody(Body b) {
        BodyPart bodyPart = b.uniPart();
        if(bodyPart == null){
            return new StringAsyncEntityProducer("");
        } else if(String.class.isAssignableFrom(bodyPart.getPartType())){
            return new StringAsyncEntityProducer((String) bodyPart.getValue());
        } else {
            return new BasicAsyncEntityProducer((byte[])bodyPart.getValue(), toApacheType(bodyPart.getContentType()));
        }
    }

    private org.apache.hc.core5.http.ContentType toApacheType(String type) {
        return org.apache.hc.core5.http.ContentType.parse(type);
    }
}
