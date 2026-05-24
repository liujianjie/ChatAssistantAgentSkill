package com.stylemirror.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StyleMirrorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // PdfBox-Android needs this once before any PDDocument.load — it sets
        // up the embedded font resources used by PDFTextStripper. Calling it
        // here means the onboarding import flow can extract PDF text without
        // the caller worrying about init order.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
