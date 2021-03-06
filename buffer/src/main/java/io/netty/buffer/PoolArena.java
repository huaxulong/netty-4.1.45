/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;

import io.netty.util.internal.LongCounter;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.lang.Math.max;

abstract class PoolArena<T> implements PoolArenaMetric {
    static final boolean HAS_UNSAFE = PlatformDependent.hasUnsafe();

    enum SizeClass {
        Tiny,
        Small,
        Normal
    }

    static final int numTinySubpagePools = 512 >>> 4;

    final PooledByteBufAllocator parent;

    private final int maxOrder;
    final int pageSize;
    final int pageShifts;
    final int chunkSize;
    final int subpageOverflowMask;
    final int numSmallSubpagePools;
    final int directMemoryCacheAlignment;
    final int directMemoryCacheAlignmentMask;
    private final PoolSubpage<T>[] tinySubpagePools;
    private final PoolSubpage<T>[] smallSubpagePools;

    private final PoolChunkList<T> q050;
    private final PoolChunkList<T> q025;
    private final PoolChunkList<T> q000;
    private final PoolChunkList<T> qInit;
    private final PoolChunkList<T> q075;
    private final PoolChunkList<T> q100;

    private final List<PoolChunkListMetric> chunkListMetrics;

    // Metrics for allocations and deallocations
    private long allocationsNormal;
    // We need to use the LongCounter here as this is not guarded via synchronized block.
    private final LongCounter allocationsTiny = PlatformDependent.newLongCounter();
    private final LongCounter allocationsSmall = PlatformDependent.newLongCounter();
    private final LongCounter allocationsHuge = PlatformDependent.newLongCounter();
    private final LongCounter activeBytesHuge = PlatformDependent.newLongCounter();

    private long deallocationsTiny;
    private long deallocationsSmall;
    private long deallocationsNormal;

    // We need to use the LongCounter here as this is not guarded via synchronized block.
    private final LongCounter deallocationsHuge = PlatformDependent.newLongCounter();

    // Number of thread caches backed by this arena.
    final AtomicInteger numThreadCaches = new AtomicInteger();

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    // ??????1???allocator ??????
    // ??????2???pageSize,8k
    // ??????3???maxOrder???11
    // ??????4???pageShifts,13 ???1 << 13 => pageSize
    // ??????5???chunkSize,16mb
    // ??????6???directMemoryCacheAlignment 0
    protected PoolArena(PooledByteBufAllocator parent, int pageSize,
          int maxOrder, int pageShifts, int chunkSize, int cacheAlignment) {
        // ???????????? arena ????????? ?????????arena????????? alloctor ?????????
        this.parent = parent;
        // 8k
        this.pageSize = pageSize;
        // 11
        this.maxOrder = maxOrder;
        // 13
        this.pageShifts = pageShifts;
        // 16mb
        this.chunkSize = chunkSize;
        // 0
        directMemoryCacheAlignment = cacheAlignment;
        directMemoryCacheAlignmentMask = cacheAlignment - 1;

        // 8k - 1 => 0b 1111111111111
        // ~(0b 1111111111111)
        //  (0b 1111 1111 1111 1111 1110 0000 0000 0000)
        // ??????????????????????????? ?????????????????? 1 page??? ?????? subpageOverflowMask ???????????????????????????????????? != 0 ?????????
        subpageOverflowMask = ~(pageSize - 1);

        // ??????tiny???32???????????????16b,32b,48b...496b  ???????????????????????????????????? 32 ??? PoolSubpage ???????????? ????????? tiny ????????? Subpage ???
        // ???arena ?????????????????????
        tinySubpagePools = newSubpagePoolArray(numTinySubpagePools);

        // ????????????????????????????????? ?????? PoolSubpage ????????????????????? head???head ???????????? prev next ??????????????????
        for (int i = 0; i < tinySubpagePools.length; i ++) {
            tinySubpagePools[i] = newSubpagePoolHead(pageSize);
        }

        // 13 - 9 => 4
        numSmallSubpagePools = pageShifts - 9;
        // ??????small???4????????????:512b,1024b,2048b,4096b
        smallSubpagePools = newSubpagePoolArray(numSmallSubpagePools);

        // ????????????????????????????????? ?????? PoolSubpage ????????????????????? head???head ???????????? prev next ??????????????????
        for (int i = 0; i < smallSubpagePools.length; i ++) {
            smallSubpagePools[i] = newSubpagePoolHead(pageSize);
        }


        q100 = new PoolChunkList<T>(this, null, 100, Integer.MAX_VALUE, chunkSize);
        q075 = new PoolChunkList<T>(this, q100, 75, 100, chunkSize);
        q050 = new PoolChunkList<T>(this, q075, 50, 100, chunkSize);
        q025 = new PoolChunkList<T>(this, q050, 25, 75, chunkSize);
        q000 = new PoolChunkList<T>(this, q025, 1, 50, chunkSize);
        qInit = new PoolChunkList<T>(this, q000, Integer.MIN_VALUE, 25, chunkSize);

        q100.prevList(q075);
        q075.prevList(q050);
        q050.prevList(q025);
        q025.prevList(q000);
        q000.prevList(null);
        qInit.prevList(qInit);

        List<PoolChunkListMetric> metrics = new ArrayList<PoolChunkListMetric>(6);
        metrics.add(qInit);
        metrics.add(q000);
        metrics.add(q025);
        metrics.add(q050);
        metrics.add(q075);
        metrics.add(q100);
        chunkListMetrics = Collections.unmodifiableList(metrics);
    }

