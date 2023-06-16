package org.voltdb.voltutil.stats;

/* This file is part of VoltDB.
 * Copyright (C) 2008-2023 VoltDB Inc.
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
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import java.util.HashMap;

/**
 * Singleton cache for histograms. Note that because this is a singleton there
 * are practical scaling limits.
 */
public class SafeHistogramCache {

    private static SafeHistogramCache instance = null;

    HashMap<String, LatencyHistogram> theHistogramMap = new HashMap<>();
    HashMap<String, Long> theCounterMap = new HashMap<>();
    HashMap<String, SizeHistogram> theSizeHistogramMap = new HashMap<>();

    final int DEFAULT_SIZE = 100;

    long lastStatsTime = System.currentTimeMillis();

    protected SafeHistogramCache() {
        // Exists only to defeat instantiation.
    }

    /**
     * @return The Cache instance.
     */
    public static SafeHistogramCache getInstance() {
        if (instance == null) {
            instance = new SafeHistogramCache();
        }
        return instance;
    }

    /**
     * Clear everything.
     */
    public void reset() {
        synchronized (theHistogramMap) {
            synchronized (theCounterMap) {
                synchronized (theSizeHistogramMap) {
                    theHistogramMap = new HashMap<>();
                    theCounterMap = new HashMap<>();
                    theSizeHistogramMap = new HashMap<>();

                }
            }
        }
    }

    /**
     * Return a histogram, creating it if needed.
     * 
     * @param type
     * @return a LatencyHistogram
     */
    public LatencyHistogram get(String type) {
        LatencyHistogram h = null;

        synchronized (theHistogramMap) {
            h = theHistogramMap.get(type);

            if (h == null) {
                h = new LatencyHistogram(DEFAULT_SIZE);
                theHistogramMap.put(type, h);
            }
        }

        return h;
    }

    /**
     * Recreate a histogram, keeping size the same.
     * 
     * @param type
     */
    public void clear(String type) {

        LatencyHistogram oldH = null;
        LatencyHistogram newH = null;

        synchronized (theHistogramMap) {
            oldH = theHistogramMap.remove(type);

            if (oldH == null) {
                newH = new LatencyHistogram(100);
            } else {
                newH = new LatencyHistogram(oldH.maxSize);
            }
            theHistogramMap.put(type, newH);

        }

    }

    /**
     * @param type
     * @return SizeHistogram
     */
    public SizeHistogram getSize(String type) {
        SizeHistogram h = null;

        synchronized (theSizeHistogramMap) {
            h = theSizeHistogramMap.get(type);

            if (h == null) {
                h = new SizeHistogram(type, DEFAULT_SIZE);
                theSizeHistogramMap.put(type, h);
            }
        }

        return h;
    }

    /**
     * Return a counter value
     * 
     * @param type
     * @return
     */
    public long getCounter(String type) {
        Long l = new Long(0);

        synchronized (theCounterMap) {
            l = theCounterMap.get(type);
            if (l == null) {
                return 0;
            }
        }

        return l.longValue();
    }

    /**
     * Set a counter value.
     *
     * @param type
     * @param value
     */
    public void setCounter(String type, long value) {

        synchronized (theCounterMap) {
            Long l = theCounterMap.get(type);
            if (l == null) {
                l = new Long(value);

            }
            theCounterMap.put(type, l);
        }

    }

    /**
     * Increment a counter in a Thread Safe way.
     * 
     * @param type
     */
    public void incCounter(String type) {

        synchronized (theCounterMap) {
            Long l = theCounterMap.get(type);
            if (l == null) {
                l = new Long(0);
            }
            theCounterMap.put(type, l.longValue() + 1);
        }

    }

    /**
     * Report a value, usually latency.
     * 
     * @param type
     * @param value
     * @param comment
     * @param defaultSize
     */
    public void report(String type, int value, String comment, int defaultSize) {

        synchronized (theHistogramMap) {
            LatencyHistogram h = theHistogramMap.get(type);
            if (h == null) {
                h = new LatencyHistogram(type, defaultSize);
                theHistogramMap.put(type, h);
            }
            h.report(value, comment);

        }

    }

    /**
     * Report a size
     * 
     * @param type
     * @param size
     * @param comment
     * @param defaultSize
     */
    public void reportSize(String type, int size, String comment, int defaultSize) {

        synchronized (theSizeHistogramMap) {
            SizeHistogram h = theSizeHistogramMap.get(type);
            if (h == null) {
                h = new SizeHistogram(type, defaultSize);
                theSizeHistogramMap.put(type, h);
            }

            h.inc(size, comment);

        }

    }

