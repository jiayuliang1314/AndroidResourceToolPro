package findunusedresources;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Find and remove unused resources in an Android project.
 * Removes the following resources defined in any .xml file located in any <root>/res/values* directory:
 * - { string, dimen, color, string-array, array, style }
 * Removes the following resources located in any <root>/res/drawable* directory:
 * - { .png, .xml }
 */
public class FindUnusedResources {
    //region 参数
    private static final int ACTION_EXIT = 4;
    private static final int ACTION_PRINT_ALL = 3;
    private static final int ACTION_PRINT_UNUSED = 1;
    private static final int ACTION_DELETE = 2;
    // each map below contains ALL indexed resources for that particular type (string/color/etc) and a reference count
    private static final Map<String, AtomicInteger> mStringMap = new TreeMap<>();
    private static final Map<String, AtomicInteger> mDimenMap = new TreeMap<>();
    private static final Map<String, AtomicInteger> mColorMap = new TreeMap<>();
    private static final Map<String, AtomicInteger> mStringArrayMap = new TreeMap<>();
    private static final Map<String, AtomicInteger> mDrawableMap = new TreeMap<>();
    private static final Map<String, AtomicInteger> mMipmapMap = new TreeMap<>();
    private static final Map<String, AtomicInteger> mLayoutMap = new TreeMap<>();
    private static final Map<String, AtomicInteger> mStylesMap = new TreeMap<>();
    private static final List<String> deletedFileList = new ArrayList<>();
    // what resources we're looking for..
    private static final String USE_STRING = "string";
    private static final String USE_DIMEN = "dimen";
    private static final String USE_COLOR = "color";
    private static final String USE_STRING_ARRAY = "string-array";
    private static final String USE_STRING_ARRAY_REFERENCE = "array"; // string-array referenced as R.array.xxx
    private static final String USE_DRAWABLE = "drawable";
    private static final String USE_MIPMAP = "mipmap";
    private static final String USE_LAYOUT = "layout";
    private static final String USE_STYLES = "style";
    private static final String[] EXCLUDE_FILES = {"analytics.xml"};
    private static final Map<String, Integer> mTotalRemovedMap = new HashMap<>();//todo 干哈的，看着没用到
    private static String TMP_FIND_UNUSED_RESOURCES = "/tmp/FindUnusedResources/";
    private static long mLastUpdateMs;  //每隔400ms更新下状态
    private static String mRootPath;//src文件夹路径
    //endregion

    public static void main(String[] args) {
        mStringMap.clear();
        mDimenMap.clear();
        mColorMap.clear();
        mStringArrayMap.clear();
        mDrawableMap.clear();
        mLayoutMap.clear();
        mStylesMap.clear();
        deletedFileList.clear();
        mTotalRemovedMap.clear();
//        args = new String[1];
//        args[0] = "/Users/admin/StudioProjects/AndroidScreenRecorder/MainModule/src/main";
        if (args.length == 0) {
            printUsage();
            System.exit(0);
        }

        String root = args[0];  //main文件夹路径

        // make sure AndroidManifest.xml at root //todo 这里修改为参数
        File mainFile = new File(root + "/AndroidManifest.xml");
        if (!mainFile.exists()) {
            System.out.println("file: " + mainFile + " does not exist!\nBase directory should point to an Android project.");
            printUsage();
            System.exit(0);
        }

        TMP_FIND_UNUSED_RESOURCES = root + TMP_FIND_UNUSED_RESOURCES;

        // get additional arguments
        List<String> additionalSearchPaths = new ArrayList<>();
        boolean promptUser = true;
        // check for "noprompt" as an argument
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("noprompt".equalsIgnoreCase(arg)) {
                promptUser = false;
            } else {
                additionalSearchPaths.add(arg);
            }
        }

        // find any directories named "res" and index all resources inside
        File parentFile = new File(root).getParentFile();
        mRootPath = parentFile.getAbsolutePath();
        System.out.println("Indexing resources...");
        indexAllResources(parentFile, false);