    private PoolSubpage<T> newSubpagePoolHead(int pageSize) {
        PoolSubpage<T> head = new PoolSubpage<T>(pageSize);
        head.prev = head;
        head.next = head;
        return head;
    }

    @SuppressWarnings("unchecked")
    private PoolSubpage<T>[] newSubpagePoolArray(int size) {
        return new PoolSubpage[size];
    }

    abstract boolean isDirect();

    // ??????1??? cache ?????????????????????PoolThreadCache??????
    // ??????2???reqCapacity?????????????????????????????????
    // ??????3???maxCapacity ??????????????????
    PooledByteBuf<T> allocate(PoolThreadCache cache, int reqCapacity, int maxCapacity) {
        // ????????????ByteBuf???????????????????????? ???ByteBuf ??????????????????????????? ????????? ?????? ??????...
        PooledByteBuf<T> buf = newByteBuf(maxCapacity);

        // ??????1???cache ?????????????????????PoolThreadCache??????
        // ??????2???buf????????????????????????ByteBuf??????????????????????????????buf ????????????????????????
        // ??????3???reqCapacity?????????????????????????????????
        allocate(cache, buf, reqCapacity);

        return buf;
    }

    static int tinyIdx(int normCapacity) {
        // 48 => 0b 110000
        // 0b 110000  >>> 4 => 0b 11 => 3
        return normCapacity >>> 4;
    }

    static int smallIdx(int normCapacity) {
        int tableIdx = 0;
        // 2048 => 0b 1000 0000 0000
        // 0b 10 => 2
        int i = normCapacity >>> 10;

        while (i != 0) {
            i >>>= 1;
            tableIdx ++;
        }
        return tableIdx;
    }

    // capacity < pageSize
    boolean isTinyOrSmall(int normCapacity) {
        return (normCapacity & subpageOverflowMask) == 0;
    }

    // normCapacity < 512 ??????false
    static boolean isTiny(int normCapacity) {
        // 0xFFFFFE00 => 0b 1111111111111111111 11110 0000 0000    ???????????????
        // ????????????512???????????????????????? ?????????????????? ?????????????????? ???0 ??????
        return (normCapacity & 0xFFFFFE00) == 0;
    }


