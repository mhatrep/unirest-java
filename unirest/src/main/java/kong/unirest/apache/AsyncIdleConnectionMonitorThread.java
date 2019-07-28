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

import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;

import java.util.concurrent.TimeUnit;


public class AsyncIdleConnectionMonitorThread extends Thread {

	private final PoolingAsyncClientConnectionManager connMgr;

	public AsyncIdleConnectionMonitorThread(PoolingAsyncClientConnectionManager connMgr) {
		super();
		super.setDaemon(true);
		this.connMgr = connMgr;
	}

	@Override
	public void run() {
		try {
			while (!Thread.currentThread().isInterrupted()) {
				synchronized (this) {
					wait(5000);
					// Close expired connections
					connMgr.closeExpired();
					// Optionally, close connections
					// that have been idle longer than 30 sec
					connMgr.closeIdle(TimeValue.of(30, TimeUnit.SECONDS));
				}
			}
		} catch (InterruptedException ex) {
			// terminate
		}
	}

	public synchronized void tryStart() {
		if(!super.isAlive()){
			super.start();
		}
	}

}
