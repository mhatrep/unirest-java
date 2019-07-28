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

package kong.unirest;

import org.apache.hc.core5.http.HttpResponse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class RawResponseBase implements RawResponse {

    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)\\bcharset=\\s*\"?([^\\s;\"]*)");
    private HttpResponse r;
    protected Config config;

    protected RawResponseBase(org.apache.hc.core5.http.HttpResponse response, Config config){
        r = response;
        this.config = config;
    }

    @Override
    public int getStatus() {
        return r.getCode();
    }

    @Override
    public String getStatusText() {
        return r.getReasonPhrase();
    }

    @Override
    public Headers getHeaders() {
        Headers h = new Headers();
        Stream.of(r.getHeaders())
                .forEachOrdered(e -> h.add(e.getName(), e.getValue()));
        return h;
    }

    protected String getCharSet() {
        String contentType = getContentType();
        String responseCharset = getCharsetFromContentType(contentType);
        if (responseCharset != null && !responseCharset.trim().equals("")) {
            return responseCharset;
        }
        return config.getDefaultResponseEncoding();
    }

    /**
     * Parse out a charset from a content type header.
     *
     * @param contentType e.g. "text/html; charset=EUC-JP"
     * @return "EUC-JP", or null if not found. Charset is trimmed and uppercased.
     */
    private String getCharsetFromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }

        Matcher m = CHARSET_PATTERN.matcher(contentType);
        if (m.find()) {
            return m.group(1).trim().toUpperCase();
        }
        return null;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public HttpResponseSummary toSummary() {
        return new ResponseSummary(this);
    }
}