    // ??????1???cache ?????????????????????PoolThreadCache??????
    // ??????2???buf????????????????????????ByteBuf??????????????????????????????buf ????????????????????????
    // ??????3???reqCapacity?????????????????????????????????
    private void allocate(PoolThreadCache cache, PooledByteBuf<T> buf, final int reqCapacity) {


        // ???????????????req????????? netty ????????? ?????????size
        final int normCapacity = normalizeCapacity(reqCapacity);

        // CASE1????????? ???????????? ??????Size ??? tiny ?????? small ?????????
        if (isTinyOrSmall(normCapacity)) { // capacity < pageSize
            // arena ?????????????????? tinySubpagePools ??? smallSubpagePools??? ?????? normCapacity ?????????????????? ???????????????
            int tableIdx;
            // table?????????????????????tinySubpagePools????????? smallSubpagePools ??????????????????
            PoolSubpage<T>[] table;
            // ??????????????????size ????????? tiny ?????????
            boolean tiny = isTiny(normCapacity);
            if (tiny) { // < 512
                // ???????????? ?????????????????????????????????????????????????????????????????? ???????????????????????????????????????
                if (cache.allocateTiny(this, buf, reqCapacity, normCapacity)) {
                    // was able to allocate out of the cache so move on
                    return;
                }

                // ?????? normCapacity ??? 48 .
                tableIdx = tinyIdx(normCapacity);
                // table?????????tinySubpagePools ??????
                table = tinySubpagePools;
            } else {
                // ????????????????????? ?????? normCapacity ????????? small ?????????

                // ???????????? ?????????????????????????????????????????????????????????????????? ???????????????????????????????????????
                if (cache.allocateSmall(this, buf, reqCapacity, normCapacity)) {
                    // was able to allocate out of the cache so move on
                    return;
                }

                // ?????? normCapacity ??? 2048  ??????????????? 2
                tableIdx = smallIdx(normCapacity);
                // table ?????? smallSubpagePools
                table = smallSubpagePools;
            }

            // ???????????? tiny ?????? small ????????? tableIdx ??? table ?????? ?????????????????????..

            // ??????????????????????????????????????? head ?????????
            final PoolSubpage<T> head = table[tableIdx];

            /**
             * Synchronize on the head. This is needed as {@link PoolChunk#allocateSubpage(int)} and
             * {@link PoolChunk#free(long)} may modify the doubly linked list as well.
             */
            // head ???????????? ???
            synchronized (head) {

                // ????????????  ??????????????? head ?????? prev ??? next ?????????????????????????????????
                // ?????? arena ??? ????????? ?????? ????????? subpage ??????????????? ??????????????? ?????? page???
                final PoolSubpage<T> s = head.next;

                // ????????????????????? ???????????? ?????? ??? subpage.  ???????????????????????????
                if (s != head) {
                    assert s.doNotDestroy && s.elemSize == normCapacity;
                    // ?????????...
                    long handle = s.allocate();
                    assert handle >= 0;
                    s.chunk.initBufWithSubpage(buf, null, handle, reqCapacity);
                    incTinySmallAllocation(tiny);
                    return;
                }
            }


            synchronized (this) {
                // ??????????????????.. arena???subpage  ??? ?????? cache ????????? ???????????????????????????????????? allocateNormal ?????????
                // ??????1???buf,?????????????????????ByteBuf??????????????????????????????buf ????????????????????????
                // ??????2???reqCapacity?????????????????????????????????
                // ??????3??????req ??????????????????size???
                allocateNormal(buf, reqCapacity, normCapacity);
            }

            incTinySmallAllocation(tiny);
            return;
        }

        // CASE2?????????????????? ??????size ?????? normal ?????? 8k  16k  32k .... <= chunkSize
        if (normCapacity <= chunkSize) {
            if (cache.allocateNormal(this, buf, reqCapacity, normCapacity)) {
                // was able to allocate out of the cache so move on
                return;
            }
            synchronized (this) {
                allocateNormal(buf, reqCapacity, normCapacity);
                ++allocationsNormal;
            }
        }

        // CASE3??? ????????????????????? chunkSize??? ??????????????????????????????????????? 16mb???
        else {
            // Huge allocations are never served via the cache so just call allocateHuge
            allocateHuge(buf, reqCapacity);
        }
    }

    // Method must be called inside synchronized(this) { ... }??block

    // ??????1???buf,?????????????????????ByteBuf??????????????????????????????buf ????????????????????????
    // ??????2???reqCapacity?????????????????????????????????
    // ??????3??????req ??????????????????size???
    private void allocateNormal(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        // ?????????????????? PoolChunkList ??????????????????... ????????? ???????????? PoolChunkList????????????????????????????????????????????????
        if (q050.allocate(buf, reqCapacity, normCapacity) || q025.allocate(buf, reqCapacity, normCapacity) ||
            q000.allocate(buf, reqCapacity, normCapacity) || qInit.allocate(buf, reqCapacity, normCapacity) ||
            q075.allocate(buf, reqCapacity, normCapacity)) {
            return;
        }
        // ??????????????????????????? PCL PoolChunkList ???????????????????????????????????????????????????chunk ?????????chunk??????????????????


        // Add a new chunk.
        // ??????1???pageSize 8k
        // ??????2???maxOrder 11
        // ??????3???pageShifts 13
        // ??????4???chunkSize 16mb
        PoolChunk<T> c = newChunk(pageSize, maxOrder, pageShifts, chunkSize);

        // ??????1???buf ?????????????????????ByteBuf??????????????????????????????buf ????????????????????????
        // ??????2???reqCapacity?????????????????????????????????
        // ??????3??????req ??????????????????size???
        boolean success = c.allocate(buf, reqCapacity, normCapacity);

        assert success;
        qInit.add(c);
    }

    private void incTinySmallAllocation(boolean tiny) {
        if (tiny) {
            allocationsTiny.increment();
        } else {
            allocationsSmall.increment();
        }
    }

    private void allocateHuge(PooledByteBuf<T> buf, int reqCapacity) {
        PoolChunk<T> chunk = newUnpooledChunk(reqCapacity);
        activeBytesHuge.add(chunk.chunkSize());
        buf.initUnpooled(chunk, reqCapacity);
        allocationsHuge.increment();
    }

