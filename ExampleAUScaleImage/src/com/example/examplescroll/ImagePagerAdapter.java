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

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class ImagePagerAdapter extends FragmentStatePagerAdapter {
    private static final String[] IMAGES = {
            "http://chivethebrigade.files.wordpress.com/2012/09/girls-2-lego-aircraft-carrier-920-0.jpg",
            "http://urduspoint.com/wp-content/uploads/2013/01/girls-920-16.jpg",
            "http://gembez.com/wp-content/uploads/2013/04/Beautiful-Girls-1920-%C3%97-1080-Wallpaper-HD.jpg",
            "http://malvarico.webs.com/photos/Bikini%20Girls%20(1).jpg",
            "http://wallpaper-million.com/Wallpapers/f/Girls/Curly-girl-and-a-bike-wallpaper_3344.jpg",
            "http://www.graffitistuck.com/images/937-wallpapers-hq-incredible-hd-wallpapers-girls-hd-wallpapers-34.jpg",
            "http://1.bp.blogspot.com/-An7cQEs0HSA/TtztSWDGEDI/AAAAAAAACqU/TrxTBTxoNhg/s1600/tattoo+girls+1.jpg",
            "http://files.myopera.com/Trynity34/albums/11355962/9375_asian_girls.jpg",
            "http://www.ngewall.com/images/2013/05/beautiful-wallpapers-beautiful-girls-wallpapers.jpg",
            "http://www.hdwallpapersarena.com/wp-content/uploads/2013/02/Desktop-Girls-Wallpapers-1080p1.jpg",
            "http://celebrityfanweb.com/blog/wp-content/uploads/2011/04/Beautiful-Girls-Color-HD-Celebrities-HQ-Wallpapers-40.jpg",
    };
    public ImagePagerAdapter(FragmentManager fm) {
        super(fm);
    }

    @Override
    public Fragment getItem(int i) {
        final String image = IMAGES[i];
        return ImageFragment.newInstance(image);
    }

    @Override
    public int getCount() {
        return IMAGES.length;
    }
}
