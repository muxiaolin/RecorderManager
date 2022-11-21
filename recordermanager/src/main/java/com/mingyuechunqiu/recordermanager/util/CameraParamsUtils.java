package com.mingyuechunqiu.recordermanager.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * <pre>
 *     author : xyj
 *     Github : https://github.com/MingYueChunQiu
 *     e-mail : xiyujieit@163.com
 *     time   : 2019/3/8
 *     desc   : 相机参数工具类
 *     version: 1.0
 * </pre>
 */
public class CameraParamsUtils {

    private static volatile CameraParamsUtils sUtils;

    private SizeComparator mSizeComparator;

    public static CameraParamsUtils getInstance() {
        if (sUtils == null) {
            synchronized (CameraParamsUtils.class) {
                if (sUtils == null) {
                    sUtils = new CameraParamsUtils();
                }
            }
        }
        return sUtils;
    }

    private CameraParamsUtils() {
        mSizeComparator = new SizeComparator();
    }

    /**
     * 找出最大像素组合
     *
     * @param cameraSizes
     * @return
     */
    public static Camera.Size findMaxCameraSize(List<Camera.Size> cameraSizes) {
        // 按照分辨率从大到小排序
        List<Camera.Size> supportedResolutions = new ArrayList<>(cameraSizes);
        Collections.sort(supportedResolutions, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });
        return supportedResolutions.get(0);
    }

    /**
     * 获取支持的合适大小
     *
     * @param list  相机大小集合
     * @param ratio 视频宽高比例
     * @return 如果获取成功返回宽高对，否则返回null
     */
    @Nullable
    public Pair<Integer, Integer> getSupportSize(List<Camera.Size> list, float ratio) {
        if (list == null || list.size() == 0) {
            return null;
        }
        //进行降序排序
        Collections.sort(list, mSizeComparator);
        int selectedIndex = 0;//选中分辨率索引位置
        float ratioDelta = 0;//选中的分辨率比例与视频比例之间的差值
        for (int i = 0, count = list.size(); i < count; i++) {
            Camera.Size size = list.get(i);
            float currentRatioDelta = Math.abs(size.width * 1.0f / size.height - ratio);
            //如果是起始位置或者当前比例差值小于前面比例差值，更新选中项
            if (i == 0 || (currentRatioDelta < ratioDelta)) {
                ratioDelta = currentRatioDelta;
                selectedIndex = i;
            }
        }
        if (ratio <= 0) {
            selectedIndex = 0;
        }
        return new Pair<>(list.get(selectedIndex).width, list.get(selectedIndex).height);
    }

    /**
     * 获取支持的合适大小
     *
     * @param list      相机大小集合
     * @param minWidth  最小宽度
     * @param minHeight 最小高度
     * @return 如果获取成功返回宽高对，否则返回null
     */
    @Nullable
    public Pair<Integer, Integer> getSupportSize(List<Camera.Size> list, int minWidth, int minHeight) {
        if (list == null || list.size() == 0) {
            return null;
        }
        //进行降序排序
        Collections.sort(list, mSizeComparator);
        for (Camera.Size size : list) {
            if (size.width >= minWidth && size.height >= minHeight) {
                return new Pair<>(size.width, size.height);
            }
        }
        return null;
    }

    /**
     * 找出最适合的分辨率
     *
     * @param cameraSizes
     * @param screenResolution 屏幕分辨率
     * @param isPictureSize
     * @param maxDistortion
     * @return
     */
    public Point findBestResolution(List<Camera.Size> cameraSizes, Point screenResolution, boolean isPictureSize, float maxDistortion) {
        //默认分辩率
        Point defaultResolution;
        if (isPictureSize) {
            defaultResolution = new Point(2000, 1500);
        } else {
            defaultResolution = new Point(1920, 1080);
        }
        if (cameraSizes == null) {
            return defaultResolution;
        }
        // 按照分辨率从大到小排序
        List<Camera.Size> supportedResolutions = new ArrayList<>(cameraSizes);
        Collections.sort(supportedResolutions, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });

        if (supportedResolutions.size() > 0) {
            defaultResolution.x = supportedResolutions.get(0).width;
            defaultResolution.y = supportedResolutions.get(0).height;
        }

        // 移除不符合条件的分辨率
        double screenAspectRatio = (double) screenResolution.x / (double) screenResolution.y;
//        Log.d("CameraParameters", "screenAspectRatio=" + screenAspectRatio);
        Iterator<Camera.Size> it = supportedResolutions.iterator();
        while (it.hasNext()) {
            Camera.Size supportedResolution = it.next();
            int width = supportedResolution.width;
            int height = supportedResolution.height;
            // 移除低于下限的分辨率，尽可能取高分辨率
            if (isPictureSize) {
                if (width * height < 2000 * 1500) {
                    it.remove();
                    continue;
                }
            } else {
                if (width * height < 1280 * 720) {
                    it.remove();
                    continue;
                }
            }

            /**
             * 在camera分辨率与屏幕分辨率宽高比不相等的情况下，找出差距最小的一组分辨率
             * 由于camera的分辨率是width>height，我们设置的portrait模式中，width<height
             * 因此这里要先交换然preview宽高比后在比较
             */
            boolean isCandidatePortrait = width > height;
            int maybeFlippedWidth = isCandidatePortrait ? height : width;
            int maybeFlippedHeight = isCandidatePortrait ? width : height;
            double aspectRatio = (double) maybeFlippedWidth / (double) maybeFlippedHeight;
            double distortion = Math.abs(aspectRatio - screenAspectRatio);
//            Log.d("CameraParameters", "aspectRatio=" + aspectRatio + ", distortion=" + distortion);
            if (distortion > maxDistortion) {
                it.remove();
                continue;
            }
            // 找到与屏幕分辨率完全匹配的预览界面分辨率直接返回
            if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y) {
                Point exactPoint = new Point(width, height);
                return exactPoint;
            }
        }

        // 如果没有找到合适的，并且还有候选的像素，则设置其中最大尺寸的，对于配置比较低的机器不太合适
        if (!supportedResolutions.isEmpty()) {
            Camera.Size largestPreview = supportedResolutions.get(0);
            Point largestSize = new Point(largestPreview.width, largestPreview.height);
            return largestSize;
        }
        // 没有找到合适的，就返回默认的
        return defaultResolution;
    }

    /**
     * 设置相机显示方向
     *
     * @param rotation 屏幕方向
     * @param cameraId
     * @return
     */
    public int getCameraDisplayOrientation(int rotation, int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int degree = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degree = 0;
                break;
            case Surface.ROTATION_90:
                degree = 90;
                break;
            case Surface.ROTATION_180:
                degree = 180;
                break;
            case Surface.ROTATION_270:
                degree = 270;
                break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degree) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degree + 360) % 360;
        }
        return result;
    }

    public void release() {
        mSizeComparator = null;
        sUtils = null;
    }

    /**
     * 大小比较器，以宽度为准
     */
    private static class SizeComparator implements Comparator<Camera.Size> {

        @Override
        public int compare(Camera.Size o1, Camera.Size o2) {
            //降序
            return (o2.width < o1.width) ? -1 : ((o2.width == o1.width) ? 0 : 1);
        }
    }
}