    /**
     * Report a latency, relative to now.
     * 
     * @param type
     * @param start
     * @param comment
     * @param defaultSize
     */
    public void reportLatency(String type, long start, String comment, int defaultSize) {

        synchronized (theHistogramMap) {
            LatencyHistogram h = theHistogramMap.get(type);
            if (h == null) {
                h = new LatencyHistogram(type, defaultSize);

            }
            h.reportLatency(start, comment);
            theHistogramMap.put(type, h);
        }

    }

    /**
     * Create a new Histogram by subtracting two existing ones. Note that while the
     * new Histogram exists in the cache, it will not be updated by changes to its
     * parents.
     * 
     * @param bigHist
     * @param smallHist
     * @param name
     * @return
     */
    public LatencyHistogram subtractTimes(String bigHist, String smallHist, String name) {

        LatencyHistogram hBig;
        LatencyHistogram hSmall;
        synchronized (theHistogramMap) {
            hBig = theHistogramMap.get(bigHist);
            hSmall = theHistogramMap.get(smallHist);
        }
        LatencyHistogram delta = LatencyHistogram.subtract(name, hBig, hSmall);

        synchronized (theHistogramMap) {
            theHistogramMap.put(name, delta);
        }

        return delta;

    }

    /**
     * @return true if we have stats to report...
     */
    public boolean hasStats() {

        synchronized (theHistogramMap) {
            synchronized (theCounterMap) {
                synchronized (theSizeHistogramMap) {

                    if (!theHistogramMap.isEmpty() || !theCounterMap.isEmpty() || !theSizeHistogramMap.isEmpty()) {
                        return true;
                    }

                }
            }
        }
        return false;

    }

    @Override
    public String toString() {
        String data = "";
        synchronized (theHistogramMap) {
            synchronized (theCounterMap) {
                synchronized (theSizeHistogramMap) {

                    data = theHistogramMap.toString() + System.lineSeparator() + theCounterMap.toString()
                            + System.lineSeparator() + theSizeHistogramMap.toString();
                }
            }
        }

        return data;
    }

    /**
     * Return stats every statsInterval ms, otherwise an empty string.
     * 
     * @param statsInterval
     * @return
     */
    public String toStringIfOlderThanMs(int statsInterval) {

        String data = "";

        if (lastStatsTime + statsInterval < System.currentTimeMillis()) {
            synchronized (theHistogramMap) {

                data = theHistogramMap.toString();
                lastStatsTime = System.currentTimeMillis();
            }

        }
        return data;
    }

    /**
     * Create a new Size histogram.
     * 
     * @param name
     * @param batchSize
     * @param description
     */
    public void initSize(String name, int batchSize, String description) {

        synchronized (theSizeHistogramMap) {
            SizeHistogram h = theSizeHistogramMap.get(name);
            if (h == null) {

                h = new SizeHistogram(name, batchSize);
                h.setDescription(description);
                theSizeHistogramMap.put(name, h);

            }

        }

    }

    /**
     * Create a new Latency Histogram.
     * 
     * @param name
     * @param batchSize
     * @param description
     */
    public void init(String name, int batchSize, String description) {

        synchronized (theHistogramMap) {
            LatencyHistogram h = theHistogramMap.get(name);
            if (h == null) {
                h = new LatencyHistogram(name, batchSize);
                h.setDescription(description);
                theHistogramMap.put(name, h);
            }

        }

    }

    /**
     * Get import stats as a string
     * 
     * @param shc            Histogram Cache
     * @param oneLineSummary StringBuffer we append to - note this is a void
     *                       method....
     * @param thingName      Thing we're tracking latency for...
     */
    public static void getProcPercentiles(SafeHistogramCache shc, StringBuffer oneLineSummary, String thingName) {

        LatencyHistogram rqu = shc.get(thingName);
        oneLineSummary.append((int) rqu.getLatencyAverage());
        oneLineSummary.append(':');

        oneLineSummary.append(rqu.getLatencyPct(50));
        oneLineSummary.append(':');

        oneLineSummary.append(rqu.getLatencyPct(99));
        oneLineSummary.append(':');

        oneLineSummary.append(rqu.getLatencyPct(99.9));
        oneLineSummary.append(':');

        oneLineSummary.append(rqu.getLatencyPct(99.99));
        oneLineSummary.append(':');

        oneLineSummary.append(rqu.getLatencyPct(99.999));
        oneLineSummary.append(':');

        oneLineSummary.append(rqu.getMaxUsedSize());
        oneLineSummary.append(':');

        oneLineSummary.append(rqu.getLatencyHistogram()[rqu.getMaxUsedSize()]);
        oneLineSummary.append(':');

    }

}