package vn.ecohome.pos0210

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory

class PosApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(OfflineVietQrFetcher.Factory())
        }
        .build()
}
