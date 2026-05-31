package com.stylemirror.app

import android.app.Application
import com.stylemirror.feature.overlay.candidate.OverlayCandidateController
import com.stylemirror.feature.overlay.service.FloatingBubbleService
import com.stylemirror.feature.realtime.candidate.CandidateGenerator
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StyleMirrorApp : Application() {
    @Inject lateinit var candidateGenerator: CandidateGenerator

    override fun onCreate() {
        super.onCreate()
        // PdfBox-Android needs this once before any PDDocument.load — it sets
        // up the embedded font resources used by PDFTextStripper. Calling it
        // here means the onboarding import flow can extract PDF text without
        // the caller worrying about init order.
        PDFBoxResourceLoader.init(applicationContext)

        // Install the P1.c overlay controller factory. Done in Application.onCreate
        // so it's in place by the time the user toggles the floating-bubble switch
        // in Settings — see FloatingBubbleService.controllerFactory KDoc.
        FloatingBubbleService.controllerFactory = { scope ->
            OverlayCandidateController(
                candidateGenerator = candidateGenerator,
                scope = scope,
            )
        }
    }
}
