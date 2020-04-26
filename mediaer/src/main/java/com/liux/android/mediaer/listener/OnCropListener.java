package com.liux.android.mediaer.listener;

import android.net.Uri;

/**
 * 裁剪结果回调
 */
public interface OnCropListener extends OnFailureListener {

    void onSucceed(Uri output);
}
