/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Piasy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.github.piasy.biv.loader.glide;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.Headers;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.github.piasy.biv.loader.ImageLoader;
import com.github.piasy.biv.metadata.ImageInfoExtractor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;

/**
 * Created by Piasy{github.com/Piasy} on 09/11/2016.
 */

public class GlideImageLoader implements ImageLoader {
    protected final RequestManager mRequestManager;

    private final Map<Integer, ImageDownloadTarget> mFlyingRequestTargets = new HashMap<>(3);

    protected GlideImageLoader(Context context, OkHttpClient okHttpClient) {
        GlideProgressSupport.init(Glide.get(context), okHttpClient);
        mRequestManager = Glide.with(context);
    }

    public static GlideImageLoader with(Context context) {
        return with(context, null);
    }

    public static GlideImageLoader with(Context context, OkHttpClient okHttpClient) {
        return new GlideImageLoader(context, okHttpClient);
    }

    @Override
    public void loadImage(final int requestId, final Uri uri, final HashMap<String, String> headers, final Callback callback) {
        final boolean[] cacheMissed = new boolean[1];
        ImageDownloadTarget target = new ImageDownloadTarget(uri.toString()) {
            @Override
            public void onResourceReady(@NonNull File resource,
                                        Transition<? super File> transition) {
                super.onResourceReady(resource, transition);
                if (cacheMissed[0]) {
                    callback.onCacheMiss(ImageInfoExtractor.getImageType(resource), resource);
                } else {
                    callback.onCacheHit(ImageInfoExtractor.getImageType(resource), resource);
                }
                callback.onSuccess(resource);
            }

            @Override
            public void onLoadFailed(final Drawable errorDrawable) {
                super.onLoadFailed(errorDrawable);
                callback.onFail(new GlideLoaderException(errorDrawable));
            }

            @Override
            public void onDownloadStart() {
                cacheMissed[0] = true;
                callback.onStart();
            }

            @Override
            public void onProgress(int progress) {
                callback.onProgress(progress);
            }

            @Override
            public void onDownloadFinish() {
                callback.onFinish();
            }
        };
        cancel(requestId);
        rememberTarget(requestId, target);

        downloadImageInto(uri, headers, target);
    }

    @Override
    public void prefetch(Uri uri, HashMap<String, String> headers) {
        downloadImageInto(uri, headers, new PrefetchTarget());
    }

    @Override
    public synchronized void cancel(int requestId) {
        clearTarget(mFlyingRequestTargets.remove(requestId));
    }

    @Override
    public synchronized void cancelAll() {
        List<ImageDownloadTarget> targets = new ArrayList<>(mFlyingRequestTargets.values());
        for (ImageDownloadTarget target : targets) {
            clearTarget(target);
        }
    }

    protected void downloadImageInto(Uri uri, final HashMap<String, String> headers, Target<File> target) {
        Headers requestHeaders = new Headers() {
            @Override
            public Map<String, String> getHeaders() {
                return headers;
            }
        };
        mRequestManager
                .downloadOnly()
                .load(new GlideUrl(uri.toString(), requestHeaders))
                .into(target);
    }

    private synchronized void rememberTarget(int requestId, ImageDownloadTarget target) {
        mFlyingRequestTargets.put(requestId, target);
    }

    private void clearTarget(ImageDownloadTarget target) {
        if (target != null) {
            mRequestManager.clear(target);
        }
    }
}