    // ??????1???chunk byteBuf ??????????????? ??????chunk
    // ??????2???tmpNioBuf ?????????.. ?????????null
    // ??????3???handle  ???????????????  ?????? ?????? ??????????????? handle
    // ??????4???maxLength  byteBuf ????????????????????? ??????
    // ??????5???cache ??????byteBuf????????? ????????????????????????  ?????? ??? ?????????????????? ????????? ???????????????
    void free(PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, int normCapacity, PoolThreadCache cache) {
        if (chunk.unpooled) {
            int size = chunk.chunkSize();
            destroyChunk(chunk);
            activeBytesHuge.add(-size);
            deallocationsHuge.increment();
        } else {
            // ????????????


            // ????????? ????????????????????????
            SizeClass sizeClass = sizeClass(normCapacity);

            // ????????? ????????? ?????????????????? ????????? ?????? poolThreadCache ???????????????
            // ??????1???this ?????????arena??????
            // ??????2???chunk????????? byteBuf ???????????? ??????chunk
            // ??????3???nioBuffer ????????????null.,.
            // ??????4???handle   ???????????????  ?????? ?????? ??????????????? handle
            // ??????5???normCapacity  ????????????
            // ??????6???sizeClass   tiny small normal
            // cache.add(this, chunk, nioBuffer, handle, normCapacity, sizeClass)

            if (cache != null && cache.add(this, chunk, nioBuffer, handle, normCapacity, sizeClass)) {
                // cached so not free it.
                // ????????????  ??????????????????
                return;
            }

            // ???????????????????????? ????????? ???????????? ??????.. ????????? ?????? ???????????????

            // ??????1????????? byteBuf ???????????? ??????chunk
            // ??????2???handle   ???????????????  ?????? ?????? ??????????????? handle
            // ??????3???????????????????????????  tiny small normal
            // ??????4???nioBuffer  ?????????null.,.
            // ??????5???false??????
            freeChunk(chunk, handle, sizeClass, nioBuffer, false);

        }
    }

    private SizeClass sizeClass(int normCapacity) {
        if (!isTinyOrSmall(normCapacity)) {
            return SizeClass.Normal;
        }
        return isTiny(normCapacity) ? SizeClass.Tiny : SizeClass.Small;
    }

    // ??????1????????? byteBuf ???????????? ??????chunk
    // ??????2???handle   ???????????????  ?????? ?????? ??????????????? handle
    // ??????3???????????????????????????  tiny small normal
    // ??????4???nioBuffer  ?????????null.,.
    // ??????5???false??????
    void freeChunk(PoolChunk<T> chunk, long handle, SizeClass sizeClass, ByteBuffer nioBuffer, boolean finalizer) {
        final boolean destroyChunk;
        synchronized (this) {
            // We only call this if freeChunk is not called because of the PoolThreadCache finalizer as otherwise this
            // may fail due lazy class-loading in for example tomcat.
            if (!finalizer) {
                switch (sizeClass) {
                    case Normal:
                        ++deallocationsNormal;
                        break;
                    case Small:
                        ++deallocationsSmall;
                        break;
                    case Tiny:
                        ++deallocationsTiny;
                        break;
                    default:
                        throw new Error();
                }
            }



            destroyChunk = !chunk.parent.free(chunk, handle, nioBuffer);
        }
        if (destroyChunk) {
            // destroyChunk not need to be called while holding the synchronized lock.
            destroyChunk(chunk);
        }
    }

    // ??????1???????????????elemSize
    PoolSubpage<T> findSubpagePoolHead(int elemSize) {
        // ??????pools???????????? ????????????elemSize??????
        int tableIdx;

        // table ?????? tinySubpagePools ?????? smallSubpagePools ????????????
        PoolSubpage<T>[] table;

        if (isTiny(elemSize)) { // < 512
            // ????????? tiny elemSize ?????????????????????
            tableIdx = elemSize >>> 4;
            table = tinySubpagePools;

        } else { // elemSize > 512 ?????? elemSize < 1page

            tableIdx = 0;

            // ?????????????????? elemSize = 1024 ???
            // 1024 => 0b 0100 0000 0000
            // 1024 >>>= 10 => 0b 1
            elemSize >>>= 10;

            while (elemSize != 0) {
                elemSize >>>= 1;
                tableIdx ++;
            }
            table = smallSubpagePools;
            // ???????????????
            // ????????? 512b ?????????????????? 0
            // ????????? 1024b ?????????????????? 1
            // ????????? 2048b ?????????????????? 2
            // ????????? 4096b ?????????????????? 3
        }

        // ?????????????????? head?????????head ??????????????? ????????? ??????????????? subpage ?????????
        return table[tableIdx];
    }

