/*
 * @file    Cache.java
 * @brief   This class is a memory-resident cache for blocks read from a hard
 *           disk. Whenever a read or write is requested, a linear search is
 *           performed on the resident cache. If the requested block is found,
 *           then the operation is performed in cache only. If it is not found,
 *           then a free cache block is sought to perform the operation. If no
 *           free slots are available, then a slot is selected to be freed by
 *           the second-chance (clock) algorithm. A dirty bit is maintained for
 *           each slot so that, if it is selected for replacement, it may be
 *           written back to the hard disk if it has been modified. A sync()
 *           and flush() method are provided to force all modified slots to be
 *           written back to disk.
 * @author  Brendan Sweeney, SID 1161836
 * @date    November 28, 2012
 */
public class Cache {
    private final static int DEFAULT_BLOCK_SIZE   = 512,    // for default
                             DEFAULT_CACHE_BLOCKS = 10;     //  constructor
    private int              nextVictim;    // index of next replacement victim
    private CacheEntry[]     pageTable;     // disk read/write cache
    
    
    /*
    * @brief   This class is a memory-resident block of cache for data read
    *           from a hard disk. The block size it uses must be set by the
    *           containing class. The other variables are set to default values
    *           used in the second-chance (clock) block replacement algorithm.
    */
    private class CacheEntry {
        public int     frame;       // hard disk index of cached block
        public boolean reference;   // recent access bit
        public boolean dirty;       // block written in cache only
        public byte[]  buffer;      // data buffer of one hard disk block
        
        
        /**
        * Initializes a block of cache with a set of default values used in the
        *  second-chance (clock) block replacement algorithm and a block size
        *  set by the containing class.
        * @param  blockSize  Number of bytes per block expected on the hard
        *                     disk to be cached.
        * @pre    blockSize matches the size, in bytes, of a block on a hard
        *          disk.
        * @post   An empty CacheEntry has been created to hold blockSize bytes
        *          of data.
        */
        public CacheEntry(int blockSize) {
            frame     = -1;
            reference = false;
            dirty     = false;
            buffer    = new byte[blockSize];
        } // end constructor
    } // end class CacheEntry
    
    
    /**
     * Initializes this Cache to a set of pre-determined default values.
     * @pre    The hard disk to cache uses a block size of DEFAULT_BLOCK_SIZE
     *          bytes.
     * @post   An empty Cache has been created to hold DEFAULT_CACHE_BLOCKS of
     *          data blocks from a hard disk.
     */
    public Cache() {
        nextVictim = 0;
        pageTable  = new CacheEntry[DEFAULT_CACHE_BLOCKS];
        
        for (int i = 0; i < DEFAULT_CACHE_BLOCKS; ++i) {
            pageTable[i] = new CacheEntry(DEFAULT_BLOCK_SIZE);
        } // end for (; i < DEFAULT_CACHE_BLOCKS; )
    } // end default constructor
    
    
    /**
     * Initializes this Cache to a set of provided values.
     * @param  blockSize  Expected block size, in bytes, used by hard disk to
     *                     cache.
     * @param  cacheBlocks  Number of blocks to store in the resident cache.
     * @pre    The hard disk to cache uses a block size of blockSize bytes.
     * @post   An empty Cache has been created to hold cacheBlocks of data
     *          blocks from a hard disk.
     */
    public Cache(int blockSize, int cacheBlocks) {
        nextVictim = 0;
        
        if (blockSize < 1) {
            blockSize = DEFAULT_BLOCK_SIZE;
        } // end if (blockSize < 1)
        
        if (cacheBlocks < 1) {
            cacheBlocks = DEFAULT_CACHE_BLOCKS;
        } // end if (cacheBlocks < 1)
        
        pageTable = new CacheEntry[cacheBlocks];
        
        for (int i = 0; i < cacheBlocks; ++i) {
            pageTable[i] = new CacheEntry(blockSize);
        } // end for (; i < cacheBlocks; )
    } // end constructor
    
    
    /**
     * Reads a block of data from the cache. If the specified block is not
     *  already in the cache, then an empty slot is sought in which to store
     *  the data. If no empty slot is available, then a slot is selected for
     *  replacement using the second-chance (clock) algorithm. If the selected
     *  slot is dirty, then its contents are written to disk before the new
     *  block is read into it.
     * @param  blockId  The location of the block on the hard disk to read.
     * @param  buffer  A data buffer to store the data of the located block.
     * @pre    blockId references a data block on the hard disk; buffer is the
     *          same size as a data block on the hard disk.
     * @post   The resident cache and buffer[] contain the data of the block
     *          referenced by blockId; any block that was replaced in the
     *          resident cache has been written back to the hard disk.
     * @return true if all operations completed successfully; false, otherwise.
     */
    public synchronized boolean read(int blockId, byte buffer[]) {
        int count = pageTable.length;
        
        for (int i = 0; i < count; ++i) {
            if (pageTable[i].frame == blockId) {
                // block in cache, read it and be done
                pageTable[i].reference = true;
                System.arraycopy(pageTable[i].buffer, 0,
                                 buffer, 0, buffer.length);
                return true;
            } // end if (pageTable[i].frame == blockId)
        } // end for(; i < count; )
        
        try {   // block not in cache, pick a slot for it
            setNextVictim();
            SysLib.rawread(blockId, buffer);
            System.arraycopy(buffer, 0, pageTable[nextVictim].buffer,
                             0, buffer.length);
            pageTable[nextVictim].frame     = blockId;
            pageTable[nextVictim].reference = true;
            pageTable[nextVictim].dirty     = false;
            nextVictim = (nextVictim + 1) % count;
        } catch (Exception e) {
            return false;
        } // ent try setNextVictim()
        
        return true;
    } // end read(int, byte[])
    
    
    /**
     * Writes a block of data to the cache. If the specified block is not
     *  already in the cache, then an empty slot is sought in which to write
     *  the data. If no empty slot is available, then a slot is selected for
     *  replacement using the second-chance (clock) algorithm. If the selected
     *  slot is dirty, then its contents are written to disk before the new
     *  block is written into it.
     * @param  blockId  The location of the block on the hard disk to write.
     * @param  buffer  A buffer containing data to be written to the specified
     *                  block.
     * @pre    blockId references a data block on the hard disk; buffer is the
     *          same size as a data block on the hard disk.
     * @post   The resident cache contains the data from the provided buffer;
     *          any block that was replaced in the resident cache has been
     *          written back to the hard disk.
     * @return true if all operations completed successfully; false, otherwise.
     */
    public synchronized boolean write(int blockId, byte buffer[]) {
        int count = pageTable.length;
        
        for (int i = 0; i < count; ++i) {
            if (pageTable[i].frame == blockId) {
                // block in cache, write to it and set the dirty bit
                pageTable[i].reference = true;
                pageTable[i].dirty     = true;
                System.arraycopy(buffer, 0, pageTable[i].buffer,
                                 0, buffer.length);
                return true;
            } // end if (pageTable[i].frame == blockId)
        } // end for(; i < count; )
        
        try {   // block not in cache, pick a slot for it
            setNextVictim();
            System.arraycopy(buffer, 0, pageTable[nextVictim].buffer,
                             0, buffer.length);
            pageTable[nextVictim].frame     = blockId;
            pageTable[nextVictim].reference = true;
            pageTable[nextVictim].dirty     = true;
            nextVictim = (nextVictim + 1) % count;
        } catch (Exception e) {
            return false;
        } // end try setNextVictim()
        
        return true;
    } // end write(int, byte[])
    
    
    /**
     * Writes all modified blocks in cache back to disk. All data in the cache
     *  remain valid.
     * @pre    None.
     * @post   All dirty blocks have been written from cache to disk; all dirty
     *          bits are set to false.
     */
    public void sync() {
        int count = pageTable.length;
        
        for (int i = 0; i < count; ++i) {
            if (pageTable[i].dirty) {
                try {
                    SysLib.rawwrite(pageTable[i].frame, pageTable[i].buffer);
                    pageTable[i].dirty = false;
                } catch (Exception e) { }
            } // end if (pageTable[i].dirty)
        } // end for(; i < count; )
        
        SysLib.sync();
    } // end sync()
    
    
    /**
     * Writes all modified blocks in cache back to disk. All data in the cache
     *  are invalidated.
     * @pre    None.
     * @post   All dirty blocks have been written from cache to disk; all cache
     *          blocks are reset to default values.
     */
    public void flush() {
        int count = pageTable.length;
        
        for (int i = 0; i < count; ++i) {
            if (pageTable[i].dirty) {
                try {
                    SysLib.rawwrite(pageTable[i].frame, pageTable[i].buffer);
                    pageTable[i].dirty = false;
                } catch (Exception e) { }
            } // end if (pageTable[i].dirty)
            
            pageTable[i].frame     = -1;
            pageTable[i].reference = false;
        } // end for(; i < count; )
        
        nextVictim = 0;
        SysLib.sync();
    } // end flush()
    
    
    /**
     * Sets the index of the next victim for replacement. It is assumed that no
     *  slots are skipped as the cache is filled, so it is safe to search for
     *  an empty slot from the last potential victim.
     * @pre    None.
     * @post   nextVictim has been set to the index of a slot that is either
     *          empty or contains the best candidate for replacement; if a slot
     *          was replaced, then its data were first written back to disk.
     */
    private void setNextVictim() {
        int count = pageTable.length;
        
        for (int i = nextVictim; i < count; ++i) {
            if (pageTable[i].frame == -1) {
                pageTable[i].reference = false;    // to bypass next loop
                nextVictim = i;
                break;
            } // end if (pageTable[i].frame == -1)
        } // end for 
        
        while(pageTable[nextVictim].reference) {
            pageTable[nextVictim].reference = false;
            nextVictim = (nextVictim + 1) % count;
        } // end while(pageTable[nextVictim].reference)

        if (pageTable[nextVictim].dirty) {
            SysLib.rawwrite(pageTable[nextVictim].frame,
                            pageTable[nextVictim].buffer);
        } // end if (pageTable[nextVictim].dirty)
    } // end setNextVictim()
} // end class Cache
