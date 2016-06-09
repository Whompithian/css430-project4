/*
 * @file    Test4.java
 * @brief   This class is a test case for checking if threads waiting on I/O
 *           block threads that only need to utilize the CPU. It spawns threads
 *           in pairs, one I/O bound and one CPU-intensive thread in each pair.
 *           The completion time of each thread, given in milliseconds from the
 *           start of the test, is printed to standard out. At the end, the
 *           total elapsed time of the test, in milliseconds, is printed to
 *           standard out.
 * @author  Brendan Sweeney, SID 1161836
 * @date    November 28, 2012
 */
import java.util.Date;
import java.util.Random;


public class Test4 extends Thread {
    private final static int RANDOM     = 1,    // random access test
                             LOCALIZED  = 2,    // localized access test
                             MIXED      = 3,    // mixed access test
                             ADVERSARY  = 4,    // adversarial access test
                             PASS       = 200,  // number of passes per test
                             BLOCK_SIZE = 512,  // block size of disk
                             BLOCKS     = 990;  // less than disk block count
    private boolean cacheEnabled;   // whether to test with or without cache
    private int     testType;       // to get test type as argument
    private byte[]  buffer;         // global read and write buffer
    
    
    /**
     * Sets up the type of test to be run and initializes a data buffer with a
     *  recognizable pattern.
     * @param  args  Arguments that determine the test type. If the first
     *                element is "enabled" or "true", then the test is run with
     *                cache enabled; otherwise, cache is disabled. If the next
     *                element is an integer between 1 and 4, then it is taken
     *                as the number of the test to perform; otherwise, all it
     *                is taken to mean that all tests will be performed.
     * @pre    None.
     * @post   Either one or four disk access tests will be run, either with or
     *          without caching.
     */
    public Test4(String[] args) {
        cacheEnabled = (args[0].contentEquals("enabled") ||
                        args[0].contentEquals("true"));
        testType     = Integer.parseInt(args[1]);
        buffer       = new byte[BLOCK_SIZE];
        
        for (int i = 0; i < BLOCK_SIZE; ++i) {
            buffer[i] = (byte)(i % 128);
        } // end for (; i < BLOCK_SIZE; )
    } // end constructor
    
    
    /**
     * Runs either one or four tests, depending on the selection made during
     *  instantiation. In each test, locations are chosen on the disk that will
     *  not overwrite the superblock or dedicated inode blocks.
     * @pre    None.
     * @post   A message is printed to standard out indicating whether caching
     *          is enabled or disabled; the specified test has been run and the
     *          cache synchronized to the disk.
     */
    @Override
    public void run() {
        SysLib.cout("Running test with cache " + 
                    (cacheEnabled ? "enabled\n" : "disabled\n"));
        
        switch (testType) {
            case RANDOM:
                randomAccess();
                break;
            case LOCALIZED:
                localizedAccess();
                break;
            case MIXED:
                mixedAccess();
                break;
            case ADVERSARY:
                adversaryAccess();
                break;
            default:
                randomAccess();
                localizedAccess();
                mixedAccess();
                adversaryAccess();
        } // end switch (testType)
        
        if (cacheEnabled) {
            SysLib.csync();
        } // end if (cacheEnabled)
        else {
            SysLib.sync();
        } // end else (!cacheEnabled)
        
        SysLib.exit();
    } // end run()
    
    
    /**
     * Randomly selects a series of blocks to read and write. Each block ID
     *  should be completely unrelated, but is guaranteed to be in range of the
     *  default disk.
     * @pre    BLOCKS is 10 less than the number of blocks available on disk.
     * @post   A set of reads and writes have been performed on the disk; time
     *          statistics are printed to standard out.
     */
    private void randomAccess() {
        Random target      = new Random();  // target block ID
        Date   stop, start = new Date();    // timers
        int    random;      // for readability when parsing random numbers
        long   time;        // for readability when parsing times
        
        for (int i = 0; i < PASS; ++i) {
            random = Math.abs(target.nextInt());
            
            if (cacheEnabled) {
                SysLib.cwrite(random % BLOCKS + 10, buffer);
            } // end if (cacheEnabled)
            else {
                SysLib.rawwrite(random % BLOCKS + 10, buffer);
            } // end else (!cacheEnabled)
        } // end for (; i < PASS; )
        
        stop  = new Date();
        time  = stop.getTime() - start.getTime();
        SysLib.cout("  Random access: write time - " + (time / PASS) +
                    " average, " + time + " total: ");
        SysLib.flush();
        start = new Date();
        
        for (int i = 0; i < PASS; ++i) {
            random = Math.abs(target.nextInt());
            
            if (cacheEnabled) {
                SysLib.cread(random % BLOCKS + 10, buffer);
            } // end if (cacheEnabled)
            else {
                SysLib.rawread(random % BLOCKS + 10, buffer);
            } // end else (!cacheEnabled)
        } // end for (; i < PASS; )
        
        stop = new Date();
        time = stop.getTime() - start.getTime();
        SysLib.cout("read time - " + (time / PASS) +
                    " average, " + time + " total\n");
        SysLib.flush();
    } // end randomAccess()
    
    
    /**
     * Performs reads and writes to a small series of blocks of the disk that
     *  are close together. Enough blocks are accessed to fill the cache, then
     *  the same set of blocks are accessed until PASS operations have been
     *  performed.
     * @pre    BLOCKS is 10 less than the number of blocks available on disk.
     * @post   A set of reads and writes have been performed on the disk; time
     *          statistics are printed to standard out.
     */
    private void localizedAccess() {
        Date stop, start = new Date();  // timers
        long time;      // for readability when parsing times
        
        for (int i = 0; i < PASS; ++i) {
            if (cacheEnabled) {
                SysLib.cwrite(i % 10 + 10, buffer);
            } // end if (cacheEnabled)
            else {
                SysLib.rawwrite(i % 10 + 10, buffer);
            } // end else (!cacheEnabled)
        } // end for (; i < PASS; )
        
        stop  = new Date();
        time  = stop.getTime() - start.getTime();
        SysLib.cout("  Localized access: write time - " + (time / PASS) +
                    " average, " + time + " total: ");
        SysLib.flush();
        start = new Date();
        
        for (int i = 0; i < PASS; ++i) {
            if (cacheEnabled) {
                SysLib.cread(i % 10 + 10, buffer);
            } // end if (cacheEnabled)
            else {
                SysLib.rawread(i % 10 + 10, buffer);
            } // end else (!cacheEnabled)
        } // end for (; i < PASS; )
        
        stop  = new Date();
        time  = stop.getTime() - start.getTime();
        SysLib.cout("read time - " + (time / PASS) +
                    " average, " + time + " total\n");
        SysLib.flush();
    } // end localizedAccess()
    
    
    /**
     * Performs reads and writes to a set of blocks that are mostly contained
     *  in a small series on disk, but some of which are randomly selected from
     *  any location on disk.
     * @pre    BLOCKS is 10 less than the number of blocks available on disk.
     * @post   A set of reads and writes have been performed on the disk; time
     *          statistics are printed to standard out.
     */
    private void mixedAccess() {
        Random target      = new Random();  // target block ID; random chance
        Date   stop, start = new Date();    // timers
        int    random;      // for readability when parsing random numbers
        long   time;        // for readability when parsing times
        
        for (int i = 0; i < PASS; ++i) {
            random = Math.abs(target.nextInt());
            
            // 10% chance of selecting a random block ID
            if (random % 10 == 0) {
                if (cacheEnabled) {
                    SysLib.cwrite(random % BLOCKS + 10, buffer);
                } // end if (cacheEnabled)
                else {
                    SysLib.rawwrite(random % BLOCKS + 10, buffer);
                } // end else (!cacheEnabled)
            } // end if (random % 10 == 0)
            else {
                if (cacheEnabled) {
                    SysLib.cwrite(i % 10 + 10, buffer);
                } // end if (cacheEnabled)
                else {
                    SysLib.rawwrite(i % 10 + 10, buffer);
                } // end else (!cacheEnabled)
            } // end else (random % 10 != 0)
        } // end for (; i < PASS; )
        
        stop  = new Date();
        time  = stop.getTime() - start.getTime();
        SysLib.cout("  Mixed access: write time - " + (time / PASS) +
                    " average, " + time + " total: ");
        SysLib.flush();
        start = new Date();
        
        for (int i = 0; i < PASS; ++i) {
            random = Math.abs(target.nextInt());
            
            if (random % 10 == 0) {
                if (cacheEnabled) {
                    SysLib.cread(random % BLOCKS + 10, buffer);
                } // end if (cacheEnabled)
                else {
                    SysLib.rawread(random % BLOCKS + 10, buffer);
                } // end else (!cacheEnabled)
            } // end if (random % 10 == 0)
            else {
                if (cacheEnabled) {
                    SysLib.cread(i % 10 + 10, buffer);
                } // end if (cacheEnabled)
                else {
                    SysLib.rawread(i % 10 + 10, buffer);
                } // end else (!cacheEnabled)
            } // end else (random % 10 != 0)
        } // end for (; i < PASS; )
        
        stop = new Date();
        time = stop.getTime() - start.getTime();
        SysLib.cout("read time - " + (time / PASS) +
                    " average, " + time + " total\n");
        SysLib.flush();
    } // end mixedAccess()
    
    
    /**
     * Performs reads and writes on a set of blocks that are spread across the
     *  disk, with a guarantee that no one block is accessed again until every
     *  other block in range has been accessed.
     * @pre    BLOCKS is 10 less than the number of blocks available on disk.
     * @post   A set of reads and writes have been performed on the disk; time
     *          statistics are printed to standard out.
     */
    private void adversaryAccess() {
        Date stop, start = new Date();  // timers
        long time;      // for readability when parsing times
        
        for (int i = 0; i < PASS; ++i) {
            if (cacheEnabled) {
                SysLib.cwrite((i * (1 + BLOCKS / 2)) % BLOCKS + 10, buffer);
            } // end if (cacheEnabled)
            else {
                SysLib.rawwrite((i * (1 + BLOCKS / 2)) % BLOCKS + 10, buffer);
            } // end else (!cacheEnabled)
        } // end for (; i < PASS; )
        
        stop  = new Date();
        time  = stop.getTime() - start.getTime();
        SysLib.cout("  Adversary access: write time - " + (time / PASS) +
                    " average, " + time + " total: ");
        SysLib.flush();
        start = new Date();
        
        for (int i = 0; i < PASS; ++i) {
            if (cacheEnabled) {
                SysLib.cread((i * (1 + BLOCKS / 2)) % BLOCKS + 10, buffer);
            } // end if (cacheEnabled)
            else {
                SysLib.rawread((i * (1 + BLOCKS / 2)) % BLOCKS + 10, buffer);
            } // end else (!cacheEnabled)
        } // end for (; i < PASS; )
        
        stop = new Date();
        time = stop.getTime() - start.getTime();
        SysLib.cout("read time - " + (time / PASS) +
                    " average, " + time + " total.\n");
        SysLib.flush();
    } // end adversaryAccess()
} // end class Test4
