/*
 * Created by Dixit Sutariya on 01/06/22, 6:24 PM
 *     dixitsutariya@gmail.com
 *     Last modified 01/06/22, 4:13 PM
 *     Copyright (c) 2022.
 *     All rights reserved.
 */

package com.dixit.flyfix;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashSet;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

public class FixDexUtil {

    public static final String DEX_DIR_EXT = "007";
    public static final String DEX_DIR = "odex";
    private static final String DEX_SUFFIX = ".dex";
    private static final String APK_SUFFIX = ".apk";
    private static final String JAR_SUFFIX = ".jar";
    private static final String ZIP_SUFFIX = ".zip";
    private static final String OPTIMIZE_DEX_DIR = "optimize_dex";
    private static HashSet<File> loadedDex = new HashSet<>();


    static {
        loadedDex.clear();
    }

    /**
     * Load the patch, use the default directory: data/data/package name/files/odex
     *
     * @param context
     */
    public static void loadFixedDex(Context context) {
        loadFixedDex(context, null);
    }

    /**
     * Load patch
     *
     * @param context     context
     * @param checkDirect check directly without specific dir
     */
    public static void loadFixedDex(Context context, Boolean checkDirect) {
        //dex before dex merge
        if (checkDirect != null && loadedDex.size() <= 0) {
            if (FixDexUtil.isGoingToFix(context.getApplicationContext())) {
                doDexInject(context, loadedDex);
            }
        } else {
            doDexInject(context, loadedDex);
        }
    }

    public static boolean isGoingToFix(@NonNull Context context) {
        //Traverse all repair dex, because there may be multiple dex repair packages
        boolean canFix = false;
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        //Traverse all repair dex, because there may be multiple dex repair packages
        File fileDir = externalStorageDirectory != null ?
                new File(externalStorageDirectory, DEX_DIR_EXT) :
                new File(context.getFilesDir(), DEX_DIR); //data/data/package name/files/odex (this can be anywhere)
        if (!fileDir.exists()) {
            fileDir.mkdirs();
        }
        File[] listFiles = fileDir.listFiles();
        canFix = iteratorThroughDirectory(listFiles);
        return canFix;
    }

    private static boolean iteratorThroughDirectory(File[] listFiles) {
        if (listFiles != null) {
            for (File file : listFiles) {
                if (file.isDirectory()) {
                    iteratorThroughDirectory(file.listFiles());
                } else {
                    if (file.getName().startsWith("classes") &&
                            (file.getName().endsWith(DEX_SUFFIX)
                                    || file.getName().endsWith(APK_SUFFIX)
                                    || file.getName().endsWith(JAR_SUFFIX)
                                    || file.getName().endsWith(ZIP_SUFFIX))) {

                        loadedDex.add(file);
                        //Save to the collection //There is a target dex file, need to fix
                    }
                }

            }
        }
        return (loadedDex.size() > 0);
    }

    private static void doDexInject(Context appContext, HashSet<File> loadedDex) {
        String optimizeDir = appContext.getFilesDir().getAbsolutePath() +
                File.separator + OPTIMIZE_DEX_DIR;
        //data/data/package name/files/optimize_dex (this must be the directory under your own program)

        File fopt = new File(optimizeDir);
        if (!fopt.exists()) {
            fopt.mkdirs();
        }
        try {
            //1. Loader to load the application dex
            PathClassLoader pathLoader = (PathClassLoader) appContext.getClassLoader();
            for (File dex : loadedDex) {
                //2. Loader
                DexClassLoader dexLoader = new DexClassLoader(
                        dex.getAbsolutePath(), //The directory where the repaired dex (patch) is located
                        fopt.getAbsolutePath(), //The decompression directory where dex is stored (used for patches in jar, zip, and apk format)
                        null, //required when loading dex Library
                        pathLoader //parent class loader
                );
                //3. Start merging
                //The target of merging is Element[], just re-assign its value

                /**
                 * There are variables in BaseDexClassLoader: DexPathList pathList
                 * There are variables Element[] dexElements in DexPathList
                 * Reflect in order
                 */

                //3.1 Prepare a reference to pathList
                Object dexPathList = getPathList(dexLoader);
                Object pathPathList = getPathList(pathLoader);
                //3.2 Reflect the element collection from pathList
                Object leftDexElements = getDexElements(dexPathList);
                Object rightDexElements = getDexElements(pathPathList);
                //3.3 merge two dex arrays
                Object dexElements = combineArray(leftDexElements, rightDexElements);

                //Rewrite to Element[] dexElements in PathList; Assign
                Object pathList = getPathList(pathLoader); //Be sure to get it again, do not use pathPathList, it will report an error
                setField(pathList, pathList.getClass(), "dexElements", dexElements);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reflect to re-assign the properties in the object
     */
    private static void setField(Object obj, Class<?> cl, String field, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field declaredField = cl.getDeclaredField(field);
        declaredField.setAccessible(true);
        declaredField.set(obj, value);
    }

    /**
     * Reflect to get the attribute value in the object
     */
    private static Object getField(Object obj, Class<?> cl, String field) throws NoSuchFieldException, IllegalAccessException {
        Field localField = cl.getDeclaredField(field);
        localField.setAccessible(true);
        return localField.get(obj);
    }


    /**
     * Reflection to get the pathList object in the class loader
     */
    private static Object getPathList(Object baseDexClassLoader) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        return getField(baseDexClassLoader, Class.forName("dalvik.system.BaseDexClassLoader"), "pathList");
    }

    /**
     * Reflect to get the dexElements in the pathList
     */
    private static Object getDexElements(Object pathList) throws NoSuchFieldException, IllegalAccessException {
        return getField(pathList, pathList.getClass(), "dexElements");
    }

    /**
     * Array merge
     */
    private static Object combineArray(Object arrayLhs, Object arrayRhs) {
        Class<?> clazz = arrayLhs.getClass().getComponentType();
        int i = Array.getLength(arrayLhs); //Get the length of the left array (patch array)
        int j = Array.getLength(arrayRhs); //Get the length of the original dex array
        int k = i + j; //Get the total group length (Patch array + original dex array)
        Object result = Array.newInstance(clazz, k); //Create a new array of type clazz and length k
        System.arraycopy(arrayLhs, 0, result, 0, i);
        System.arraycopy(arrayRhs, 0, result, i, j);
        return result;
    }

    public static void getDexfilesOfCurrentApp(Context context) {
        String sourceDir = context.getApplicationInfo().sourceDir;
        try {
            DexFile dexFile = new DexFile(sourceDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