        System.out.println("got " + mStringMap.size() + " " + USE_STRING + " resources");
        System.out.println("got " + mDimenMap.size() + " " + USE_DIMEN + " resources");
        System.out.println("got " + mColorMap.size() + " " + USE_COLOR + " resources");
        System.out.println("got " + mStringArrayMap.size() + " " + USE_STRING_ARRAY + " resources");
        System.out.println("got " + mStylesMap.size() + " " + USE_STYLES + " resources");
        System.out.println("got " + mLayoutMap.size() + " " + USE_LAYOUT + " resources");
        System.out.println("got " + mDrawableMap.size() + " " + USE_DRAWABLE + " resources");
        System.out.println("got " + mMipmapMap.size() + " " + USE_MIPMAP + " resources");

        // may need to loop a few times to find & delete all unused variables
        // for example, a drawable 'abc' may be referenced by a layout which isn't referenced in any code.
        // - the first pass will delete the layout and the second pass will delete the drawable
        int totalRemoved = 0;
        for (int i = 1; true; i++) {
            System.out.println("\nPASS " + i);

            // search root directory for resource usage
            int unused = findUnusedResources(root);
            if (unused == 0) {
                System.out.println("findUnusedResources 0,break down");
                break;
            }

            // search any additional paths for resources
            for (String additionalPath : additionalSearchPaths) {
                unused = findUnusedResources(additionalPath);
                if (unused == 0) {
                    System.out.println("findUnusedResources additional 0,break down");
                    break;
                }
            }
            if (unused == 0) {
                break;
            }

            // prepare to remove all remaining resources that weren't referenced
            int numRemoved = 0;

            // prompt next action..
            while (true) {
                int command;
                if (promptUser) {
                    command = promptNext();
                } else {
                    command = ACTION_DELETE;
                }

                if (command == ACTION_PRINT_UNUSED) {
                    printResources(true, false);
                } else if (command == ACTION_DELETE) {
                    numRemoved = deleteUnusedResources(root, i);
                    // break out of loop and continue search..
                    break;
                }
                if (command == ACTION_PRINT_ALL) {
                    printResources(false, false);
                } else if (command == ACTION_EXIT) {
                    // STOP & exit!
                    System.exit(1);
                    return;
                }
            }

            if (numRemoved == 0) {
                // all DONE!
                break;
            }

            totalRemoved += numRemoved;
        }