    // ?????????reqCapacity ???????????????????????????????????????????????????
    // return ???????????????????????????size
    int normalizeCapacity(int reqCapacity) {
        checkPositiveOrZero(reqCapacity, "reqCapacity");

        // ???????????????????????????????????????????????? buffer??? ??????..
        if (reqCapacity >= chunkSize) {
            return directMemoryCacheAlignment == 0 ? reqCapacity : alignCapacity(reqCapacity);
        }

        // ?????????????????????reqCapacity ??????????????? 512 ????????????????????????  tiny ???????????????
        if (!isTiny(reqCapacity)) { // >= 512
            // small???normal ??????????????????????????????

            // Doubled
            // ?????? reqCapacity ?????? 555
            int normalizedCapacity = reqCapacity;
            // 0b 0000 0000 0000 0000 0000 0010 0010 1010
            normalizedCapacity --;
            // 0b 0000 0000 0000 0000 0000 0010 0010 1010
            // 0b 0000 0000 0000 0000 0000 0001 0001 0101
            // 0b 0000 0000 0000 0000 0000 0011 0011 1111
            normalizedCapacity |= normalizedCapacity >>>  1;
            // 0b 0000 0000 0000 0000 0000 0011 0011 1111
            // 0b 0000 0000 0000 0000 0000 0000 1100 1111
            // 0b 0000 0000 0000 0000 0000 0011 1111 1111
            normalizedCapacity |= normalizedCapacity >>>  2;
            // 0b 0000 0000 0000 0000 0000 0011 1111 1111
            // 0b 0000 0000 0000 0000 0000 0000 0011 1111
            // 0b 0000 0000 0000 0000 0000 0011 1111 1111
            normalizedCapacity |= normalizedCapacity >>>  4;
            // 0b 0000 0000 0000 0000 0000 0011 1111 1111
            // 0b 0000 0000 0000 0000 0000 0000 0000 0011
            // 0b 0000 0000 0000 0000 0000 0011 1111 1111
            normalizedCapacity |= normalizedCapacity >>>  8;
            // 0b 0000 0000 0000 0000 0000 0011 1111 1111
            // 0b 0000 0000 0000 0000 0000 0000 0000 0000
            // 0b 0000 0000 0000 0000 0000 0011 1111 1111   =>  ????????? 1023
            normalizedCapacity |= normalizedCapacity >>> 16;

            // 1024
            normalizedCapacity ++;

            if (normalizedCapacity < 0) {
                normalizedCapacity >>>= 1;
            }

            assert directMemoryCacheAlignment == 0 || (normalizedCapacity & directMemoryCacheAlignmentMask) == 0;
            // ???????????? ??????????????? ???????????? >= ??????reqCapacity ?????????2????????????
            // ????????? req = 512 ?????? 512
            //       req = 513 ?????? 1024
            //       req = 1025 ?????? 2048 ....
            return normalizedCapacity;
        }

        // ???????????????????????? reqCapacity < 512 ???reqCapacity ????????? tiny
        // ?????????...
        if (directMemoryCacheAlignment > 0) {
            return alignCapacity(reqCapacity);
        }

        // Quantum-spaced
        // ????????????req ????????? 16 32 48 64... ??????
        // ?????? req = 32 => 0b 0010 0000
        // 0b 0010 0000
        // 0b 0000 1111 => 0
        //

        // ?????? req = 19 => 0b 0001 0011
        // 0b 0001 0011
        // 0b 0000 1111 => 3
        if ((reqCapacity & 15) == 0) {
            return reqCapacity;
        }

        // ???????????????????????? req ?????? ?????? 16 32 48 ... ???????????? ???????????? 17  33 52...
        // ??????????????????????????? > req  ????????? ????????????
        // ?????????req = 17 => 0b 0001 0001
        // 15 => 0b 1111
        // ~ 15 => 0b 1111 1111 1111 1111 1111 1111 1111 0000
        // 0b 0000 0000 0000 0000 0000 0000 0001 0001
        // 0b 1111 1111 1111 1111 1111 1111 1111 0000
        // 0b 0000 0000 0000 0000 0000 0000 0001 0000  => 16
        // 16 + 16 => 32

        return (reqCapacity & ~15) + 16;
    }

    int alignCapacity(int reqCapacity) {
        int delta = reqCapacity & directMemoryCacheAlignmentMask;
        return delta == 0 ? reqCapacity : reqCapacity + directMemoryCacheAlignment - delta;
    }

