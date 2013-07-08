/*
 * Copyright 2013 Jacek Marchwicki <jacek.marchwicki@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.examplescroll;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.appunite.imageloader.RemoteImageLoader;
import com.appunite.scroll.ScaleImageView;

public class ImageFragment extends Fragment {
    private static final String ARG_IMAGE = "image";
    private String mImageUrl;
    private ScaleImageView mImageView;

    public static Fragment newInstance(String image) {
        Bundle args = new Bundle();
        args.putString(ARG_IMAGE, image);
        final ImageFragment fragment = new ImageFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle args = getArguments();
        mImageUrl = args.getString(ARG_IMAGE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final MainActivity activity = (MainActivity) getActivity();
        final RemoteImageLoader remoteImageLoader = activity.getFullRemoteImageLoader();
        final View view = inflater.inflate(R.layout.image_fragment, container, false);
        assert view != null;
        mImageView = (ScaleImageView) view.findViewById(android.R.id.icon);
        mImageView.setAllowParentHorizontalScroll(true);

        final Resources resources = activity.getResources();
        final Drawable placeholderDrawable = resources.getDrawable(R.drawable.ic_send);
        final Drawable failDrawable = resources.getDrawable(R.drawable.img_thumb_error);
        final RemoteImageLoader.ImageHolder imageHolder = new RemoteImageLoader.ImageHolder() {

            @Override
            public void setRemoteBitmap(Bitmap bitmap, boolean b) {
                if (bitmap == null) {
                    mImageView.setSrcDrawable(null);
                } else {
                    mImageView.setSrcDrawable(new BitmapDrawable(resources, bitmap));
                }
            }

            @Override
            public void failDownloading(boolean b) {
                mImageView.setSrcDrawable(failDrawable);
            }

            @Override
            public void setPlaceholder(boolean b) {
                mImageView.setSrcDrawable(placeholderDrawable);
            }
        };
        remoteImageLoader.loadImage(imageHolder, mImageUrl);

        return view;
    }
}