        if (totalRemoved > 0) {
            System.out.println("DONE! Removed " + totalRemoved + " TOTAL resources");

            Iterator<String> keyItor = mTotalRemovedMap.keySet().iterator();
            while (keyItor.hasNext()) {
                String key = keyItor.next();
                Integer value = mTotalRemovedMap.get(key);
                System.out.println("-> " + value + " " + key + " resources");
            }

            System.out.println("-- FILES REMOVED --");
            for (String filename : deletedFileList) {
                System.out.println(filename);
            }
        }
    }


    //region ok
    private static void backupAndDeleteFile(File file) {
        // backup file to /tmp folder using the same folder structure to avoid name conflicts
        String fileNameFull = file.getAbsolutePath();
        String relativeName = fileNameFull.replace(mRootPath, "");
        if (relativeName.startsWith("/") && relativeName.length() > 1) {
            relativeName = relativeName.substring(1);
        }
        File backupFile = new File(TMP_FIND_UNUSED_RESOURCES + relativeName);
        File backupFolder = backupFile.getParentFile();
        if (!backupFolder.exists()) {
            boolean isOk = backupFolder.mkdirs();
            if (!isOk) {
                System.out.print("ERROR creating backup folder: " + backupFolder.getAbsolutePath() + " to backup: " + fileNameFull);
                return;
            }
        }
        try {
            Files.copy(file.toPath(), backupFile.toPath());
        } catch (IOException e) {
            System.out.print("ERROR backing up: " + fileNameFull + " to: " + backupFile + ", Exception: " + e.getMessage());
            return;
        }

        boolean isOk = file.delete();
        if (!isOk) {
            System.out.print("ERROR deleting: " + fileNameFull);
            return;
        }
        deletedFileList.add(file.toString());
    }


    /**
     * main文件夹路径
     *
     * @param root
     * @param i
     * @return
     */
    private static int deleteUnusedResources(String root, int i) {
        // first time through remove backup folder
        if (i == 1) {
            // TODO: use this to support windows better
            //String tmpFolder = System.getProperty("java.io.tmpdir");
            File backupFolder = new File(TMP_FIND_UNUSED_RESOURCES);
            if (backupFolder.exists()) {
                // delete tmp folder and all of it's contents
                System.out.println("Deleting backup folder: " + TMP_FIND_UNUSED_RESOURCES);
                final File[] files = backupFolder.listFiles();
                for (File f : files) {
                    f.delete();
                }
                backupFolder.delete();
            }
        }

        // find any directories named "res" and DELETE all unused resources inside
        File parentFile = new File(root).getParentFile();
        System.out.println("Deleting resources...");
        indexAllResources(parentFile, true);

        // pring and clear deleted resources from maps for next time through
        int totalRemoved = 0;
        totalRemoved += resetCounters(mStringMap, USE_STRING);
        totalRemoved += resetCounters(mDimenMap, USE_DIMEN);
        totalRemoved += resetCounters(mColorMap, USE_COLOR);
        totalRemoved += resetCounters(mStringArrayMap, USE_STRING_ARRAY);
        totalRemoved += resetCounters(mStylesMap, USE_STYLES);
        totalRemoved += resetCounters(mLayoutMap, USE_LAYOUT);
        totalRemoved += resetCounters(mDrawableMap, USE_DRAWABLE);
        totalRemoved += resetCounters(mMipmapMap, USE_MIPMAP);

        return totalRemoved;
    }

    private static int resetCounters(Map<String, AtomicInteger> map, String text) {
        int count = 0;
        Iterator<String> it = map.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            AtomicInteger value = map.get(key);
            if (value.get() == 0) {
                // UNUSED RESOURCE
                count++;
                // delete this key
                it.remove();
            } else {
                // USED - reset back to 0
                value.set(0);
            }
        }
        if (count > 0) {
            System.out.println("REMOVED " + count + " " + text + " resources");
        }

        return count;
    }

    private static boolean searchFileForUse(File file) {
        boolean isAnyMatch = false;
        String fileName = file.getName();
        boolean isJava = fileName.endsWith(".java") || fileName.endsWith(".kt");
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    // done reading file
                    break;
                }

                // ignore commented out lines
                if (isJava && line.trim().startsWith("//")) {
                    continue;
                }

                // search line for a reference to one of the indexed resources
                // NOTE: I'm expecting at most a line can only contain a reference to a single resource type (string/color/etc)
                // > Once one is found - we can save time by skiping searching for others on the same line
                // Multiple references for the same time are checked:
                // ex: int resId = (isSomething ? R.string.one : R.string.two);
                boolean isMatch;
                isMatch = searchLineForUse(isJava, line, mStringMap, USE_STRING);
                if (!isMatch) {
                    isMatch = searchLineForUse(isJava, line, mDimenMap, USE_DIMEN);//todo？为啥没有isMatch
                }
                if (!isMatch) {
                    isMatch = searchLineForUse(isJava, line, mColorMap, USE_COLOR);
                }
                if (!isMatch) {
                    isMatch = searchLineForUse(isJava, line, mStringArrayMap, USE_STRING_ARRAY_REFERENCE);
                }
                if (!isMatch) {
                    isMatch = searchLineForUse(isJava, line, mDrawableMap, USE_DRAWABLE);
                }
                if (!isMatch) {
                    isMatch = searchLineForUse(isJava, line, mMipmapMap, USE_MIPMAP);
                }
                if (!isMatch) {
                    isMatch = searchLineForUse(isJava, line, mStylesMap, USE_STYLES);
                }
                if (!isMatch) {
                    isMatch = searchLineForUse(isJava, line, mLayoutMap, USE_LAYOUT);
                }

                if (isMatch) {
                    isAnyMatch = true;
                }
            }
        } catch (Exception e) {
            System.out.println("searchFileForUse: Error reading file: " + file + ", " + e.getMessage());
//            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                }
            }
        }
        return isAnyMatch;
    }

    private static boolean searchLineForUse(boolean isJava, String line, Map<String, AtomicInteger> map, String type) {
        String searchFor; // primary use case (ie: R.string.value)
//        String searchFor2 = null; // secondary use case (ie: R.id.value)，//todo 这里有问题

        boolean isFound = false;

        // check each indexed value in map
        for (String value : map.keySet()) {
            if (isJava) {
                String convertedValue = value;
                // special case: in java files, dot is replaced with underscore
                // eg: Parent.Style is referenced as Parent_Style
                if (convertedValue.indexOf('.') > 0) {
                    convertedValue = value.replace('.', '_');
                }
                searchFor = "R." + type + "." + convertedValue; // R.string.value
//                searchFor2 = "R.id." + convertedValue; // R.id.value
            } else {
                // XML file
                searchFor = "@" + type + "/" + value; // @string/value
//                searchFor2 = "@id/" + value; //  @id/value
            }

            isFound = searchLineForUseWithKey(line, map, searchFor);
//            if (!isFound && searchFor2 != null) {
//                isFound = searchLineForUseWithKey(line, map, searchFor2);
//            }

            if (!isFound && !isJava && map == mStylesMap) {
                // special case: styles can reference a parent 3 ways in XML file:
                // 1) parent=
                // <style name="SquareButtonStyle">
                // <style name="GreenSquareButtonStyle" parent="@style/SquareButtonStyle">
                if (line.indexOf("parent=\"@" + type + "/" + value + "\"") >= 0) {
                    isFound = true;
                }
                // 2) parent.child
                // <style name="DialogButton">
                // <style name="DialogButton.Left">
                if (!isFound && line.indexOf("\"" + value + ".") >= 0) {
                    isFound = true;
                }
                // 3) parent=
                // <style name="SquareButtonStyle">
                // <style name="GreenSquareButtonStyle" parent="SquareButtonStyle">
                if (line.indexOf("parent=\"" + value + "\"") >= 0) {
                    isFound = true;
                }
            }

            if (isFound) {
                // incremement value reference
                AtomicInteger count = map.get(value);
                count.addAndGet(1);
            }
        }
        return isFound;
    }

    private static boolean searchLineForUseWithKey(String line, Map<String, AtomicInteger> map, String searchFor) {
        int stPos = 0;
        boolean isFound = false;
        // while() loop is to handle multiple resources referenced on a single line
        // eg: ? R.drawable.myfiles_file_mp4_thumb_lock : R.drawable.myfiles_file_mp4_thumb
        while (true) {
            // check if string exists in line
            int pos = line.indexOf(searchFor, stPos);
            if (pos < 0) {
                // not found!
                break;
            }

            isFound = true;
            System.out.println("searchFor isFound true " + searchFor);

            if (pos + searchFor.length() < line.length()) {
                // need to check next character. can be letter/digit/_/. which means we didn't find this key
                char nextChar = line.charAt(pos + searchFor.length());
                System.out.println("searchFor isFound true,next char is " + nextChar);
                if (nextChar == '_' || nextChar == '.' || Character.isLetterOrDigit(nextChar)) {
                    // false positive.. keep searching rest of line
                    System.out.println("searchFor isFound true,but next chat not fit,so set false");
                    isFound = false;

                    // special case: <searchFor> can be found later on in the same line. check 1 more time..
                    // eg: ? R.drawable.myfiles_file_mp4_thumb_lock : R.drawable.myfiles_file_mp4_thumb
                    stPos = pos + 1;
                    continue;
                }
            }

            // only want to loop once.. while() is for case above
            break;
        }

        return isFound;
    }

    /**
     * main文件夹路径
     *
     * @param root - directory to search through
     * @return number of unused resources still remaining (targets to delete)
     */
    private static int findUnusedResources(String root) {
        // search through AndroidManifext.xml
        searchFileForUse(new File(root + "/AndroidManifest.xml"));

        // search through all JAVA and XML files at <root>/../
        searchDirForUse(new File(root + "/../"));

        // done searching
        System.out.println();

        // print out summary for this pass1
        return printResources(true, false);
    }

    private static int promptNext() {
        System.out.println();
        System.out.println("Select Option:");
        System.out.println(ACTION_PRINT_UNUSED + ") show UNUSED resources");
        System.out.println(ACTION_DELETE + ") DELETE unused resources");
        System.out.println(ACTION_PRINT_ALL + ") show ALL indexed resources & usage counts");
        System.out.println(ACTION_EXIT + ") exit");

        BufferedReader br = null;
        String choice = null;
        try {
            //  open up standard input
            br = new BufferedReader(new InputStreamReader(System.in));
            choice = br.readLine();

            return Integer.parseInt(choice);
        } catch (IOException ioe) {
            System.out.println("> IOException: " + choice);
            ioe.printStackTrace();
        } catch (NumberFormatException nfe) {
            System.out.println("> invalid choice: " + choice);
        }
        return 0;
    }

    private static void searchDirForUse(File dir) {
        // now, look through all .java and .xml files to find uses
        File[] fileArr = dir.listFiles();
        if (fileArr == null) {
            //System.out.println("searchDirForUse: no files: " + dir);
            return;
        }
        boolean isAnyMatch = false;
        for (File file : fileArr) {
            if (file.isDirectory()) {
                searchDirForUse(file);
            } else {
                String filename = file.getName();
                if (filename.endsWith(".xml") || filename.endsWith(".java") || filename.endsWith(".kt")) {
                    // System.out.println("searching: " + file);
                    boolean isMatch = searchFileForUse(file);
                    if (isMatch) {
                        isAnyMatch = true;
                    }

                    // print out some progress indicator
                    long timeMs = System.currentTimeMillis();
                    if (timeMs - mLastUpdateMs >= 400) {
                        System.out.print(isAnyMatch ? "+" : ".");
                        isAnyMatch = false;
                        mLastUpdateMs = timeMs;
                    }
                }
            }
        }
    }


    /*
     * ok
     * main文件夹下的res路径遍历，获得初始文件个数
     */
    private static void indexAllResources(File parentFile, boolean isDeleteMode) {
        for (File file : parentFile.listFiles()) {
            if (file.isDirectory()) {
                String fileName = file.getName();
                if ("build".equalsIgnoreCase(fileName)) {
                    // ignore build folder
                    continue;
                } else if ("res".equals(fileName)) {
                    if (!isDeleteMode) {
                        System.out.println(" > " + file.getAbsolutePath());
                    }
                    // index contents of all .xml files in values*/ directory
                    indexValues(file, isDeleteMode);
                    // index all filenames in every /res/drawable*/ directory
                    indexDrawables(file, isDeleteMode);
                    // index all filenames in every /res/layout*/ directory
                    indexLayout(file, isDeleteMode);
                } else {//src
                    // recurse into sub-directory
                    indexAllResources(file, isDeleteMode);
                }
            }
        }
    }


    private static void indexValues(File dir, boolean isDeleteMode) {
        File[] fileArr = dir.listFiles();
        for (File file : fileArr) {
            String filename = file.getName();
            if (file.isDirectory() && filename.startsWith("values")) {
                indexValues(file, isDeleteMode);
            } else if (filename.endsWith(".xml") && !isExcludedFile(filename)) {
                if (isDeleteMode) {
                    replaceFileContents(file);
                } else {
                    readFileContents(file);
                }
            }
        }
    }

    /**
     * @param dir
     * @param isDeleteMode
     */
    private static void indexDrawables(File dir, boolean isDeleteMode) {
        File[] fileArr = dir.listFiles();
        for (File file : fileArr) {
            String filename = file.getName();
            if (file.isDirectory() && filename.startsWith("drawable")) {
                indexDrawables(file, isDeleteMode);
            }
            // NOTE: drawables can be png files or xml files:
            // ie: background=@drawable/selector.xml
            else if (!file.isDirectory() && (
                    filename.endsWith(".png")
                            || filename.endsWith(".jpg")
                            || filename.endsWith(".jpeg")
                            || filename.endsWith(".webp")
                            || filename.endsWith(".xml") && !isExcludedFile(filename))
            ) {
                filename = filename.substring(0, filename.length() - 4);
                if (filename.endsWith(".9")) {
                    filename = filename.substring(0, filename.length() - 2);
                }

                if (isDeleteMode) {
                    AtomicInteger count = mDrawableMap.get(filename);
                    if (count != null && count.get() == 0) {
                        backupAndDeleteFile(file);
                    }
                } else {
                    if (mDrawableMap.containsKey(filename) == false) {
                        mDrawableMap.put(filename, new AtomicInteger());
                    }
                }
            }
        }
    }

    private static void indexLayout(File dir, boolean isDeleteMode) {
        File[] fileArr = dir.listFiles();
        for (File file : fileArr) {
            String filename = file.getName();
            if (file.isDirectory() && filename.startsWith("layout")) {
                indexLayout(file, isDeleteMode);
            } else if (!file.isDirectory() && filename.endsWith(".xml") && !isExcludedFile(filename)) {
                filename = filename.substring(0, filename.length() - 4);

                if (isDeleteMode) {
                    AtomicInteger count = mLayoutMap.get(filename);
                    if (count != null && count.get() == 0) {
                        backupAndDeleteFile(file);
                    }
                } else {
                    if (!mLayoutMap.containsKey(filename)) {
                        mLayoutMap.put(filename, new AtomicInteger());
                    }
                }
            }
        }
    }

    private static boolean isExcludedFile(String filename) {
        for (String exclude : EXCLUDE_FILES) {
            if (filename.equals(exclude)) {
                return true;
            }
        }
        return false;
    }

    private static void printUsage() {
        System.out.println("Program to find and remove unused resources");
        System.out.println("usage: FindUnusedResources <path>");
        System.out.println("- where <path> is the path to an Android project (where AndroidManifest.xml exists)");
        System.out.println();
        System.out.println("- optionally, if project is a LIBRARY module you can pass additional paths to search for uses of it's resources");
        System.out.println("- optionally, add \"noprompt\" after <path> to remove unused w/out prompting");
        System.out.println("eg: java FindUnusedResources ~/working/AndroidProject/src/main");
        System.out.println("eg: java FindUnusedResources ~/working/AndroidProject/src/main noprompt");
    }

    /**
     * ok
     * 资源id map填充数据
     *
     * @param file
     */
    private static void readFileContents(File file) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }

                // each line in an xml file can contain at most 1 of the below
                boolean isFound;
                isFound = addLineEntry(line, mStringMap, createBeginTag(USE_STRING));
                if (!isFound) {
                    isFound = addLineEntry(line, mDimenMap, createBeginTag(USE_DIMEN));
                }
                if (!isFound) {
                    isFound = addLineEntry(line, mColorMap, createBeginTag(USE_COLOR));
                }
                if (!isFound) {
                    isFound = addLineEntry(line, mStringArrayMap, createBeginTag(USE_STRING_ARRAY));
                }
                if (!isFound) {
                    isFound = addLineEntry(line, mStylesMap, createBeginTag(USE_STYLES));
                }
            }
        } catch (Exception e) {
            System.out.println("readFileContents: Error reading file: " + file + ", " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * ok
     *
     * @param tag 资源名
     * @return
     */
    private static String createBeginTag(String tag) {
        return "<" + tag + " name=\"";
    }

    /**
     * ok
     * 添加资源名到map里
     *
     * @param line
     * @param map
     * @param key
     * @return
     */
    private static boolean addLineEntry(String line, Map<String, AtomicInteger> map, String key) {
        int pos = line.indexOf(key);
        if (pos >= 0) {
            String value = line.substring(pos + key.length());
            int p2 = value.indexOf("\"");
            if (p2 > 0) {
                value = value.substring(0, p2);
                if (map.containsKey(value) == false) {
                    map.put(value, new AtomicInteger(0));
                    //System.out.println("adding: " + key + value + "\"");
                }
                return true;
            }
        }
        return false;
    }


    private static int printResources(boolean showUnusedOnly, boolean showSummaryOnly) {
        int total = 0;
        total += printResources(mStringMap, USE_STRING, showUnusedOnly, showSummaryOnly);
        total += printResources(mDimenMap, USE_DIMEN, showUnusedOnly, showSummaryOnly);
        total += printResources(mColorMap, USE_COLOR, showUnusedOnly, showSummaryOnly);
        total += printResources(mStringArrayMap, USE_STRING_ARRAY, showUnusedOnly, showSummaryOnly);
        total += printResources(mStylesMap, USE_STYLES, showUnusedOnly, showSummaryOnly);
        total += printResources(mLayoutMap, USE_LAYOUT, showUnusedOnly, showSummaryOnly);
        total += printResources(mDrawableMap, USE_DRAWABLE, showUnusedOnly, showSummaryOnly);
        total += printResources(mMipmapMap, USE_MIPMAP, showUnusedOnly, showSummaryOnly);

        return total;
    }

    private static int printResources(Map<String, AtomicInteger> map, String text, boolean showUnusedOnly, boolean showSummaryOnly) {
        int count = 0;
        StringBuffer unused = new StringBuffer();
        Iterator<String> it = map.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            AtomicInteger value = map.get(key);
            if (value == null) {
                continue;
            }
            if (showUnusedOnly && value.get() == 0) {
                // UNUSED RESOURCE
                count++;
                if (!showSummaryOnly) {
                    unused.append(key).append('\n');
                }
            } else if (!showUnusedOnly) {
                count++;
                if (!showSummaryOnly) {
                    unused.append(key + ", " + value.get()).append('\n');
                }
            }
        }

        if (count > 0) {
            if (showUnusedOnly) {
                System.out.println("found " + count + " unused " + text + " resources");
            } else {
                System.out.println("showing " + count + " " + text + " resources:");
                System.out.println("<resource>, <# of references>");
                System.out.println("-----------------------------");
            }

            if (!showSummaryOnly) {
                System.out.println(unused.toString());
            }
        }

        return count;
    }

    //endregion

    /**
     * check if given key is in the line AND that the value associated is UNUSED
     * key:     <string name="
     */
    private static boolean checkLineEntry(String line, Map<String, AtomicInteger> map, String key) {
        int pos = line.indexOf(key);
        if (pos >= 0) {
            String value = line.substring(pos + key.length());//返回从起始位置（beginIndex）至字符串末尾的字符串
            int p2 = value.indexOf("\"");
            if (p2 > 0) {
                value = value.substring(0, p2);
                AtomicInteger count = map.get(value);
                return (count != null && count.get() == 0);
            }
        }
        return false;
    }

    private static void replaceFileContents(File file) {
        StringBuffer sb = new StringBuffer();
        int numLinesDeleted = 0;
        String deleteUntilTag = null;
        BufferedReader br = null;
        BufferedWriter bw = null;
        try {
            br = new BufferedReader(new FileReader(file));
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }

                boolean isFound = false;

                // check if we're looking for an end tag
                if (deleteUntilTag != null) {
                    // delete this line no matter what
                    isFound = true;
                    if (line.indexOf(deleteUntilTag) >= 0) {
                        // found end tag
                        deleteUntilTag = null;
                    }
                }

                // each line in the xml file should only contain at most 1 of
                // the below entries (no need to look for all)
                if (!isFound) {
                    isFound = checkLineEntry(line, mStringMap, createBeginTag(USE_STRING));
                    // could be multi-line.. but, typically not
                    if (isFound && !line.contains("</string>") && !line.contains("/>")) {
                        deleteUntilTag = "</" + USE_STRING + ">";
                    }
                }

                if (!isFound) {
                    isFound = checkLineEntry(line, mDimenMap, createBeginTag(USE_DIMEN));
                }
                if (!isFound) {
                    isFound = checkLineEntry(line, mColorMap, createBeginTag(USE_COLOR));
                }

                // NOTE: the following entries aren't always 1-line

                if (!isFound) {
                    isFound = checkLineEntry(line, mStringArrayMap, createBeginTag(USE_STRING_ARRAY));
                    if (isFound) {
                        // exception: empty string-array:
                        // <string-array name="featured_images"/>
                        if (!line.endsWith("/>")) {
                            deleteUntilTag = "</" + USE_STRING_ARRAY + ">";
                        }
                    }
                }

                if (!isFound) {
                    isFound = checkLineEntry(line, mStylesMap, createBeginTag(USE_STYLES));
                    if (isFound) {
                        deleteUntilTag = "</" + USE_STYLES + ">";
                    }
                }

                // check if end tag is on same line as begin tag
                if (deleteUntilTag != null && line.contains(deleteUntilTag)) {
                    deleteUntilTag = null;
                }

                // if entry was found - remove it; otherwise, keep it
                if (!isFound) {
                    sb.append(line).append('\n');
                } else {
                    numLinesDeleted++;
                }
            }

            if (numLinesDeleted > 0 && sb.length() > 0) {
                // replace file with filtered version
                bw = new BufferedWriter(new FileWriter(file));
                bw.write(sb.toString());
                bw.close();
            }

        } catch (Exception e) {
            System.out.println("replaceFileContents: Error reading file: " + file + ", " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
                if (bw != null) {
                    bw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