    void reallocate(PooledByteBuf<T> buf, int newCapacity, boolean freeOldMemory) {
        assert newCapacity >= 0 && newCapacity <= buf.maxCapacity();

        int oldCapacity = buf.length;
        if (oldCapacity == newCapacity) {
            return;
        }

        PoolChunk<T> oldChunk = buf.chunk;
        ByteBuffer oldNioBuffer = buf.tmpNioBuf;
        long oldHandle = buf.handle;
        T oldMemory = buf.memory;
        int oldOffset = buf.offset;
        int oldMaxLength = buf.maxLength;

        // This does not touch buf's reader/writer indices
        allocate(parent.threadCache(), buf, newCapacity);
        int bytesToCopy;
        if (newCapacity > oldCapacity) {
            bytesToCopy = oldCapacity;
        } else {
            buf.trimIndicesToCapacity(newCapacity);
            bytesToCopy = newCapacity;
        }
        memoryCopy(oldMemory, oldOffset, buf, bytesToCopy);
        if (freeOldMemory) {
            free(oldChunk, oldNioBuffer, oldHandle, oldMaxLength, buf.cache);
        }
    }

    @Override
    public int numThreadCaches() {
        return numThreadCaches.get();
    }

    @Override
    public int numTinySubpages() {
        return tinySubpagePools.length;
    }

    @Override
    public int numSmallSubpages() {
        return smallSubpagePools.length;
    }

    @Override
    public int numChunkLists() {
        return chunkListMetrics.size();
    }

    @Override
    public List<PoolSubpageMetric> tinySubpages() {
        return subPageMetricList(tinySubpagePools);
    }

    @Override
    public List<PoolSubpageMetric> smallSubpages() {
        return subPageMetricList(smallSubpagePools);
    }

    @Override
    public List<PoolChunkListMetric> chunkLists() {
        return chunkListMetrics;
    }

    private static List<PoolSubpageMetric> subPageMetricList(PoolSubpage<?>[] pages) {
        List<PoolSubpageMetric> metrics = new ArrayList<PoolSubpageMetric>();
        for (PoolSubpage<?> head : pages) {
            if (head.next == head) {
                continue;
            }
            PoolSubpage<?> s = head.next;
            for (;;) {
                metrics.add(s);
                s = s.next;
                if (s == head) {
                    break;
                }
            }
        }
        return metrics;
    }

    @Override
    public long numAllocations() {
        final long allocsNormal;
        synchronized (this) {
            allocsNormal = allocationsNormal;
        }
        return allocationsTiny.value() + allocationsSmall.value() + allocsNormal + allocationsHuge.value();
    }

    @Override
    public long numTinyAllocations() {
        return allocationsTiny.value();
    }

    @Override
    public long numSmallAllocations() {
        return allocationsSmall.value();
    }

    @Override
    public synchronized long numNormalAllocations() {
        return allocationsNormal;
    }

    @Override
    public long numDeallocations() {
        final long deallocs;
        synchronized (this) {
            deallocs = deallocationsTiny + deallocationsSmall + deallocationsNormal;
        }
        return deallocs + deallocationsHuge.value();
    }

    @Override
    public synchronized long numTinyDeallocations() {
        return deallocationsTiny;
    }

    @Override
    public synchronized long numSmallDeallocations() {
        return deallocationsSmall;
    }

    @Override
    public synchronized long numNormalDeallocations() {
        return deallocationsNormal;
    }

    @Override
    public long numHugeAllocations() {
        return allocationsHuge.value();
    }

    @Override
    public long numHugeDeallocations() {
        return deallocationsHuge.value();
    }

    @Override
    public  long numActiveAllocations() {
        long val = allocationsTiny.value() + allocationsSmall.value() + allocationsHuge.value()
                - deallocationsHuge.value();
        synchronized (this) {
            val += allocationsNormal - (deallocationsTiny + deallocationsSmall + deallocationsNormal);
        }
        return max(val, 0);
    }

    @Override
    public long numActiveTinyAllocations() {
        return max(numTinyAllocations() - numTinyDeallocations(), 0);
    }

    @Override
    public long numActiveSmallAllocations() {
        return max(numSmallAllocations() - numSmallDeallocations(), 0);
    }

    @Override
    public long numActiveNormalAllocations() {
        final long val;
        synchronized (this) {
            val = allocationsNormal - deallocationsNormal;
        }
        return max(val, 0);
    }

    @Override
    public long numActiveHugeAllocations() {
        return max(numHugeAllocations() - numHugeDeallocations(), 0);
    }

    @Override
    public long numActiveBytes() {
        long val = activeBytesHuge.value();
        synchronized (this) {
            for (int i = 0; i < chunkListMetrics.size(); i++) {
                for (PoolChunkMetric m: chunkListMetrics.get(i)) {
                    val += m.chunkSize();
                }
            }
        }
        return max(0, val);
    }

