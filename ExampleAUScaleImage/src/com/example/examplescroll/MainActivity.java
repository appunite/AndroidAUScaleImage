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

import android.annotation.TargetApi;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.view.Display;

import com.appunite.imageloader.DiskCache;
import com.appunite.imageloader.MemoryCache;
import com.appunite.imageloader.RemoteImageLoader;

public class MainActivity extends FragmentActivity {

    private static final int NUMBER_OF_SCREENS_IN_MEMORY = 3;

    private ViewPager mViewPager;
    private ImagePagerAdapter mAdapter;
    private RemoteImageLoader mFullRemoteImageLoader;
    private DiskCache mDiskCache;
    private MemoryCache mMemoryCache;

    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_activity);

        FragmentManager fm = getSupportFragmentManager();
        mAdapter = new ImagePagerAdapter(fm);
        mViewPager = (ViewPager) findViewById(R.id.posts_pager);
        mViewPager.setAdapter(mAdapter);
	}

    @Override
    public void onResume() {
        super.onResume();

        getFullRemoteImageLoader().onActivityResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        getFullRemoteImageLoader().onActivityPause();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        getFullRemoteImageLoader().onActivityLowMemory();
    }

    public DiskCache getDiskCache() {
        if (mDiskCache == null) {
            return mDiskCache;
        }
        mDiskCache = new DiskCache(this, RemoteImageLoader.IMAGE_CACHE_DIR_PREFIX);
        return mDiskCache;
    }

    public MemoryCache getMemoryCache() {
        if (mMemoryCache == null) {
            return mMemoryCache;
        }

        Point displaySize = new Point();
        getDisplaySize(displaySize);
        int displayMemory = displaySize.x * displaySize.y * MemoryCache.BYTES_PER_PIXEL;
        int cacheSize = NUMBER_OF_SCREENS_IN_MEMORY * displayMemory;
        mMemoryCache = new MemoryCache(cacheSize);
        return mMemoryCache;
    }

    public RemoteImageLoader getFullRemoteImageLoader() {
        if (mFullRemoteImageLoader != null) {
            return mFullRemoteImageLoader;
        }

        Point displaySize = new Point();
        getDisplaySize(displaySize);
        mFullRemoteImageLoader = new RemoteImageLoader(this,
                getDiskCache(),
                getMemoryCache(),
                displaySize.y / 2,
                displaySize.x / 2);
        return mFullRemoteImageLoader;
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void getDisplaySize(Point displaySize) {
        Display display = getWindowManager().getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            display.getSize(displaySize);
        } else {
            displaySize.x = display.getWidth();
            displaySize.y = display.getHeight();
        }
    }
}