    protected abstract PoolChunk<T> newChunk(int pageSize, int maxOrder, int pageShifts, int chunkSize);
    protected abstract PoolChunk<T> newUnpooledChunk(int capacity);
    protected abstract PooledByteBuf<T> newByteBuf(int maxCapacity);
    protected abstract void memoryCopy(T src, int srcOffset, PooledByteBuf<T> dst, int length);
    protected abstract void destroyChunk(PoolChunk<T> chunk);

    @Override
    public synchronized String toString() {
        StringBuilder buf = new StringBuilder()
            .append("Chunk(s) at 0~25%:")
            .append(StringUtil.NEWLINE)
            .append(qInit)
            .append(StringUtil.NEWLINE)
            .append("Chunk(s) at 0~50%:")
            .append(StringUtil.NEWLINE)
            .append(q000)
            .append(StringUtil.NEWLINE)
            .append("Chunk(s) at 25~75%:")
            .append(StringUtil.NEWLINE)
            .append(q025)
            .append(StringUtil.NEWLINE)
            .append("Chunk(s) at 50~100%:")
            .append(StringUtil.NEWLINE)
            .append(q050)
            .append(StringUtil.NEWLINE)
            .append("Chunk(s) at 75~100%:")
            .append(StringUtil.NEWLINE)
            .append(q075)
            .append(StringUtil.NEWLINE)
            .append("Chunk(s) at 100%:")
            .append(StringUtil.NEWLINE)
            .append(q100)
            .append(StringUtil.NEWLINE)
            .append("tiny subpages:");
        appendPoolSubPages(buf, tinySubpagePools);
        buf.append(StringUtil.NEWLINE)
           .append("small subpages:");
        appendPoolSubPages(buf, smallSubpagePools);
        buf.append(StringUtil.NEWLINE);

        return buf.toString();
    }

    private static void appendPoolSubPages(StringBuilder buf, PoolSubpage<?>[] subpages) {
        for (int i = 0; i < subpages.length; i ++) {
            PoolSubpage<?> head = subpages[i];
            if (head.next == head) {
                continue;
            }

            buf.append(StringUtil.NEWLINE)
                    .append(i)
                    .append(": ");
            PoolSubpage<?> s = head.next;
            for (;;) {
                buf.append(s);
                s = s.next;
                if (s == head) {
                    break;
                }
            }
        }
    }

    @Override
    protected final void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            destroyPoolSubPages(smallSubpagePools);
            destroyPoolSubPages(tinySubpagePools);
            destroyPoolChunkLists(qInit, q000, q025, q050, q075, q100);
        }
    }

    private static void destroyPoolSubPages(PoolSubpage<?>[] pages) {
        for (PoolSubpage<?> page : pages) {
            page.destroy();
        }
    }

    private void destroyPoolChunkLists(PoolChunkList<T>... chunkLists) {
        for (PoolChunkList<T> chunkList: chunkLists) {
            chunkList.destroy(this);
        }
    }

    static final class HeapArena extends PoolArena<byte[]> {

        HeapArena(PooledByteBufAllocator parent, int pageSize, int maxOrder,
                int pageShifts, int chunkSize, int directMemoryCacheAlignment) {
            super(parent, pageSize, maxOrder, pageShifts, chunkSize,
                    directMemoryCacheAlignment);
        }

        private static byte[] newByteArray(int size) {
            return PlatformDependent.allocateUninitializedArray(size);
        }

        @Override
        boolean isDirect() {
            return false;
        }

        @Override
        protected PoolChunk<byte[]> newChunk(int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            return new PoolChunk<byte[]>(this, newByteArray(chunkSize), pageSize, maxOrder, pageShifts, chunkSize, 0);
        }

        @Override
        protected PoolChunk<byte[]> newUnpooledChunk(int capacity) {
            return new PoolChunk<byte[]>(this, newByteArray(capacity), capacity, 0);
        }

        @Override
        protected void destroyChunk(PoolChunk<byte[]> chunk) {
            // Rely on GC.
        }

        @Override
        protected PooledByteBuf<byte[]> newByteBuf(int maxCapacity) {
            return HAS_UNSAFE ? PooledUnsafeHeapByteBuf.newUnsafeInstance(maxCapacity)
                    : PooledHeapByteBuf.newInstance(maxCapacity);
        }

        @Override
        protected void memoryCopy(byte[] src, int srcOffset, PooledByteBuf<byte[]> dst, int length) {
            if (length == 0) {
                return;
            }

            System.arraycopy(src, srcOffset, dst.memory, dst.offset, length);
        }
    }

    static final class DirectArena extends PoolArena<ByteBuffer> {

        // ??????1???allocator ??????
        // ??????2???pageSize,8k
        // ??????3???maxOrder???11
        // ??????4???pageShifts,13 ???1 << 13 => pageSize
        // ??????5???chunkSize,16mb
        // ??????6???directMemoryCacheAlignment 0
        DirectArena(PooledByteBufAllocator parent, int pageSize, int maxOrder,
                int pageShifts, int chunkSize, int directMemoryCacheAlignment) {

            // ??????1???allocator ??????
            // ??????2???pageSize,8k
            // ??????3???maxOrder???11
            // ??????4???pageShifts,13 ???1 << 13 => pageSize
            // ??????5???chunkSize,16mb
            // ??????6???directMemoryCacheAlignment 0
            super(parent, pageSize, maxOrder, pageShifts, chunkSize,
                    directMemoryCacheAlignment);
        }

        @Override
        boolean isDirect() {
            return true;
        }

        // mark as package-private, only for unit test
        int offsetCacheLine(ByteBuffer memory) {
            // We can only calculate the offset if Unsafe is present as otherwise directBufferAddress(...) will
            // throw an NPE.
            int remainder = HAS_UNSAFE
                    ? (int) (PlatformDependent.directBufferAddress(memory) & directMemoryCacheAlignmentMask)
                    : 0;

            // offset = alignment - address & (alignment - 1)
            return directMemoryCacheAlignment - remainder;
        }

        @Override
        // ??????1???pageSize 8k
        // ??????2???maxOrder 11
        // ??????3???pageShifts 13
        // ??????4???chunkSize 16mb
        protected PoolChunk<ByteBuffer> newChunk(int pageSize, int maxOrder,
                int pageShifts, int chunkSize) {

            // ???????????? ????????????????????????
            if (directMemoryCacheAlignment == 0) {

                // ??????1???this, ??????directArena?????????poolChunk????????????????????????????????????
                // ??????2???allocateDirect(chunkSize) ??????????????? ??????????????????unsafe????????? ?????? DirectByteBuffer ????????????????????????????????? 16mb ???????????????
                // ByteBuffer ?????????
                // ??????3???pageSize 8k
                // ??????4???maxOrder 11
                // ??????5???pageShifts 13
                // ??????6???chunkSize 16mb
                // ??????7??? offset 0
                return new PoolChunk<ByteBuffer>(this,
                        allocateDirect(chunkSize), pageSize, maxOrder,
                        pageShifts, chunkSize, 0);
            }

            // ????????????????????????..

            final ByteBuffer memory = allocateDirect(chunkSize
                    + directMemoryCacheAlignment);
            return new PoolChunk<ByteBuffer>(this, memory, pageSize,
                    maxOrder, pageShifts, chunkSize,
                    offsetCacheLine(memory));
        }

        @Override
        protected PoolChunk<ByteBuffer> newUnpooledChunk(int capacity) {
            if (directMemoryCacheAlignment == 0) {
                return new PoolChunk<ByteBuffer>(this,
                        allocateDirect(capacity), capacity, 0);
            }
            final ByteBuffer memory = allocateDirect(capacity
                    + directMemoryCacheAlignment);
            return new PoolChunk<ByteBuffer>(this, memory, capacity,
                    offsetCacheLine(memory));
        }

        private static ByteBuffer allocateDirect(int capacity) {
            return PlatformDependent.useDirectBufferNoCleaner() ?
                    PlatformDependent.allocateDirectNoCleaner(capacity) : ByteBuffer.allocateDirect(capacity);
        }

        @Override
        protected void destroyChunk(PoolChunk<ByteBuffer> chunk) {
            if (PlatformDependent.useDirectBufferNoCleaner()) {
                PlatformDependent.freeDirectNoCleaner(chunk.memory);
            } else {
                PlatformDependent.freeDirectBuffer(chunk.memory);
            }
        }

        @Override
        protected PooledByteBuf<ByteBuffer> newByteBuf(int maxCapacity) {
            if (HAS_UNSAFE) {
                return PooledUnsafeDirectByteBuf.newInstance(maxCapacity);
            } else {
                return PooledDirectByteBuf.newInstance(maxCapacity);
            }
        }

        @Override
        protected void memoryCopy(ByteBuffer src, int srcOffset, PooledByteBuf<ByteBuffer> dstBuf, int length) {
            if (length == 0) {
                return;
            }

            if (HAS_UNSAFE) {
                PlatformDependent.copyMemory(
                        PlatformDependent.directBufferAddress(src) + srcOffset,
                        PlatformDependent.directBufferAddress(dstBuf.memory) + dstBuf.offset, length);
            } else {
                // We must duplicate the NIO buffers because they may be accessed by other Netty buffers.
                src = src.duplicate();
                ByteBuffer dst = dstBuf.internalNioBuffer();
                src.position(srcOffset).limit(srcOffset + length);
                dst.position(dstBuf.offset);
                dst.put(src);
            }
        }
    }
}
